package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.SimAccumulators
import com.example.superstoresimulator.domain.ZoningState
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.metrics.MetricsArchiver
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TickOrchestrator @Inject constructor(
    private val timeManager: TimeManager,
    private val spoilageManager: SpoilageManager,
    private val storeController: StoreController,
    private val pricingManager: PricingManager,
    private val registerManager: RegisterManager,
    private val transactionEngine: TransactionEngine,
    private val trafficManager: TrafficManager,
    private val dayRolloverProcessor: DayRolloverProcessor,
    private val trafficProcessor: TrafficProcessor,
    private val staffTickProcessor: StaffTickProcessor,
    private val playerTickProcessor: PlayerTickProcessor,
    private val utilizationTracker: UtilizationTracker,
    private val staffManager: StaffManager,
    private val dayManager: DayManager,
    private val researchTickProcessor: ResearchTickProcessor,
    private val tutorialTickProcessor: TutorialTickProcessor,
    private val metricsArchiver: MetricsArchiver,
    private val itemMetadataCache: ItemMetadataCache,
) {
    fun resetTickProcessors() {
        researchTickProcessor.reset()
        tutorialTickProcessor.reset()
    }

    fun tick(
        state: GameState,
        deltaMilliseconds: Long,
        offlineMode: Boolean = false,
        sampleUtilization: Boolean = true,
        skipAccumulators: Boolean = false,
    ): GameState {
        if (state.playerPausedTime) return state

        var s = state
        val trimCount = metricsArchiver.consumeTrim()
        if (trimCount > 0) {
            val trimmed = s.completedDayMetrics.drop(trimCount)
            val archived = s.completedDayMetrics.take(trimCount)
            s = s.copy(
                completedDayMetrics = trimmed,
                archivedCumulativeRevenueCents = s.archivedCumulativeRevenueCents +
                        archived.sumOf { it.revenue.cents },
                archivedCumulativeExpiredItems = s.archivedCumulativeExpiredItems +
                        archived.sumOf { it.itemsExpired },
                archivedCumulativeExpiredWasteCostCents = s.archivedCumulativeExpiredWasteCostCents +
                        archived.sumOf { it.expiredWasteCost.cents },
            )
        }

        s = advanceTime(s, deltaMilliseconds)
        s = processSpoilage(s)
        s = dayRolloverProcessor.process(s)
        s = updateStoreState(s)
        s = updatePricing(s)

        val delta = deltaMilliseconds / 1000.0
        val speedMultiplier = s.storeConfig.gameSpeedMultiplier
        val currentHour = s.currentTime.hour

        val tickPricingData = if (s.pendingCustomers > 0 || s.registers.any { it.transactionActive }) {
            pricingManager.computePricingData(s)
        } else null

        val dayOfWeek = s.currentTime.dayOfWeek
        s = registerManager.performShiftCheckAndReassignment(s, currentHour, dayOfWeek)
        s = trafficProcessor.process(s, delta, currentHour, tickPricingData)
        // One inventory scan per tick, shared by the staff processor and utilization sampler.
        val inventoryScan = scanInventory(s.inventory, itemMetadataCache)
        s = staffTickProcessor.process(s, delta, speedMultiplier, currentHour, tickPricingData, inventoryScan)
        if (!offlineMode) {
            s = playerTickProcessor.process(s, delta)
        }
        if (sampleUtilization) {
            utilizationTracker.sample(s, currentHour, inventoryScan)
        }
        s = researchTickProcessor.process(s)
        s = tutorialTickProcessor.process(s)

        return if (skipAccumulators) s else s.copy(simAccumulators = snapshotAccumulators())
    }

    internal fun snapshotAccumulators(): SimAccumulators = SimAccumulators(
        timeAccumulatorMs = timeManager.accumulatedMilliseconds,
        trafficAccumulator = trafficManager.accumulatedCustomers,
        lastKnownDayNumber = dayManager.lastKnownDayNumber,
        cashierProgressByRegister = staffManager.cashierProgressByRegister.toMap(),
        stockerProgress = staffManager.stockerProgress,
        stockingManagerProgress = staffManager.stockingManagerProgress,
        freshHandlerProgress = staffManager.freshHandlerProgress,
        zoningByStockerId = staffManager.zoningByStockerId.mapValues {
            ZoningState(it.value.targetItemId, it.value.progress)
        },
    )

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
}
