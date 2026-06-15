package com.example.superstoresimulator.ui.components.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.staff.EmployeeActivity
import com.example.superstoresimulator.ui.theme.CriticalRed
import com.example.superstoresimulator.ui.theme.ErrorSurface
import com.example.superstoresimulator.ui.theme.InfoChipSurface
import com.example.superstoresimulator.ui.theme.PlaceholderSurface
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.WarningChipSurface
import com.example.superstoresimulator.ui.theme.WarningTextDark

/** Chip surface and text colors for an employee activity state. */
fun activityChipColors(activity: EmployeeActivity): Pair<Color, Color> = when (activity) {
    EmployeeActivity.CASHIERING -> SuccessChipSurface to SuccessTextDark
    EmployeeActivity.WAITING_FOR_CUSTOMER -> WarningChipSurface to WarningTextDark
    EmployeeActivity.STOCKING -> SuccessChipSurface to SuccessTextDark
    EmployeeActivity.ZONING -> InfoChipSurface to PrimaryDark
    EmployeeActivity.HANDLING_FRESH -> SuccessChipSurface to SuccessTextDark
    EmployeeActivity.RESEARCHING -> InfoChipSurface to PrimaryDark
    EmployeeActivity.CONSULTING -> InfoChipSurface to PrimaryDark
    EmployeeActivity.IDLE -> ErrorSurface to CriticalRed
    EmployeeActivity.OFF_SHIFT -> PlaceholderSurface to TextSecondary
}

/** Small rounded chip showing [text] colored by [activity]. */
@Composable
fun ActivityChip(text: String, activity: EmployeeActivity) {
    val (chipColor, textColor) = activityChipColors(activity)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = chipColor,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
