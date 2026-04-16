package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.ui.theme.*
import java.util.Locale

/**
 * Modal dialog shown automatically at midnight when a game day rolls over.
 * Displays a summary of the day's performance across sales, customers, and inventory.
 */
@Composable
fun EndOfDayReportDialog(
    report: DailyMetrics,
    onDismiss: () -> Unit,
    /** True when the dialog is shown automatically at midnight (shows "Start Day" + pauses time).
     *  False when opened manually from the Metrics screen (shows plain "Close"). */
    isAutoShown: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryDark, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "End of Day Report",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Day ${report.dayNumber + 1} — ${report.dayOfWeekName}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Sales section ─────────────────────────────────────────
                SectionHeader("💰 Sales")
                StatRow(Icons.Default.AttachMoney, "Revenue",           report.revenue.toString(),     highlight = true)
                StatRow(Icons.Default.Receipt,      "Subtotal",          report.subtotal.toString())
                StatRow(Icons.Default.AccountBalance,"Tax Collected",    report.taxCollected.toString())
                StatRow(Icons.Default.ShoppingCart, "Transactions",      report.transactionsCompleted.toString())
                StatRow(Icons.AutoMirrored.Filled.TrendingUp, "Avg. Sale Value", report.averageTransactionValue.toString())

                if (report.refundsProcessed > 0) {
                    Spacer(Modifier.height(4.dp))
                    StatRow(Icons.AutoMirrored.Filled.Undo, "Refunds Issued",
                        "${report.refundsProcessed} (${report.refundAmount})",
                        tint = Color(0xFFE74C3C))
                }

                if (report.lostRevenue.cents > 0) {
                    Spacer(Modifier.height(4.dp))
                    StatRow(
                        Icons.Default.RemoveShoppingCart,
                        "Lost Revenue (OOS)",
                        "${report.itemsLostToOutOfStock} units · ${report.lostRevenue}",
                        tint = Color(0xFFB91C1C)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Operating Costs section ──────────────────────────────
                SectionHeader("💸 Operating Costs")
                StatRow(Icons.Default.Home, "Daily Rent", "-${report.rentPaid}", tint = Color(0xFFE74C3C))
                StatRow(Icons.Default.People, "Staff Wages", "-${report.wagesPaid}", tint = Color(0xFFE74C3C))
                
                // Net revenue calculation
                val netAfterCosts = report.netRevenue
                StatRow(
                    Icons.Default.AccountBalance,
                    "Net Revenue",
                    netAfterCosts.toString(),
                    highlight = true,
                    tint = if (netAfterCosts.cents >= 0) Color(0xFF27AE60) else Color(0xFFE74C3C)
                )

                Spacer(Modifier.height(12.dp))

                // ── Customers section ─────────────────────────────────────
                SectionHeader("👥 Customers")
                StatRow(Icons.Default.People,       "Customers Served",  report.customersServed.toString())
                StatRow(Icons.Default.Inventory2,   "Items Sold",        "${report.itemsSold} units")
                StatRow(Icons.Default.BarChart,     "Avg. Basket Size",
                    String.format(Locale.US, "%.1f items", report.averageBasketSize))

                Spacer(Modifier.height(12.dp))

                // ── Inventory section ─────────────────────────────────────
                SectionHeader("📦 Inventory")
                StatRow(Icons.Default.MoveToInbox,  "Items Stocked",     "${report.itemsStocked} units")
                StatRow(Icons.Default.AddShoppingCart, "Items Ordered",  "${report.itemsOrdered} units")

                Spacer(Modifier.height(20.dp))

                // ── Dismiss button ────────────────────────────────────────
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isAutoShown) "Start Day ${report.dayNumber + 2}" else "Close",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
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
private fun StatRow(
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
            textAlign = TextAlign.End
        )
    }
}

