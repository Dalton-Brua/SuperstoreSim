package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.staff.StaffManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayRolloverProcessor @Inject constructor(
    private val staffManager: StaffManager,
    private val dayManager: DayManager,
    private val truckManager: TruckManager,
    private val inventoryManager: InventoryManager,
) {
    fun process(state: GameState): GameState {
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

        val stockers = s.hiredEntityRegistry.getByDef(EntityDef.STOCKER)
        val hasFastStocker = stockers.any { it.tier.ordinal >= Tier.FAST.ordinal }
        val hasStockingManager = stockers.any { it.tier == Tier.MANAGER }
        if (hasFastStocker && !hasStockingManager) {
            s = inventoryManager.attemptDayRolloverAutoOrder(s)
        }

        return s
    }
}
