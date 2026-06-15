package com.example.superstoresimulator.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.superstoresimulator.ui.components.common.ActivityChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.staff.EmployeeActivity
import com.example.superstoresimulator.ui.state.StaffScheduleEntryUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.CriticalRed
import com.example.superstoresimulator.ui.theme.ErrorSurface
import com.example.superstoresimulator.ui.theme.InfoChipSurface
import com.example.superstoresimulator.ui.theme.PlaceholderSurface
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.WarningChipSurface
import com.example.superstoresimulator.ui.theme.WarningTextDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffActivityCard(
    scheduleEntries: List<StaffScheduleEntryUI>,
    employeeActivities: Map<Int, EmployeeActivity>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val nonCashierEntries = scheduleEntries.filter {
        !it.entityTypeName.lowercase().contains("cashier")
    }

    if (nonCashierEntries.isEmpty()) return

    val workingCount = nonCashierEntries.count { entry ->
        val activity = employeeActivities[entry.entityId]
        activity != null && activity != EmployeeActivity.IDLE &&
            activity != EmployeeActivity.OFF_SHIFT
    }
    val idleCount = nonCashierEntries.count { entry ->
        employeeActivities[entry.entityId] == EmployeeActivity.IDLE
    }
    val offShiftCount = nonCashierEntries.count { entry ->
        val activity = employeeActivities[entry.entityId]
        activity == null || activity == EmployeeActivity.OFF_SHIFT
    }

    // Group by role, then count activities within each role
    val roleGroups = nonCashierEntries.groupBy { it.entityTypeName }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Always-visible header ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Staff Activity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryDark,
                    )
                    val parts = buildList {
                        if (workingCount > 0) add("$workingCount working")
                        if (idleCount > 0) add("$idleCount idle")
                        if (offShiftCount > 0) add("$offShiftCount off shift")
                    }
                    Text(
                        text = parts.joinToString(" · "),
                        fontSize = 12.sp,
                        color = if (workingCount > 0) TextSecondary else TextMuted,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                )
            }

            // ── Expandable: aggregate by role ─────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    roleGroups.entries.forEachIndexed { index, (roleName, entries) ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = ChipSurface,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        } else {
                            HorizontalDivider(color = ChipSurface)
                            Spacer(Modifier.padding(top = 6.dp))
                        }

                        val activityCounts = entries
                            .map { employeeActivities[it.entityId] ?: EmployeeActivity.OFF_SHIFT }
                            .groupingBy { it }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${entries.size} ${roleName}${if (entries.size != 1) "s" else ""}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = PrimaryDark,
                                modifier = Modifier.width(120.dp),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                activityCounts.forEach { (activity, count) ->
                                    ActivityCountChip(activity, count)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCountChip(activity: EmployeeActivity, count: Int) {
    val label = when (activity) {
        EmployeeActivity.STOCKING -> "Stocking"
        EmployeeActivity.ZONING -> "Zoning"
        EmployeeActivity.HANDLING_FRESH -> "Fresh"
        EmployeeActivity.CASHIERING -> "Cashiering"
        EmployeeActivity.WAITING_FOR_CUSTOMER -> "Waiting"
        EmployeeActivity.RESEARCHING -> "Researching"
        EmployeeActivity.CONSULTING -> "Consulting"
        EmployeeActivity.IDLE -> "Idle"
        EmployeeActivity.OFF_SHIFT -> "Off shift"
    }
    ActivityChip("$count $label", activity)
}
