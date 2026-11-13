package dev.shizzi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class SessionNotification(private val context: Context) {

    fun build(state: SessionUiState, isStopping: Boolean): Notification {
        createChannel()

        val text = bodyFor(state, isStopping)

        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(titleFor(state, isStopping))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent())
            .setOngoing(state.status != UiStatus.ERROR)
            .addAction(stopAction())
            .build()
    }

    /** A notice that a device joined or left the hotspot. */
    fun buildDeviceChange(joined: Boolean, count: Int): Notification {
        createAlertChannel()

        val title = if (joined) tr("A device connected") else tr("A device disconnected")
        val body = when (count) {
            0 -> tr("No devices connected")
            1 -> tr("1 device connected", "1 dispositivo conectado")
            else -> tr("$count devices connected", "$count dispositivos conectados")
        }

        return Notification.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .build()
    }

    private fun createAlertChannel() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            tr("Device alerts", "Avisos de dispositivos"),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = tr("When a device joins or leaves", "Cuando un dispositivo se conecta o desconecta") }

        notificationManager().createNotificationChannel(channel)
    }

    /** A one-off notice that a session limit ended sharing. */
    fun buildLimitReached(reason: LimitReason): Notification {
        createChannel()

        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(SessionLimits.describe(reason))
            .setContentText(tr("Sharing was stopped automatically.", "Se dejó de compartir automáticamente."))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
    }

    private fun titleFor(state: SessionUiState, isStopping: Boolean): String =
        when (state.status) {
            UiStatus.CONNECTED -> connectedTitle(state)
            UiStatus.LOADING -> if (isStopping) tr("Cleaning up…") else tr("Getting ready…")
            UiStatus.ERROR -> tr("Session ended")
            UiStatus.READY -> tr("Session ended")
        }

    private fun connectedTitle(state: SessionUiState): String = when {
        state.isWaitingForUsb -> tr("Waiting for USB")
        state.isVpnBypassed -> tr("Connected · VPN ignored")
        state.isVpnBound -> tr("Connected · VPN")
        else -> tr("Connected")
    }

    private fun bodyFor(state: SessionUiState, isStopping: Boolean): String = when {
        state.lastError.isNotEmpty() -> userFacingError(state.lastError)
        state.status == UiStatus.LOADING -> loadingBody(isStopping)
        state.isWaitingForUsb -> tr(
            "Connect over USB within $USB_GRACE_MINUTES minutes to keep sharing",
            "Conecta por USB en menos de $USB_GRACE_MINUTES minutos para seguir compartiendo",
        )
        state.status == UiStatus.CONNECTED -> connectedBody(state)
        else -> tr("Not sharing")
    }

    private fun userFacingError(raw: String): String = when {
        raw.contains(EXCEPTION_MARKER) -> tr("Something went wrong. Check the app for details.")
        else -> raw
    }

    private fun loadingBody(isStopping: Boolean): String =
        if (isStopping) tr("Turning the hotspot off…") else tr("Turning the hotspot on…")

    private fun connectedBody(state: SessionUiState): String = when (state.clientCount) {
        0 -> tr("No devices connected")
        else -> "${deviceCount(state.clientCount)} · " +
            "$DOWN_ARROW ${Traffic.format(state.traffic.down)} · " +
            "$UP_ARROW ${Traffic.format(state.traffic.up)}"
    }

    private fun deviceCount(clients: Int): String = when (clients) {
        1 -> tr("1 device")
        else -> tr("$clients devices", "$clients dispositivos")
    }

    fun describeLoss(problem: String?): String = when (problem) {
        null -> tr(
            "Shizuku stopped, so the session ended. The hotspot was turned off. " +
                "Start again when you are ready.",
        )

        else -> tr(
            "The session ended but the hotspot may still be on: $problem — " +
                "turn it off in Settings.",
            "La sesión terminó, pero el punto de acceso puede seguir encendido: $problem — " +
                "apágalo en Ajustes.",
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        null,
        tr("Stop"),
        PendingIntent.getService(
            context,
            1,
            Intent(context, SessionService::class.java).setAction(SessionService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            tr("Tethering session"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = tr("Shown while a tethering session is running") }

        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private companion object {
        const val CHANNEL_ID = "tethering-session"
        const val ALERT_CHANNEL_ID = "device-alerts"
        const val ALERT_TIMEOUT_MS = 60_000L

        const val EXCEPTION_MARKER = "Exception"

        const val DOWN_ARROW = "\u2193"
        const val UP_ARROW = "\u2191"

        const val USB_GRACE_MINUTES = UsbReconnect.GIVE_UP_AFTER_MS / 60_000
    }
}
