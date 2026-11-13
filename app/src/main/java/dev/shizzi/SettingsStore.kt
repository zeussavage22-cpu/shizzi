package dev.shizzi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ThemeChoice
import dev.shizzi.ui.theme.parseAccent
import dev.shizzi.ui.theme.parseAccents
import dev.shizzi.ui.theme.serialize
import dev.shizzi.ui.theme.serializeAccents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val design: DesignLanguage = DesignLanguage.MATERIAL_EXPRESSIVE,
    val accent: AccentChoice = AccentChoice.Default,
    val customAccents: List<Int> = emptyList(),
    val isLogging: Boolean = true,

    val vpnMode: VpnMode = VpnMode.AUTO,
    val usbTethering: UsbTethering = UsbTethering.NEVER,

    val hasCompletedOnboarding: Boolean = false,

    val isAutomationEnabled: Boolean = false,
    val automationToken: String = "",

    /** Stop sharing after this many minutes. 0 = off (the default). */
    val timeLimitMinutes: Int = 0,
    /** Stop sharing once this many megabytes have been used. 0 = off (the default). */
    val dataLimitMb: Int = 0,
    /** Stop sharing when the battery drops to this percent while not charging. 0 = off. */
    val batteryLimitPercent: Int = 0,
    /** Stop sharing after this many minutes with no device connected. 0 = off. */
    val idleStopMinutes: Int = 0,

    /** Daily automatic start/stop. */
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinute: Int = DEFAULT_SCHEDULE_START,
    val scheduleStopMinute: Int = DEFAULT_SCHEDULE_STOP,

    /** Notify when a device joins or leaves the hotspot. */
    val notifyDevices: Boolean = false,
)

const val DEFAULT_SCHEDULE_START = 8 * 60
const val DEFAULT_SCHEDULE_STOP = 22 * 60

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(RenamedKeys.migration()) },
)

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map(::toSettings)

    suspend fun backfillTokenIfEnabled() {
        context.dataStore.edit { preferences ->
            if (preferences[AUTOMATION] != true) return@edit

            preferences.mintTokenIfAbsent()
        }
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[THEME] = choice.name }
    }

    suspend fun setDesign(design: DesignLanguage) {
        context.dataStore.edit { it[DESIGN] = design.name }
    }

    suspend fun setAccent(accent: AccentChoice) {
        context.dataStore.edit { it[ACCENT] = accent.serialize() }
    }

    suspend fun addCustomAccent(argb: Int) {
        context.dataStore.edit { preferences ->
            val existing = parseAccents(preferences[CUSTOM_ACCENTS])
            if (argb in existing) return@edit

            preferences[CUSTOM_ACCENTS] = serializeAccents(existing + argb)
        }
    }

    suspend fun setLogging(enabled: Boolean) {
        context.dataStore.edit { it[LOGGING] = enabled }
    }

    suspend fun setVpnMode(mode: VpnMode) {
        context.dataStore.edit { it[VPN_MODE] = mode.name }
    }

    suspend fun setUsbTethering(mode: UsbTethering) {
        context.dataStore.edit { it[USB_TETHERING] = mode.name }
    }

    suspend fun setOnboardingComplete(hasCompleted: Boolean) {
        context.dataStore.edit { it[ONBOARDED] = hasCompleted }
    }

    suspend fun setAutomationEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOMATION] = isEnabled
            if (isEnabled) preferences.mintTokenIfAbsent()
        }
    }

    suspend fun setAutomationToken(token: String) {
        context.dataStore.edit { it[AUTOMATION_TOKEN] = token }
    }

    suspend fun setTimeLimit(minutes: Int) {
        context.dataStore.edit { it[TIME_LIMIT_MINUTES] = minutes.coerceAtLeast(0) }
    }

    suspend fun setIdleStop(minutes: Int) {
        context.dataStore.edit { it[IDLE_STOP_MINUTES] = minutes.coerceAtLeast(0) }
    }

    suspend fun setSchedule(isEnabled: Boolean, startMinute: Int, stopMinute: Int) {
        context.dataStore.edit {
            it[SCHEDULE_ENABLED] = isEnabled
            it[SCHEDULE_START] = startMinute.coerceIn(0, 24 * 60 - 1)
            it[SCHEDULE_STOP] = stopMinute.coerceIn(0, 24 * 60 - 1)
        }
    }

    suspend fun setNotifyDevices(isEnabled: Boolean) {
        context.dataStore.edit { it[NOTIFY_DEVICES] = isEnabled }
    }

    suspend fun setBatteryLimit(percent: Int) {
        context.dataStore.edit { it[BATTERY_LIMIT_PERCENT] = percent.coerceIn(0, 100) }
    }

    suspend fun setDataLimit(megabytes: Int) {
        context.dataStore.edit { it[DATA_LIMIT_MB] = megabytes.coerceAtLeast(0) }
    }

    private fun MutablePreferences.mintTokenIfAbsent() {
        if (!this[AUTOMATION_TOKEN].isNullOrEmpty()) return

        this[AUTOMATION_TOKEN] = AutomationToken.generate()
    }

}

internal val THEME = stringPreferencesKey("theme")
internal val DESIGN = stringPreferencesKey("design")
internal val ACCENT = stringPreferencesKey("accent")
internal val CUSTOM_ACCENTS = stringPreferencesKey("custom_accents")
internal val LOGGING = booleanPreferencesKey("logging")
internal val VPN_MODE = stringPreferencesKey("vpn_mode")
internal val USB_TETHERING = stringPreferencesKey("usb_tethering")
internal val ONBOARDED = booleanPreferencesKey("onboarded")
internal val AUTOMATION = booleanPreferencesKey("automation")
internal val AUTOMATION_TOKEN = stringPreferencesKey("automation_token")
internal val TIME_LIMIT_MINUTES = intPreferencesKey("time_limit_minutes")
internal val DATA_LIMIT_MB = intPreferencesKey("data_limit_mb")
internal val BATTERY_LIMIT_PERCENT = intPreferencesKey("battery_limit_percent")
internal val IDLE_STOP_MINUTES = intPreferencesKey("idle_stop_minutes")
internal val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
internal val SCHEDULE_START = intPreferencesKey("schedule_start_minute")
internal val SCHEDULE_STOP = intPreferencesKey("schedule_stop_minute")
internal val NOTIFY_DEVICES = booleanPreferencesKey("notify_devices")

internal fun toSettings(preferences: Preferences) = Settings(
    theme = runCatching { ThemeChoice.valueOf(preferences[THEME].orEmpty()) }
        .getOrDefault(ThemeChoice.SYSTEM),
    design = runCatching { DesignLanguage.valueOf(preferences[DESIGN].orEmpty()) }
        .getOrDefault(DesignLanguage.MATERIAL_EXPRESSIVE),
    accent = parseAccent(preferences[ACCENT]),
    customAccents = parseAccents(preferences[CUSTOM_ACCENTS]),
    isLogging = preferences[LOGGING] ?: true,
    vpnMode = parseVpnMode(preferences[VPN_MODE]),
    usbTethering = parseUsbTethering(preferences[USB_TETHERING]),
    hasCompletedOnboarding = preferences[ONBOARDED] ?: false,
    isAutomationEnabled = preferences[AUTOMATION] ?: false,
    automationToken = preferences[AUTOMATION_TOKEN].orEmpty(),
    timeLimitMinutes = (preferences[TIME_LIMIT_MINUTES] ?: 0).coerceAtLeast(0),
    dataLimitMb = (preferences[DATA_LIMIT_MB] ?: 0).coerceAtLeast(0),
    batteryLimitPercent = (preferences[BATTERY_LIMIT_PERCENT] ?: 0).coerceIn(0, 100),
    idleStopMinutes = (preferences[IDLE_STOP_MINUTES] ?: 0).coerceAtLeast(0),
    scheduleEnabled = preferences[SCHEDULE_ENABLED] ?: false,
    scheduleStartMinute = (preferences[SCHEDULE_START] ?: DEFAULT_SCHEDULE_START).coerceIn(0, 24 * 60 - 1),
    scheduleStopMinute = (preferences[SCHEDULE_STOP] ?: DEFAULT_SCHEDULE_STOP).coerceIn(0, 24 * 60 - 1),
    notifyDevices = preferences[NOTIFY_DEVICES] ?: false,
)
