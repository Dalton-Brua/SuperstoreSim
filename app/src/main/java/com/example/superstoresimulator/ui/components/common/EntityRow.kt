package com.example.superstoresimulator.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun EntityRow(
    entityDef: EntityDef,
    canAfford: Boolean,
    onBuy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entityDef.displayName, fontWeight = FontWeight.Medium)
            Text(
                entityDef.description,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        Button(
            onClick = onBuy,
            enabled = canAfford
        ) {
            Text("Hire")
        }
    }
}

