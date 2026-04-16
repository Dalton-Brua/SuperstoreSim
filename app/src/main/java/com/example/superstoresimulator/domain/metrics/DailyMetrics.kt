package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.Money

/**
 * One item that a customer wanted but could not be rung up due to zero shelf stock.
 * Aggregated per-day to drive the "Out-of-Stock Report" on the Metrics screen.
 */
data class OutOfStockEvent(
    val itemId: Int,
    val itemName: String,
    /** How many units the customer wanted that we could not fulfil. */
    val quantityLost: Int,
    /** Revenue we would have earned on those units. */
    val revenueLost: Money,
)

/**
 * One line-item that was successfully sold during the day.
 * Aggregated per-day to drive the "Items Sold Report" on the Metrics screen.
 */
data class SoldItemEvent(
    val itemId: Int,
    val itemName: String,
    /** How many units were sold across all transactions this day. */
    val quantitySold: Int,
    /** Revenue earned from those units. */
    val revenue: Money,
)

/**
 * Immutable snapshot of all tracked metrics for a single game day.
 * Created at midnight when the day rolls over (or at game end).
 */
data class DailyMetrics(
    val dayNumber: Int,
    val dayOfWeek: Int,                  // 0 = Monday … 6 = Sunday

    // ── Sales ────────────────────────────────────────────────────────────
    val revenue: Money = Money.ZERO,     // gross revenue (subtotal + tax)
    val subtotal: Money = Money.ZERO,
    val taxCollected: Money = Money.ZERO,
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

    /** Net revenue after refunds, rent, and wages. */
    val netRevenue: Money
        get() = (revenue - refundAmount) - rentPaid - wagesPaid

    val dayOfWeekName: String
        get() = when (dayOfWeek) {
            0 -> "Monday"; 1 -> "Tuesday"; 2 -> "Wednesday"; 3 -> "Thursday"
            4 -> "Friday"; 5 -> "Saturday"; 6 -> "Sunday"
            else -> "Unknown"
        }
}

/**
 * In-progress accumulator for the current game day.
 * Stored in GameState as an immutable data class — updated via `.copy()`.
 * Converted to [DailyMetrics] at midnight via [toSnapshot].
 */
data class DailyMetricsAccumulator(
    val dayNumber: Int = 0,

    val revenue: Money = Money.ZERO,
    val subtotal: Money = Money.ZERO,
    val taxCollected: Money = Money.ZERO,
    val transactionsCompleted: Int = 0,

    val rentPaid: Money = Money.ZERO,
    val wagesPaid: Money = Money.ZERO,

    val refundsProcessed: Int = 0,
    val refundAmount: Money = Money.ZERO,

    val customersServed: Int = 0,

    val itemsSold: Int = 0,
    val itemsStocked: Int = 0,
    val itemsOrdered: Int = 0,

    // ── Out-of-stock losses ───────────────────────────────────────────────
    val lostRevenue: Money = Money.ZERO,
    val itemsLostToOutOfStock: Int = 0,
    val outOfStockEvents: List<OutOfStockEvent> = emptyList(),

    // ── Per-item sales breakdown ──────────────────────────────────────────
    val soldItemEvents: List<SoldItemEvent> = emptyList(),
) {
    /** Snapshot this accumulator into an immutable [DailyMetrics]. */
    fun toSnapshot(dayOfWeek: Int): DailyMetrics = DailyMetrics(
        dayNumber = dayNumber,
        dayOfWeek = dayOfWeek,
        revenue = revenue,
        subtotal = subtotal,
        taxCollected = taxCollected,
        transactionsCompleted = transactionsCompleted,
        rentPaid = rentPaid,
        wagesPaid = wagesPaid,
        refundsProcessed = refundsProcessed,
        refundAmount = refundAmount,
        customersServed = customersServed,
        itemsSold = itemsSold,
        itemsStocked = itemsStocked,
        itemsOrdered = itemsOrdered,
        lostRevenue = lostRevenue,
        itemsLostToOutOfStock = itemsLostToOutOfStock,
        outOfStockEvents = outOfStockEvents,
        soldItemEvents = soldItemEvents,
    )
}
