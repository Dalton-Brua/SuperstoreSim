package com.example.superstoresimulator.ui.screens.empire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.empire.EmpireSpeed
import com.example.superstoresimulator.domain.empire.ManagerPersonality
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.state.EmpireUIState
import com.example.superstoresimulator.ui.state.ManagerUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.Success
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.ui.theme.WarningChipSurface
import com.example.superstoresimulator.ui.theme.WarningTextDarker

/**
 * Top-level empire-mode (loop 2) screen. Shown when [EmpireUIState.active].
 * Renders the empire header, clock controls, manager panel, and a scrollable
 * list of regions, each with its stores.
 */
@Composable
fun EmpireRootScreen(
    empire: EmpireUIState,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EmpireHeader(empire)
        }

        item {
            EmpireClockBar(empire, onEvent)
        }

        item {
            ManagerPanel(empire, onEvent)
        }

        item {
            Text(
                text = "Regions",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = PrimaryDark,
            )
        }

        items(empire.regions) { region ->
            RegionSection(region = region, onEvent = onEvent)
        }
    }
}

@Composable
private fun EmpireHeader(empire: EmpireUIState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Empire",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = PrimaryDark,
                )
                Text(
                    text = "Day ${empire.clock.currentDayIndex}",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
            }
            Text(
                text = empire.money.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Success,
            )
        }
    }
}

@Composable
private fun EmpireClockBar(
    empire: EmpireUIState,
    onEvent: (GameEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Pending decision banner (auto-pause; player must act).
            empire.clock.pendingDecisionReason?.let { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningChipSurface, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Destructive,
                    )
                    Column {
                        Text(
                            text = "Decision needed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = WarningTextDarker,
                        )
                        Text(
                            text = reason,
                            fontSize = 13.sp,
                            color = WarningTextDarker,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpeedButton("Pause", empire.clock.speed == EmpireSpeed.PAUSED) {
                    onEvent(GameEvent.SetEmpireSpeed(EmpireSpeed.PAUSED))
                }
                SpeedButton("Normal", empire.clock.speed == EmpireSpeed.NORMAL) {
                    onEvent(GameEvent.SetEmpireSpeed(EmpireSpeed.NORMAL))
                }
                SpeedButton("Fast", empire.clock.speed == EmpireSpeed.FAST) {
                    onEvent(GameEvent.SetEmpireSpeed(EmpireSpeed.FAST))
                }
            }

            OutlinedButton(
                onClick = { onEvent(GameEvent.AdvanceToNextDecision) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
            ) {
                Text("Advance to next decision", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RowScope.SpeedButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Primary else ChipSurface,
            contentColor = if (selected) TextWhite else TextSecondary,
        ),
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ManagerPanel(
    empire: EmpireUIState,
    onEvent: (GameEvent) -> Unit,
) {
    val manager = empire.manager
    when {
        manager != null -> HiredManagerCard(manager, onEvent)
        empire.canHireManager -> HireManagerCard(onEvent)
        else -> Unit
    }
}

@Composable
private fun HiredManagerCard(
    manager: ManagerUI,
    onEvent: (GameEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = manager.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryDark,
                    )
                    Text(
                        text = manager.personality.displayName,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = "${manager.salaryPerDay}/day",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SubtleText,
                )
            }
            Text(
                text = "${manager.storesOnPreferred} on preferred / ${manager.storesDeviating} deviating",
                fontSize = 13.sp,
                color = SubtleText,
            )
            OutlinedButton(
                onClick = { onEvent(GameEvent.FireRegionalManager) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Destructive),
            ) {
                Text("Fire manager", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HireManagerCard(onEvent: (GameEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Hire a regional manager",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PrimaryDark,
            )
            Text(
                text = "A manager auto-sets store directions toward their preferred posture.",
                fontSize = 13.sp,
                color = TextSecondary,
            )
            ManagerPersonality.values().forEach { personality ->
                Button(
                    onClick = { onEvent(GameEvent.HireRegionalManager(personality)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = TextWhite,
                        disabledContainerColor = TextMuted,
                        disabledContentColor = TextWhite,
                    ),
                ) {
                    Text(
                        text = "${personality.displayName} (prefers ${personality.preferred.displayName})",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
