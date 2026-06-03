package com.example.superstoresimulator.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun StoreOverviewCard(
    cash: Money,
    totalEmployees: Int,
    modifier: Modifier = Modifier,
    activeEmployees: Int = totalEmployees,
    avgZoneScore: Float = 1.0f,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CashMetric(cash)
                EmployeeMetric(totalEmployees = totalEmployees, activeEmployees = activeEmployees)
                ZoneMetric(avgZoneScore)
            }
        }
    }
}

@Composable
private fun CashMetric(cash: Money) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Cash", color = TextSecondary, fontSize = 14.sp)
        Text(
            "$${String.format(java.util.Locale.US, "%.2f", cash.toDouble())}",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Primary
        )
    }
}

@Composable
private fun EmployeeMetric(totalEmployees: Int, activeEmployees: Int = totalEmployees) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.People, null, Modifier.size(20.dp), Primary)
            Spacer(Modifier.width(4.dp))
        }
        Text("$totalEmployees", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryDark)
        if (activeEmployees < totalEmployees) {
            Text("$activeEmployees active / $totalEmployees total", color = TextSecondary, fontSize = 12.sp)
        } else {
            Text("Employees", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ZoneMetric(avgZoneScore: Float) {
    val pct = (avgZoneScore * 100).toInt().coerceIn(0, 100)
    val zoneColor = when {
        pct >= 80 -> Color(0xFF22C55E)
        pct >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CleaningServices, null, Modifier.size(20.dp), zoneColor)
            Spacer(Modifier.width(4.dp))
        }
        Text("$pct%", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = zoneColor)
        Text("Appearance", color = TextSecondary, fontSize = 12.sp)
    }
}
