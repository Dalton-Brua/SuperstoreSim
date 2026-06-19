package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.metrics.OutOfStockEvent
import com.example.superstoresimulator.ui.theme.*

/**
 * Dialog showing every item that could not be rung up due to zero shelf stock on a given day.
 * Items are grouped by name; quantities and revenue lost are summed per item.
 */
@Composable
fun OutOfStockReportDialog(
    report: DailyMetrics,
    onDismiss: () -> Unit,
    /** Called with the item's ID when the player taps its name row. */
    onItemClick: (itemId: Int) -> Unit = {},
    itemNames: Map<Int, String> = emptyMap(),
) {
    // Group events by itemId, aggregating quantityLost and revenueLost
    val grouped: List<OutOfStockEvent> = remember(report.outOfStockEvents) {
        report.outOfStockEvents
            .groupBy { it.itemId }
            .map { (_, events) ->
                OutOfStockEvent(
                    itemId = events.first().itemId,
                    quantityLost = events.sumOf { it.quantityLost },
                    revenueLost = events.fold(Money.ZERO) { acc, e -> acc + e.revenueLost },
                )
            }
            .sortedByDescending { it.revenueLost.cents }
    }

    val totalLost = report.lostRevenue

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CriticalRed, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Out-of-Stock Report",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "Day ${report.dayNumber + 1} — ${report.dayOfWeekName}",
                            color = TextWhite.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (grouped.isEmpty()) {
                    // Shouldn't normally show, but handle gracefully
                    Text(
                        text = "No out-of-stock events recorded.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    // ── Column headers ────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Item",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Units",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(48.dp)
                        )
                        Text(
                            text = "Lost",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(72.dp)
                        )
                    }
                    HorizontalDivider(color = LightBackground, thickness = 1.dp)

                    // ── Item rows ─────────────────────────────────────────
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(grouped, key = { it.itemId }) { event ->
                            OutOfStockRow(
                                event,
                                displayName = itemNames[event.itemId] ?: event.itemName.ifEmpty { "Item ${event.itemId}" },
                                onItemClick = onItemClick,
                            )
                        }
                    }

                    HorizontalDivider(color = LightBackground, thickness = 1.dp)

                    // ── Totals row ────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.RemoveShoppingCart,
                                contentDescription = null,
                                tint = CriticalRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Total Lost Revenue",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }
                        Text(
                            text = totalLost.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CriticalRed
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close", color = TextWhite, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OutOfStockRow(
    event: OutOfStockEvent,
    displayName: String,
    onItemClick: (itemId: Int) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(event.itemId) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            fontSize = 13.sp,
            color = Primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "×${event.quantityLost}",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = event.revenueLost.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CriticalRed,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
    }
}
