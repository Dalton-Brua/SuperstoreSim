package com.example.superstoresimulator.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.time.StoreState
import com.example.superstoresimulator.ui.state.TimeUIState
import com.example.superstoresimulator.ui.theme.DarkBackground
import com.example.superstoresimulator.ui.theme.OpenGreen
import com.example.superstoresimulator.ui.theme.ClosedRed
import com.example.superstoresimulator.ui.theme.ClosingOrange
import com.example.superstoresimulator.ui.theme.ClosingProceduresPurple
import com.example.superstoresimulator.ui.theme.OpeningBlue
import com.example.superstoresimulator.ui.theme.TextWhite

/**
 * Display bar showing current game time and store state
 * Includes speed controls
 */
@Composable
fun TimeDisplayBar(
    timeUI: TimeUIState,
    onSpeedChanged: (Float) -> Unit,
    onStoreStateClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time and store state display
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time
            Text(
                text = timeUI.currentTime.getFormattedTime(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
            
            // Store state indicator (clickable)
            // Show PAUSED if player has paused the time, otherwise show actual store state
            val displayState = if (timeUI.playerPausedTime) "PAUSED" else timeUI.storeState.name
            val stateColor = if (timeUI.playerPausedTime) {
                Color(0xFFE67E22)  // Orange for paused
            } else {
                when (timeUI.storeState) {
                    StoreState.OPEN -> OpenGreen
                    StoreState.CLOSED -> ClosedRed
                    StoreState.CLOSING -> ClosingOrange
                    StoreState.CLOSING_PROCEDURES -> ClosingProceduresPurple
                    StoreState.OPENING -> OpeningBlue
                }
            }
            
            val interactionSource = remember { MutableInteractionSource() }
            
            Text(
                text = displayState,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = stateColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            onStoreStateClick()
                        }
                    )
                    .padding(4.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Speed controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedButton("1x", 1.0f, timeUI.speedMultiplier, onSpeedChanged)
            SpeedButton("2x", 2.0f, timeUI.speedMultiplier, onSpeedChanged)
            SpeedButton("4x", 4.0f, timeUI.speedMultiplier, onSpeedChanged)
        }
    }
}

@Composable
private fun SpeedButton(
    label: String,
    speed: Float,
    current: Float,
    onSpeedChanged: (Float) -> Unit
) {
    Button(
        onClick = { onSpeedChanged(speed) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (current == speed) OpeningBlue else Color(0xFF34495E),
            contentColor = TextWhite
        ),
        modifier = Modifier.size(48.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

