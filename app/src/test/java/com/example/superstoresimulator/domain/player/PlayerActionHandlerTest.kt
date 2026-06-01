package com.example.superstoresimulator.domain.player

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.store.StoreConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PlayerActionHandler].
 *
 * [PlayerActionHandler] is a pure sub-system: [setPlayerRole] receives a [GameState]
 * and returns a new [GameState]; [calculateCashierWork] and [calculateStockerWork]
 * receive a [GameState] and return a [PlayerWorkResult]. No FakeItemDao or GameEngine
 * required — [GameState] is constructed directly with named parameters.
 *
 * Accumulator rates (from companion constants):
 *   Cashier : 0.5 items/second
 *   Stocker : 0.3 case-packs/second
 *
 * Floating-point comparisons use a tolerance of 1e-4 to guard against IEEE 754
 * rounding differences across platforms.
 *
 * Covers:
 *  [setPlayerRole]
 *   - NONE → CASHIER (activates role)
 *   - CASHIER → NONE (same role passed again toggles off)
 *   - STOCKER → NONE (same toggle for stocker)
 *   - CASHIER → STOCKER (switches between active roles)
 *   - Both progress accumulators are zeroed on every role change
 *   - Unrelated GameState fields are not modified
 *
 *  [calculateCashierWork]
 *   - Returns 0 actions when delta too small to reach 1.0
 *   - Returns correct action count when accumulated progress crosses 1.0
 *   - Adds to existing playerCashierProgress (does not start from zero)
 *   - Returns correct fractional newProgress after deducting whole actions
 *   - Respects game-speed multiplier
 *   - Returns multiple actions when progress crosses 2.0+
 *
 *  [calculateStockerWork]
 *   - Returns 0 actions when delta too small to reach 1.0
 *   - Returns correct action count when accumulated progress crosses 1.0
 *   - Adds to existing playerStockerProgress
 *   - Returns correct fractional newProgress
 *   - Respects game-speed multiplier
 *   - Returns multiple actions when progress crosses 2.0+
 */
class PlayerActionHandlerTest {

    private lateinit var handler: PlayerActionHandler

    @Before
    fun setUp() {
        handler = PlayerActionHandler()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stateWith(
        playerRole: PlayerRole = PlayerRole.MANAGE,
        cashierProgress: Float = 0f,
        stockerProgress: Float = 0f,
        gameSpeedMultiplier: Float = 1.0f,
    ): GameState = GameState(
        playerRole = playerRole,
        playerCashierProgress = cashierProgress,
        playerStockerProgress = stockerProgress,
        storeConfig = StoreConfig(gameSpeedMultiplier = gameSpeedMultiplier),
    )

    // ── setPlayerRole ─────────────────────────────────────────────────────────

    @Test
    fun `setPlayerRole activates CASHIER when current role is NONE`() {
        val state = stateWith(playerRole = PlayerRole.MANAGE)
        val result = handler.setPlayerRole(state, PlayerRole.CASHIER)
        assertEquals(PlayerRole.CASHIER, result.playerRole)
    }

    @Test
    fun `setPlayerRole toggles CASHIER off when it is already active`() {
        val state = stateWith(playerRole = PlayerRole.CASHIER)
        val result = handler.setPlayerRole(state, PlayerRole.CASHIER)
        assertEquals(PlayerRole.MANAGE, result.playerRole)
    }

    @Test
    fun `setPlayerRole toggles STOCKER off when it is already active`() {
        val state = stateWith(playerRole = PlayerRole.STOCKER)
        val result = handler.setPlayerRole(state, PlayerRole.STOCKER)
        assertEquals(PlayerRole.MANAGE, result.playerRole)
    }

    @Test
    fun `setPlayerRole switches directly from CASHIER to STOCKER`() {
        val state = stateWith(playerRole = PlayerRole.CASHIER)
        val result = handler.setPlayerRole(state, PlayerRole.STOCKER)
        assertEquals(PlayerRole.STOCKER, result.playerRole)
    }

    @Test
    fun `setPlayerRole zeros playerCashierProgress on every role change`() {
        val state = stateWith(playerRole = PlayerRole.MANAGE, cashierProgress = 0.7f)
        val result = handler.setPlayerRole(state, PlayerRole.CASHIER)
        assertEquals(0f, result.playerCashierProgress, 1e-4f)
    }

    @Test
    fun `setPlayerRole zeros playerStockerProgress on every role change`() {
        val state = stateWith(playerRole = PlayerRole.STOCKER, stockerProgress = 0.4f)
        val result = handler.setPlayerRole(state, PlayerRole.STOCKER) // toggle off
        assertEquals(0f, result.playerStockerProgress, 1e-4f)
    }

    @Test
    fun `setPlayerRole does not modify unrelated GameState fields`() {
        val state = stateWith().copy(storeName = "My Store")
        val result = handler.setPlayerRole(state, PlayerRole.CASHIER)
        assertEquals("My Store", result.storeName)
    }

    // ── calculateCashierWork ──────────────────────────────────────────────────

    @Test
    fun `calculateCashierWork returns 0 actions when delta is too small`() {
        // 0.5 items/s × 1.0 s × 1× = 0.5 — below threshold
        val result = handler.calculateCashierWork(stateWith(), deltaSeconds = 1.0)
        assertEquals(0, result.actionsToTake)
    }

    @Test
    fun `calculateCashierWork returns 1 action when progress just crosses 1_0`() {
        // 0.5 items/s × 2.0 s × 1× = 1.0 exactly
        val result = handler.calculateCashierWork(stateWith(), deltaSeconds = 2.0)
        assertEquals(1, result.actionsToTake)
    }

    @Test
    fun `calculateCashierWork accumulates existing playerCashierProgress`() {
        // Existing 0.6 + (0.5 × 1.0 × 1×) = 1.1 → 1 whole action
        val state = stateWith(cashierProgress = 0.6f)
        val result = handler.calculateCashierWork(state, deltaSeconds = 1.0)
        assertEquals(1, result.actionsToTake)
    }

    @Test
    fun `calculateCashierWork newProgress is fractional remainder after deducting whole actions`() {
        // 0.5 × 3.0 × 1× = 1.5 → 1 whole, 0.5 remainder
        val result = handler.calculateCashierWork(stateWith(), deltaSeconds = 3.0)
        assertEquals(1, result.actionsToTake)
        assertEquals(0.5f, result.newProgress, 1e-4f)
    }

    @Test
    fun `calculateCashierWork returns zero newProgress when progress is exactly whole`() {
        // 0.5 × 2.0 × 1× = 1.0 exactly → 1 whole, 0.0 remainder
        val result = handler.calculateCashierWork(stateWith(), deltaSeconds = 2.0)
        assertEquals(0f, result.newProgress, 1e-4f)
    }

    @Test
    fun `calculateCashierWork respects game-speed multiplier`() {
        // 0.5 × 1.0 × 4× = 2.0 → 2 whole actions
        val result = handler.calculateCashierWork(
            stateWith(gameSpeedMultiplier = 4.0f), deltaSeconds = 1.0,
        )
        assertEquals(2, result.actionsToTake)
    }

    @Test
    fun `calculateCashierWork returns multiple actions when progress crosses 2_0`() {
        // 0.5 × 4.0 × 1× = 2.0 → 2 whole actions, 0.0 remainder
        val result = handler.calculateCashierWork(stateWith(), deltaSeconds = 4.0)
        assertEquals(2, result.actionsToTake)
        assertEquals(0f, result.newProgress, 1e-4f)
    }

    // ── calculateStockerWork ──────────────────────────────────────────────────

    @Test
    fun `calculateStockerWork returns 0 actions when delta is too small`() {
        // 0.3 case-packs/s × 1.0 s × 1× = 0.3 — below threshold
        val result = handler.calculateStockerWork(stateWith(), deltaSeconds = 1.0)
        assertEquals(0, result.actionsToTake)
    }

    @Test
    fun `calculateStockerWork returns 1 action when accumulated progress crosses 1_0`() {
        // Existing 0.8 + (0.3 × 1.0 × 1×) = 1.1 → 1 whole action
        val state = stateWith(stockerProgress = 0.8f)
        val result = handler.calculateStockerWork(state, deltaSeconds = 1.0)
        assertEquals(1, result.actionsToTake)
    }

    @Test
    fun `calculateStockerWork accumulates existing playerStockerProgress`() {
        // Existing 0.5 + (0.3 × 1.0 × 1×) = 0.8 → still 0 actions
        val state = stateWith(stockerProgress = 0.5f)
        val result = handler.calculateStockerWork(state, deltaSeconds = 1.0)
        assertEquals(0, result.actionsToTake)
    }

    @Test
    fun `calculateStockerWork newProgress is fractional remainder after deducting whole`() {
        // 0.3 × 10.0 × 1× = 3.0 → 3 whole actions, ~0.0 remainder
        val result = handler.calculateStockerWork(stateWith(), deltaSeconds = 10.0)
        assertEquals(3, result.actionsToTake)
        assertEquals(0f, result.newProgress, 1e-3f)
    }

    @Test
    fun `calculateStockerWork respects game-speed multiplier`() {
        // 0.3 × 1.0 × 4× = 1.2 → 1 whole action
        val result = handler.calculateStockerWork(
            stateWith(gameSpeedMultiplier = 4.0f), deltaSeconds = 1.0,
        )
        assertEquals(1, result.actionsToTake)
    }

    @Test
    fun `calculateStockerWork returns multiple actions when progress crosses 2_0`() {
        // 0.3 × 7.0 × 1× = 2.1 → 2 whole actions
        val result = handler.calculateStockerWork(stateWith(), deltaSeconds = 7.0)
        assertEquals(2, result.actionsToTake)
    }
}

