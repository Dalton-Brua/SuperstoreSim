package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.Money
import kotlinx.serialization.Serializable

@Serializable
data class DeliveredItemLine(
    val itemId: Int,
    val itemName: String,
    val casePacks: Int,
    val quantity: Int,
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
    val itemName: String,
    val casePacksOrdered: Int,
    val costPerCasePack: Money,
    val totalCost: Money,
)

@Serializable
data class IncompleteAutoOrderLineItem(
    val itemId: Int,
    val itemName: String,
    val casePacksRequested: Int,
    val costPerCasePack: Money,
    val totalCost: Money,
    val reason: String,
)

@Serializable
data class ExpiredItemEvent(
    val itemId: Int,
    val itemName: String,
    /** How many units expired (shelf + backroom combined). */
    val quantity: Int,
    /** Value lost (at unit cost, not sale price — this is what was paid for the lost inventory). */
    val valueLost: Money,
)

@Serializable
data class OutOfStockEvent(
    val itemId: Int,
    val itemName: String,
    /** How many units the customer wanted that we could not fulfil. */
    val quantityLost: Int,
    /** Revenue we would have earned on those units. */
    val revenueLost: Money,
)

@Serializable
enum class AutoHireAction { HIRED, SKIPPED, REBALANCED, PURCHASED }

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
data class SoldItemEvent(
    val itemId: Int,
    val itemName: String,
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
    val revenue: get() = subtotal + taxCollected,      // gross revenue (subtotal + tax)
    val transactionsCompleted: Int = 0,

    // ── Operating Costs ──────────────────────────────────────────────────
    val rentPaid: Money = Money.ZERO,       // Daily rent deducted
    val wagesPaid: Money = Money.ZERO,      // Total staff wages for the day

    // ── Refunds ──────────────────────────────────────────────────────────
    val refundsProcessed: Int = 0,
    val refundAmount: Money = Money.ZERO,

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

    // ── Pricing ─────────────────────────────────────────────────────────
    val markdownsSaved: Money = Money.ZERO,
    val markupExtraRevenue: Money = Money.ZERO,
    val itemsMarkedDown: Int = 0,
) {
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

    /** Net revenue after refunds, rent, wages, and waste costs. */
    val netRevenue: Money
        get() = (revenue - refundAmount) - rentPaid - wagesPaid - expiredWasteCost

    val dayOfWeekName: String
        get() = when (dayOfWeek) {
            0 -> "Monday"; 1 -> "Tuesday"; 2 -> "Wednesday"; 3 -> "Thursday"
            4 -> "Friday"; 5 -> "Saturday"; 6 -> "Sunday"
            else -> "Unknown"
        }
}
