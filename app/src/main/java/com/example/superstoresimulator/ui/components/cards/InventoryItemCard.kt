package com.example.superstoresimulator.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.yourapp.ui.theme.GameButtonStyles
import com.example.superstoresimulator.ui.theme.Amber
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.CautionDark
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.ErrorSurface
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.Success
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

@Composable
fun InventoryItemCard(
    item: InventoryItemUI,
    canAffordBuy: Boolean,
    onBuy: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // Name
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryDark
            )

            // First stat row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Shelf: ${item.shelfStock}", color = TextSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    val priceSuffix = if (item.soldByWeight) "/lb" else ""
                    if (item.priceModifierPercent != 0) {
                        Text("Price: ${item.effectivePrice}$priceSuffix", color = PrimaryDark)
                        val modSign = if (item.priceModifierPercent > 0) "+" else ""
                        val modColor = if (item.priceModifierPercent > 0) DestructiveDark else Success
                        val modBg = if (item.priceModifierPercent > 0) ErrorSurface else SuccessChipSurface
                        Text(
                            "$modSign${item.priceModifierPercent}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = modColor,
                            modifier = Modifier
                                .background(modBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    } else {
                        Text("Price: ${item.price}$priceSuffix", color = PrimaryDark)
                    }
                    if (item.hasActiveMarkdown) {
                        Text(
                            "SALE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            modifier = Modifier
                                .background(DestructiveDark, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Second stat row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Back: ${item.backroomStock}", color = TextSecondary)
                    if (item.backroomFull) {
                        Text(
                            "FULL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Destructive
                        )
                    }
                }
                Text("Unit Cost: ${item.unitCost}", color = PrimaryDark)
            }

            // Case pack info
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Case Pack: ${item.casePack} items", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextSecondary)
                Text("Case Cost: ${item.casePackCost}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = PrimaryDark)
            }

            // Zone score
            if (item.shelfStock > 0 && item.zoneScore < 1.0f) {
                val zonePct = (item.zoneScore * 100).toInt()
                val zoneColor = when {
                    zonePct >= 80 -> Secondary
                    zonePct >= 50 -> Amber
                    else -> Destructive
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Zone: $zonePct%", fontSize = 11.sp, color = zoneColor, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { item.zoneScore.coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = zoneColor,
                        trackColor = ProgressBarTrack,
                    )
                }
            }

            // In-transit badge
            if (item.pendingCasePacks > 0) {
                val arrivalDow = item.earliestArrivalDay?.let { day ->
                    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    names.getOrElse(day % 7) { "" }
                } ?: ""
                val dayLabel = item.earliestArrivalDay?.let { " · Day ${it + 1} ($arrivalDow)" } ?: ""
                Text(
                    text = "🚚 ${item.pendingCasePacks} cases in transit$dayLabel",
                    fontSize = 11.sp,
                    color = CautionDark,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onBuy,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = PrimaryDark,
                    contentColor = TextWhite,
                    disabledContainerColor = ChipSurface,
                    disabledContentColor = TextMuted
                ),
                enabled = canAffordBuy && !item.backroomFull,
                shape = GameButtonStyles.Shape,
            ) {
                Text(
                    if (item.backroomFull) "Backroom Full" else "Order (${item.casePackCost})",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

