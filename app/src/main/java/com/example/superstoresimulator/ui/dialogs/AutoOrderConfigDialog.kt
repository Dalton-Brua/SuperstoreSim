package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.InfoChipSurface
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.PrimaryLight
import com.example.superstoresimulator.ui.theme.TextPrimary
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun AutoOrderConfigDialog(
    title: String,
    subtitle: String,
    enabled: Boolean,
    minStockThreshold: Int,
    casePacksPerItem: Int,
    onConfigChanged: (enabled: Boolean, threshold: Int, casePacks: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var localEnabled by remember { mutableStateOf(enabled) }
    var localThreshold by remember { mutableFloatStateOf(minStockThreshold.toFloat()) }
    var localPacks by remember { mutableFloatStateOf(casePacksPerItem.toFloat()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                    fontSize = 16.sp,
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Auto-Ordering", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Switch(
                        checked = localEnabled,
                        onCheckedChange = { localEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryDark,
                            checkedTrackColor = PrimaryLight,
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Min Stock Threshold: ${localThreshold.toInt()} items",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    "Auto-order triggers when total stock falls below this",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Slider(
                    value = localThreshold,
                    onValueChange = { localThreshold = it },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark),
                    enabled = localEnabled
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Case Packs Per Order: ${localPacks.toInt()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Slider(
                    value = localPacks,
                    onValueChange = { localPacks = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark),
                    enabled = localEnabled
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfigChanged(localEnabled, localThreshold.toInt(), localPacks.toInt())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun BasicAutoOrderInfoDialog(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var localEnabled by remember { mutableStateOf(enabled) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Auto-Reorder",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                    fontSize = 16.sp,
                )
                Text(
                    "Your Fast Stocker automatically reorders out-of-stock items at the end of each day.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = InfoChipSurface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("How it works:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ChipTextDark)
                        Spacer(Modifier.height(4.dp))
                        Text("• Scans inventory at end of day", fontSize = 11.sp, color = ChipTextDark)
                        Text("• Orders 1 case pack for any item at 0 stock", fontSize = 11.sp, color = ChipTextDark)
                        Text("• Arrives on next scheduled truck", fontSize = 11.sp, color = ChipTextDark)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = InfoChipSurface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Upgrade to Stocking Manager (T3) for:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ChipTextDark)
                        Spacer(Modifier.height(4.dp))
                        Text("• Configurable stock threshold", fontSize = 11.sp, color = ChipTextDark)
                        Text("• Multiple case packs per order", fontSize = 11.sp, color = ChipTextDark)
                        Text("• Proactive ordering throughout the day", fontSize = 11.sp, color = ChipTextDark)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Auto-Reorder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Switch(
                        checked = localEnabled,
                        onCheckedChange = { localEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryDark,
                            checkedTrackColor = PrimaryLight,
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onToggle(localEnabled)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
