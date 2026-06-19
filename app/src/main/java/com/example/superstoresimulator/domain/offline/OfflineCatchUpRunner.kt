package com.example.superstoresimulator.domain.offline

import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.metrics.AutoHireAction
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.time.TimeManager

class OfflineCatchUpRunner(
    private val gameEngine: GameEngine,
) {
    companion object {
        private const val OFFLINE_SPEED_FACTOR = 0.25f
        private const val MAX_GAME_MINUTES = 10_080L // 7 game days
        private const val SIMULATION_DELTA_MS = 500L
        private const val PROGRESS_REPORT_INTERVAL = 100
    }

    fun simulate(
        elapsedRealMs: Long,
        onProgress: (OfflineProgress) -> Unit,
    ): OfflineCatchUpResult {
        val totalGameMinutes = computeGameMinutes(elapsedRealMs).coerceAtMost(MAX_GAME_MINUTES)
        if (totalGameMinutes <= 0) {
            return emptyResult()
        }

        val savedSpeed = gameEngine.state.storeConfig.gameSpeedMultiplier
        val savedRole = gameEngine.state.playerRole

        val startMoney = gameEngine.state.money
        val startRevenue = cumulativeRevenue()
        val startExpired = cumulativeExpired()
        val startTrucks = allDeliveredTrucks().size
        val startStaffHired = allAutoHireEvents().size
        val startDayNumber = gameEngine.state.currentTime.dayNumber
        val startCompletedDays = gameEngine.state.completedDayMetrics.size

        gameEngine.isSimulating = true
        gameEngine.setGameSpeed(1f)
        gameEngine.setPlayerRole(PlayerRole.MANAGE)

        if (gameEngine.state.playerPausedTime && gameEngine.state.pausedByEndOfDay) {
            gameEngine.dismissEndOfDayReport()
        }

        try {
            val startMinutes = gameEngine.state.currentTime.totalMinutesElapsed
            val targetMinutes = startMinutes + totalGameMinutes
            var tickIndex = 0

            val events = mutableListOf<OfflineEvent>()
            var prevDayNumber = gameEngine.state.currentTime.dayNumber
            var prevTruckCount = allDeliveredTrucks().size
            var prevHireCount = allAutoHireEvents().size
            var prevFreshOrderCount = allFreshOrderItems().size
            var prevNormalOrderCount = allNormalOrderItems().size
            var prevExpiredCount = cumulativeExpired()
            var prevExpiredCost = cumulativeExpiredCost()
            var prevScheduledTruckIds = gameEngine.state.scheduledTrucks.map { it.truckId }.toSet()

            while (gameEngine.state.currentTime.totalMinutesElapsed < targetMinutes) {
                if (gameEngine.state.showEndOfDayReport) {
                    gameEngine.dismissEndOfDayReport()
                }

                val sampleUtilization = tickIndex % 60 == 0
                gameEngine.tick(SIMULATION_DELTA_MS, offlineMode = true, sampleUtilization = sampleUtilization, skipAccumulators = true)
                tickIndex++

                val currentDay = gameEngine.state.currentTime.dayNumber
                if (currentDay != prevDayNumber) {
                    events.add(OfflineEvent.NewDay(currentDay))
                    prevDayNumber = currentDay
                }

                if (tickIndex % PROGRESS_REPORT_INTERVAL == 0) {
                    detectEvents(events, currentDay,
                        prevTruckCount, prevHireCount,
                        prevFreshOrderCount, prevNormalOrderCount,
                        prevExpiredCount, prevExpiredCost,
                        prevScheduledTruckIds)

                    prevTruckCount = allDeliveredTrucks().size
                    prevHireCount = allAutoHireEvents().size
                    prevFreshOrderCount = allFreshOrderItems().size
                    prevNormalOrderCount = allNormalOrderItems().size
                    prevExpiredCount = cumulativeExpired()
                    prevExpiredCost = cumulativeExpiredCost()
                    prevScheduledTruckIds = gameEngine.state.scheduledTrucks.map { it.truckId }.toSet()

                    val elapsed = gameEngine.state.currentTime.totalMinutesElapsed - startMinutes
                    onProgress(
                        OfflineProgress(
                            currentMinute = elapsed,
                            totalMinutes = totalGameMinutes,
                            currentDay = currentDay,
                            fractionComplete = (elapsed.toFloat() / totalGameMinutes).coerceIn(0f, 1f),
                            currentMoney = gameEngine.state.money,
                            revenueEarned = cumulativeRevenue() - startRevenue,
                            expensesPaid = startMoney + (cumulativeRevenue() - startRevenue) - gameEngine.state.money,
                            events = events.toList(),
                        )
                    )
                }
            }

            if (gameEngine.state.showEndOfDayReport) {
                gameEngine.dismissEndOfDayReport()
            }

            val endDayNumber = gameEngine.state.currentTime.dayNumber
            val newCompletedDays = gameEngine.state.completedDayMetrics.drop(startCompletedDays)

            val totalRevenue = cumulativeRevenue() - startRevenue
            return OfflineCatchUpResult(
                gameMinutesSimulated = totalGameMinutes,
                daysCrossed = endDayNumber - startDayNumber,
                revenue = totalRevenue,
                expenses = startMoney + totalRevenue - gameEngine.state.money,
                currentMoney = gameEngine.state.money,
                itemsExpired = cumulativeExpired() - startExpired,
                trucksArrived = allDeliveredTrucks().size - startTrucks,
                staffHired = allAutoHireEvents().size - startStaffHired,
                completedDayMetrics = newCompletedDays,
                events = events.toList(),
            )
        } finally {
            gameEngine.setGameSpeed(savedSpeed)
            gameEngine.setPlayerRole(savedRole)
            gameEngine.isSimulating = false
        }
    }

    private fun detectEvents(
        events: MutableList<OfflineEvent>,
        currentDay: Int,
        prevTrucks: Int, prevHires: Int,
        prevFreshOrders: Int, prevNormalOrders: Int,
        prevExpired: Int, prevExpiredCost: Money,
        prevScheduledTruckIds: Set<Int>,
    ) {
        val allTrucks = allDeliveredTrucks()
        if (allTrucks.size > prevTrucks) {
            allTrucks.drop(prevTrucks).forEach { truck ->
                events.add(OfflineEvent.TruckArrived(currentDay, truck.isFreshTruck))
            }
        }

        val currentScheduledTrucks = gameEngine.state.scheduledTrucks
        val currentIds = currentScheduledTrucks.map { it.truckId }.toSet()
        val newIds = currentIds - prevScheduledTruckIds
        for (truck in currentScheduledTrucks.filter { it.truckId in newIds }) {
            events.add(OfflineEvent.TruckScheduled(currentDay, truck.scheduledArrivalDay, truck.isFreshTruck))
        }

        val allHires = allAutoHireEvents()
        if (allHires.size > prevHires) {
            allHires.drop(prevHires).forEach { hire ->
                when (hire.action) {
                    AutoHireAction.HIRED ->
                        events.add(OfflineEvent.StaffHired(currentDay, hire.entityDefName, hire.reason))
                    AutoHireAction.PURCHASED ->
                        events.add(OfflineEvent.ManagerAction(currentDay, hire.entityDefName, hire.reason))
                    AutoHireAction.REBALANCED ->
                        events.add(OfflineEvent.ManagerAction(currentDay, hire.entityDefName, hire.reason))
                    AutoHireAction.PROMOTED ->
                        events.add(OfflineEvent.ManagerAction(currentDay, hire.entityDefName, hire.reason))
                    AutoHireAction.TERMINATED ->
                        events.add(OfflineEvent.StaffTerminated(currentDay, hire.entityDefName, hire.reason))
                    AutoHireAction.REDUCED_HOURS ->
                        events.add(OfflineEvent.ManagerAction(currentDay, hire.entityDefName, hire.reason))
                    AutoHireAction.SCHEDULE_OPTIMIZED ->
                        events.add(OfflineEvent.ManagerAction(currentDay, hire.entityDefName, hire.reason))
                    AutoHireAction.SKIPPED -> { }
                }
            }
        }

        val allFresh = allFreshOrderItems()
        if (allFresh.size > prevFreshOrders) {
            val items = allFresh.drop(prevFreshOrders)
            val totalCost = items.fold(Money.ZERO) { acc, it -> acc + it.totalCost }
            val count = items.sumOf { it.casePacksOrdered }
            mergeOrAddOrder(events, currentDay, count, totalCost, isFresh = true)
        }

        val allNormal = allNormalOrderItems()
        if (allNormal.size > prevNormalOrders) {
            val items = allNormal.drop(prevNormalOrders)
            val totalCost = items.fold(Money.ZERO) { acc, it -> acc + it.totalCost }
            val count = items.sumOf { it.casePacksOrdered }
            mergeOrAddOrder(events, currentDay, count, totalCost, isFresh = false)
        }

        val newExpired = cumulativeExpired()
        if (newExpired > prevExpired) {
            val expiredDelta = newExpired - prevExpired
            val costDelta = cumulativeExpiredCost() - prevExpiredCost
            val last = events.lastOrNull()
            if (last is OfflineEvent.ItemsExpired && last.dayNumber == currentDay) {
                events[events.lastIndex] = last.copy(
                    count = last.count + expiredDelta,
                    valueLost = last.valueLost + costDelta,
                )
            } else {
                events.add(OfflineEvent.ItemsExpired(currentDay, expiredDelta, costDelta))
            }
        }
    }

    private fun mergeOrAddOrder(
        events: MutableList<OfflineEvent>,
        currentDay: Int,
        count: Int,
        totalCost: Money,
        isFresh: Boolean,
    ) {
        val last = events.lastOrNull()
        if (last is OfflineEvent.AutoOrderPlaced && last.isFresh == isFresh && last.dayNumber == currentDay) {
            events[events.lastIndex] = last.copy(
                itemCount = last.itemCount + count,
                totalCost = last.totalCost + totalCost,
            )
        } else {
            events.add(OfflineEvent.AutoOrderPlaced(currentDay, count, totalCost, isFresh))
        }
    }

    private fun computeGameMinutes(elapsedRealMs: Long): Long {
        return (elapsedRealMs * TimeManager.BASE_SPEED * OFFLINE_SPEED_FACTOR / 60_000f).toLong()
    }

    private fun cumulativeRevenue(): Money {
        val archived = Money(gameEngine.state.archivedCumulativeRevenueCents)
        val completed = gameEngine.state.completedDayMetrics.fold(Money.ZERO) { acc, d -> acc + d.revenue }
        return archived + completed + gameEngine.state.currentDayMetrics.revenue
    }

    private fun cumulativeExpired(): Int {
        val archived = gameEngine.state.archivedCumulativeExpiredItems
        val completed = gameEngine.state.completedDayMetrics.sumOf { it.itemsExpired }
        return archived + completed + gameEngine.state.currentDayMetrics.itemsExpired
    }

    private fun cumulativeExpiredCost(): Money {
        val archived = Money(gameEngine.state.archivedCumulativeExpiredWasteCostCents)
        val completed = gameEngine.state.completedDayMetrics.fold(Money.ZERO) { acc, d -> acc + d.expiredWasteCost }
        return archived + completed + gameEngine.state.currentDayMetrics.expiredWasteCost
    }

    /** Concatenate [selector] across all completed days plus the current day. */
    private fun <T> allOf(selector: (com.example.superstoresimulator.domain.metrics.DailyMetrics) -> List<T>): List<T> =
        gameEngine.state.completedDayMetrics.flatMap(selector) + selector(gameEngine.state.currentDayMetrics)

    private fun allDeliveredTrucks() = allOf { it.deliveredTrucks }
    private fun allAutoHireEvents() = allOf { it.autoHireEvents }
    private fun allFreshOrderItems() = allOf { it.autoOrderedFreshItems }
    private fun allNormalOrderItems() = allOf { it.autoOrderedNormalItems }

    private fun emptyResult() = OfflineCatchUpResult(
        gameMinutesSimulated = 0,
        daysCrossed = 0,
        revenue = Money.ZERO,
        expenses = Money.ZERO,
        currentMoney = gameEngine.state.money,
        itemsExpired = 0,
        trucksArrived = 0,
        staffHired = 0,
        completedDayMetrics = emptyList(),
    )
}
