package com.example.superstoresimulator.ui.screens.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.superstoresimulator.domain.offline.OfflineCatchUpResult

@Composable
fun OfflineSummaryDialog(
    result: OfflineCatchUpResult,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Welcome Back!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.height(4.dp))

                val hoursAway = result.gameMinutesSimulated / 60
                val daysText = if (result.daysCrossed > 0) "${result.daysCrossed} day${if (result.daysCrossed != 1) "s" else ""}" else ""
                val hoursText = "${hoursAway % 24}h"
                val timeText = if (daysText.isNotEmpty()) "$daysText $hoursText simulated" else "$hoursText simulated"
                Text(
                    text = timeText,
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                )

                Spacer(modifier = Modifier.height(16.dp))

                SummaryRow(Icons.Default.AttachMoney, "Revenue", result.revenue.toString(), Color(0xFF4ADE80))
                SummaryRow(Icons.Default.MoneyOff, "Expenses", result.expenses.toString(), Color(0xFFF87171))

                if (result.itemsExpired > 0) {
                    SummaryRow(Icons.Default.DeleteSweep, "Expired", "${result.itemsExpired} items", Color(0xFFFBBF24))
                }
                if (result.trucksArrived > 0) {
                    SummaryRow(Icons.Default.LocalShipping, "Deliveries", "${result.trucksArrived} truck${if (result.trucksArrived != 1) "s" else ""}", Color(0xFF60A5FA))
                }
                if (result.staffHired > 0) {
                    SummaryRow(Icons.Default.PersonAdd, "Auto-Hired", "${result.staffHired} staff", Color(0xFFA78BFA))
                }
                if (result.daysCrossed > 0) {
                    SummaryRow(Icons.Default.WbSunny, "Days Passed", "${result.daysCrossed}", Color(0xFFFDE68A))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 8.dp))
        Text(label, color = Color(0xFFCBD5E1), fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
