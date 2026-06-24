package com.example.superstoresimulator.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.StoreManagerConfig
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.PrimaryLight
import com.example.superstoresimulator.ui.theme.TextPrimary
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun StoreManagerConfigDialog(
    config: StoreManagerConfig,
    onConfigChanged: (StoreManagerConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var autoHire by remember { mutableStateOf(config.autoHireEnabled) }
    var hireCashiers by remember { mutableStateOf(config.autoHireCashiers) }
    var hireStockers by remember { mutableStateOf(config.autoHireStockers) }
    var hireFresh by remember { mutableStateOf(config.autoHireFreshHandlers) }
    var hireOnBackroom by remember { mutableStateOf(config.hireStockerOnBackroomFull) }
    var hireOnZone by remember { mutableStateOf(config.hireStockerOnLowZoneScore) }
    var zoneThreshold by remember { mutableFloatStateOf(config.zoneScoreHireThreshold.toFloat()) }
    var fillSlots by remember { mutableStateOf(config.autoFillDeliverySlots) }
    var earlyTruck by remember { mutableStateOf(config.autoEarlyTruckEnabled) }
    var earlyPercent by remember { mutableFloatStateOf(config.earlyTruckOosPercent.toFloat()) }
    var buySlot by remember { mutableStateOf(config.autoBuyTruckSlotEnabled) }
    var buyThreshold by remember { mutableFloatStateOf(config.buySlotOosThreshold.toFloat()) }
    var autoPromote by remember { mutableStateOf(config.autoPromoteEnabled) }
    var autoTerminate by remember { mutableStateOf(config.autoTerminateEnabled) }
    var buyRegisters by remember { mutableStateOf(config.autoBuyRegistersEnabled) }
    var rebalance by remember { mutableStateOf(config.autoRebalanceShiftsEnabled) }
    var autoOptimizeSchedule by remember { mutableStateOf(config.autoOptimizeWeeklySchedule) }
    var minDays by remember { mutableFloatStateOf(config.minDaysPerWeek.toFloat()) }
    var maxDays by remember { mutableFloatStateOf(config.maxDaysPerWeek.toFloat()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Store Manager Settings",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                    fontSize = 16.sp,
                )
                Text(
                    "Configure autonomous actions your Store Manager performs at day rollover",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ── Staffing ──
                SectionHeader("Staffing")
                var autoHireExpanded by remember { mutableStateOf(false) }
                CollapsibleToggleRow(
                    label = "Auto-Hire Staff",
                    checked = autoHire,
                    onCheckedChange = { autoHire = it },
                    expanded = autoHireExpanded,
                    onExpandToggle = { autoHireExpanded = !autoHireExpanded },
                )
                AnimatedVisibility(
                    visible = autoHire && autoHireExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        ToggleRow("  Hire Cashiers", hireCashiers) { hireCashiers = it }
                        ToggleRow("  Hire Stockers", hireStockers) { hireStockers = it }
                        if (hireStockers) {
                            ToggleRow("    Backroom Full", hireOnBackroom) { hireOnBackroom = it }
                            ToggleRow("    Low Zone Score", hireOnZone) { hireOnZone = it }
                            if (hireOnZone) {
                                ConfigSlider(
                                    label = "Zone Score Threshold",
                                    value = zoneThreshold,
                                    range = 50f..95f,
                                    steps = 8,
                                    onValueChange = { zoneThreshold = it },
                                    valueSuffix = "% zone score",
                                )
                            }
                        }
                        ToggleRow("  Hire Fresh Handlers", hireFresh) { hireFresh = it }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ToggleRow("Auto-Promote (Lv5)", autoPromote) { autoPromote = it }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Terminate Idle Staff", autoTerminate) { autoTerminate = it }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                // ── Trucks ──
                SectionHeader("Truck Management")
                ToggleRow("Fill Free Delivery Slots", fillSlots) { fillSlots = it }

                Spacer(Modifier.height(8.dp))
                ToggleRow("Order Early Truck", earlyTruck) { earlyTruck = it }
                if (earlyTruck) {
                    ConfigSlider(
                        label = "OOS Threshold",
                        value = earlyPercent,
                        range = 1f..25f,
                        steps = 23,
                        onValueChange = { earlyPercent = it },
                        valueSuffix = "% items OOS",
                    )
                }

                Spacer(Modifier.height(8.dp))
                ToggleRow("Buy Extra Truck Slot", buySlot) { buySlot = it }
                if (buySlot) {
                    ConfigSlider(
                        label = "OOS Threshold",
                        value = buyThreshold,
                        range = 1f..30f,
                        steps = 28,
                        onValueChange = { buyThreshold = it },
                        valueSuffix = " items OOS",
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                // ── Weekly Scheduling ──
                SectionHeader("Weekly Scheduling")
                ToggleRow("Auto-Optimize Schedules", autoOptimizeSchedule) { autoOptimizeSchedule = it }
                if (autoOptimizeSchedule) {
                    ConfigSlider(
                        label = "Min Days/Week",
                        value = minDays,
                        range = 1f..5f,
                        steps = 3,
                        onValueChange = { minDays = it; if (maxDays < it) maxDays = it },
                        valueSuffix = " days",
                    )
                    ConfigSlider(
                        label = "Max Days/Week",
                        value = maxDays,
                        range = 1f..5f,
                        steps = 3,
                        onValueChange = { maxDays = it; if (minDays > it) minDays = it },
                        valueSuffix = " days",
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                // ── Operations ──
                SectionHeader("Operations")
                ToggleRow("Auto-Buy Registers", buyRegisters) { buyRegisters = it }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Rebalance Shifts", rebalance) { rebalance = it }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfigChanged(
                                StoreManagerConfig(
                                    autoHireEnabled = autoHire,
                                    autoHireCashiers = hireCashiers,
                                    autoHireStockers = hireStockers,
                                    autoHireFreshHandlers = hireFresh,
                                    hireStockerOnBackroomFull = hireOnBackroom,
                                    hireStockerOnLowZoneScore = hireOnZone,
                                    zoneScoreHireThreshold = zoneThreshold.toInt(),
                                    autoFillDeliverySlots = fillSlots,
                                    autoEarlyTruckEnabled = earlyTruck,
                                    earlyTruckOosPercent = earlyPercent.toInt(),
                                    autoBuyTruckSlotEnabled = buySlot,
                                    buySlotOosThreshold = buyThreshold.toInt(),
                                    autoBuyRegistersEnabled = buyRegisters,
                                    autoRebalanceShiftsEnabled = rebalance,
                                    autoPromoteEnabled = autoPromote,
                                    autoTerminateEnabled = autoTerminate,
                                    autoOptimizeWeeklySchedule = autoOptimizeSchedule,
                                    minDaysPerWeek = minDays.toInt(),
                                    maxDaysPerWeek = maxDays.toInt(),
                                )
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = PrimaryDark,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun CollapsibleToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onExpandToggle)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryDark,
                checkedTrackColor = PrimaryLight,
            )
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryDark,
                checkedTrackColor = PrimaryLight,
            )
        )
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueSuffix: String,
) {
    Text(
        "$label: ${value.toInt()}$valueSuffix",
        fontSize = 11.sp,
        color = TextSecondary,
        modifier = Modifier.padding(start = 8.dp)
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark),
    )
}
