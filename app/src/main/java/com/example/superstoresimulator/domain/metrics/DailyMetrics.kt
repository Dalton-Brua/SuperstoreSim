package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.time.GameTime
import kotlinx.serialization.Serializable

@Serializable
data class DeliveredItemLine(
    val itemId: Int,
    val itemName: String = "",
    val casePacks: Int,
    val quantity: Int,
    val deferredCasePacks: Int = 0,
    val deferredQuantity: Int = 0,
)

@Serializable
data class DeliveredTruckRecord(
    val truckId: Int,
    val arrivalDay: Int,
    val isFreshTruck: Boolean,
    val isEarlyTruck: Boolean,
    val totalCasePacks: Int,
    val lines: List<DeliveredItemLine>,
)

@Serializable
data class AutoOrderLineItem(
    val itemId: Int,
    val itemName: String = "",
    val casePacksOrdered: Int,
    val costPerCasePack: Money,
    val totalCost: Money,
)

@Serializable
data class IncompleteAutoOrderLineItem(
    val itemId: Int,
    val itemName: String = "",
    val casePacksRequested: Int,
    val costPerCasePack: Money,
    val totalCost: Money,
    val reason: String,
)

@Serializable
data class ExpiredItemEvent(
    val itemId: Int,
    val itemName: String = "",
    /** How many units expired (shelf + backroom combined). */
    val quantity: Int,
    /** Value lost (at unit cost, not sale price — this is what was paid for the lost inventory). */
    val valueLost: Money,
)

@Serializable
data class OutOfStockEvent(
    val itemId: Int,
    val itemName: String = "",
    /** How many units the customer wanted that we could not fulfil. */
    val quantityLost: Int,
    /** Revenue we would have earned on those units. */
    val revenueLost: Money,
)

@Serializable
enum class AutoHireAction { HIRED, SKIPPED, REBALANCED, PURCHASED, PROMOTED, TERMINATED, REDUCED_HOURS, SCHEDULE_OPTIMIZED }

@Serializable
data class AutoHireEvent(
    val entityDefName: String,
    val reason: String,
    val detail: String = "",
    val blocked: Boolean = false,
    val blockReason: String = "",
    val action: AutoHireAction = if (blocked) AutoHireAction.SKIPPED else AutoHireAction.HIRED,
)

@Serializable
data class CompletedResearchEvent(
    val upgradeId: String,
    val displayName: String,
    /** How many items this upgrade unlocked. */
    val unlockedItemCount: Int,
)

@Serializable
data class SoldItemEvent(
    val itemId: Int,
    val itemName: String = "",
    /** How many units were sold across all transactions this day. */
    val quantitySold: Int,
    /** Revenue earned from those units. */
    val revenue: Money,
    val effectivePrice: Money = Money.ZERO,
    val basePrice: Money = Money.ZERO,
)

@Serializable
data class DailyMetrics(
    val dayNumber: Int = 0,
    val dayOfWeek: Int = 0,              // 0 = Monday … 6 = Sunday

    // ── Sales ────────────────────────────────────────────────────────────
    val subtotal: Money = Money.ZERO,
    val taxCollected: Money = Money.ZERO,
    val transactionsCompleted: Int = 0,

    // ── Operating Costs ──────────────────────────────────────────────────
    val rentPaid: Money = Money.ZERO,       // Daily rent deducted
    val wagesPaid: Money = Money.ZERO,      // Total staff wages for the day

    // ── Customers ────────────────────────────────────────────────────────
    val customersServed: Int = 0,

    // ── Inventory ────────────────────────────────────────────────────────
    val itemsSold: Int = 0,              // individual units sold via transactions
    val itemsStocked: Int = 0,           // units moved from backroom → shelf
    val itemsOrdered: Int = 0,           // units bought/ordered to backroom

    // ── Out-of-stock losses ───────────────────────────────────────────────
    val lostRevenue: Money = Money.ZERO,
    val itemsLostToOutOfStock: Int = 0,
    val outOfStockEvents: List<OutOfStockEvent> = emptyList(),

    // ── Per-item sales breakdown ──────────────────────────────────────────
    val soldItemEvents: List<SoldItemEvent> = emptyList(),

    // ── Expiration & Shrinkage ────────────────────────────────────────────
    val itemsExpired: Int = 0,           // units removed due to expiration
    val expiredWasteCost: Money = Money.ZERO,  // value lost to expiration (at unit cost)
    val expiredItemEvents: List<ExpiredItemEvent> = emptyList(),

    // ── Fresh Item Auto-Ordering ──────────────────────────────────────────
    val autoOrderedFreshItems: List<AutoOrderLineItem> = emptyList(),
    val incompleteOrderedFreshItems: List<IncompleteAutoOrderLineItem> = emptyList(),

    // ── Normal Item Auto-Ordering (Stocking Manager) ─────────────────────
    val autoOrderedNormalItems: List<AutoOrderLineItem> = emptyList(),
    val incompleteOrderedNormalItems: List<IncompleteAutoOrderLineItem> = emptyList(),

    // ── Truck Deliveries ──────────────────────────────────────────────────
    val deliveredTrucks: List<DeliveredTruckRecord> = emptyList(),

    // ── Auto-Hire Events ─────────────────────────────────────────────────
    val autoHireEvents: List<AutoHireEvent> = emptyList(),

    // ── Staff Utilization ────────────────────────────────────────────────
    val avgCashierUtilization: Float = 0f,
    val avgStockerUtilization: Float = 0f,
    val avgFreshUtilization: Float = 0f,
    val avgZoneScore: Float = 1f,

    // ── Pricing ─────────────────────────────────────────────────────────
    val markdownsSaved: Money = Money.ZERO,
    val markupExtraRevenue: Money = Money.ZERO,
    val itemsMarkedDown: Int = 0,

    // ── Vendor System ────────────────────────────────────────────────────
    val vendorCommissionPaid: Money = Money.ZERO,
    val vendorItemsSold: Int = 0,
    val vendorRevenue: Money = Money.ZERO,

    // ── Research ──────────────────────────────────────────────────────────
    val completedResearch: List<CompletedResearchEvent> = emptyList(),
) {
    val revenue: Money get() = subtotal + taxCollected

    /** Average value per completed transaction (ZERO if no transactions). */
    val averageTransactionValue: Money
        get() = if (transactionsCompleted > 0)
            Money(revenue.cents / transactionsCompleted)
        else Money.ZERO

    /** Average number of items per transaction (0f if no transactions). */
    val averageBasketSize: Float
        get() = if (transactionsCompleted > 0)
            itemsSold.toFloat() / transactionsCompleted
        else 0f

    /** Net revenue after rent, wages, and waste costs. */
    val netRevenue: Money
        get() = revenue - rentPaid - wagesPaid - expiredWasteCost - vendorCommissionPaid

    val dayOfWeekName: String
        get() = GameTime.fullDayName(dayOfWeek)
}
