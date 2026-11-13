package dev.shizzi

import android.app.Application
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        SessionLog.useAppStorage(filesDir)
        applyStoredSettings()

        liftHiddenApiRestrictions()
        followSchedule()
    }

    /** Re-arms the daily schedule whenever its settings change (and once at startup). */
    private fun followSchedule() {
        CoroutineScope(Dispatchers.Default).launch {
            settingsStore.settings
                .map { Triple(it.scheduleEnabled, it.scheduleStartMinute, it.scheduleStopMinute) }
                .distinctUntilChanged()
                .collect { runCatching { Schedule.sync(this@App, settingsStore.settings.first()) } }
        }
    }

    private fun applyStoredSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            val isLogging = settingsStore.settings.first().isLogging
            SessionLog.setEnabled(isLogging)

            settingsStore.backfillTokenIfEnabled()
        }
    }

    private fun liftHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "L")
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
