package com.example.superstoresimulator.ui.screens.staff

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.state.StaffScheduleEntryUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary

/**
 * Shows all hired employees with their current shift window and on-shift status.
 * Register assignment is managed on the home screen's Registers card.
 *
 * @param scheduleEntries Ordered list of all hired employees with shift data.
 * @param onUpdateShift   Called when the player adjusts a shift start hour.
 */
@Composable
fun ScheduleScreen(
    scheduleEntries: List<StaffScheduleEntryUI>,
    onUpdateShift: (entityId: Int, newStartHour: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Employee Schedules",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap ± to adjust shift windows. Shifts are always 8 hours long.",
                fontSize = 12.sp,
                color = TextMuted,
            )
        }

        items(scheduleEntries) { entry ->
            ScheduleEntryCard(
                entry = entry,
                onUpdateShift = onUpdateShift,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun ScheduleEntryCard(
    entry: StaffScheduleEntryUI,
    onUpdateShift: (entityId: Int, newStartHour: Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Name row ─────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = roleIcon(entry.entityTypeName),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.entityName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PrimaryDark,
                    )
                    Text(
                        text = entry.entityTypeName,
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                OnShiftBadge(isOnShift = entry.isOnShift)
            }

            // ── Shift time row ────────────────────────────────────────────
            val shiftLabel = if (entry.startHour != null && entry.endHour != null)
                "${formatHour(entry.startHour)} – ${formatHour(entry.endHour)}"
            else
                "Always on (no shift set)"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = shiftLabel,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val current = entry.startHour ?: 8
                    IconButton(
                        onClick = { onUpdateShift(entry.entityId, (current - 1).coerceAtLeast(6)) },
                        enabled = current > 6,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Earlier",
                            modifier = Modifier.size(16.dp),
                            tint = if (current > 6) Primary else TextMuted,
                        )
                    }
                    IconButton(
                        onClick = { onUpdateShift(entry.entityId, (current + 1).coerceAtMost(13)) },
                        enabled = current < 13,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Later",
                            modifier = Modifier.size(16.dp),
                            tint = if (current < 13) Primary else TextMuted,
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun OnShiftBadge(isOnShift: Boolean) {
    val chipColor = if (isOnShift) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
    val textColor = if (isOnShift) Color(0xFF166534) else Color(0xFF64748B)
    val dotColor  = if (isOnShift) Color(0xFF16A34A) else Color(0xFF94A3B8)
    Surface(shape = RoundedCornerShape(12.dp), color = chipColor) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Text(
                text = if (isOnShift) "On Shift" else "Off Shift",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }
    }
}

private fun formatHour(hour: Int): String = when {
    hour == 0  -> "12 AM"
    hour < 12  -> "$hour AM"
    hour == 12 -> "12 PM"
    else       -> "${hour - 12} PM"
}

private fun roleIcon(entityTypeName: String): ImageVector {
    val lower = entityTypeName.lowercase()
    return when {
        lower.contains("cashier") -> Icons.Default.Person
        lower.contains("fresh")   -> Icons.Default.Eco
        lower.contains("stocker") -> Icons.Default.Build
        else                      -> Icons.Default.Person
    }
}
