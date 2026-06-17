package com.example.superstoresimulator.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChipDefaults
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
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.state.DeliveryUIState
import com.example.superstoresimulator.ui.state.TruckOrderLineUI
import com.example.superstoresimulator.ui.state.TruckUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.CautionDark
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.SuccessAccent
import com.example.superstoresimulator.ui.theme.Teal
import com.example.superstoresimulator.ui.theme.Violet
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.domain.time.GameTime

@Composable
fun DeliveriesScreen(
    deliveries: DeliveryUIState,
    money: Money,
    currentDay: Int,
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit,
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit,
    onRequestEarlyTruck: () -> Unit,
    onTruckConfigChanged: (deliveryDays: Set<Int>, regularCap: Int, freshCap: Int) -> Unit = { _, _, _ -> },
    onPurchaseExtraTruckSlot: () -> Unit = {},
    onPurchaseTruckUpgrade: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Track which truck cards are expanded
    val expandedTrucks = remember { mutableStateMapOf<Int, Boolean>() }

    val truckConfig = deliveries.truckConfig
    var selectedDays by remember { mutableStateOf(truckConfig.deliveryDays) }

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

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Delivery Schedule Card ──────────────────────────────────
            item(key = "schedule_card") {
                DeliveryScheduleCard(
                    selectedDays = selectedDays,
                    maxTrucksPerWeek = deliveries.maxTrucksPerWeek,
                    freeTrucksPerWeek = deliveries.freeTrucksPerWeek,
                    extraSlotsUnlocked = deliveries.extraTruckSlotsUnlocked,
                    money = money,
                    onDayToggle = { index ->
                        val isSelected = index in selectedDays
                        val isLastSelected = selectedDays.size == 1 && isSelected
                        val atMax = selectedDays.size >= deliveries.maxTrucksPerWeek
                        val disabledByLimit = !isSelected && atMax
                        if (!isLastSelected && !disabledByLimit) {
                            val newDays = if (isSelected) selectedDays - index else selectedDays + index
                            selectedDays = newDays
                            onTruckConfigChanged(newDays, truckConfig.regularTruckCapacityCasePacks, truckConfig.freshTruckCapacityCasePacks)
                        }
                    },
                    onPurchaseExtraTruckSlot = onPurchaseExtraTruckSlot,
                )
            }
            // ── Fleet Upgrade Card ────────────────────────────────────────
            item(key = "fleet_upgrade_card") {
                FleetUpgradeCard(
                    currentTierName = deliveries.currentFleetTierName,
                    nextTierName = deliveries.nextFleetTierName,
                    nextTierCost = deliveries.nextFleetUpgradeCost,
                    isResearched = deliveries.fleetUpgradeResearched,
                    money = money,
                    onUpgrade = onPurchaseTruckUpgrade,
                )
            }
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
                        expanded = expandedTrucks[fresh.truckId] ?: false,
                        onToggleExpand = {
                            expandedTrucks[fresh.truckId] = !(expandedTrucks[fresh.truckId] ?: false)
                        },
                        onCancelOrderLine = onCancelOrderLine,
                        onDecrementOrderLine = onDecrementOrderLine,
                    )
                }
            }

            // ── Vendor Trucks ────────────────────────────────────────────
            if (deliveries.vendorTrucks.isNotEmpty()) {
                item(key = "vendor_header") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📦 Vendor Deliveries",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Violet,
                    )
                }
                items(deliveries.vendorTrucks, key = { "vendor_${it.vendorName}" }) { truck ->
                    val vendorKey = "vendor_${truck.vendorName}"
                    TruckCard(
                        truck = truck,
                        expanded = expandedTrucks[vendorKey.hashCode()] ?: false,
                        onToggleExpand = {
                            val k = vendorKey.hashCode()
                            expandedTrucks[k] = !(expandedTrucks[k] ?: false)
                        },
                        onCancelOrderLine = { _, _ -> },
                        onDecrementOrderLine = { _, _ -> },
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
                        text = "${GameTime.shortDayName(truck.arrivalDayOfWeek)} · Day ${truck.arrivalDay + 1}",
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
                    if (truck.isVendorTruck) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Violet,
                        ) {
                            Text(
                                text = truck.vendorName ?: "VENDOR",
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

            // Capacity fill bar (hidden for vendor trucks)
            if (!truck.isVendorTruck) {
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
            }

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
                    label = { Text("Cancel 1", fontSize = 11.sp, color = DestructiveDark) },
                )
            }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = false,
                onClick = { onCancel(line.itemId, truckId) },
                label = { Text("Cancel all", fontSize = 11.sp, color = DestructiveDark) },
            )
        }
    }
}

@Composable
private fun DeliveryScheduleCard(
    selectedDays: Set<Int>,
    maxTrucksPerWeek: Int,
    freeTrucksPerWeek: Int,
    extraSlotsUnlocked: Int,
    money: Money,
    onDayToggle: (Int) -> Unit,
    onPurchaseExtraTruckSlot: () -> Unit,
) {
    val atMaxDays = selectedDays.size >= maxTrucksPerWeek

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Delivery Schedule",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PrimaryDark,
            )
            Text(
                "Slots used: ${selectedDays.size} / $maxTrucksPerWeek" +
                    if (extraSlotsUnlocked > 0) " ($freeTrucksPerWeek free + $extraSlotsUnlocked purchased)" else " (free)",
                fontSize = 11.sp,
                color = if (atMaxDays) CautionDark else TextSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GameTime.SHORT_DAY_NAMES.forEachIndexed { index, label ->
                    val isSelected = index in selectedDays
                    val isLastSelected = selectedDays.size == 1 && isSelected
                    val disabledByLimit = !isSelected && atMaxDays
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDayToggle(index) },
                        label = { Text(label, fontSize = 11.sp) },
                        enabled = !isLastSelected && !disabledByLimit,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = TextWhite,
                            labelColor = ChipTextDark,
                            disabledContainerColor = ChipSurface,
                            disabledLabelColor = TextMuted,
                            disabledSelectedContainerColor = Primary.copy(alpha = 0.5f),
                        ),
                    )
                }
            }
            if (atMaxDays && maxTrucksPerWeek < 7) {
                Button(
                    onClick = onPurchaseExtraTruckSlot,
                    enabled = money >= TruckConfig.EXTRA_SLOT_COST,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal,
                        contentColor = TextWhite,
                        disabledContainerColor = ChipSurface,
                        disabledContentColor = TextMuted,
                    ),
                ) {
                    Text(
                        if (money >= TruckConfig.EXTRA_SLOT_COST) "🚛 Add Truck Slot (${TruckConfig.EXTRA_SLOT_COST})"
                        else "🚛 Add Truck Slot — need ${TruckConfig.EXTRA_SLOT_COST}",
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "Purchase an extra weekly delivery day beyond your free limit.",
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun FleetUpgradeCard(
    currentTierName: String,
    nextTierName: String?,
    nextTierCost: Money?,
    isResearched: Boolean,
    money: Money,
    onUpgrade: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Fleet Capacity",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PrimaryDark,
            )
            Text(
                "Current: $currentTierName",
                fontSize = 13.sp,
                color = TextSecondary,
            )
            if (nextTierName != null && nextTierCost != null) {
                if (isResearched) {
                    val canAfford = money >= nextTierCost
                    Button(
                        onClick = onUpgrade,
                        enabled = canAfford,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Teal,
                            contentColor = TextWhite,
                            disabledContainerColor = ChipSurface,
                            disabledContentColor = TextMuted,
                        ),
                    ) {
                        Text(
                            if (canAfford) "Upgrade to $nextTierName ($nextTierCost)"
                            else "Upgrade to $nextTierName — need $nextTierCost",
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    Text(
                        "Research fleet upgrade to unlock $nextTierName",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            } else {
                Text(
                    "Maximum fleet capacity",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessAccent,
                )
            }
        }
    }
}
