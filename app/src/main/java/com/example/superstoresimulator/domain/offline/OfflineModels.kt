package com.example.superstoresimulator.domain.offline

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.metrics.DailyMetrics

data class OfflineCatchUpResult(
    val gameMinutesSimulated: Long,
    val daysCrossed: Int,
    val revenue: Money,
    val expenses: Money,
    val currentMoney: Money,
    val itemsExpired: Int,
    val trucksArrived: Int,
    val staffHired: Int,
    val completedDayMetrics: List<DailyMetrics>,
    val events: List<OfflineEvent> = emptyList(),
)

sealed interface OfflineEvent {
    val dayNumber: Int

    data class NewDay(override val dayNumber: Int) : OfflineEvent
    data class TruckArrived(override val dayNumber: Int, val isFresh: Boolean) : OfflineEvent
    data class StaffHired(override val dayNumber: Int, val role: String, val reason: String) : OfflineEvent
    data class AutoOrderPlaced(override val dayNumber: Int, val itemCount: Int, val totalCost: Money, val isFresh: Boolean) : OfflineEvent
    data class ItemsExpired(override val dayNumber: Int, val count: Int, val valueLost: Money) : OfflineEvent
    data class ManagerAction(override val dayNumber: Int, val subject: String, val detail: String) : OfflineEvent
    data class StaffTerminated(override val dayNumber: Int, val role: String, val reason: String) : OfflineEvent
    data class TruckScheduled(override val dayNumber: Int, val arrivalDay: Int, val isFresh: Boolean) : OfflineEvent
}

data class OfflineProgress(
    val currentMinute: Long,
    val totalMinutes: Long,
    val currentDay: Int,
    val fractionComplete: Float,
    val currentMoney: Money = Money.ZERO,
    val revenueEarned: Money = Money.ZERO,
    val expensesPaid: Money = Money.ZERO,
    val events: List<OfflineEvent> = emptyList(),
)

sealed interface OfflineState {
    data object Idle : OfflineState
    data class CatchingUp(val progress: OfflineProgress) : OfflineState
    data class Summary(val result: OfflineCatchUpResult) : OfflineState
}
