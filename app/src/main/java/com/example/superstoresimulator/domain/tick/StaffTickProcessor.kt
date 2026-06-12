package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.staff.StaffManager.Companion.ActiveWeightResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffTickProcessor @Inject constructor(
    private val staffManager: StaffManager,
    private val inventoryManager: InventoryManager,
    private val transactionEngine: TransactionEngine,
    private val registerManager: RegisterManager,
    private val pricingManager: PricingManager,
    private val itemMetadataCache: ItemMetadataCache,
) {
    fun process(state: GameState, delta: Double, speedMultiplier: Float, currentHour: Int): GameState {
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
        val scan = scanInventory(s.inventory, itemMetadataCache)
        var hasFreshBackroomStock = scan.hasFreshBackroomStock

        // Stocker work — split stocking managers from regular stockers
        val fullStockerResult = StaffManager.activeWeightedCountWithIds(
            EntityDef.STOCKER, currentHour, s.staffSchedules, s.hiredEntityRegistry
        )

        val regularIds = mutableListOf<Int>()
        val regularWeights = mutableListOf<Float>()
        val managerIdList = mutableListOf<Int>()
        val managerWeights = mutableListOf<Float>()
        fullStockerResult.onShiftIds.forEachIndexed { i, id ->
            val w = fullStockerResult.perEntityWeights[i]
            if (s.hiredEntityRegistry.getById(id).tier == Tier.MANAGER) {
                managerIdList.add(id); managerWeights.add(w)
            } else {
                regularIds.add(id); regularWeights.add(w)
            }
        }
        val regularResult = ActiveWeightResult(regularWeights.sum(), regularIds, regularWeights)
        val managerResult = ActiveWeightResult(managerWeights.sum(), managerIdList, managerWeights)

        // Stocking Manager work: Order → Stock → Zone
        if (managerResult.weight > 0f) {
            val managerActions = staffManager.advanceStockingManagerProgressWithAssignment(
                managerResult, delta, speedMultiplier * stockerBonus
            )
            var remainingManagerActions = managerActions.size

            if (remainingManagerActions > 0) {
                val orderResult = inventoryManager.attemptStockingManagerAutoOrder(s, remainingManagerActions)
                s = orderResult.state
                remainingManagerActions -= orderResult.itemsOrdered

                if (remainingManagerActions > 0 && (scan.hasActionableBackroom || hasFreshBackroomStock)) {
                    while (remainingManagerActions > 0 && hasFreshBackroomStock) {
                        s = inventoryManager.stockRandomFreshItemFromBackroom(s)
                        remainingManagerActions--
                        hasFreshBackroomStock = hasFreshBackroomStock(s)
                    }
                    repeat(remainingManagerActions) { s = inventoryManager.stockRandomItemFromBackroom(s) }
                }
            }

            if (managerActions.isNotEmpty()) {
                s = grantXpAll(s, managerActions, StaffManager.XP_PER_STOCK_ACTION)
                totalEmployeeActions += managerActions.size
            }
        }

        // Regular stocker work: Stock → Zone
        val activeStockers = regularResult.weight

        if (scan.hasActionableBackroom || hasFreshBackroomStock) {
            val assignedStockers = staffManager.advanceStockerProgressWithAssignment(regularResult, delta, speedMultiplier * stockerBonus)
            var remainingStockerActions = assignedStockers.size
            while (remainingStockerActions > 0 && hasFreshBackroomStock) {
                s = inventoryManager.stockRandomFreshItemFromBackroom(s)
                remainingStockerActions--
                hasFreshBackroomStock = hasFreshBackroomStock(s)
            }
            repeat(remainingStockerActions) { s = inventoryManager.stockRandomItemFromBackroom(s) }
            if (assignedStockers.isNotEmpty()) {
                s = grantXpAll(s, assignedStockers, StaffManager.XP_PER_STOCK_ACTION)
                totalEmployeeActions += assignedStockers.size
            }
        } else if (scan.hasUnzonedItems && (activeStockers > 0f || managerResult.weight > 0f)) {
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
            s = grantXpAll(s, assignedFreshHandlers, StaffManager.XP_PER_STOCK_ACTION)
            totalEmployeeActions += assignedFreshHandlers.size
        }

        // Manager XP
        if (totalEmployeeActions > 0 && bonuses.onShiftManagerIds.isNotEmpty()) {
            val managerXp = totalEmployeeActions * StaffManager.MANAGER_XP_PER_SUPERVISED_ACTION
            val xpEach = (managerXp / bonuses.onShiftManagerIds.size).coerceAtLeast(1)
            s = grantXpAll(s, bonuses.onShiftManagerIds, xpEach)
        }

        // Fresh auto-ordering when handlers idle
        if (activeFreshHandlers > 0f && !hasFreshBackroomStock) {
            s = inventoryManager.attemptFreshHandlerAutoOrder(s)
        }

        return s
    }

    private fun hasFreshBackroomStock(state: GameState): Boolean =
        state.inventory.any { (itemId, inv) ->
            inv.backroomStock > 0 && itemMetadataCache.get(itemId)?.isPerishable == true
        }

    private fun grantXpAll(state: GameState, ids: Collection<Int>, xp: Int): GameState {
        var registry = state.hiredEntityRegistry
        for (id in ids) registry = registry.grantXp(id, xp)
        return state.copy(hiredEntityRegistry = registry)
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
        var registry = s.hiredEntityRegistry
        for (stocker in onShiftStockers) {
            val weight = stocker.throughputWeight * stocker.levelMultiplier * stocker.trait.throughputMultiplier
            val actions = staffManager.advanceZoningForStocker(stocker.id, weight, delta, multiplier)
            for (action in actions) {
                val inv = s.inventory[action.targetItemId] ?: continue
                val newScore = (inv.zoneScore + StaffManager.ZONE_PER_ACTION).coerceAtMost(1.0f)
                s = s.copy(inventory = s.inventory + (action.targetItemId to inv.copy(zoneScore = newScore)))
                if (newScore >= 1.0f) {
                    registry = registry.grantXp(stocker.id, StaffManager.XP_PER_STOCK_ACTION)
                }
            }
        }
        return s.copy(hiredEntityRegistry = registry)
    }
}
