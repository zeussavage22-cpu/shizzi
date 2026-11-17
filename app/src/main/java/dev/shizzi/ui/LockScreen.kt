package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.AppInfo
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme

/** Shown instead of the app until the fingerprint / screen lock is passed. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = ShizziTheme.colors.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(ShizziTheme.spacing.lg))
        Text(
            text = AppInfo.NAME,
            style = ShizziTheme.typography.display,
            color = ShizziTheme.colors.onSurface,
        )
        Text(
            text = tr("Locked", "Bloqueada"),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(ShizziTheme.spacing.xxl))
        ConnectButton(
            label = tr("Unlock", "Desbloquear"),
            state = ConnectButtonState.START,
            onClick = onUnlock,
        )
    }
}
