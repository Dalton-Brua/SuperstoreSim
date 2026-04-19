package com.example.superstoresimulator.ui.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.state.AppUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextPrimary
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.dialogs.ResetGameConfirmationDialog

@Composable
fun SettingsPanel(
    app: AppUIState,
    onStoreNameChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit = {},
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Improved settings panel: local edit state, Save/Cancel, Reset, and read-only stats.
    Card(
        modifier = modifier
            .fillMaxHeight()
            .width(340.dp)
            .statusBarsPadding(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        // Local editable copy of the store name so changes can be reviewed before saving
        var draftName by remember { mutableStateOf(app.storeName) }
        
        // State for reset confirmation dialog
        var showResetConfirmation by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Store Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = TextSecondary)
                }
            }

            HorizontalDivider()

            // Main content
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Store Name", fontWeight = FontWeight.Medium, color = PrimaryDark)
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextSecondary,
                        cursorColor = PrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    maxLines = 1
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        draftName = "Superstore"
                    }) {
                        Text("Reset")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        // Cancel: discard local edits and close
                        draftName = app.storeName
                        onClose()
                    }) { Text("Cancel") }
                    Button(onClick = {
                        // Save and close
                        onStoreNameChange(draftName)
                        onClose()
                    }) { Text("Save") }
                }

                // Quick read-only stats to give context to settings
                HorizontalDivider()
                
                // Save Game and Reset Game buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Game")
                    }
                    
                    Button(
                        onClick = { showResetConfirmation = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE74C3C)
                        )
                    ) {
                        Text("Reset Store", color = Color.White)
                    }
                }
                
                // A small hint / help text
                Text(
                    "Tip: Changes to the store name are applied when you press Save. Use Reset to restore the default name.",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Show reset confirmation dialog
        if (showResetConfirmation) {
            ResetGameConfirmationDialog(
                onConfirm = {
                    onReset()
                    onClose()  // Close settings panel after reset
                },
                onDismiss = { showResetConfirmation = false }
            )
        }
    }
}

