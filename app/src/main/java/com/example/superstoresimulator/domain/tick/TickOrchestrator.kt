package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.GameStateChange
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.findRegisterById
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
import javax.inject.Inject
import javax.inject.Singleton

data class TickResult(val state: GameState, val changes: List<GameStateChange>)

@Singleton
class TickOrchestrator @Inject constructor(
    private val timeManager: TimeManager,
    private val trafficManager: TrafficManager,
    private val staffManager: StaffManager,
    private val dayManager: DayManager,
    private val storeController: StoreController,
    private val spoilageManager: SpoilageManager,
    private val pricingManager: PricingManager,
    private val truckManager: TruckManager,
    private val transactionEngine: TransactionEngine,
    private val inventoryManager: InventoryManager,
    private val registerManager: RegisterManager,
    private val playerActionHandler: PlayerActionHandler,
    private val itemMetadataCache: ItemMetadataCache,
) {
    private companion object {
        const val XP_PER_STOCK_ACTION = 1
        const val MANAGER_XP_PER_SUPERVISED_ACTION = 1
    }

    fun tick(state: GameState, deltaMilliseconds: Long): TickResult {
        if (state.playerPausedTime) return TickResult(state, emptyList())

        val changes = mutableListOf<GameStateChange>()
        var s = advanceTime(state, deltaMilliseconds)
        s = processSpoilage(s)
        s = checkDayRollover(s)
        s = updateStoreState(s)
        s = updatePricing(s)

        val delta = deltaMilliseconds / 1000.0
        val speedMultiplier = s.storeConfig.gameSpeedMultiplier
        val currentHour = s.currentTime.hour

        s = registerManager.performShiftCheckAndReassignment(s, currentHour)
        s = processTraffic(s, delta, currentHour)
        s = processStaffWork(s, delta, speedMultiplier, currentHour)
        s = processPlayerWork(s, delta, currentHour)
        trackUtilization(s, currentHour)

        return TickResult(s, changes)
    }

    private fun advanceTime(state: GameState, deltaMilliseconds: Long): GameState {
        timeManager.update(deltaMilliseconds)
        return state.copy(currentTime = timeManager.currentTime)
    }

    private fun processSpoilage(state: GameState): GameState {
        val spoilageResult = spoilageManager.processExpiration(state)
        var s = spoilageResult.state
        if (spoilageResult.expiredItemIds.isNotEmpty()) {
            for (itemId in spoilageResult.expiredItemIds) {
                s = transactionEngine.clearStaleExpiryMarkdown(s, itemId)
            }
        }
        return s
    }

    private fun checkDayRollover(state: GameState): GameState {
        val newDayNumber = state.currentTime.dayNumber
        if (newDayNumber == dayManager.lastKnownDayNumber) return state

        var s = staffManager.evaluateStoreManagerActions(state)
        s = staffManager.evaluateAutoHire(s)
        s = dayManager.rollOverDay(s, dayManager.lastKnownDayNumber)
        dayManager.advanceDay(newDayNumber)
        s = s.copy(
            registers = s.registers.map {
                it.copy(
                    currentTransaction = Transaction(),
                    transactionActive = false,
                    dailyTransactions = 0,
                    dailyRevenue = Money.ZERO,
                )
            },
            manuallyUnassignedCashiers = emptySet(),
        )
        staffManager.reset()
        s = truckManager.processArrivals(s, newDayNumber)
        return s
    }

    private fun updateStoreState(state: GameState): GameState {
        val newStoreState = timeManager.getStoreState()
        var s = state
        if (newStoreState != s.storeState) {
            s = storeController.handleStoreStateChange(s, newStoreState, trafficManager)
        }
        return s.copy(storeState = newStoreState)
    }

    private fun updatePricing(state: GameState): GameState {
        val currentHour = state.currentTime.hour
        if (currentHour != state.pricingState.lastPriceIndexHour) {
            return pricingManager.updateSmoothedPriceIndex(state)
        }
        return state
    }

    private fun processTraffic(state: GameState, delta: Double, currentHour: Int): GameState {
        var s = state
        if (s.storeState == StoreState.OPEN) {
            val newCustomers = trafficManager.update(s, delta)
            if (newCustomers.isNotEmpty()) {
                s = s.copy(pendingCustomers = s.pendingCustomers + newCustomers.size)
            }
        }

        if (s.storeState == StoreState.OPEN && s.pendingCustomers > 0) {
            for (reg in s.registers) {
                if (s.pendingCustomers <= 0) break
                if (reg.transactionActive) continue
                if (!registerManager.isRegisterMannedAndOnShift(reg.registerId, s, currentHour)) continue
                val baseBasket = (2..5).random()
                val basketSize = (baseBasket * s.currentStoreSize.basketSizeMultiplier).toInt().coerceAtLeast(1)
                s = transactionEngine.generateRandomTransaction(s, basketSize, reg.registerId)
                if (s.registers.findRegisterById(reg.registerId)?.transactionActive == true) {
                    s = s.copy(pendingCustomers = (s.pendingCustomers - 1).coerceAtLeast(0))
                }
            }
        }
        return s
    }

    private fun processStaffWork(state: GameState, delta: Double, speedMultiplier: Float, currentHour: Int): GameState {
        var s = state
        val bonuses = StaffManager.computeAllBonuses(
            s.playerRole, currentHour, s.staffSchedules, s.hiredEntityRegistry
        )
        val cashierBonus = bonuses.cashierBonus
        val stockerBonus = bonuses.stockerBonus
        val freshBonus = bonuses.freshBonus

        var totalEmployeeActions = 0

        // Cashier work
        if (s.registers.any { it.transactionActive }) {
            for (reg in s.registers) {
                if (!reg.transactionActive) continue
                val cashierWeight = registerManager.getCashierWeightForRegister(reg.registerId, s, currentHour)
                if (cashierWeight <= 0f) continue
                val actions = staffManager.advanceCashierProgressForRegister(
                    reg.registerId, cashierWeight, delta, speedMultiplier * cashierBonus
                )
                repeat(actions) { s = transactionEngine.ringUpItemOnRegister(s, reg.registerId) }
                totalEmployeeActions += actions
            }
        }

        // Inventory scan
        var hasActionableBackroom = false
        var hasUnzonedItems = false
        var hasFreshBackroomStock = false
        for ((itemId, inv) in s.inventory) {
            if (!hasActionableBackroom && inv.backroomStock > 0 && itemMetadataCache.get(itemId)?.isPerishable != true) {
                hasActionableBackroom = true
            }
            if (!hasUnzonedItems && inv.shelfStock > 0 && inv.zoneScore < 1.0f) {
                hasUnzonedItems = true
            }
            if (!hasFreshBackroomStock && inv.backroomStock > 0 && itemMetadataCache.get(itemId)?.isPerishable == true) {
                hasFreshBackroomStock = true
            }
            if (hasActionableBackroom && hasUnzonedItems && hasFreshBackroomStock) break
        }

        // Stocker work
        val stockerResult = StaffManager.activeWeightedCountWithIds(
            EntityDef.STOCKER, currentHour, s.staffSchedules, s.hiredEntityRegistry
        )
        val activeStockers = stockerResult.weight

        if (hasActionableBackroom || hasFreshBackroomStock) {
            val assignedStockers = staffManager.advanceStockerProgressWithAssignment(stockerResult, delta, speedMultiplier * stockerBonus)
            var remainingStockerActions = assignedStockers.size
            while (remainingStockerActions > 0 && hasFreshBackroomStock) {
                s = inventoryManager.stockRandomFreshItemFromBackroom(s)
                remainingStockerActions--
                hasFreshBackroomStock = s.inventory.any { (itemId, inv) ->
                    inv.backroomStock > 0 && itemMetadataCache.get(itemId)?.isPerishable == true
                }
            }
            repeat(remainingStockerActions) { s = inventoryManager.stockRandomItemFromBackroom(s) }
            if (assignedStockers.isNotEmpty()) {
                var registry = s.hiredEntityRegistry
                for (entityId in assignedStockers) {
                    registry = registry.grantXp(entityId, XP_PER_STOCK_ACTION)
                }
                s = s.copy(hiredEntityRegistry = registry)
                totalEmployeeActions += assignedStockers.size
            }
        } else if (hasUnzonedItems && activeStockers > 0f) {
            s = advanceStockerZoning(s, currentHour, delta, speedMultiplier * stockerBonus)
        }

        // Fresh handler work
        val freshResult = StaffManager.activeWeightedCountWithIds(
            EntityDef.FRESH_HANDLER, currentHour, s.staffSchedules, s.hiredEntityRegistry
        )
        val activeFreshHandlers = freshResult.weight
        val assignedFreshHandlers = staffManager.advanceFreshProgressWithAssignment(freshResult, delta, speedMultiplier * freshBonus)

        var remainingFreshActions = assignedFreshHandlers.size
        if (remainingFreshActions > 0) {
            val currentDay = s.currentTime.dayNumber
            for ((itemId, inv) in s.inventory) {
                if (remainingFreshActions <= 0) break
                val meta = itemMetadataCache.get(itemId) ?: continue
                if (!meta.isPerishable) continue
                if (s.pricingState.activeMarkdowns.containsKey(itemId)) continue
                val hasExpiringBatch = inv.shelfBatches.any { batch ->
                    batch.expirationDay - currentDay <= pricingManager.config.expiryThresholdDays
                }
                if (hasExpiringBatch) {
                    s = pricingManager.applyExpiryMarkdown(s, itemId, currentDay)
                    remainingFreshActions--
                }
            }
        }

        repeat(remainingFreshActions) { s = inventoryManager.stockRandomFreshItemFromBackroom(s) }
        if (assignedFreshHandlers.isNotEmpty()) {
            var registry = s.hiredEntityRegistry
            for (entityId in assignedFreshHandlers) {
                registry = registry.grantXp(entityId, XP_PER_STOCK_ACTION)
            }
            s = s.copy(hiredEntityRegistry = registry)
            totalEmployeeActions += assignedFreshHandlers.size
        }

        // Manager XP
        if (totalEmployeeActions > 0 && bonuses.onShiftManagerIds.isNotEmpty()) {
            val managerXp = totalEmployeeActions * MANAGER_XP_PER_SUPERVISED_ACTION
            val xpEach = (managerXp / bonuses.onShiftManagerIds.size).coerceAtLeast(1)
            var registry = s.hiredEntityRegistry
            for (mgrId in bonuses.onShiftManagerIds) {
                registry = registry.grantXp(mgrId, xpEach)
            }
            s = s.copy(hiredEntityRegistry = registry)
        }

        // Fresh auto-ordering when handlers idle
        if (activeFreshHandlers > 0f && !hasFreshBackroomStock) {
            s = inventoryManager.attemptFreshHandlerAutoOrder(s)
        }

        return s
    }

    private fun processPlayerWork(state: GameState, delta: Double, currentHour: Int): GameState {
        var s = state
        if (s.playerRole == PlayerRole.CASHIER && s.playerAssignedRegisterId == null) {
            val freeReg = s.registers.firstOrNull { it.assignedCashierId == null }
            if (freeReg != null) {
                s = s.copy(playerAssignedRegisterId = freeReg.registerId)
            }
        }
        return when (s.playerRole) {
            PlayerRole.CASHIER -> performPlayerCashierWork(s, delta)
            PlayerRole.STOCKER -> performPlayerStockerWork(s, delta)
            PlayerRole.MANAGE -> {
                if (s.playerCashierProgress != 0f || s.playerStockerProgress != 0f) {
                    s.copy(playerCashierProgress = 0f, playerStockerProgress = 0f)
                } else s
            }
        }
    }

    private fun performPlayerCashierWork(state: GameState, deltaSeconds: Double): GameState {
        val registerId = state.playerAssignedRegisterId ?: return state
        val register = state.registers.findRegisterById(registerId) ?: return state

        if (state.playerAssignedRegisterId != null && register.assignedCashierId != null) {
            return state.copy(playerCashierProgress = 0f)
        }

        val result = playerActionHandler.calculateCashierWork(state, deltaSeconds)
        var s = state
        var actionsLeft = result.actionsToTake
        while (actionsLeft > 0) {
            val currentRegister = s.registers.findRegisterById(registerId) ?: break
            if (!currentRegister.transactionActive) break
            s = transactionEngine.ringUpItemOnRegister(s, registerId)
            actionsLeft--
        }
        val finalProgress =
            if (s.registers.findRegisterById(registerId)?.transactionActive == true) result.newProgress
            else 0f
        return s.copy(playerCashierProgress = finalProgress)
    }

    private fun performPlayerStockerWork(state: GameState, deltaSeconds: Double): GameState {
        val result = playerActionHandler.calculateStockerWork(state, deltaSeconds)
        val hasActionableBackroom = state.inventory.values.any { it.backroomStock > 0 }
        var s = state
        if (hasActionableBackroom) {
            var actionsRemaining = result.actionsToTake
            while (actionsRemaining > 0 && s.inventory.any { (itemId, inv) ->
                    inv.backroomStock > 0 && itemMetadataCache.get(itemId)?.isPerishable == true
                }) {
                s = inventoryManager.stockRandomFreshItemFromBackroom(s)
                actionsRemaining--
            }
            repeat(actionsRemaining) { s = inventoryManager.stockRandomItemFromBackroom(s) }
            return s.copy(playerStockerProgress = result.newProgress)
        } else {
            val unzonedItems = s.inventory.entries
                .filter { (_, inv) -> inv.shelfStock > 0 && inv.zoneScore < 1.0f }
                .sortedBy { (_, inv) -> inv.zoneScore }
            if (unzonedItems.isEmpty()) {
                return s.copy(playerRole = PlayerRole.MANAGE, playerStockerProgress = 0f)
            }
            var actionsLeft = result.actionsToTake
            var idx = 0
            while (actionsLeft > 0 && idx < unzonedItems.size) {
                val (itemId, inv) = unzonedItems[idx]
                val newScore = (inv.zoneScore + StaffManager.ZONE_PER_ACTION).coerceAtMost(1.0f)
                s = s.copy(inventory = s.inventory + (itemId to inv.copy(zoneScore = newScore)))
                actionsLeft--
                if (newScore >= 1.0f) idx++
            }
            return s.copy(playerStockerProgress = result.newProgress)
        }
    }

    private fun advanceStockerZoning(state: GameState, currentHour: Int, delta: Double, multiplier: Float): GameState {
        val onShiftStockers = state.hiredEntityRegistry.getByDef(EntityDef.STOCKER).filter { entity ->
            state.staffSchedules.firstOrNull { it.entityId == entity.id }?.isOnShift(currentHour) == true
        }
        if (onShiftStockers.isEmpty()) return state

        val unzonedItems = state.inventory.entries
            .filter { (_, inv) -> inv.shelfStock > 0 && inv.zoneScore < 1.0f }
            .sortedBy { (_, inv) -> inv.zoneScore }
            .map { (id, _) -> id }

        for (stocker in onShiftStockers) {
            val currentTarget = staffManager.getZoningTarget(stocker.id)
            if (currentTarget != null) {
                val inv = state.inventory[currentTarget]
                if (inv != null && inv.zoneScore < 1.0f && inv.shelfStock > 0) continue
                staffManager.clearZoningTarget(stocker.id)
            }
            val claimed = staffManager.allClaimedZoningTargets()
            val newTarget = unzonedItems.firstOrNull { it !in claimed }
            if (newTarget != null) {
                staffManager.assignZoningTarget(stocker.id, newTarget)
            }
        }

        var s = state
        for (stocker in onShiftStockers) {
            val weight = stocker.throughputWeight * stocker.levelMultiplier * stocker.trait.throughputMultiplier
            val actions = staffManager.advanceZoningForStocker(stocker.id, weight, delta, multiplier)
            for (action in actions) {
                val inv = s.inventory[action.targetItemId] ?: continue
                val newScore = (inv.zoneScore + StaffManager.ZONE_PER_ACTION).coerceAtMost(1.0f)
                s = s.copy(inventory = s.inventory + (action.targetItemId to inv.copy(zoneScore = newScore)))
            }
        }
        return s
    }

    private fun trackUtilization(state: GameState, currentHour: Int) {
        if (state.hiredEntityRegistry.hiredEntities.isEmpty()) return
        var hasActionableBackroom = false
        var hasUnzonedItems = false
        var hasFreshBackroomStock = false
        for ((itemId, inv) in state.inventory) {
            if (!hasActionableBackroom && inv.backroomStock > 0 && itemMetadataCache.get(itemId)?.isPerishable != true) hasActionableBackroom = true
            if (!hasUnzonedItems && inv.shelfStock > 0 && inv.zoneScore < 1.0f) hasUnzonedItems = true
            if (!hasFreshBackroomStock && inv.backroomStock > 0 && itemMetadataCache.get(itemId)?.isPerishable == true) hasFreshBackroomStock = true
            if (hasActionableBackroom && hasUnzonedItems && hasFreshBackroomStock) break
        }
        val hasFreshWork = hasFreshBackroomStock ||
            (state.freshAutoOrderConfig.enabled && state.inventory.any { (itemId, _) ->
                inventoryManager.shouldAutoOrderFreshItem(state, itemId, state.freshAutoOrderConfig)
            })
        staffManager.updateUtilization(state, currentHour, hasActionableBackroom, hasUnzonedItems, hasFreshWork)
    }
}
