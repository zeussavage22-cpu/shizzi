package dev.shizzi

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One finished sharing session. */
data class SessionRecord(
    val start: Long,
    val end: Long,
    val bytes: Long,
    val peakDevices: Int,
) {
    val durationMs: Long get() = (end - start).coerceAtLeast(0)
}

/**
 * Data shared through the hotspot, per day (ISO date -> bytes) plus the most recent sessions.
 *
 * Immutable and free of Android types so it can be unit tested on the JVM.
 */
data class UsageLedger(
    val days: Map<String, Long> = emptyMap(),
    val sessions: List<SessionRecord> = emptyList(),
) {

    fun addBytes(day: LocalDate, bytes: Long): UsageLedger {
        if (bytes <= 0) return this
        val key = day.toString()
        return copy(days = days + (key to (days[key] ?: 0L) + bytes))
    }

    fun addSession(record: SessionRecord): UsageLedger =
        copy(sessions = (listOf(record) + sessions).take(MAX_SESSIONS))

    /** Drops days older than [keepDays] before [today]. */
    fun pruned(today: LocalDate): UsageLedger {
        val oldest = today.minusDays(KEEP_DAYS.toLong())
        val kept = days.filterKeys { key ->
            runCatching { !LocalDate.parse(key).isBefore(oldest) }.getOrDefault(false)
        }
        return if (kept.size == days.size) this else copy(days = kept)
    }

    fun bytesOn(day: LocalDate): Long = days[day.toString()] ?: 0L

    /** Total of the [count] days ending on [today], today included. */
    fun lastDays(today: LocalDate, count: Int): Long =
        (0 until count).sumOf { bytesOn(today.minusDays(it.toLong())) }

    fun month(today: LocalDate): Long = days.entries.sumOf { (key, bytes) ->
        val day = runCatching { LocalDate.parse(key) }.getOrNull()
        if (day != null && day.year == today.year && day.month == today.month) bytes else 0L
    }

    /** Bytes per day for the [count] days ending on [today], oldest first. */
    fun series(today: LocalDate, count: Int): List<Pair<LocalDate, Long>> =
        (count - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            day to bytesOn(day)
        }

    fun toJson(): String {
        val root = JSONObject()
        val dayJson = JSONObject()
        days.forEach { (key, bytes) -> dayJson.put(key, bytes) }
        root.put("days", dayJson)

        val sessionJson = JSONArray()
        sessions.forEach { s ->
            sessionJson.put(
                JSONObject()
                    .put("start", s.start)
                    .put("end", s.end)
                    .put("bytes", s.bytes)
                    .put("devices", s.peakDevices),
            )
        }
        root.put("sessions", sessionJson)
        return root.toString()
    }

    companion object {
        const val KEEP_DAYS = 120
        const val MAX_SESSIONS = 50

        fun fromJson(raw: String?): UsageLedger {
            if (raw.isNullOrBlank()) return UsageLedger()
            return runCatching {
                val root = JSONObject(raw)
                val days = mutableMapOf<String, Long>()
                root.optJSONObject("days")?.let { obj ->
                    obj.keys().forEach { key -> days[key] = obj.optLong(key) }
                }
                val sessions = mutableListOf<SessionRecord>()
                root.optJSONArray("sessions")?.let { array ->
                    for (i in 0 until array.length()) {
                        val s = array.optJSONObject(i) ?: continue
                        sessions += SessionRecord(
                            start = s.optLong("start"),
                            end = s.optLong("end"),
                            bytes = s.optLong("bytes"),
                            peakDevices = s.optInt("devices"),
                        )
                    }
                }
                UsageLedger(days, sessions)
            }.getOrDefault(UsageLedger())
        }

        fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }
}

/** App-wide usage history, persisted in a private preferences file. */
object UsageHistory {

    private const val PREFS = "usage_history"
    private const val KEY = "ledger"

    private val state = MutableStateFlow(UsageLedger())
    val ledger: StateFlow<UsageLedger> = state.asStateFlow()

    @Volatile
    private var isLoaded = false

    fun load(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            val raw = runCatching { prefs(context).getString(KEY, null) }.getOrNull()
            state.value = UsageLedger.fromJson(raw).pruned(LocalDate.now())
            isLoaded = true
        }
    }

    fun addBytes(context: Context, bytes: Long, now: Long = System.currentTimeMillis()) {
        load(context)
        state.update { it.addBytes(UsageLedger.dayOf(now), bytes) }
    }

    fun addSession(context: Context, record: SessionRecord) {
        load(context)
        state.update { it.addSession(record) }
    }

    fun save(context: Context) {
        load(context)
        val json = state.value.pruned(LocalDate.now()).toJson()
        runCatching { prefs(context).edit().putString(KEY, json).apply() }
    }

    fun clear(context: Context) {
        load(context)
        state.value = UsageLedger()
        runCatching { prefs(context).edit().remove(KEY).apply() }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Turns the live session state into history entries: adds the new bytes to today's total as
 * they arrive (so a crash loses at most a few seconds) and records the session when it ends.
 */
class UsageRecorder(private val context: Context) {

    private var sessionStart = 0L
    private var countedBytes = 0L
    private var peakDevices = 0
    private var lastSavedAt = 0L

    @Synchronized
    fun observe(state: SessionUiState, now: Long = System.currentTimeMillis()) {
        if (state.status != UiStatus.CONNECTED) return

        if (sessionStart == 0L) {
            sessionStart = if (state.connectedSince > 0) state.connectedSince else now
            countedBytes = 0L
            peakDevices = 0
        }

        val total = state.traffic.up + state.traffic.down
        val delta = if (total >= countedBytes) total - countedBytes else total
        if (delta > 0) UsageHistory.addBytes(context, delta, now)
        countedBytes = total
        peakDevices = maxOf(peakDevices, state.clientCount)

        if (now - lastSavedAt >= SAVE_EVERY_MS) {
            lastSavedAt = now
            UsageHistory.save(context)
        }
    }

    @Synchronized
    fun finish(now: Long = System.currentTimeMillis()) {
        if (sessionStart == 0L) return

        UsageHistory.addSession(
            context,
            SessionRecord(
                start = sessionStart,
                end = now,
                bytes = countedBytes,
                peakDevices = peakDevices,
            ),
        )
        UsageHistory.save(context)
        sessionStart = 0L
        countedBytes = 0L
        peakDevices = 0
    }

    private companion object {
        const val SAVE_EVERY_MS = 30_000L
    }
}
