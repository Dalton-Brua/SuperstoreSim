package com.example.superstoresimulator.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.ui.components.PlayerRoleButtons
import com.example.superstoresimulator.ui.components.buttons.PendingRefundsButton
import com.example.superstoresimulator.ui.components.panels.SettingsPanel
import com.example.superstoresimulator.ui.components.cards.RegistersCard
import com.example.superstoresimulator.ui.components.cards.StaffActivityCard
import com.example.superstoresimulator.ui.components.cards.StoreSizeCard
import com.example.superstoresimulator.ui.components.cards.StoreOverviewCard
import com.example.superstoresimulator.ui.components.cards.StorePricingCard
import com.example.superstoresimulator.ui.components.common.TimeDisplayBar
import com.example.superstoresimulator.ui.dialogs.PendingRefundsDialog
import com.example.superstoresimulator.ui.dialogs.RegisterDetailDialog
import com.example.superstoresimulator.domain.tutorial.TutorialManager
import com.example.superstoresimulator.ui.state.GameUiState
import com.example.superstoresimulator.ui.state.isFeatureVisible
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.DisabledGrey
import com.example.superstoresimulator.ui.theme.IconBlue
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.PlaceholderSurface
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Scrim
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreHomeScreen (
    state: GameUiState,
    onViewItem: (Int) -> Unit,
    onStoreNameChange: (String) -> Unit,
    itemDao: ItemDao,
    onNavigateToInventory: () -> Unit,
    modifier: Modifier = Modifier,
    onProcessRefundLine: (refundId: Int, itemId: Int, qty: Int) -> Unit = { _, _, _ -> },
    onSpeedChanged: (Float) -> Unit = {},
    onOpenStore: () -> Unit = {},
    onSetPlayerRole: (PlayerRole) -> Unit = {},
    onSkipDay: () -> Unit = {},
    onUpgradeStore: () -> Unit = {},
    onPurchaseBuilding: () -> Unit = {},
    onSave: () -> Unit = {},
    onReset: () -> Unit = {},
    onPurchaseRegister: () -> Unit = {},
    onAssignPlayerToRegister: (registerId: Int?) -> Unit = {},
    onAssignCashierToRegister: (cashierId: Int?, registerId: Int) -> Unit = { _, _ -> },
    onSetDefaultMarkup: (Int) -> Unit = {},
    onSetCategoryMarkup: (com.example.superstoresimulator.domain.items.ItemCategory, Int) -> Unit = { _, _ -> },
    contentPadding: PaddingValues = PaddingValues(),
) {
    var showPendingRefunds by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var selectedRegisterId by remember { mutableStateOf<Int?>(null) }

    Box {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(LightBackground),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 68.dp,  // Extra padding for header bar
                bottom = 12.dp
            )
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            // Time display
            item {
                if (state.time != null) {
                    TimeDisplayBar(
                        timeUI = state.time,
                        onSpeedChanged = onSpeedChanged,
                        onStoreStateClick = onOpenStore
                    )
                }
            }

            // Player role buttons (Cashier, Stocker) with progress bars
            item {
                if (state.time != null &&
                    state.tutorial.isFeatureVisible(TutorialManager.FEATURE_PLAYER_ROLES)
                ) {
                    PlayerRoleButtons(
                        currentRole = state.time.playerRole,
                        onRoleChanged = onSetPlayerRole,
                        playerCashierProgress = state.time.playerCashierProgress,
                        playerStockerProgress = state.time.playerStockerProgress,
                    )
                }
            }

            // Overview card
            item {
                StoreOverviewCard(
                    cash = state.dashboard.money,
                    totalEmployees = state.dashboard.totalStaff,
                    activeEmployees = state.dashboard.activeStaff,
                    avgZoneScore = state.dashboard.avgZoneScore,
                )
            }

            // Registers card (collapsible, always shows active count + queue)
            item {
                val cashierEntries = state.staff.scheduleEntries.filter {
                    it.entityTypeName.lowercase().contains("cashier")
                }
                RegistersCard(
                    registersState = state.registers,
                    cashierEntries = cashierEntries,
                    pendingCustomers = state.transactions.pendingCustomers,
                    onAssignPlayer = onAssignPlayerToRegister,
                    onAssignCashier = onAssignCashierToRegister,
                    onPurchaseRegister = onPurchaseRegister,
                    onRegisterClick = { register -> selectedRegisterId = register.registerId },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            // Staff activity card (all roles incl. cashiers)
            item {
                StaffActivityCard(
                    scheduleEntries = state.staff.scheduleEntries,
                    employeeActivities = state.staff.employeeActivities,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }



            // Store size card
            item {
                if (state.time != null) {
                    StoreSizeCard(
                        currentSize = state.time.currentStoreSize,
                        dailyRent = state.time.dailyRent,
                        dailyWages = state.time.dailyWages,
                        playerMoney = state.app.money,
                        researchedUpgrades = state.research.researchedUpgrades,
                        buildingOwned = state.time.buildingOwned,
                        onUpgrade = onUpgradeStore,
                        onPurchaseBuilding = onPurchaseBuilding,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // Store Pricing card — gated behind the category-pricing research
            if (state.tutorial.isFeatureVisible(TutorialManager.FEATURE_PRICING_UI)) {
                item {
                    StorePricingCard(
                        pricingState = state.pricing,
                        onSetDefaultMarkup = onSetDefaultMarkup,
                        onSetCategoryMarkup = onSetCategoryMarkup,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // Skip Day button — simulate the rest of the day instantly
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onSkipDay,
                        enabled = !(state.metrics.showEndOfDayReport),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryDark,
                            disabledContentColor = DisabledGrey
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip Day",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Skip Day", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Pending refunds button - only show when there are pending refunds
            if (state.transactions.pendingRefunds.isNotEmpty()) {
                item {
                    PendingRefundsButton(
                        onClick = { showPendingRefunds = true },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }

        // Settings panel drawer with overlay
        // Background overlay - fades in/out
        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Scrim.copy(alpha = 0.5f))
                    .clickable(
                        onClick = { settingsOpen = false },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
            )
        }
        
        // Settings panel - slides in/out from left
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            )
        ) {
            SettingsPanel(
                app = state.app,
                onStoreNameChange = onStoreNameChange,
                onClose = { settingsOpen = false },
                onSave = onSave,
                onReset = onReset,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }

        // Header with menu button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.app.storeName,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryDark
            )
            IconButton(
                onClick = { settingsOpen = !settingsOpen },
                colors = IconButtonDefaults.iconButtonColors(contentColor = PrimaryDark)) {
                Icon(Icons.Default.Menu, "Menu")
            }
        }

        // Dialogs
        val selectedRegisterLive = state.registers.registers.find { it.registerId == selectedRegisterId }
        if (selectedRegisterId != null && selectedRegisterLive != null) {
            RegisterDetailDialog(
                register = selectedRegisterLive,
                state = state,
                itemDao = itemDao,
                onDismiss = { selectedRegisterId = null },
                onAssignCashier = onAssignCashierToRegister,
                onAssignPlayer = onAssignPlayerToRegister,
            )
        }

        if (showPendingRefunds) {
            PendingRefundsDialog(
                pending = state.transactions.pendingRefunds,
                itemDao = itemDao,
                onDismiss = { showPendingRefunds = false },
                onProcessLine = onProcessRefundLine
            )
        }
    }
}
