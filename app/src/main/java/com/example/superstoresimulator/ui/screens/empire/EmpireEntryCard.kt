package com.example.superstoresimulator.ui.screens.empire

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.state.EmpireUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.Violet

/**
 * Loop-1 advert card for going corporate. Visible when the player can enter empire mode.
 * Tapping opens an irreversible-action confirmation dialog; only [GameEvent.EnterEmpireMode]
 * commits the transition.
 */
@Composable
fun EmpireEntryCard(
    empire: EmpireUIState,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (empire.active || !empire.canEnterEmpire) return

    var confirmOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { confirmOpen = true },
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = Violet,
                modifier = Modifier.size(28.dp),
            )
            Column {
                Text(
                    text = "Open a second location",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryDark,
                )
                Text(
                    text = "Go corporate and become a regional operator.",
                    fontSize = 13.sp,
                    color = SubtleText,
                )
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Go corporate?") },
            text = {
                Text(
                    "Opening a second location makes you a regional operator — for good. " +
                        "Your stores will mostly run themselves by direction. You can still step in " +
                        "and run any store by hand whenever you want, but you can never return to the " +
                        "simple single-store game.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(GameEvent.EnterEmpireMode)
                        confirmOpen = false
                    },
                ) {
                    Text("Open second location", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) {
                    Text("Cancel", color = Destructive)
                }
            },
        )
    }
}
