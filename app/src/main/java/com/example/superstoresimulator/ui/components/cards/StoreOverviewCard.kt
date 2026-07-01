package com.example.superstoresimulator.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.state.ReputationUIState
import com.example.superstoresimulator.ui.theme.Amber
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun StoreOverviewCard(
    cash: Money,
    totalEmployees: Int,
    modifier: Modifier = Modifier,
    activeEmployees: Int = totalEmployees,
    avgZoneScore: Float = 1.0f,
    reputationUiState: ReputationUIState? = null,
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
            if (reputationUiState?.isActive == true) {
                Spacer(Modifier.height(12.dp))
                ReputationMetric(reputationUiState)
            }
        }
    }
}

@Composable
private fun CashMetric(cash: Money) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Cash", color = TextSecondary, fontSize = 14.sp)
        Text(
            cash.toString(),
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
private fun ReputationMetric(rep: ReputationUIState) {
    val repPct = rep.reputationScore.toInt()
    val repColor = when {
        rep.reputationScore >= 125f -> Secondary
        rep.reputationScore >= 75f  -> Amber
        else                        -> Destructive
    }
    val trafficDeltaPct = ((rep.trafficMultiplier - 1f) * 100).toInt()
    val trafficSign = if (trafficDeltaPct >= 0) "+" else ""
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Reputation: $repPct%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = repColor)
        Spacer(Modifier.width(12.dp))
        Text("Traffic $trafficSign${trafficDeltaPct}%", fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun ZoneMetric(avgZoneScore: Float) {
    val pct = (avgZoneScore * 100).toInt().coerceIn(0, 100)
    val zoneColor = when {
        pct >= 80 -> Secondary
        pct >= 50 -> Amber
        else -> Destructive
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
