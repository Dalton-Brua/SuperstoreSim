package com.example.superstoresimulator.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.state.DeliveryUIState
import com.example.superstoresimulator.ui.state.TruckOrderLineUI
import com.example.superstoresimulator.ui.state.TruckUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.CautionDark
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.SuccessAccent
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private fun dayOfWeekName(dow: Int) = dayNames.getOrElse(dow % 7) { "Day" }

@Composable
fun DeliveriesScreen(
    deliveries: DeliveryUIState,
    money: Money,
    currentDay: Int,
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit,
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit,
    onRequestEarlyTruck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track which truck cards are expanded
    val expandedTrucks = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(24.dp),
    ) {
        // Header
        ScreenHeader(
            title = "Deliveries",
            money = money,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // Empty state
        if (deliveries.regularTrucks.isEmpty() && deliveries.freshTruck == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No deliveries scheduled.\nPlace an order to see it here.",
                    fontSize = 16.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Fresh Truck ──────────────────────────────────────────────
            deliveries.freshTruck?.let { fresh ->
                item(key = "fresh_header") {
                    Text(
                        text = "🥦 Fresh Delivery",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessAccent,
                    )
                }
                item(key = "fresh_truck") {
                    TruckCard(
                        truck = fresh,
                        expanded = expandedTrucks[fresh.truckId] ?: true,
                        onToggleExpand = {
                            expandedTrucks[fresh.truckId] = !(expandedTrucks[fresh.truckId] ?: true)
                        },
                        onCancelOrderLine = onCancelOrderLine,
                        onDecrementOrderLine = onDecrementOrderLine,
                    )
                }
            }

            // ── Regular Trucks ───────────────────────────────────────────
            item(key = "regular_header") {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Upcoming Trucks",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark,
                    )
                    if (deliveries.earlyTruckAvailable && money >= deliveries.earlyTruckCost) {
                        Button(
                            onClick = onRequestEarlyTruck,
                            colors = ButtonDefaults.buttonColors(containerColor = CautionDark),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "⚡ Early Truck (${deliveries.earlyTruckCost})",
                                fontSize = 12.sp,
                                color = TextWhite,
                            )
                        }
                    }
                }
            }

            if (deliveries.regularTrucks.isEmpty()) {
                item(key = "no_regular") {
                    Text(
                        text = "No regular trucks scheduled.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(deliveries.regularTrucks, key = { it.truckId }) { truck ->
                    TruckCard(
                        truck = truck,
                        expanded = expandedTrucks[truck.truckId] ?: false,
                        onToggleExpand = {
                            expandedTrucks[truck.truckId] = !(expandedTrucks[truck.truckId] ?: false)
                        },
                        onCancelOrderLine = onCancelOrderLine,
                        onDecrementOrderLine = onDecrementOrderLine,
                    )
                }
            }
        }
    }
}

@Composable
private fun TruckCard(
    truck: TruckUIState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit,
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onToggleExpand,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Truck header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${dayOfWeekName(truck.arrivalDayOfWeek)} · Day ${truck.arrivalDay + 1}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = PrimaryDark,
                    )
                    if (truck.isEarlyTruck) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CautionDark,
                        ) {
                            Text(
                                text = "EARLY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (truck.isFreshTruck) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessAccent,
                        ) {
                            Text(
                                text = "FRESH",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }

            // Capacity fill bar
            val fillFraction = if (truck.capacityTotal > 0)
                truck.capacityUsed.toFloat() / truck.capacityTotal else 0f
            LinearProgressIndicator(
                progress = { fillFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = Primary,
                trackColor = ProgressBarTrack,
            )
            Text(
                text = "${truck.capacityUsed} / ${truck.capacityTotal} case packs",
                fontSize = 12.sp,
                color = TextSecondary,
            )

            // Collapsible order lines
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                if (truck.orderLines.isEmpty()) {
                    Text(
                        text = "No items on this truck.",
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                } else {
                    truck.orderLines.forEach { line ->
                        OrderLineRow(
                            line = line,
                            truckId = truck.truckId,
                            onCancel = onCancelOrderLine,
                            onDecrementOrderLine = onDecrementOrderLine,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderLineRow(
    line: TruckOrderLineUI,
    truckId: Int,
    onCancel: (itemId: Int, truckId: Int) -> Unit,
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.itemName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ChipTextDark,
            )
            Text(
                text = "${line.casePacks} cases · ${line.quantity} units",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        if (line.canCancel) {
            Spacer(Modifier.width(8.dp))
            if (line.casePacks > 1) {
                FilterChip(
                    selected = false,
                    onClick = { onDecrementOrderLine(line.itemId, truckId) },
                    label = { Text("Cancel 1", fontSize = 11.sp) },
                )
            }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = false,
                onClick = { onCancel(line.itemId, truckId) },
                label = { Text("Cancel all", fontSize = 11.sp) },
            )
        }
    }
}

