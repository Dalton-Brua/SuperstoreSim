package com.example.superstoresimulator.ui.screens.empire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.state.RegionUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

/**
 * A single region: header with name + saturation bar + demand-profile chips, then either
 * its stores (with an "open location" button) or an unlock entry.
 */
@Composable
fun RegionSection(
    region: RegionUI,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = region.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryDark,
                )
                if (!region.unlocked) {
                    Surface(color = ChipSurface, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "LOCKED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChipTextDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Saturation bar
            SaturationBar(load = region.load, overCapacity = region.overCapacity)

            // Demand profile chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemandChip("Spending ${formatMult(region.baseSpendingPower)}")
                DemandChip("Traffic ${formatMult(region.baseTraffic)}")
            }

            HorizontalDivider(color = ProgressBarTrack)

            if (region.unlocked) {
                if (region.stores.isEmpty()) {
                    Text(
                        text = "No locations here yet.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                region.stores.forEach { store ->
                    StoreCard(store = store, onEvent = onEvent)
                }
                Button(
                    onClick = { onEvent(GameEvent.OpenLocation(region.regionId)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = TextWhite,
                    ),
                ) {
                    Text("Open location here", fontWeight = FontWeight.SemiBold)
                }
                if (region.investing) {
                    OutlinedButton(
                        onClick = { onEvent(GameEvent.SetRegionInvesting(region.regionId, false)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Destructive),
                    ) {
                        Text("Stop investing — ${region.investDailyCost}/day", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "Investing: capacity + demand grow ~1%/month, compounding daily.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                } else {
                    OutlinedButton(
                        onClick = { onEvent(GameEvent.SetRegionInvesting(region.regionId, true)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    ) {
                        Text("Invest in region — ${region.investDailyCost}/day", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "Ongoing spend grows capacity + demand over months — eases saturation, raises traffic.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
            } else {
                Text(
                    text = "Unlock cost: ${region.unlockCost}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SubtleText,
                )
                OutlinedButton(
                    onClick = { onEvent(GameEvent.UnlockRegion(region.regionId)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                ) {
                    Text("Unlock region", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SaturationBar(load: Float, overCapacity: Boolean) {
    val fill = load.coerceIn(0f, 1f)
    val tint = if (overCapacity) Destructive else Primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Region load", fontSize = 12.sp, color = SubtleText)
            Text(
                text = "Load ${formatMult(load)}" + if (overCapacity) " (over capacity)" else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (overCapacity) Destructive else PrimaryDark,
            )
        }
        LinearProgressIndicator(
            progress = { fill },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            color = tint,
            trackColor = ProgressBarTrack,
        )
    }
}

@Composable
private fun DemandChip(label: String) {
    Surface(color = ChipSurface, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = ChipTextDark,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Format a multiplier like 1.30 as "1.3x". */
internal fun formatMult(value: Float): String =
    String.format(java.util.Locale.US, "%.1f", value) + "×"
