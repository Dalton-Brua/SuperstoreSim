package com.example.superstoresimulator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.time.StoreState
import com.example.superstoresimulator.ui.theme.DarkBackground
import com.example.superstoresimulator.ui.theme.DarkSurface
import com.example.superstoresimulator.ui.theme.LightGrey
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

/**
 * Option A — Segmented Control Style
 *
 * A unified card: three-segment pill on top, progress track glued to the bottom edge.
 * Colors follow the app theme — DarkBackground container, Secondary (green) for cashier,
 * Primary (blue) for stocker, LightGrey for manage.
 *
 * Visual (CASHIER active):
 * ┌──────────────────────────────────────────────────┐  ← DarkBackground
 * │  [⚙ MANAGE]    [🛒 CASHIER ✓]    [📦 STOCKER]   │
 * │████████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│  ← Secondary progress bar
 * └──────────────────────────────────────────────────┘
 */
@Composable
fun PlayerRoleButtons(
    currentRole: PlayerRole,
    hasBackroomItems: Boolean,
    onRoleChanged: (PlayerRole) -> Unit,
    modifier: Modifier = Modifier,
    playerCashierProgress: Float = 0f,
    playerStockerProgress: Float = 0f,
) {
    // Role accent colors — mapped to named theme tokens
    val cashierColor = Secondary      // green  (matches app's action green)
    val stockerColor = Primary        // blue   (matches app's primary blue)
    val manageColor  = LightGrey      // grey   (neutral / no active role)

    val progress = when (currentRole) {
        PlayerRole.CASHIER -> playerCashierProgress
        PlayerRole.STOCKER -> playerStockerProgress
        PlayerRole.NONE    -> 0f
    }
    val barColor: Color = when (currentRole) {
        PlayerRole.CASHIER -> cashierColor
        PlayerRole.STOCKER -> stockerColor
        PlayerRole.NONE    -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Manage — always tappable
            Segment(
                icon = Icons.Default.ManageAccounts,
                label = "MANAGE",
                isActive = currentRole == PlayerRole.NONE,
                isEnabled = true,
                activeColor = manageColor,
                modifier = Modifier.weight(1f)
            ) { onRoleChanged(PlayerRole.NONE) }

            // Cashier — available whenever the store is open; player waits for next customer
            Segment(
                icon = Icons.Default.AddShoppingCart,
                label = "CASHIER",
                isActive = currentRole == PlayerRole.CASHIER,
                isEnabled = true,
                activeColor = cashierColor,
                modifier = Modifier.weight(1f)
            ) { onRoleChanged(PlayerRole.CASHIER) }

            // Stocker — available even when store is closed (overnight restocking)
            Segment(
                icon = Icons.Default.Inventory,
                label = "STOCKER",
                isActive = currentRole == PlayerRole.STOCKER,
                isEnabled = hasBackroomItems,
                activeColor = stockerColor,
                modifier = Modifier.weight(1f)
            ) { onRoleChanged(PlayerRole.STOCKER) }
        }

        // Progress bar — always rendered (transparent when idle to prevent layout shift)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = barColor,
            trackColor = PrimaryDark.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun Segment(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isEnabled: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isActive  -> activeColor
            isEnabled -> DarkSurface   // elevated, clearly tappable
            else      -> DarkBackground  // blends into container — visually dimmed
        },
        animationSpec = tween(200),
        label = "segBg"
    )
    val contentColor: Color = when {
        isActive  -> TextWhite
        isEnabled -> TextSecondary
        else      -> TextSecondary.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .background(bgColor)
            .clickable(enabled = isEnabled || isActive) { onClick() }
            .padding(vertical = 11.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}
