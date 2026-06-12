package com.example.superstoresimulator.ui.navigation

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.superstoresimulator.domain.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Holds all top-level navigation state: the current screen, pager position,
 * and overlay/detail selections that gate navigation.
 *
 * Programmatic navigation (nav bar clicks, cross-screen shortcuts) goes through
 * [navigateTo] so the pager animation and swipe-sync suppression live in one place.
 */
@Stable
class GameNavigationState(
    val mainScreens: List<Screen>,
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var currentScreen by mutableStateOf(Screen.GAME)
    var selectedStaffTab by mutableIntStateOf(0)
    var selectedInventoryItemId by mutableStateOf<Int?>(null)

    /** Incremented to tell the inventory screen to reset its local search/detail state. */
    var inventoryResetTrigger by mutableIntStateOf(0)

    /** Suppresses pager→screen sync while an animated programmatic scroll is in flight. */
    var isNavigatingProgrammatically by mutableStateOf(false)
        private set

    /** True when a full-screen overlay is open; gates nav bar clicks and pager swipes. */
    val isOverlayOpen: Boolean
        get() = currentScreen == Screen.STAFF_ENTITY_LIST || selectedInventoryItemId != null

    fun navigateTo(screen: Screen) {
        val targetIndex = mainScreens.indexOf(screen)
        if (targetIndex != -1) {
            isNavigatingProgrammatically = true
            currentScreen = screen
            coroutineScope.launch {
                pagerState.animateScrollToPage(targetIndex)
                // Clear flag after animation completes
                isNavigatingProgrammatically = false
            }
        }
    }

    /** Navigate to the Staff screen with the Unlocks tab preselected (Staff=0, Schedule=1, Unlocks=2). */
    fun navigateToUnlocks() {
        selectedStaffTab = 2
        navigateTo(Screen.STAFF)
    }
}

@Composable
fun rememberGameNavigationState(): GameNavigationState {
    val mainScreens = remember {
        listOf(Screen.GAME, Screen.INVENTORY, Screen.STAFF, Screen.HISTORY, Screen.METRICS)
    }
    val pagerState = rememberPagerState(pageCount = { mainScreens.size })
    val coroutineScope = rememberCoroutineScope()
    val navState = remember { GameNavigationState(mainScreens, pagerState, coroutineScope) }

    // Sync currentScreen with pager (only when user swipes, not when nav button clicked)
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!navState.isNavigatingProgrammatically && !pagerState.isScrollInProgress) {
            // User finished swiping to a new page
            navState.currentScreen = mainScreens[pagerState.currentPage]
        }
    }

    return navState
}
