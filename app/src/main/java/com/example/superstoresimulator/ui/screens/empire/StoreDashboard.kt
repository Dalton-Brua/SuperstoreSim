package com.example.superstoresimulator.ui.screens.empire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.empire.EmpireTuning
import com.example.superstoresimulator.domain.empire.StoreDirection
import com.example.superstoresimulator.domain.empire.StoreUpgrade
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.state.StoreUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.PositiveGreen
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.Success
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

/**
 * Full store dashboard shown in a dialog. Metrics + history trend, direction selector,
 * upgrades, expand, and a hands-on "run this store myself" entry.
 */
@Composable
fun StoreDashboard(
    store: StoreUI,
    onEvent: (GameEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = store.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = PrimaryDark,
                        )
                        Text(
                            text = store.size.displayName,
                            fontSize = 13.sp,
                            color = TextSecondary,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                HorizontalDivider(color = ProgressBarTrack)

                // Today's metrics
                Text("Today", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PrimaryDark)
                MetricRow("Customers", store.today.customers.toString())
                MetricRow("Revenue", store.today.revenue.toString())
                MetricRow("Operating cost", store.today.operatingCost.toString())
                MetricRow(
                    label = "Net profit",
                    value = store.today.netProfit.toString(),
                    valueColor = if (store.unprofitable) Destructive else PositiveGreen,
                )
                MetricRow("Traffic vs goal", formatMult(store.today.trafficVsGoal))

                // Trend (last ~7 days net profit)
                HistoryTrend(store)

                HorizontalDivider(color = ProgressBarTrack)

                // Direction selector
                DirectionSelector(store, onEvent)

                HorizontalDivider(color = ProgressBarTrack)

                // Upgrades
                UpgradesSection(store, onEvent)

                // Expand size
                store.expandCost?.let { cost ->
                    Button(
                        onClick = { onEvent(GameEvent.ExpandStoreSize(store.storeId)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = TextWhite,
                        ),
                    ) {
                        Text("Expand size ($cost)", fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(color = ProgressBarTrack)

                // Hands-on operating
                OperatingSection(store, onEvent)

                HorizontalDivider(color = ProgressBarTrack)

                // Close (sell off) the store
                CloseStoreSection(store, onEvent, onDismiss)
            }
        }
    }
}

@Composable
private fun CloseStoreSection(
    store: StoreUI,
    onEvent: (GameEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Destructive),
    ) {
        Text("Close this store", fontWeight = FontWeight.SemiBold)
    }
    Text(
        text = "Frees up regional capacity — eases saturation for nearby stores.",
        fontSize = 11.sp,
        color = TextSecondary,
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Close ${store.name}?") },
            text = { Text("This permanently shuts the store down. There is no refund.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onEvent(GameEvent.CloseStore(store.storeId))
                    onDismiss()
                }) {
                    Text("Close store", color = Destructive, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Keep open") }
            },
        )
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = PrimaryDark,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = SubtleText)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun HistoryTrend(store: StoreUI) {
    val recent = store.history.takeLast(7)
    if (recent.isEmpty()) return

    Text(
        text = "Net profit (last ${recent.size} days)",
        fontSize = 12.sp,
        color = SubtleText,
    )
    val maxAbs = recent.maxOf { kotlin.math.abs(it.netProfit.cents) }.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        recent.forEach { metrics ->
            val cents = metrics.netProfit.cents
            val fraction = (kotlin.math.abs(cents).toFloat() / maxAbs).coerceIn(0.05f, 1f)
            val barColor = if (cents < 0) Destructive else Success
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((48 * fraction).dp)
                        .background(barColor, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun DirectionSelector(
    store: StoreUI,
    onEvent: (GameEvent) -> Unit,
) {
    Text("Direction", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PrimaryDark)
    if (store.managedByManager) {
        Text(
            text = "Set by your regional manager.",
            fontSize = 12.sp,
            color = TextSecondary,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StoreDirection.values().forEach { direction ->
            val selected = store.direction == direction
            Button(
                onClick = { onEvent(GameEvent.SetStoreDirection(store.storeId, direction)) },
                enabled = !store.managedByManager,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Primary else ChipSurface,
                    contentColor = if (selected) TextWhite else ChipTextDark,
                    disabledContainerColor = if (selected) Primary else ChipSurface,
                    disabledContentColor = if (selected) TextWhite else ChipTextDark,
                ),
            ) {
                Text(
                    text = direction.displayName,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun UpgradesSection(
    store: StoreUI,
    onEvent: (GameEvent) -> Unit,
) {
    Text("Upgrades", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PrimaryDark)

    val owned = StoreUpgrade.values().filter { it in store.upgrades }
    val available = StoreUpgrade.values().filter { it !in store.upgrades }

    if (owned.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            owned.forEach { upgrade -> OwnedUpgradeChip(upgrade.displayName) }
        }
    }

    available.forEach { upgrade ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OutlinedButton(
                onClick = { onEvent(GameEvent.BuyStoreUpgrade(store.storeId, upgrade)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
            ) {
                Text(
                    text = "${upgrade.displayName} — ${EmpireTuning.upgradeCost(upgrade)}",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = upgrade.description,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun OwnedUpgradeChip(label: String) {
    Surface(color = SuccessChipSurface, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = SuccessTextDark,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun OperatingSection(
    store: StoreUI,
    onEvent: (GameEvent) -> Unit,
) {
    Text("Hands-on", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PrimaryDark)
    Text(
        text = "Operating bonus: ${formatMult(store.operatingPerformance)}",
        fontSize = 13.sp,
        color = SubtleText,
    )
    Text(
        text = store.daysSinceOperated?.let { "Operated $it days ago" } ?: "Never operated",
        fontSize = 12.sp,
        color = TextSecondary,
    )
    Button(
        onClick = { onEvent(GameEvent.DropIntoStore(store.storeId)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Success,
            contentColor = TextWhite,
        ),
    ) {
        Text("Run this store myself", fontWeight = FontWeight.SemiBold)
    }
}
