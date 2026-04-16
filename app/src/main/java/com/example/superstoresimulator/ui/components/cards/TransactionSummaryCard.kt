package com.example.superstoresimulator.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.CardBlue
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.ProgressBarIndicator

@Composable
fun TransactionSummaryCard(
    transactionId: Int,
    totalRung: Int,
    totalRequired: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBlue)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Transaction #$transactionId", fontWeight = FontWeight.Bold, color = PrimaryDark)
            val progress = if (totalRequired == 0) 0f else totalRung.toFloat() / totalRequired
            Text("$totalRung/$totalRequired items", color = PrimaryDark)
            LinearProgressIndicator(
                progress = { progress },
                color = ProgressBarIndicator,
                trackColor = ProgressBarTrack
            )
            Text("Tap to view details", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

