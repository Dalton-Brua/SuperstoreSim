package com.example.superstoresimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.ui.theme.DarkBackground
import com.example.superstoresimulator.ui.theme.DarkNavy
import com.example.superstoresimulator.ui.theme.ManageGrey
import com.example.superstoresimulator.ui.theme.OpenGreen
import com.example.superstoresimulator.ui.theme.OpeningBlue
import com.example.superstoresimulator.ui.theme.TextWhite

/**
 * Compact status bar showing what role the player is currently performing.
 * Displays a color-coded badge and a progress bar when actively working.
 *
 * Colors:
 *  - Green  → CASHIER
 *  - Blue   → STOCKER
 *  - Gray   → MANAGING (idle)
 */
@Composable
fun PlayerRoleIndicator(
    playerRole: PlayerRole,
    playerCashierProgress: Float,
    playerStockerProgress: Float,
    modifier: Modifier = Modifier
) {
    val roleColor = when (playerRole) {
        PlayerRole.CASHIER -> OpenGreen
        PlayerRole.STOCKER -> OpeningBlue
        PlayerRole.MANAGE    -> ManageGrey
    }

    val roleLabel = when (playerRole) {
        PlayerRole.CASHIER -> "YOU: CASHIER"
        PlayerRole.STOCKER -> "YOU: STOCKER"
        PlayerRole.MANAGE    -> "YOU: MANAGING"
    }

    val progress = when (playerRole) {
        PlayerRole.CASHIER -> playerCashierProgress
        PlayerRole.STOCKER -> playerStockerProgress
        PlayerRole.MANAGE    -> 0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkNavy)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Role badge
        Box(
            modifier = Modifier
                .background(roleColor, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = roleLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        // Progress bar — only visible when actively working
        if (playerRole != PlayerRole.MANAGE) {
            Spacer(Modifier.width(10.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f),
                color = roleColor,
                trackColor = DarkBackground
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 10.sp,
                color = TextWhite
            )
        }
    }
}

