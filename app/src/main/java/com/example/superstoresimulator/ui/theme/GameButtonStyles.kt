package com.yourapp.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.TextWhite

object GameButtonStyles {

    // Colors must be provided via @Composable functions
    @Composable
    fun primaryBlueColor(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Primary,
        contentColor = TextWhite
    )

    @Composable
    fun primaryGreenColor(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Secondary
    )

    @Composable
    fun destructiveColors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Destructive,
        contentColor = TextWhite
    )

    @Composable
    fun outlinedPrimaryColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = Primary
    )

    @Composable
    fun outlinedDestructiveColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = Destructive
    )

    // Non-composable values are fine as vals
    val PrimaryBorder = BorderStroke(1.dp, Primary)
    val DestructiveBorder = BorderStroke(1.dp, Destructive)

    val Shape = RoundedCornerShape(8.dp)
}
