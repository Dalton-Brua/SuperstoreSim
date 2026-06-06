package com.example.superstoresimulator.ui.components.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.state.RegisterUIState
import com.example.superstoresimulator.ui.state.RegistersUIState
import com.example.superstoresimulator.ui.state.StaffScheduleEntryUI
import com.example.superstoresimulator.ui.theme.CardBlue
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.InactiveGrey
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.yourapp.ui.theme.GameButtonStyles

/**
 * Card displayed on the home screen showing all owned registers.
 *
 * Each row lets the manager:
 * - Assign a hired cashier to this register via a dropdown.
 * - (Player-as-cashier) claim or release the register.
 *
 * When a new register is purchased its assignment dropdown is automatically opened.
 *
 * @param registersState       Aggregate register data.
 * @param cashierEntries       All hired cashiers with their current register assignments.
 * @param onAssignPlayer       Called when the player taps "Assign Me" / "Leave".
 * @param onAssignCashier      Called to pin (cashierId != null) or unpin (null) a cashier.
 * @param onPurchaseRegister   Called when the player taps "Buy Register".
 */
@Composable
fun RegistersCard(
    registersState: RegistersUIState,
    cashierEntries: List<StaffScheduleEntryUI> = emptyList(),
    onAssignPlayer: (registerId: Int?) -> Unit,
    onAssignCashier: (cashierId: Int?, registerId: Int) -> Unit = { _, _ -> },
    onPurchaseRegister: () -> Unit,
    onRegisterClick: (RegisterUIState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Track when the owned count increases so we can auto-open the new register's dropdown.
    var autoExpandRegisterId by remember { mutableStateOf<Int?>(null) }
    val lastKnownCount = remember { mutableIntStateOf(registersState.ownedCount) }

    LaunchedEffect(registersState.ownedCount) {
        if (registersState.ownedCount > lastKnownCount.intValue) {
            autoExpandRegisterId = registersState.registers.lastOrNull()?.registerId
        }
        lastKnownCount.intValue = registersState.ownedCount
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PointOfSale,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Registers",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryDark,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBlue,
                ) {
                    Text(
                        text = "${registersState.ownedCount} / ${registersState.maxRegisters}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryDark,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Register rows ───────────────────────────────────────────────
            registersState.registers.forEachIndexed { index, reg ->
                if (index > 0) {
                    HorizontalDivider(
                        color = ChipSurface,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                RegisterRow(
                    reg = reg,
                    cashierEntries = cashierEntries,
                    isPlayerHere = reg.registerId == registersState.playerAssignedRegisterId,
                    playerAssignedElsewhere = registersState.playerAssignedRegisterId != null &&
                        reg.registerId != registersState.playerAssignedRegisterId,
                    autoExpandAssignment = autoExpandRegisterId == reg.registerId,
                    onAutoExpandConsumed = { autoExpandRegisterId = null },
                    onAssignPlayer = onAssignPlayer,
                    onAssignCashier = onAssignCashier,
                    onRegisterClick = onRegisterClick,
                )
            }

            // ── Buy register button ─────────────────────────────────────────
            if (registersState.ownedCount < registersState.maxRegisters) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = ChipSurface)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPurchaseRegister,
                    enabled = registersState.canPurchase,
                    colors = GameButtonStyles.primaryBlueColor(),
                    shape = GameButtonStyles.Shape,
                    border = GameButtonStyles.PrimaryBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Buy Register  (${registersState.nextRegisterCost})",
                        color = TextWhite,
                    )
                }
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun RegisterRow(
    reg: RegisterUIState,
    cashierEntries: List<StaffScheduleEntryUI>,
    isPlayerHere: Boolean,
    playerAssignedElsewhere: Boolean,
    autoExpandAssignment: Boolean,
    onAutoExpandConsumed: () -> Unit,
    onAssignPlayer: (registerId: Int?) -> Unit,
    onAssignCashier: (cashierId: Int?, registerId: Int) -> Unit,
    onRegisterClick: (RegisterUIState) -> Unit = {},
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            reg.transactionActive -> Secondary
            reg.isManned          -> Primary
            else                  -> InactiveGrey
        },
        label = "register_status",
    )

    // Assignment dropdown state — starts open when this row was just purchased.
    var assignMenuExpanded by remember(reg.registerId) { mutableStateOf(false) }

    LaunchedEffect(autoExpandAssignment) {
        if (autoExpandAssignment) {
            assignMenuExpanded = true
            onAutoExpandConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Status / name / player button row ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onRegisterClick(reg)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Register ${reg.registerId + 1}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = PrimaryDark,
                    )
                    if (reg.transactionActive) {
                        StatusChip(
                            label = "Active",
                            chipColor = SuccessChipSurface,
                            textColor = SuccessTextDark,
                        )
                    }
                }
                val staffLine = when {
                    isPlayerHere -> "You (Player)"
                    reg.assignedCashierName != null ->
                        if (reg.cashierOnShift) reg.assignedCashierName
                        else "${reg.assignedCashierName} (off shift)"
                    else -> "Auto-managed"
                }
                Text(
                    text = staffLine,
                    fontSize = 12.sp,
                    color = if (reg.isManned) TextSecondary else TextMuted,
                )
            }

            // Right side: Leave button (if player here) OR daily stats
            if (isPlayerHere) {
                OutlinedButton(
                    onClick = { onAssignPlayer(null) },
                    colors = GameButtonStyles.outlinedDestructiveColors(),
                    shape = GameButtonStyles.Shape,
                    border = GameButtonStyles.DestructiveBorder,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(Icons.Default.PersonOff, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Leave", fontSize = 12.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = reg.dailyRevenue.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryDark,
                    )
                    Text(
                        text = "${reg.dailyTransactions} tx",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        // ── Cashier assignment row ────────────────────────────────────────────
        if (cashierEntries.isNotEmpty() || reg.assignedCashierId != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Cashier:",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.width(56.dp),
                )
                Box {
                    OutlinedButton(
                        onClick = { assignMenuExpanded = true },
                        shape = GameButtonStyles.Shape,
                        colors = GameButtonStyles.outlinedPrimaryColors(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text(
                            text = when {
                                reg.assignedCashierName != null -> reg.assignedCashierName
                                else -> "Assign cashier…"
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }

                    DropdownMenu(
                        expanded = assignMenuExpanded,
                        onDismissRequest = { assignMenuExpanded = false },
                    ) {
                        // Unpin option
                        if (reg.assignedCashierId != null) {
                            DropdownMenuItem(
                                text = { Text("Remove assignment", color = Destructive) },
                                onClick = {
                                    onAssignCashier(null, reg.registerId)
                                    assignMenuExpanded = false
                                },
                            )
                            HorizontalDivider()
                        }

                        // Available cashiers (not pinned to another register)
                        val available = cashierEntries.filter { c ->
                            c.assignedRegisterId == null ||
                                c.assignedRegisterId == reg.registerId
                        }
                        if (available.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No cashiers available", color = TextMuted) },
                                onClick = { assignMenuExpanded = false },
                                enabled = false,
                            )
                        } else {
                            available.forEach { cashier ->
                                val isHere = cashier.assignedRegisterId == reg.registerId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = cashier.entityName +
                                                    if (isHere) " ✓" else "",
                                                fontWeight = if (isHere)
                                                    FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                            Text(
                                                text = cashier.entityTypeName,
                                                fontSize = 11.sp,
                                                color = TextMuted,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onAssignCashier(cashier.entityId, reg.registerId)
                                        assignMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, chipColor: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = chipColor) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

