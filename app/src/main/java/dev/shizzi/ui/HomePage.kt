package dev.shizzi.ui

import dev.shizzi.tr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.SessionLimits
import dev.shizzi.SessionUiState
import dev.shizzi.Traffic
import dev.shizzi.UiStatus
import dev.shizzi.UsageHistory
import dev.shizzi.UsageLedger
import dev.shizzi.ui.theme.HeaderHeight
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween
import java.time.LocalDate

private fun buttonLabel(status: UiStatus): String =
    if (status == UiStatus.CONNECTED) tr("Stop") else tr("Start")

private fun buttonState(state: SessionUiState): ConnectButtonState = when {
    state.status == UiStatus.LOADING -> ConnectButtonState.LOADING
    state.status == UiStatus.CONNECTED -> ConnectButtonState.STOP
    state.canStart -> ConnectButtonState.START
    else -> ConnectButtonState.DISABLED
}

/** The user's optional limits, shown as an outer arc on the ring. */
data class HomeLimits(val timeLimitMinutes: Int = 0, val dataLimitMb: Int = 0)

@Composable
fun HomePage(
    state: SessionUiState,
    actions: HomeActions,
    limits: HomeLimits = HomeLimits(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        HomeBody(state = state, actions = actions, limits = limits)

        HomeHeader(
            state = state,
            onOpenHistory = actions.onOpenHistory,
            onOpenSettings = actions.onOpenSettings,
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Box(
                modifier = Modifier.height(ShizziTheme.spacing.xxxl),
                contentAlignment = Alignment.BottomCenter,
            ) {
                RiseIn(isVisible = isShowingVpn(state)) {
                    VpnChip(isBypassed = state.isVpnBypassed)
                }
            }

            StatusRow(state = state, onVersionClick = actions.onOpenEasterEgg)
        }
    }
}

data class HomeActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenEasterEgg: () -> Unit,
    val onOpenHistory: () -> Unit = {},
)

@Composable
private fun HomeHeader(
    state: SessionUiState,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = ShizziTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(start = ShizziTheme.spacing.sm)) {
            ShizukuBadge(state.shizukuState)
        }

        Spacer(Modifier.weight(1f))

        ShizziIconButton(
            icon = Icons.Filled.BarChart,
            contentDescription = tr("Usage history"),
            onClick = onOpenHistory,
            tint = ShizziTheme.colors.onSurfaceMuted,
        )

        ShizziIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = tr("Settings"),
            onClick = onOpenSettings,
            tint = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}

private fun isShowingVpn(state: SessionUiState): Boolean {
    val hasVpn = state.isVpnBound || state.isVpnBypassed

    return hasVpn && state.status == UiStatus.CONNECTED
}

/** Fades content up into a slot the layout already reserves. */
@Composable
private fun RiseIn(isVisible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(standardTween()) + slideInVertically(standardSpring()) { it / 2 },
        exit = fadeOut(standardTween()) + slideOutVertically(standardSpring()) { it / 2 },
    ) {
        content()
    }
}

@Composable
private fun HomeBody(state: SessionUiState, actions: HomeActions, limits: HomeLimits) {
    val isStarting = state.status == UiStatus.LOADING
    val isConnected = state.status == UiStatus.CONNECTED
    val speed = rememberSpeedHistory(state)

    val headline = when {
        isConnected && !state.isWaitingForUsb -> "${Traffic.format(speed.current)}/s"
        isConnected -> "USB"
        state.status == UiStatus.LOADING -> "…"
        state.status == UiStatus.ERROR -> "!"
        else -> "Shizzi"
    }
    val limitProgress = if (isConnected) {
        SessionLimits.progress(
            timeLimitMinutes = limits.timeLimitMinutes,
            dataLimitMb = limits.dataLimitMb,
            state = state,
            now = System.currentTimeMillis(),
        )
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(top = HeaderHeight + ShizziTheme.spacing.lg, bottom = ShizziTheme.spacing.xxxl * 3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        ConnectionRing(
            status = state.status,
            isWaitingForUsb = state.isWaitingForUsb,
            headline = headline,
            caption = ringCaption(state.status, state.isWaitingForUsb),
            limitProgress = limitProgress,
        )

        Spacer(Modifier.height(ShizziTheme.spacing.xl))

        ConnectButton(
            label = buttonLabel(state.status),
            state = buttonState(state),
            onClick = actions.onToggle,
        )

        Box(modifier = Modifier.height(ShizziTheme.spacing.xxxl)) {
            RiseIn(isVisible = isStarting) {
                CancelButton(onClick = actions.onCancel)
            }
        }

        RiseIn(isVisible = isConnected) {
            SessionStats(state = state, speed = speed)
        }

        RiseIn(isVisible = !isConnected && !isStarting) {
            UsageSummary(onOpenHistory = actions.onOpenHistory)
        }
    }
}

/** Today / week / month totals from the usage history, shown while not sharing. */
@Composable
private fun UsageSummary(onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    remember { UsageHistory.load(context) }
    val ledger: UsageLedger by UsageHistory.ledger.collectAsState()
    val today = LocalDate.now()
    val gap = ShizziTheme.spacing.sm

    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(
            text = tr("Data shared").uppercase(),
            style = ShizziTheme.typography.caption,
            color = ShizziTheme.colors.onSurfaceMuted,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            StatTile(Icons.Filled.Today, tr("Today"), Traffic.format(ledger.bytesOn(today)), Modifier.weight(1f))
            StatTile(Icons.Filled.DateRange, tr("7 days"), Traffic.format(ledger.lastDays(today, 7)), Modifier.weight(1f))
            StatTile(Icons.Filled.CalendarMonth, tr("This month"), Traffic.format(ledger.month(today)), Modifier.weight(1f))
        }

        GhostButton(label = tr("View history"), onClick = onOpenHistory)
    }
}
