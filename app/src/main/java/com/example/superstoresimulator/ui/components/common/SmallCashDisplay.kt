package com.example.superstoresimulator.ui.components.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun SmallCashDisplay(money: Money) {
    Text(
        text = "Cash: $money",
        color = TextSecondary,
        fontSize = 16.sp
    )
}

