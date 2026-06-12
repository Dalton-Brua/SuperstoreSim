package com.example.superstoresimulator.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.offline.OfflineState
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.navigation.BottomNavBar
import com.example.superstoresimulator.ui.navigation.rememberGameNavigationState
import com.example.superstoresimulator.ui.screens.offline.OfflineCatchUpScreen
import com.example.superstoresimulator.ui.viewmodels.GameViewModel

/**
 * Root composable for the whole game UI: collects ViewModel state, hosts the
 * scaffold with bottom navigation, the main screen pager, full-screen overlays,
 * top-level dialogs, and the offline catch-up overlay.
 */
@Composable
fun SuperstoreApp(
    viewModel: GameViewModel,
    itemDao: ItemDao,
) {
    // Fresh auto-order dialog states
    var showFreshBulkOrderDialog by remember { mutableStateOf(false) }
    var showIncompleteOrdersDialog by remember { mutableStateOf(false) }
    var showIncompleteNormalOrdersDialog by remember { mutableStateOf(false) }

    // Snackbar for truck order confirmations
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            // Only show when no snackbar is currently on screen.
            // The 1-slot DROP_OLDEST buffer in the ViewModel means at most one
            // pending message ever builds up, so this guard reliably prevents
            // duplicate "Order placed" banners from rapid / bulk orders.
            if (snackbarHostState.currentSnackbarData == null) {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // Collect UI state from ViewModel's StateFlow
    val uiState by viewModel.uiState.collectAsState()
    val offlineState by viewModel.offlineState.collectAsState()

    // Show loading indicator while uiState is null
    if (uiState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F8FF)),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading items...", fontSize = 18.sp, color = Color(0xFF1E40AF))
        }
        return
    }

    // Extract non-null uiState to avoid smart cast issues with delegated properties
    val state = uiState!!

    val navState = rememberGameNavigationState()
    val onEvent: (GameEvent) -> Unit = remember(viewModel) { viewModel::onEvent }

    Box(modifier = Modifier.statusBarsPadding()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                BottomNavBar(
                    current = navState.mainScreens[navState.pagerState.currentPage],
                    onSelect = { screen ->
                        // Disable navigation when overlay screens are open
                        if (navState.isOverlayOpen) {
                            return@BottomNavBar
                        }

                        // If clicking the current screen button, reset its state
                        if (navState.currentScreen == screen) {
                            when (screen) {
                                Screen.INVENTORY -> {
                                    // Reset inventory filters and trigger local state reset
                                    onEvent(GameEvent.SelectItemCategory(null))
                                    onEvent(GameEvent.FocusInventoryItem(null))
                                    navState.inventoryResetTrigger++  // Trigger reset of search bar and detail view
                                    navState.selectedInventoryItemId = null  // Close detail screen if open
                                }
                                Screen.STAFF -> {
                                    // Reset to Staff tab (tab 0)
                                    navState.selectedStaffTab = 0
                                }
                                else -> {
                                    // Other screens don't need reset yet
                                }
                            }
                        } else {
                            navState.navigateTo(screen)
                        }
                    },
                    pendingRefundsCount = state.app.pendingRefunds
                )
            }
        ) { paddingValues ->
            MainScreenPager(
                state = state,
                navState = navState,
                itemDao = itemDao,
                paddingValues = paddingValues,
                onEvent = onEvent,
                onFreshBulkOrder = { showFreshBulkOrderDialog = true },
                onViewIncompleteOrders = { showIncompleteOrdersDialog = true },
                onViewIncompleteNormalOrders = { showIncompleteNormalOrdersDialog = true },
            )
        }

        StaffEntityOverlay(
            state = state,
            navState = navState,
            onEvent = onEvent,
        )

        InventoryItemDetailOverlay(
            state = state,
            navState = navState,
            onEvent = onEvent,
            getInventoryStateForItem = viewModel::getInventoryStateForItem,
        )

        GameDialogs(
            state = state,
            showFreshBulkOrderDialog = showFreshBulkOrderDialog,
            onDismissFreshBulkOrder = { showFreshBulkOrderDialog = false },
            showIncompleteOrdersDialog = showIncompleteOrdersDialog,
            onDismissIncompleteOrders = { showIncompleteOrdersDialog = false },
            showIncompleteNormalOrdersDialog = showIncompleteNormalOrdersDialog,
            onDismissIncompleteNormalOrders = { showIncompleteNormalOrdersDialog = false },
            onEvent = onEvent,
        )

        // Offline catch-up overlay
        when (val offline = offlineState) {
            is OfflineState.CatchingUp -> {
                OfflineCatchUpScreen(
                    progress = offline.progress,
                    result = null,
                    onContinue = null,
                )
            }
            is OfflineState.Summary -> {
                OfflineCatchUpScreen(
                    progress = null,
                    result = offline.result,
                    onContinue = { viewModel.dismissOfflineSummary() },
                )
            }
            OfflineState.Idle -> { /* normal game */ }
        }
    }
}
