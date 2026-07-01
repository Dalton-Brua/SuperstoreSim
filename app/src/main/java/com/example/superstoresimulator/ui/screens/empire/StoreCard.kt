package com.example.superstoresimulator.ui.screens.empire

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.state.StoreUI
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.ErrorSurface
import com.example.superstoresimulator.ui.theme.PositiveGreen
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.SurfaceSubtle
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.ui.theme.Violet

/**
 * Per-store summary card inside a [RegionSection]. Tapping opens the
 * [StoreDashboard] dialog.
 */
@Composable
fun StoreCard(
    store: StoreUI,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dashboardOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { dashboardOpen = true },
        colors = CardDefaults.cardColors(containerColor = SurfaceSubtle),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: name + size + direction badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = store.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PrimaryDark,
                    )
                    Text(
                        text = store.size.displayName,
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DirectionBadge(store.direction.displayName)
                    if (store.managedByManager) {
                        Surface(color = Violet, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "MGR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Today's metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricCell("Customers", store.today.customers.toString())
                MetricCell("Revenue", store.today.revenue.toString())
                MetricCell(
                    label = "Net",
                    value = store.today.netProfit.toString(),
                    valueColor = if (store.unprofitable) Destructive else PositiveGreen,
                )
                MetricCell("Traffic/goal", formatMult(store.today.trafficVsGoal))
            }

            if (store.unprofitable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorSurface, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Destructive,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    Text(
                        text = "Running at a loss",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Destructive,
                    )
                }
            }
        }
    }

    if (dashboardOpen) {
        StoreDashboard(
            store = store,
            onEvent = onEvent,
            onDismiss = { dashboardOpen = false },
        )
    }
}

@Composable
private fun DirectionBadge(label: String) {
    Surface(color = Primary, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = PrimaryDark,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = valueColor)
        Text(label, fontSize = 10.sp, color = SubtleText)
    }
}

@Composable
internal fun MetricChip(label: String) {
    Surface(color = ChipSurface, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = ChipTextDark,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
