package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.ui.theme.*

/**
 * Confirmation dialog shown before resetting the game.
 * Warns the user that all progress will be lost.
 */
@Composable
fun ResetGameConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            DangerSurface,
                            RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = ClosedRed,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Title
                Text(
                    text = "Reset Store?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                    textAlign = TextAlign.Center
                )

                // Warning message
                Text(
                    text = "This will permanently delete all your progress, including:\n\n" +
                            "• All money and revenue\n" +
                            "• Inventory and stock\n" +
                            "• Hired staff\n" +
                            "• Store upgrades and unlocks\n" +
                            "• Daily metrics history\n\n" +
                            "This action cannot be undone!",
                    fontSize = 14.sp,
                    color = TextTertiary,
                    textAlign = TextAlign.Start,
                    lineHeight = 20.sp
                )

                HorizontalDivider()

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    // Confirm button (danger style)
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClosedRed
                        )
                    ) {
                        Text("Reset Store", color = TextWhite)
                    }
                }
            }
        }
    }
}

