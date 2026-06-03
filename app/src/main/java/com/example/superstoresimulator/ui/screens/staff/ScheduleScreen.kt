package com.example.superstoresimulator.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.ui.state.StaffScheduleEntryUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary

private enum class ScheduleFilter(val label: String) {
    ALL("All"), CASHIER("Cashiers"), STOCKER("Stockers"), FRESH_HANDLER("Fresh"), MANAGER("Managers")
}

private val GANTT_HOURS = 6..21
private const val GANTT_HOUR_COUNT = 16

private val ROLE_COLOR_CASHIER = Color(0xFF3B82F6)
private val ROLE_COLOR_STOCKER = Color(0xFFF59E0B)
private val ROLE_COLOR_FRESH = Color(0xFF10B981)
private val ROLE_COLOR_MANAGER = Color(0xFF8B5CF6)

private val COVERAGE_RED = Color(0xFFEF4444)
private val COVERAGE_AMBER = Color(0xFFF59E0B)
private val COVERAGE_GREEN = Color(0xFF22C55E)

@Composable
fun ScheduleScreen(
    scheduleEntries: List<StaffScheduleEntryUI>,
    onUpdateShift: (entityId: Int, newStartHour: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(ScheduleFilter.ALL) }
    var editingEntry by remember { mutableStateOf<StaffScheduleEntryUI?>(null) }

    if (scheduleEntries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(LightBackground),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No employees yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                )
                Text(
                    text = "Hire staff from the Staff tab to see their schedules here.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )
            }
        }
        return
    }

    val filtered = when (filter) {
        ScheduleFilter.ALL -> scheduleEntries
        ScheduleFilter.CASHIER -> scheduleEntries.filter { it.entityDefKey == "cashier" }
        ScheduleFilter.STOCKER -> scheduleEntries.filter { it.entityDefKey == "stocker" }
        ScheduleFilter.FRESH_HANDLER -> scheduleEntries.filter { it.entityDefKey == "fresh_handler" }
        ScheduleFilter.MANAGER -> scheduleEntries.filter { it.entityDefKey == "manager" }
    }

    val grouped = filtered.groupBy { it.entityDefKey }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Filter chips
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ScheduleFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        // Role groups
        val roleOrder = listOf("cashier", "stocker", "fresh_handler", "manager")
        val rolesToShow = if (filter == ScheduleFilter.ALL) roleOrder else listOf(filter.name.lowercase())

        for (role in rolesToShow) {
            val entries = grouped[role] ?: continue
            val roleName = roleDisplayName(role)
            val roleColor = roleColor(role)

            // Group header
            item(key = "header_$role") {
                Text(
                    text = "$roleName (${entries.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }

            // Coverage row
            item(key = "coverage_$role") {
                val coverage = coverageByHour(entries)
                CoverageRow(coverage)
            }

            // Employee rows
            items(entries, key = { "entry_${it.entityId}" }) { entry ->
                GanttEmployeeRow(
                    entry = entry,
                    roleColor = roleColor,
                    onClick = { editingEntry = entry },
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    // Shift edit dialog
    val editing = editingEntry
    if (editing != null) {
        ShiftEditDialog(
            entry = editing,
            onUpdateShift = { entityId, newStart ->
                onUpdateShift(entityId, newStart)
                editingEntry = null
            },
            onDismiss = { editingEntry = null },
        )
    }
}

// ── Coverage Row ─────────────────────────────────────────────────────────────

@Composable
private fun CoverageRow(coverage: Map<Int, Int>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp)),
    ) {
        for (hour in GANTT_HOURS) {
            val count = coverage[hour] ?: 0
            val bg = when {
                count == 0 -> COVERAGE_RED
                count == 1 -> COVERAGE_AMBER
                else -> COVERAGE_GREEN
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
    // Hour labels
    Row(modifier = Modifier.fillMaxWidth()) {
        for (hour in GANTT_HOURS) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (hour < 10) "$hour" else "$hour",
                    fontSize = 8.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

// ── Gantt Employee Row ───────────────────────────────────────────────────────

@Composable
private fun GanttEmployeeRow(
    entry: StaffScheduleEntryUI,
    roleColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        // Name row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = entry.entityName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subLabel = buildString {
                append("Lv${entry.level}")
                if (entry.tierLabel.isNotEmpty()) append(" ${entry.tierLabel}")
            }
            Text(
                text = subLabel,
                fontSize = 10.sp,
                color = TextSecondary,
            )
        }

        // Gantt bar — full width, aligned to coverage row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(Color(0xFFF1F5F9), RoundedCornerShape(2.dp)),
        ) {
            if (entry.startHour != null && entry.endHour != null) {
                val leadingSlots = entry.startHour - 6
                if (leadingSlots > 0) {
                    Spacer(Modifier.weight(leadingSlots.toFloat()))
                }
                Box(
                    modifier = Modifier
                        .weight(8f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (entry.isOnShift) roleColor
                            else roleColor.copy(alpha = 0.4f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${formatHour(entry.startHour)}–${formatHour(entry.endHour)}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
                val trailingSlots = 21 - entry.endHour
                if (trailingSlots > 0) {
                    Spacer(Modifier.weight(trailingSlots.toFloat()))
                }
            }
        }
    }
}

// ── Shift Edit Dialog ────────────────────────────────────────────────────────

@Composable
private fun ShiftEditDialog(
    entry: StaffScheduleEntryUI,
    onUpdateShift: (entityId: Int, newStartHour: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentStart by remember(entry.entityId) {
        mutableStateOf(entry.startHour ?: 8)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Edit Shift — ${entry.entityName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "${formatHour(currentStart)} – ${formatHour(currentStart + 8)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { if (currentStart > 6) currentStart-- },
                        enabled = currentStart > 6,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (currentStart > 6) Primary.copy(alpha = 0.1f) else Color(0xFFF1F5F9),
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Earlier",
                            tint = if (currentStart > 6) Primary else TextMuted,
                        )
                    }

                    IconButton(
                        onClick = { if (currentStart < 13) currentStart++ },
                        enabled = currentStart < 13,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (currentStart < 13) Primary.copy(alpha = 0.1f) else Color(0xFFF1F5F9),
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Later",
                            tint = if (currentStart < 13) Primary else TextMuted,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onUpdateShift(entry.entityId, currentStart) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun coverageByHour(entries: List<StaffScheduleEntryUI>): Map<Int, Int> =
    (6..21).associateWith { hour ->
        entries.count { e ->
            e.startHour != null && e.endHour != null &&
                hour >= e.startHour && hour < e.endHour
        }
    }

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

private fun roleDisplayName(key: String): String = when (key) {
    "cashier" -> "CASHIERS"
    "stocker" -> "STOCKERS"
    "fresh_handler" -> "FRESH HANDLERS"
    "manager" -> "MANAGERS"
    else -> key.uppercase()
}

private fun roleColor(key: String): Color = when (key) {
    "cashier" -> ROLE_COLOR_CASHIER
    "stocker" -> ROLE_COLOR_STOCKER
    "fresh_handler" -> ROLE_COLOR_FRESH
    "manager" -> ROLE_COLOR_MANAGER
    else -> ROLE_COLOR_CASHIER
}
