package com.example.superstoresimulator.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.yourapp.ui.theme.GameButtonStyles
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextSecondary

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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Shelf: ${item.shelfStock}", color = TextSecondary)
                Text("Price: ${item.price}", color = PrimaryDark)
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
                    zonePct >= 80 -> Color(0xFF22C55E)
                    zonePct >= 50 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
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
                        trackColor = Color(0xFFE2E8F0),
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
                    color = Color(0xFFD97706),
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onBuy,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E40AF),  // Primary blue
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE2E8F0),  // Light grey
                    disabledContentColor = Color(0xFF94A3B8)  // Grey text
                ),
                enabled = canAffordBuy && !item.backroomFull,
                shape = GameButtonStyles.Shape
            ) {
                Text(
                    if (item.backroomFull) "Backroom Full" else "Order (${item.casePackCost})",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

