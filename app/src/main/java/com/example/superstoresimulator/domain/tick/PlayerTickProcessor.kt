package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.findRegisterById
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.staff.StaffManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerTickProcessor @Inject constructor(
    private val playerActionHandler: PlayerActionHandler,
    private val transactionEngine: TransactionEngine,
    private val inventoryManager: InventoryManager,
    private val itemMetadataCache: ItemMetadataCache,
) {
    fun process(state: GameState, delta: Double): GameState {
        var s = state
        if (s.playerRole == PlayerRole.CASHIER && s.playerAssignedRegisterId == null) {
            val freeReg = s.registers.firstOrNull { it.assignedCashierId == null }
            if (freeReg != null) {
                s = s.copy(playerAssignedRegisterId = freeReg.registerId)
            }
        }
        return when (s.playerRole) {
            PlayerRole.CASHIER -> performCashierWork(s, delta)
            PlayerRole.STOCKER -> performStockerWork(s, delta)
            PlayerRole.MANAGE -> {
                if (s.playerCashierProgress != 0f || s.playerStockerProgress != 0f) {
                    s.copy(playerCashierProgress = 0f, playerStockerProgress = 0f)
                } else s
            }
        }
    }

    private fun performCashierWork(state: GameState, deltaSeconds: Double): GameState {
        val registerId = state.playerAssignedRegisterId ?: return state
        val register = state.registers.findRegisterById(registerId) ?: return state

        if (register.assignedCashierId != null) {
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

    private fun performStockerWork(state: GameState, deltaSeconds: Double): GameState {
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
}
