package dev.shizzi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

enum class SpeedPhase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, FAILED }

data class SpeedTestState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    /** Live speed of the running phase, in megabits per second. */
    val liveMbps: Double = 0.0,
    val pingMs: Long? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    /** 0..1 progress of the whole test. */
    val progress: Float = 0f,
    val error: String = "",
) {
    val isRunning: Boolean get() = phase == SpeedPhase.PING || phase == SpeedPhase.DOWNLOAD || phase == SpeedPhase.UPLOAD
}

/** Converts [bytes] moved in [millis] to megabits per second. */
fun mbps(bytes: Long, millis: Long): Double =
    if (millis <= 0) 0.0 else bytes * 8.0 / 1_000_000.0 / (millis / 1000.0)

/**
 * Measures this phone's own internet speed against Cloudflare's public speed test endpoints:
 * latency, then download and upload over several parallel connections for a few seconds each.
 */
object SpeedTest {

    private const val BASE = "https://speed.cloudflare.com"
    private const val PING_SAMPLES = 5
    private const val STREAMS = 4
    private const val PHASE_MS = 8_000L
    private const val DOWNLOAD_CHUNK = 50_000_000L
    private const val UPLOAD_CHUNK = 8_000_000
    private const val TIMEOUT_MS = 10_000

    private val mutable = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = mutable.asStateFlow()

    suspend fun run() {
        if (mutable.value.isRunning) return
        mutable.value = SpeedTestState(phase = SpeedPhase.PING)

        try {
            val ping = withContext(Dispatchers.IO) { measurePing() }
            mutable.value = mutable.value.copy(pingMs = ping, progress = 0.1f, phase = SpeedPhase.DOWNLOAD)

            val down = measureThroughput(upload = false, progressFrom = 0.1f, progressTo = 0.55f)
            mutable.value = mutable.value.copy(downloadMbps = down, phase = SpeedPhase.UPLOAD, liveMbps = 0.0)

            val up = measureThroughput(upload = true, progressFrom = 0.55f, progressTo = 1f)
            mutable.value = mutable.value.copy(uploadMbps = up, phase = SpeedPhase.DONE, liveMbps = 0.0, progress = 1f)
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) {
                mutable.value = SpeedTestState()
                throw failure
            }
            mutable.value = mutable.value.copy(
                phase = SpeedPhase.FAILED,
                liveMbps = 0.0,
                error = failure.message ?: failure.javaClass.simpleName,
            )
        }
    }

    private fun measurePing(): Long {
        val samples = (0 until PING_SAMPLES).mapNotNull {
            runCatching {
                val started = System.nanoTime()
                open("$BASE/__down?bytes=0").apply {
                    inputStream.use { it.readBytes() }
                    disconnect()
                }
                (System.nanoTime() - started) / 1_000_000
            }.getOrNull()
        }
        check(samples.isNotEmpty()) { "No connection" }
        // The first request also pays for DNS and TLS, so take the best of the rest.
        return (if (samples.size > 1) samples.drop(1) else samples).min()
    }

    private suspend fun measureThroughput(upload: Boolean, progressFrom: Float, progressTo: Float): Double =
        coroutineScope {
            val moved = AtomicLong(0)
            val started = System.currentTimeMillis()
            val deadline = started + PHASE_MS

            val reporter = launch {
                var lastBytes = 0L
                var lastAt = started
                while (isActive) {
                    delay(250)
                    val now = System.currentTimeMillis()
                    val bytes = moved.get()
                    val live = mbps(bytes - lastBytes, now - lastAt)
                    lastBytes = bytes
                    lastAt = now
                    val fraction = ((now - started).toFloat() / PHASE_MS).coerceIn(0f, 1f)
                    mutable.value = mutable.value.copy(
                        liveMbps = live,
                        progress = progressFrom + (progressTo - progressFrom) * fraction,
                    )
                }
            }

            val workers = (0 until STREAMS).map {
                async(Dispatchers.IO) {
                    while (System.currentTimeMillis() < deadline) {
                        runCatching { if (upload) uploadOnce(moved, deadline) else downloadOnce(moved, deadline) }
                            .onFailure { if (moved.get() == 0L) throw it }
                    }
                }
            }
            workers.awaitAll()
            reporter.cancel()

            val total = mbps(moved.get(), System.currentTimeMillis() - started)
            check(moved.get() > 0) { "No data transferred" }
            total
        }

    private fun downloadOnce(moved: AtomicLong, deadline: Long) {
        val connection = open("$BASE/__down?bytes=$DOWNLOAD_CHUNK")
        try {
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (System.currentTimeMillis() < deadline) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    moved.addAndGet(read.toLong())
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private val uploadPayload: ByteArray by lazy { Random.nextBytes(64 * 1024) }

    private fun uploadOnce(moved: AtomicLong, deadline: Long) {
        val connection = open("$BASE/__up").apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(UPLOAD_CHUNK)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            connection.outputStream.use { output ->
                var sent = 0
                while (sent < UPLOAD_CHUNK) {
                    val size = minOf(uploadPayload.size, UPLOAD_CHUNK - sent)
                    output.write(uploadPayload, 0, size)
                    sent += size
                    moved.addAndGet(size.toLong())
                    if (System.currentTimeMillis() >= deadline) break
                }
            }
            if (sent >= UPLOAD_CHUNK) runCatching { connection.inputStream.use { it.readBytes() } }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
}
