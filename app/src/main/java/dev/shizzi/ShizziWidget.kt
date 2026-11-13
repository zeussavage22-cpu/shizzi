package dev.shizzi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home screen widget: shows whether sharing is on, devices and data, and toggles it on tap
 * (the same start/stop the notification and quick settings tile use).
 */
class ShizziWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context, SessionService.liveState.value)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {

        /** Pushes [state] to every placed widget; cheap no-op when none is placed. */
        fun refresh(context: Context, state: SessionUiState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ShizziWidget::class.java))
            if (ids.isEmpty()) return

            val views = render(context, state)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun render(context: Context, state: SessionUiState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_shizzi)
            val isOn = state.status == UiStatus.CONNECTED

            views.setTextViewText(R.id.widget_title, title(state))
            views.setTextViewText(R.id.widget_subtitle, subtitle(state))
            views.setInt(
                R.id.widget_icon,
                "setBackgroundResource",
                if (isOn) R.drawable.widget_icon_on else R.drawable.widget_icon_off,
            )
            views.setOnClickPendingIntent(R.id.widget_root, clickIntent(context, state))
            return views
        }

        private fun title(state: SessionUiState): String = when (state.status) {
            UiStatus.CONNECTED -> if (state.isWaitingForUsb) tr("Waiting for USB") else tr("Sharing")
            UiStatus.LOADING -> tr("Starting…")
            UiStatus.ERROR -> tr("Session ended")
            UiStatus.READY -> "Shizzi"
        }

        private fun subtitle(state: SessionUiState): String = when (state.status) {
            UiStatus.CONNECTED -> {
                val devices = when (state.clientCount) {
                    1 -> tr("1 device")
                    else -> tr("${state.clientCount} devices", "${state.clientCount} dispositivos")
                }
                "$devices · ${Traffic.format(state.traffic.up + state.traffic.down)}"
            }
            UiStatus.LOADING -> ""
            else -> tr("Tap to share")
        }

        private fun clickIntent(context: Context, state: SessionUiState): PendingIntent {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val service = Intent(context, SessionService::class.java)

            return when (state.status) {
                UiStatus.CONNECTED -> PendingIntent.getForegroundService(
                    context,
                    REQUEST_STOP,
                    service.setAction(SessionService.ACTION_STOP),
                    flags,
                )

                UiStatus.LOADING -> openApp(context, flags)

                else -> if (ShizukuGate.currentState() is ShizukuState.Ready) {
                    PendingIntent.getForegroundService(context, REQUEST_START, service, flags)
                } else {
                    openApp(context, flags)
                }
            }
        }

        private fun openApp(context: Context, flags: Int): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags,
        )

        private const val REQUEST_START = 51
        private const val REQUEST_STOP = 52
        private const val REQUEST_OPEN = 53
    }
}
