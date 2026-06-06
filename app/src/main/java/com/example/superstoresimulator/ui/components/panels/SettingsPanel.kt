package com.example.superstoresimulator.ui.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.ui.state.AppUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.CautionDark
import com.example.superstoresimulator.ui.theme.ClosedRed
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.PrimaryLight
import com.example.superstoresimulator.ui.theme.Teal
import com.example.superstoresimulator.ui.theme.TextPrimary
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextTertiary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.ui.dialogs.ResetGameConfirmationDialog

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun SettingsPanel(
    app: AppUIState,
    onStoreNameChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit = {},
    onReset: () -> Unit = {},
    freshAutoOrderEnabled: Boolean = true,
    freshMinStockThreshold: Int = 5,
    freshCasePacksPerItem: Int = 1,
    onFreshAutoOrderConfigChanged: (enabled: Boolean, threshold: Int, packs: Int) -> Unit = { _, _, _ -> },
    truckConfig: TruckConfig = TruckConfig(),
    currentStoreSize: StoreSize = StoreSize.MOM_AND_POP,
    onTruckConfigChanged: (deliveryDays: Set<Int>, regularCap: Int, freshCap: Int) -> Unit = { _, _, _ -> },
    onPurchaseExtraTruckSlot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .width(340.dp)
            .statusBarsPadding(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        var draftName by remember { mutableStateOf(app.storeName) }
        var showResetConfirmation by remember { mutableStateOf(false) }

        // Fresh auto-order settings
        var freshEnabled by remember { mutableStateOf(freshAutoOrderEnabled) }
        var freshMinStock by remember { mutableStateOf(freshMinStockThreshold.toFloat()) }
        var freshPacks by remember { mutableStateOf(freshCasePacksPerItem.toFloat()) }

        // Truck config settings
        var selectedDays by remember { mutableStateOf(truckConfig.deliveryDays) }
        var regularCap by remember { mutableStateOf(truckConfig.regularTruckCapacityCasePacks.toFloat()) }
        var freshCap by remember { mutableStateOf(truckConfig.freshTruckCapacityCasePacks.toFloat()) }

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

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    Button(onClick = { draftName = "Superstore" }) { Text("Reset") }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        draftName = app.storeName
                        onClose()
                    }) { Text("Cancel") }
                    Button(onClick = {
                        onStoreNameChange(draftName)
                        onClose()
                    }) { Text("Save") }
                }

                HorizontalDivider()

                // ── Fresh Auto-Order Settings ──────────────────────────────
                Text(
                    "Fresh Item Auto-Ordering",
                    fontWeight = FontWeight.Medium,
                    color = PrimaryDark,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Auto-Ordering", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Fresh handlers auto-order when idle", fontSize = 10.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = freshEnabled,
                        onCheckedChange = { v ->
                            freshEnabled = v
                            onFreshAutoOrderConfigChanged(v, freshMinStock.toInt(), freshPacks.toInt())
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryDark,
                            checkedTrackColor = PrimaryLight,
                        )
                    )
                }
                Text("Min Stock: ${freshMinStock.toInt()} items", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Slider(
                    value = freshMinStock,
                    onValueChange = { v ->
                        freshMinStock = v
                        onFreshAutoOrderConfigChanged(freshEnabled, v.toInt(), freshPacks.toInt())
                    },
                    valueRange = 1f..30f, steps = 28,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark),
                    enabled = freshEnabled
                )
                Text("Packs Per Order: ${freshPacks.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
                Slider(
                    value = freshPacks,
                    onValueChange = { v ->
                        freshPacks = v
                        onFreshAutoOrderConfigChanged(freshEnabled, freshMinStock.toInt(), v.toInt())
                    },
                    valueRange = 1f..10f, steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark),
                    enabled = freshEnabled
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ── Delivery Schedule ──────────────────────────────────────
                // Compute slot limits from params (not remembered — reactive to param changes)
                val freeSlotsForSize = TruckConfig.BASE_FREE_SLOTS + currentStoreSize.ordinal
                val maxTrucksPerWeek = freeSlotsForSize + truckConfig.extraTruckSlotsUnlocked
                val atMaxDays = selectedDays.size >= maxTrucksPerWeek

                Text(
                    "Delivery Schedule",
                    fontWeight = FontWeight.Medium,
                    color = PrimaryDark,
                    fontSize = 14.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Slots used: ${selectedDays.size} / $maxTrucksPerWeek" +
                            if (truckConfig.extraTruckSlotsUnlocked > 0) " (${freeSlotsForSize} free + ${truckConfig.extraTruckSlotsUnlocked} purchased)" else " (free)",
                        fontSize = 11.sp,
                        color = if (atMaxDays) CautionDark else TextSecondary,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val isSelected = index in selectedDays
                        val isLastSelected = selectedDays.size == 1 && isSelected
                        // Disable unselected days when at the max truck limit
                        val disabledByLimit = !isSelected && atMaxDays
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isLastSelected && !disabledByLimit) {
                                    val newDays = if (isSelected) selectedDays - index else selectedDays + index
                                    selectedDays = newDays
                                    onTruckConfigChanged(newDays, regularCap.toInt(), freshCap.toInt())
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            enabled = !isLastSelected && !disabledByLimit,
                        )
                    }
                }
                // Extra slot purchase button — shown when at the day limit
                if (atMaxDays) {
                    Button(
                        onClick = onPurchaseExtraTruckSlot,
                        enabled = app.money >= TruckConfig.EXTRA_SLOT_COST,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    ) {
                        Text(
                            "🚛 Add Truck Slot (${TruckConfig.EXTRA_SLOT_COST})",
                            fontSize = 12.sp,
                            color = TextWhite,
                        )
                    }
                    Text(
                        "Purchase an extra weekly delivery day beyond your free limit.",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    "Regular truck capacity: ${regularCap.toInt()} case packs",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                Slider(
                    value = regularCap,
                    onValueChange = { v ->
                        regularCap = v
                        onTruckConfigChanged(selectedDays, v.toInt(), freshCap.toInt())
                    },
                    valueRange = 100f..5000f, steps = 48,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark),
                )
                Text(
                    "Fresh truck capacity: ${freshCap.toInt()} case packs",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                Slider(
                    value = freshCap,
                    onValueChange = { v ->
                        freshCap = v
                        onTruckConfigChanged(selectedDays, regularCap.toInt(), v.toInt())
                    },
                    valueRange = 100f..2000f, steps = 19,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save Game") }
                    Button(
                        onClick = { showResetConfirmation = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ClosedRed)
                    ) { Text("Reset Store", color = TextWhite) }
                }
                Text(
                    "Tip: Changes to the store name are applied when you press Save.",
                    color = TextTertiary, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        if (showResetConfirmation) {
            ResetGameConfirmationDialog(
                onConfirm = { onReset(); onClose() },
                onDismiss = { showResetConfirmation = false }
            )
        }
    }
}
