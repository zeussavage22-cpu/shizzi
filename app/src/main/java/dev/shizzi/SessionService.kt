package dev.shizzi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private val controller = TetherClient()
    private val notification by lazy { SessionNotification(this) }
    private val statusPoller = SessionStatusPoller(scope, controller)

    private val internalState get() = sessionState

    private var isStopping = false

    private var reportTo: AutomationCommand? = null

    private var startJob: Job? = null

    private var generation = 0

    private val sessionLock = Mutex()

    @Volatile
    private var timeLimitMinutes = 0

    @Volatile
    private var dataLimitMb = 0

    @Volatile
    private var batteryLimitPercent = 0

    private val usage by lazy { UsageRecorder(applicationContext) }

    /** Set once a limit has asked the session to stop, so it asks only once. */
    @Volatile
    private var isLimitStopRequested = false

    override fun onCreate() {
        super.onCreate()
        controller.onSessionLost = ::handleSessionLost
        liveService = this
        followLimits()
    }

    /** Keeps the user's session limits current, including changes made mid-session. */
    private fun followLimits() {
        scope.launch {
            settingsStore().settings.collect { settings ->
                timeLimitMinutes = settings.timeLimitMinutes
                dataLimitMb = settings.dataLimitMb
                batteryLimitPercent = settings.batteryLimitPercent
            }
        }
    }

    /** Stops the session the same way the notification's Stop button does once a limit is hit. */
    private fun enforceLimits() {
        if (isLimitStopRequested || isStopping) return

        val battery = readBattery()
        val reason = SessionLimits.reached(
            timeLimitMinutes = timeLimitMinutes,
            dataLimitMb = dataLimitMb,
            state = internalState.value,
            now = System.currentTimeMillis(),
            batteryLimitPercent = batteryLimitPercent,
            batteryPercent = battery.first,
            isCharging = battery.second,
        ) ?: return

        isLimitStopRequested = true
        SessionLog.info("session limit reached: $reason; stopping")
        notificationManager().notify(
            LIMIT_NOTIFICATION_ID,
            notification.buildLimitReached(reason),
        )
        runCatching { stop(this) }
            .onFailure { SessionLog.error("could not stop at limit: ${it.message}") }
    }

    /** Battery percent (or -1 if unknown) and whether the phone is charging. */
    private fun readBattery(): Pair<Int, Boolean> {
        if (batteryLimitPercent <= 0) return -1 to false
        return runCatching {
            val manager = getSystemService(BatteryManager::class.java)
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to manager.isCharging
        }.getOrDefault(-1 to false)
    }

    /** Records usage history; never lets a history problem disturb the session. */
    private fun recordUsage() {
        runCatching { usage.observe(internalState.value) }
    }

    private fun finishUsage() {
        runCatching { usage.finish() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isStopping = intent?.action == ACTION_STOP
        reportTo = intent?.getStringExtra(EXTRA_REPORT_AS)
            ?.let { runCatching { AutomationCommand.valueOf(it) }.getOrNull() }
        startForeground(
            NOTIFICATION_ID,
            notification.build(
                SessionUiState(status = UiStatus.LOADING),
                isStopping,
            ),
        )

        when {
            isStopping -> stopSession()
            else -> startSession()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSession() {
        if (internalState.value.isBusy) {
            announceOutcome()
            return
        }
        val attempt = ++generation
        isLimitStopRequested = false
        internalState.update {
            it.copy(isBusy = true, status = UiStatus.LOADING, lastError = "", detail = "")
        }

        startJob = scope.launch {

            val settings = settingsStore().settings.first()

            val outcome = sessionLock.withLock {
                if (attempt != generation) return@launch
                runCatching {
                    controller.start(settings.isLogging, settings.vpnMode, settings.usbTethering)
                }
            }
            if (attempt != generation) return@launch

            outcome.exceptionOrNull()?.let { failure ->
                SessionLog.error(
                    "start failed in the app process: " +
                        "${failure.javaClass.name}: ${failure.message}",
                )
            }

            internalState.update { current ->
                current.applyOutcome(outcome).stampConnectedSince(System.currentTimeMillis())
            }
            publishState()
            recordUsage()
            announceOutcome()
            followStatus()
        }
    }

    private fun followStatus() = statusPoller.follow(
        isConnected = { internalState.value.status == UiStatus.CONNECTED },
        onStatus = { outcome ->
            internalState.update { current ->
                current.applyOutcome(outcome).stampConnectedSince(System.currentTimeMillis())
            }
            publishState()
            recordUsage()
            if (internalState.value.status == UiStatus.READY) finishEndedSession()
            enforceLimits()
        },
    )

    /** The session stopped on its own, as a stop would; release it the same way. */
    private fun finishEndedSession() {
        val ended = generation
        finishUsage()

        scope.launch {
            sessionLock.withLock { controller.unbindAndStopDaemon() }
            if (ended == generation) stopSelf()
        }
    }

    private fun settingsStore(): SettingsStore =
        (application as App).settingsStore

    private fun stopSession() {
        val stopped = ++generation

        val abandoned = startJob
        startJob = null

        statusPoller.stop()
        finishUsage()

        internalState.update {
            it.asStopped()
        }

        publishState()

        scope.launch {

            abandoned?.cancelAndJoin()

            sessionLock.withLock {
                runCatching { controller.stop() }
                    .onFailure { SessionLog.error("teardown failed: ${it.message}") }

                controller.unbindAndStopDaemon()
            }

            announceOutcome()

            if (stopped == generation) stopSelf()
        }
    }

    private fun handleSessionLost() {
        statusPoller.stop()
        finishUsage()

        isStopping = false
        SessionLog.error("shell process died; recovering any downstream it left up")

        internalState.update {
            it.copy(
                isBusy = false,
                status = UiStatus.ERROR,
                interfaceName = "",
                lastError = tr("Session ended unexpectedly — dropping the hotspot…"),
            )
        }
        publishState()

        scope.launch {
            val problem = runCatching { controller.releaseOrphanedDownstream() }
                .getOrElse { failure -> "teardown failed: ${failure.message}" }

            when (problem) {
                null -> SessionLog.info("orphan recovery: hotspot dropped")
                else -> SessionLog.error("orphan recovery failed: $problem")
            }

            internalState.update { current -> current.copy(lastError = notification.describeLoss(problem)) }
            publishState()
            stopSelf()
        }
    }

    private fun announceOutcome() {
        val command = reportTo ?: return
        reportTo = null
        AutomationResult.announce(this, command, internalState.value)
    }

    private fun publishState() {
        notificationManager().notify(
            NOTIFICATION_ID,
            notification.build(internalState.value, isStopping),
        )
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        finishUsage()
        controller.onSessionLost = null
        controller.unbind()
        scope.cancel()
        liveService = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val LIMIT_NOTIFICATION_ID = 2
        const val ACTION_STOP = "dev.shizzi.STOP_SESSION"
        const val EXTRA_REPORT_AS = "reportAs"

        private val sessionState = MutableStateFlow(SessionUiState())

        val liveState: StateFlow<SessionUiState> = sessionState.asStateFlow()

        private var liveService: SessionService? = null

        val isRunning: Boolean get() = liveService != null

        val isSessionUp: Boolean get() = liveState.value.status == UiStatus.CONNECTED

        val isSessionBusy: Boolean get() = liveState.value.status == UiStatus.LOADING

        fun start(context: Context, reportAs: AutomationCommand? = null) {
            context.startForegroundService(
                Intent(context, SessionService::class.java).reporting(reportAs),
            )
        }

        fun stop(context: Context, reportAs: AutomationCommand? = null) {
            context.startForegroundService(
                Intent(context, SessionService::class.java)
                    .setAction(ACTION_STOP)
                    .reporting(reportAs),
            )
        }

        private fun Intent.reporting(command: AutomationCommand?): Intent = when (command) {
            null -> this
            else -> putExtra(EXTRA_REPORT_AS, command.name)
        }
    }
}
