package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.metrics.WeeklyReport
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.ui.theme.*
import java.util.Locale

@Composable
fun EndOfWeekReportDialog(
    report: WeeklyReport,
    onDismiss: () -> Unit,
) {
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
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryDark, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Weekly Summary",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Day ${report.startDay + 1} — Day ${report.endDay} (${report.daysSimulated} days)",
                            color = TextWhite.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Balance section
                WeekSectionHeader("💵 Balance")
                WeekStatRow(Icons.Default.AccountBalanceWallet, "Starting Balance", report.startingBalance.toString())
                WeekStatRow(Icons.Default.AccountBalanceWallet, "Ending Balance", report.endingBalance.toString())
                WeekStatRow(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    "Change",
                    (if (report.balanceChange.cents >= 0) "+" else "") + report.balanceChange.toString(),
                    highlight = true,
                    tint = if (report.balanceChange.cents >= 0) PositiveGreen else ClosedRed
                )

                Spacer(Modifier.height(12.dp))

                // Sales section
                WeekSectionHeader("💰 Sales")
                WeekStatRow(Icons.Default.AttachMoney, "Revenue", report.totalRevenue.toString(), highlight = true)
                WeekStatRow(Icons.Default.Receipt, "Subtotal", report.totalSubtotal.toString())
                WeekStatRow(Icons.Default.AccountBalance, "Tax Collected", report.totalTaxCollected.toString())
                WeekStatRow(Icons.Default.ShoppingCart, "Transactions", report.totalTransactions.toString())
                WeekStatRow(Icons.AutoMirrored.Filled.TrendingUp, "Avg. Sale Value", report.averageTransactionValue.toString())

                if (report.totalLostRevenue.cents > 0) {
                    Spacer(Modifier.height(4.dp))
                    WeekStatRow(
                        Icons.Default.RemoveShoppingCart,
                        "Lost Revenue (OOS)",
                        "${report.totalItemsLostToOOS} units · ${report.totalLostRevenue}",
                        tint = CriticalRed
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Operating Costs section
                WeekSectionHeader("💸 Operating Costs")
                WeekStatRow(Icons.Default.Home, "Rent", "-${report.totalRentPaid}", tint = ClosedRed)
                WeekStatRow(Icons.Default.People, "Staff Wages", "-${report.totalWagesPaid}", tint = ClosedRed)
                WeekStatRow(
                    Icons.Default.AccountBalance,
                    "Net Revenue",
                    report.totalNetRevenue.toString(),
                    highlight = true,
                    tint = if (report.totalNetRevenue.cents >= 0) PositiveGreen else ClosedRed
                )

                Spacer(Modifier.height(12.dp))

                // Shrinkage section
                if (report.totalItemsExpired > 0) {
                    WeekSectionHeader("🗑️ Shrinkage")
                    WeekStatRow(Icons.Default.Delete, "Items Expired", "${report.totalItemsExpired} units", tint = ClosedRed)
                    WeekStatRow(Icons.Default.AttachMoney, "Waste Cost", "-${report.totalExpiredWasteCost}", tint = ClosedRed)
                    Spacer(Modifier.height(12.dp))
                }

                // Customers section
                WeekSectionHeader("👥 Customers")
                WeekStatRow(Icons.Default.People, "Customers Served", report.totalCustomersServed.toString())
                WeekStatRow(Icons.Default.Inventory2, "Items Sold", "${report.totalItemsSold} units")
                WeekStatRow(
                    Icons.Default.BarChart, "Avg. Basket Size",
                    String.format(Locale.US, "%.1f items", report.averageBasketSize)
                )

                Spacer(Modifier.height(12.dp))

                // Inventory section
                WeekSectionHeader("📦 Inventory")
                WeekStatRow(Icons.Default.MoveToInbox, "Cases Stocked", "${report.totalItemsStocked}")
                WeekStatRow(Icons.Default.AddShoppingCart, "Items Ordered", "${report.totalItemsOrdered} units")
                if (report.totalDeliveredTrucks > 0) {
                    WeekStatRow(Icons.Default.LocalShipping, "Trucks Received", "${report.totalDeliveredTrucks}")
                }

                Spacer(Modifier.height(12.dp))

                // Daily breakdown (expandable)
                if (report.dailyReports.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LightBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📊 Daily Breakdown",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = PrimaryDark
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                    tint = PrimaryDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = PrimaryDark.copy(alpha = 0.2f), thickness = 1.dp)
                                Spacer(Modifier.height(8.dp))
                                report.dailyReports.forEach { day ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Day ${day.dayNumber + 1} (${day.dayOfWeekName})",
                                            fontSize = 12.sp,
                                            color = TextNeutralDark,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "Rev: ${day.revenue}",
                                                fontSize = 11.sp,
                                                color = PositiveGreen,
                                            )
                                            Text(
                                                "Net: ${day.netRevenue}",
                                                fontSize = 11.sp,
                                                color = if (day.netRevenue.cents >= 0) TextNeutralDark else ClosedRed,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Dismiss button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Continue — Day ${report.endDay + 1}",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = PrimaryDark,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
    HorizontalDivider(color = CardBlue, thickness = 1.dp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun WeekStatRow(
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean = false,
    tint: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) PrimaryDark else TextDark,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
