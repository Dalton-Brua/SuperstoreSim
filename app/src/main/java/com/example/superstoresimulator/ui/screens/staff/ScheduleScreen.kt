package com.example.superstoresimulator.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.superstoresimulator.domain.Entities.EntityDef
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
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.ui.state.StaffScheduleEntryUI
import com.example.superstoresimulator.ui.theme.Amber
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.Emerald
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.PlaceholderSurface
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.ui.theme.Violet

private enum class ScheduleFilter(val label: String, val defKey: String?) {
    ALL("All", null),
    CASHIER("Cashiers", "cashier"),
    STOCKER("Stockers", "stocker"),
    FRESH_HANDLER("Fresh", "fresh_handler"),
    MANAGER("Managers", "manager"),
    ANALYST("Analysts", "market_analyst"),
}

private val GANTT_HOURS = 6..20
private const val GANTT_HOUR_COUNT = 15

private val ROLE_COLOR_CASHIER = Primary
private val ROLE_COLOR_STOCKER = Amber
private val ROLE_COLOR_FRESH = Emerald
private val ROLE_COLOR_MANAGER = Violet
private val ROLE_COLOR_ANALYST = Secondary

private val COVERAGE_RED = Destructive
private val COVERAGE_AMBER = Amber
private val COVERAGE_GREEN = Secondary

@Composable
fun ScheduleScreen(
    scheduleEntries: List<StaffScheduleEntryUI>,
    currentDayOfWeek: Int = 0,
    onUpdateShift: (entityId: Int, newStartHour: Int, newDuration: Int, workDays: Set<Int>) -> Unit,
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

    val filtered = filter.defKey?.let { key ->
        scheduleEntries.filter { it.entityDefKey == key }
    } ?: scheduleEntries

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
                            selectedLabelColor = TextWhite,
                            labelColor = ChipTextDark,
                        ),
                    )
                }
            }
        }

        // Role groups
        val roleOrder = listOf("cashier", "stocker", "fresh_handler", "manager", "market_analyst")
        val rolesToShow = if (filter == ScheduleFilter.ALL) roleOrder else listOf(filter.defKey!!)

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
                    currentDayOfWeek = currentDayOfWeek,
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
            onUpdateShift = { entityId, newStart, newDuration, workDays ->
                onUpdateShift(entityId, newStart, newDuration, workDays)
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
                    color = TextWhite,
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
    currentDayOfWeek: Int = 0,
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
            Spacer(Modifier.weight(1f))
            // Day-of-week dots
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                GameTime.SHORT_DAY_NAMES.forEachIndexed { index, name ->
                    val isScheduled = entry.workDays.isEmpty() || index in entry.workDays
                    val isToday = index == currentDayOfWeek
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                when {
                                    isScheduled && isToday -> roleColor
                                    isScheduled -> roleColor.copy(alpha = 0.3f)
                                    else -> PlaceholderSurface
                                },
                                RoundedCornerShape(7.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name.first().toString(),
                            fontSize = 7.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isScheduled) TextWhite else TextMuted,
                        )
                    }
                }
            }
            Text(
                text = "${entry.daysPerWeek}d",
                fontSize = 9.sp,
                color = TextMuted,
            )
        }

        // Gantt bar — full width, aligned to coverage row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(PlaceholderSurface, RoundedCornerShape(2.dp)),
        ) {
            if (entry.startHour != null && entry.endHour != null) {
                val duration = entry.endHour - entry.startHour
                val leadingSlots = entry.startHour - 6
                if (leadingSlots > 0) {
                    Spacer(Modifier.weight(leadingSlots.toFloat()))
                }
                Box(
                    modifier = Modifier
                        .weight(duration.toFloat())
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
                        color = TextWhite,
                    )
                }
                val trailingSlots = 21 - entry.endHour.coerceAtMost(21)
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
    onUpdateShift: (entityId: Int, newStartHour: Int, newDuration: Int, workDays: Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val initDuration = if (entry.startHour != null && entry.endHour != null)
        (entry.endHour - entry.startHour).coerceIn(2, 8) else 8
    var currentStart by remember(entry.entityId) { mutableStateOf(entry.startHour ?: 8) }
    var currentDuration by remember(entry.entityId) { mutableStateOf(initDuration) }
    var currentWorkDays by remember(entry.entityId) {
        mutableStateOf(entry.workDays.ifEmpty { (0..6).toSet() })
    }
    val maxStart = 21 - currentDuration

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
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
                    text = "${formatHour(currentStart)} – ${formatHour(currentStart + currentDuration)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
                Text(
                    text = "${currentDuration}hr shift · ${currentWorkDays.size}d/wk",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))

                // Work days toggles
                Text("Work Days", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GameTime.SHORT_DAY_NAMES.forEachIndexed { index, name ->
                        val selected = index in currentWorkDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                currentWorkDays = if (selected && currentWorkDays.size > 1) {
                                    currentWorkDays - index
                                } else if (!selected) {
                                    currentWorkDays + index
                                } else currentWorkDays
                            },
                            label = {
                                Text(
                                    text = name.take(1),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor = TextWhite,
                                containerColor = PlaceholderSurface,
                                labelColor = ChipTextDark,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Start time controls
                Text("Start Time", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { if (currentStart > 6) currentStart-- },
                        enabled = currentStart > 6,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (currentStart > 6) Primary.copy(alpha = 0.1f) else PlaceholderSurface,
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Earlier",
                            tint = if (currentStart > 6) Primary else TextMuted)
                    }

                    IconButton(
                        onClick = { if (currentStart < maxStart) currentStart++ },
                        enabled = currentStart < maxStart,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (currentStart < maxStart) Primary.copy(alpha = 0.1f) else PlaceholderSurface,
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Later",
                            tint = if (currentStart < maxStart) Primary else TextMuted)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Duration controls
                Text("Duration", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (currentDuration > 2) currentDuration--
                        },
                        enabled = currentDuration > 2,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (currentDuration > 2) Primary.copy(alpha = 0.1f) else PlaceholderSurface,
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Shorter",
                            tint = if (currentDuration > 2) Primary else TextMuted)
                    }

                    IconButton(
                        onClick = {
                            if (currentDuration < 8 && currentStart + currentDuration < 21) currentDuration++
                        },
                        enabled = currentDuration < 8 && currentStart + currentDuration < 21,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (currentDuration < 8 && currentStart + currentDuration < 21) Primary.copy(alpha = 0.1f) else PlaceholderSurface,
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Longer",
                            tint = if (currentDuration < 8 && currentStart + currentDuration < 21) Primary else TextMuted)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        val days = if (currentWorkDays.size == 7) emptySet() else currentWorkDays
                        onUpdateShift(entry.entityId, currentStart, currentDuration, days)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Done", color = TextWhite, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun coverageByHour(entries: List<StaffScheduleEntryUI>): Map<Int, Int> =
    (6..20).associateWith { hour ->
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

private fun roleDisplayName(key: String): String =
    EntityDef.allEntities.firstOrNull { it.key == key }
        ?.let { "${it.displayName.uppercase()}S" }
        ?: key.uppercase()

private fun roleColor(key: String): Color = when (key) {
    "cashier" -> ROLE_COLOR_CASHIER
    "stocker" -> ROLE_COLOR_STOCKER
    "fresh_handler" -> ROLE_COLOR_FRESH
    "manager" -> ROLE_COLOR_MANAGER
    "market_analyst" -> ROLE_COLOR_ANALYST
    else -> ROLE_COLOR_CASHIER
}
