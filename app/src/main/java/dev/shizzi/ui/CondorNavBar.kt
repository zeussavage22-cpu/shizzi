package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween

private val BarShape = RoundedCornerShape(28.dp)
private val PillShape = RoundedCornerShape(20.dp)
private val ItemIconSize = 22.dp

private data class NavItem(val screen: Screen, val icon: ImageVector, val label: () -> String)

private val Items = listOf(
    NavItem(Screen.HOME, Icons.Filled.Home) { tr("Home", "Inicio") },
    NavItem(Screen.SPEED_TEST, Icons.Filled.Speed) { tr("Speed", "Velocidad") },
    NavItem(Screen.HISTORY, Icons.Filled.BarChart) { tr("History", "Historial") },
    NavItem(Screen.SETTINGS, Icons.Filled.Settings) { tr("Settings") },
)

/** Screens reachable from the bottom bar; the bar shows only on these. */
val TopLevelScreens = Items.map { it.screen }.toSet()

/** Floating, rounded bottom navigation with a glowing pill on the selected item. */
@Composable
fun CondorNavBar(current: Screen, onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val colors = ShizziTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(BarShape)
            .background(colors.surface.copy(alpha = 0.92f))
            .border(1.dp, colors.primary.copy(alpha = 0.18f), BarShape)
            .height(64.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Items.forEach { item ->
            NavButton(
                item = item,
                isSelected = item.screen == current,
                onClick = { if (item.screen != current) onSelect(item.screen) },
            )
        }
    }
}

@Composable
private fun NavButton(item: NavItem, isSelected: Boolean, onClick: () -> Unit) {
    val colors = ShizziTheme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    val tint by animateColorAsState(
        targetValue = if (isSelected) colors.onPrimary else colors.onSurfaceMuted,
        animationSpec = standardSpring(),
        label = "navTint",
    )

    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(
                if (isSelected) {
                    Brush.horizontalGradient(listOf(colors.primary, colors.secondary))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label(),
            tint = tint,
            modifier = Modifier.size(ItemIconSize),
        )

        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(standardTween()) + expandHorizontally(standardSpring()),
            exit = fadeOut(standardTween()) + shrinkHorizontally(standardSpring()),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.label(),
                    style = ShizziTheme.typography.label,
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
}
