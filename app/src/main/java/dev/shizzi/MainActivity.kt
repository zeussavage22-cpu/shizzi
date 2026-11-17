package dev.shizzi

import android.content.pm.PackageManager
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import dev.shizzi.ui.LockScreen
import androidx.core.view.WindowCompat
import dev.shizzi.ui.Screen
import dev.shizzi.ui.theme.Appearance
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.condorBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, granted ->
            viewModel.refreshShizukuState()
            viewModel.refreshPermissions()
            onShizukuResult(isGranted = granted == PackageManager.PERMISSION_GRANTED)
        }

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener { viewModel.refreshShizukuState() }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener { viewModel.refreshShizukuState() }

    private var requested: AppPermission? = null

    private var isChaining = false

    private val asked = mutableSetOf<AppPermission>()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
            onPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerShizukuListeners()

        // Long-pressing the quick settings tile lands on settings, like the stock hotspot tile.
        val startScreen = when (intent?.action) {
            TileService.ACTION_QS_TILE_PREFERENCES -> Screen.SETTINGS
            else -> Screen.HOME
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            val loaded = settings ?: return@setContent

            val appearance = Appearance(
                theme = loaded.theme,
                design = loaded.design,
                accent = loaded.accent,
            )

            ShizziTheme(appearance = appearance) {
                val colors = ShizziTheme.colors

                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !colors.isDark
                }

                Surface(color = Color.Transparent, modifier = Modifier.condorBackdrop(colors)) {
                    val state by viewModel.state.collectAsState()
                    val diagnostics by viewModel.diagnosticsState.collectAsState()
                    val compatibility by viewModel.compatibilityState.collectAsState()
                    val permissions by viewModel.permissionState.collectAsState()

                    if (loaded.appLock && !isUnlocked) {
                        LockScreen(onUnlock = ::authenticate)
                        LaunchedEffect(Unit) { authenticate() }
                        return@Surface
                    }

                    ShizziApp(
                        state = AppState(
                            session = state,
                            settings = loaded,
                            diagnostics = diagnostics,
                            permissions = permissions,
                        ),
                        onboarding = OnboardingEntry(
                            compatibility = compatibility,
                            onCheckCompatibility = viewModel::checkCompatibility,
                            onDownloadTetheringApex = viewModel::downloadTetheringApex,
                            onInstallTetheringApex = viewModel::installTetheringApex,
                            onRebootDevice = viewModel::rebootDevice,
                            onComplete = viewModel::completeOnboarding,
                        ),
                        actions = AppActions(
                            onToggle = viewModel::toggle,
                            onCancel = viewModel::cancel,
                            onRequestPermission = viewModel::requestPermission,
                            onRequestAllPermissions = ::requestAllPermissions,
                            onGrantPermission = ::grantPermission,
                            onShizukuAction = viewModel::actOnShizuku,
                            onSetTheme = viewModel::setTheme,
                            onSetDesign = viewModel::setDesign,
                            onSetAccent = viewModel::setAccent,
                            onAddCustomAccent = viewModel::addCustomAccent,
                            onSetLogging = viewModel::setLogging,
                            onSetVpnMode = viewModel::setVpnMode,
                            onSetUsbTethering = viewModel::setUsbTethering,
                            onRunProbes = viewModel::runProbes,
                            onDismissDiagnostics = viewModel::dismissDiagnostics,
                            onClearLog = viewModel::clearLog,
                            onRestartOnboarding = viewModel::restartOnboarding,
                            onSetAutomation = viewModel::setAutomation,
                            onRegenerateAutomationToken =
                                viewModel::regenerateAutomationToken,
                            onSetTimeLimit = viewModel::setTimeLimit,
                            onSetDataLimit = viewModel::setDataLimit,
                            onSetBatteryLimit = viewModel::setBatteryLimit,
                            onClearHistory = viewModel::clearHistory,
                            onSetIdleStop = viewModel::setIdleStop,
                            onSetSchedule = viewModel::setSchedule,
                            onSetNotifyDevices = viewModel::setNotifyDevices,
                            onSaveHotspotCredentials = viewModel::setHotspotCredentials,
                            onRunSpeedTest = viewModel::runSpeedTest,
                            onSetQuota = viewModel::setQuota,
                            onSetAppLock = viewModel::setAppLock,
                        ),
                        startScreen = startScreen,
                    )
                }
            }
        }

        holdFirstFrameUntilSettingsLoad()
    }

    private fun holdFirstFrameUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)

        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (viewModel.settings.value == null) return false

                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    private fun requestAllPermissions() {
        isChaining = true
        requestNextOutstanding()
    }

    private fun requestNextOutstanding() {
        if (requestShizuku()) return

        val outstanding = viewModel.permissionState.value.firstOrNull { !it.isGranted }
        val permission = outstanding?.permission

        if (permission == null) {
            isChaining = false
            return
        }

        grantPermission(permission)
    }

    // Only PermissionRequired reports back through the Shizuku listener; the other
    // states hand off to another app, so the chain ends there.
    private fun requestShizuku(): Boolean {
        val state = viewModel.state.value.shizukuState
        if (state is ShizukuState.Ready) return false

        if (state !is ShizukuState.PermissionRequired) stopChain()

        viewModel.actOnShizuku()
        return true
    }

    // A permission with no manifest name is granted on a settings screen in
    // another app, so there is no dialog to launch and no result to wait for.
    private fun grantPermission(permission: AppPermission) {
        val name = permission.manifestName

        if (name == null || isDialogSuppressed(permission)) {
            stopChain()
            viewModel.openPermissionSettings(permission)
            return
        }

        requested = permission
        asked += permission
        permissionLauncher.launch(name)
    }

    private fun onPermissionResult() {
        val permission = requested ?: return
        requested = null

        if (!isChaining) return

        if (viewModel.isPermissionGranted(permission)) requestNextOutstanding() else stopChain()
    }

    private fun onShizukuResult(isGranted: Boolean) {
        if (!isChaining) return

        if (isGranted) requestNextOutstanding() else stopChain()
    }

    private fun stopChain() {
        isChaining = false
    }

    // shouldShowRequestPermissionRationale is also false before the first ask, so
    // only a permission this process has already requested can be suppressed.
    private fun isDialogSuppressed(permission: AppPermission): Boolean {
        val name = permission.manifestName ?: return false
        if (permission !in asked) return false

        return !shouldShowRequestPermissionRationale(name)
    }

    private fun registerShizukuListeners() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    /** Whether the app lock has been passed; resets after a while in the background. */
    private var isUnlocked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > RELOCK_AFTER_MS) isUnlocked = false
    }

    private fun authenticate() {
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(AppInfo.NAME)
            .setSubtitle(tr("Unlock to continue", "Desbloquea para continuar"))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()

        runCatching {
            prompt.authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isUnlocked = true
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // A phone with no screen lock at all cannot be locked out of the app.
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
                        ) {
                            isUnlocked = true
                        }
                    }
                },
            )
        }.onFailure { isUnlocked = true }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshShizukuState()
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private companion object {
        const val RELOCK_AFTER_MS = 60_000L
    }
}
