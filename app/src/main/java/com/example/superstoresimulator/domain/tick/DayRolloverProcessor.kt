package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionDao
import com.example.superstoresimulator.domain.Transactions.toEntity
import com.example.superstoresimulator.domain.Transactions.toLineEntity
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.reputation.ReputationManager
import com.example.superstoresimulator.domain.research.ResearchGates
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.metrics.MetricsArchiver
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.vendor.VendorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayRolloverProcessor @Inject constructor(
    private val staffManager: StaffManager,
    private val dayManager: DayManager,
    private val truckManager: TruckManager,
    private val inventoryManager: InventoryManager,
    private val transactionDao: TransactionDao,
    private val vendorManager: VendorManager,
    private val metricsArchiver: MetricsArchiver,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun process(state: GameState): GameState {
        val newDayNumber = state.currentTime.dayNumber
        if (newDayNumber == dayManager.lastKnownDayNumber) return state

        val transactionsToSync = state.salesHistory

        val staffMetrics = staffManager.snapshotDailyMetrics()
        var s = state.copy(
            currentDayMetrics = state.currentDayMetrics.copy(
                avgCashierUtilization = staffMetrics.avgCashierUtilization,
                avgStockerUtilization = staffMetrics.avgStockerUtilization,
                avgFreshUtilization = staffMetrics.avgFreshUtilization,
                avgZoneScore = state.avgZoneScore,
            ),
        )
        s = staffManager.evaluateStoreManagerActions(s)
        s = staffManager.optimizeWeeklySchedules(s)
        s = truckManager.evaluateStoreManagerTruckActions(s, newDayNumber)
        s = staffManager.evaluateAutoHire(s)
        s = dayManager.rollOverDay(s, dayManager.lastKnownDayNumber)

        val repSnapshot = s.lastEndOfDayReport
        if (repSnapshot != null
            && s.currentStoreSize.baseRevenueTarget != null
            && ResearchGates.isResearched(s.researchState.researchedUpgrades, ResearchGates.REVENUE_REPUTATION)
        ) {
            var updatedReputation = ReputationManager.updateReputation(
                reputationState = s.reputationState,
                snapshot = repSnapshot,
                storeSize = s.currentStoreSize,
            )
            val nextDay = s.currentTime.dayNumber
            if (ReputationManager.checkGoobSaleEligible(updatedReputation, nextDay)) {
                updatedReputation = ReputationManager.applyGoobSale(updatedReputation, nextDay)
            } else {
                updatedReputation = updatedReputation.copy(goobSaleActiveToday = false)
            }
            s = s.copy(reputationState = updatedReputation)
        }

        // Snapshot before launch: `s` is reassigned below before the coroutine runs.
        val metricsToArchive = s.completedDayMetrics
        syncScope.launch {
            metricsArchiver.archiveIfNeeded(metricsToArchive)
        }

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
            salesHistory = emptyList(),
        )
        staffManager.reset()
        s = truckManager.processArrivals(s, newDayNumber)
        s = vendorManager.processVendorArrivals(s, newDayNumber)

        val stockers = s.hiredEntityRegistry.getByDef(EntityDef.STOCKER)
        val hasFastStocker = stockers.any { it.canAutoReorder }
        val hasStockingManager = stockers.any { it.tier == Tier.MANAGER }
        if (hasFastStocker && !hasStockingManager) {
            s = inventoryManager.attemptDayRolloverAutoOrder(s)
        }

        if (transactionsToSync.isNotEmpty()) {
            syncScope.launch {
                val entities = transactionsToSync.map { it.toEntity() }
                val lines = transactionsToSync.flatMap { tx ->
                    tx.lines.map { line -> line.toLineEntity(tx.id) }
                }
                transactionDao.insertTransactionsWithLines(entities, lines)
            }
        }

        return s
    }
}
