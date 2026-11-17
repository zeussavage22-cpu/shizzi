package dev.shizzi

import android.app.Activity
import android.os.Bundle

/** Invisible target of the launcher shortcuts: starts or stops sharing, then closes. */
class ShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            when (intent?.action) {
                ACTION_START -> if (ShizukuGate.currentState() is ShizukuState.Ready) {
                    SessionService.start(this)
                } else {
                    startActivity(android.content.Intent(this, MainActivity::class.java))
                }
                ACTION_STOP -> SessionService.stop(this)
            }
        }.onFailure { SessionLog.error("shortcut failed: ${it.message}") }

        finish()
    }

    private companion object {
        const val ACTION_START = "dev.shizzi.shortcut.START"
        const val ACTION_STOP = "dev.shizzi.shortcut.STOP"
    }
}
