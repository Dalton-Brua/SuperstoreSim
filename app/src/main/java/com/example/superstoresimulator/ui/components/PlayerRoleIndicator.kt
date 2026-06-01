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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.player.PlayerRole

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
        PlayerRole.CASHIER -> Color(0xFF2ECC71)
        PlayerRole.STOCKER -> Color(0xFF3498DB)
        PlayerRole.MANAGE    -> Color(0xFF95A5A6)
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
            .background(Color(0xFF1E2A3A))
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
                color = Color.White
            )
        }

        // Progress bar — only visible when actively working
        if (playerRole != PlayerRole.MANAGE) {
            Spacer(Modifier.width(10.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f),
                color = roleColor,
                trackColor = Color(0xFF2C3E50)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 10.sp,
                color = Color.White
            )
        }
    }
}

