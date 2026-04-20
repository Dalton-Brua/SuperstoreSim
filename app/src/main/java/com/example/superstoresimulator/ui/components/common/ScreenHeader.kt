package com.example.superstoresimulator.ui.components.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextSecondary

/**
 * Reusable screen header component showing a title and optional subtitle/money display.
 * Used across all major screens for consistent styling.
 *
 * @param title The main screen title (e.g., "Inventory", "Staff Management", "Sales History")
 * @param subtitle Optional subtitle text (e.g., "Cash: $1,234.56")
 * @param money Optional Money to display (will show as "Cash: $XXX")
 * @param modifier Modifier for the entire header
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    money: Money? = null
) {
    Column(
        modifier = modifier
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryDark
        )
        
        // Show either subtitle or money, prioritizing subtitle if both provided
        when {
            subtitle != null -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    color = TextSecondary
                )
            }
            money != null -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Cash: $money",
                    fontSize = 16.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

