package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.metrics.DailyMetricsAccumulator
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreState
import java.util.Locale

// ── Truck Delivery System ─────────────────────────────────────────────────────

/**
 * Single item line awaiting delivery. Capacity is measured in case packs.
 */
data class PendingOrderLine(
    val itemId: Int,
    val quantity: Int,         // total units in this line
    val casePacksCount: Int,   // number of case packs (for capacity tracking)
    val unitCost: Money,       // per-unit cost (used for refund on cancellation)
    val orderedOnDay: Int,
    val isFresh: Boolean,
)

/**
 * A truck with a locked arrival day, carrying [orders].
 */
data class ScheduledTruck(
    val truckId: Int,
    val scheduledArrivalDay: Int,
    val capacityCasePacks: Int,
    val orders: List<PendingOrderLine> = emptyList(),
    val isFreshTruck: Boolean = false,
    val isEarlyTruck: Boolean = false,
) {
    val usedCapacityCasePacks: Int get() = orders.sumOf { it.casePacksCount }
    val remainingCapacityCasePacks: Int get() = capacityCasePacks - usedCapacityCasePacks
}

/**
 * Player-configurable truck delivery schedule and capacities.
 */
data class TruckConfig(
    /** Day-of-week indices for regular trucks (0 = Monday … 6 = Sunday). */
    val deliveryDays: Set<Int> = setOf(0, 3),  // Monday and Thursday by default
    val regularTruckCapacityCasePacks: Int = DEFAULT_REGULAR_TRUCK_CAPACITY,
    val freshTruckCapacityCasePacks: Int = DEFAULT_FRESH_TRUCK_CAPACITY,
) {
    companion object {
        const val DEFAULT_REGULAR_TRUCK_CAPACITY = 2000
        const val DEFAULT_FRESH_TRUCK_CAPACITY = 500
    }
}

/**
 * Configuration for fresh item auto-ordering behavior.
 */
data class FreshAutoOrderConfig(
    val enabled: Boolean = true,
    val minStockThreshold: Int = 5,      // Player sets minimum total stock (shelf + backroom)
    val casePacksPerItem: Int = 1,       // How many case packs to order per item when triggered
)


/**
 * Represents a fresh item order that failed to complete (e.g., insufficient funds).
 */
data class IncompleteOrderRequest(
    val itemId: Int,
    val casePacksRequested: Int,
    val requestedOnDay: Int,
    val reason: String,
)

data class GameState(

    val storeName: String = "Grocery Store",

    val money: Money = Money(0), // (Dollars, Cents)

    val currentTransaction: Transaction = Transaction(),

    val hiredEntityRegistry: HiredEntityRegistry = HiredEntityRegistry(),

    val transactionActive: Boolean = false,
    val totalTransactionsCompleted: Int = 0,
    val salesHistory: List<Transaction> = emptyList(),

    val totalTaxCollected: Money = Money(0),

    // Pending refunds that must be handled manually (by player or staff)
    val pendingRefunds: List<RefundRequest> = emptyList(),
    // Counter for assigning refund IDs
    val nextRefundId: Int = 1,

    val inventory: Map<Int, InventoryState> = emptyMap(),

    // Time and store state
    val currentTime: GameTime = GameTime(0),
    val storeConfig: StoreConfig = StoreConfig(),
    val storeState: StoreState = StoreState.CLOSED,
    val playerPausedTime: Boolean = false,

    val currentStoreSize: StoreSize = StoreSize.MOM_AND_POP,

    val playerRole: PlayerRole = PlayerRole.NONE,
    val playerCashierProgress: Float = 0f,  // Fractional ring-up accumulator
    val playerStockerProgress: Float = 0f,  // Fractional stock accumulator

    val pendingCustomers: Int = 0,

    // Progression: cumulative revenue (never decreases) and the resolved unlock tier
    val totalRevenue: Money = Money.ZERO,
    val currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1,

    val currentDayMetrics: DailyMetricsAccumulator = DailyMetricsAccumulator(),
    val completedDayMetrics: List<DailyMetrics> = emptyList(),
    val showEndOfDayReport: Boolean = false,
    val lastEndOfDayReport: DailyMetrics? = null,
    // True when the game engine paused time on behalf of the player for the end-of-day report.
    // Allows dismissEndOfDayReport() to restore the correct paused/running state.
    val pausedByEndOfDay: Boolean = false,

    val objectiveBonusEarned: Money = Money.ZERO,  // Bonus from completed objectives today

    // Fresh item auto-ordering
    val freshAutoOrderConfig: FreshAutoOrderConfig = FreshAutoOrderConfig(),
    val incompleteFreshOrders: List<IncompleteOrderRequest> = emptyList(),

    // Truck delivery system
    val scheduledTrucks: List<ScheduledTruck> = emptyList(),
    val truckConfig: TruckConfig = TruckConfig(),
    val nextTruckId: Int = 1,
)


@JvmInline
value class Money(val cents: Long) {

    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun compareTo(other: Money) = cents.compareTo(other.cents)
    operator fun times(multiplier: Int) = Money(cents * multiplier)
    operator fun times(multiplier: Double) = Money(cents/100 * (multiplier*100).toLong())
    operator fun times(multiplier: Long) = Money(cents * multiplier)
    operator fun unaryMinus() = Money(-cents)
    fun toDouble() = cents / 100.0
    override fun toString(): String = String.format(Locale.US, "$%.2f", cents / 100.0)
    companion object {
        val ZERO = Money(0)
        fun fromCents(c: Long) = Money(c)
        fun fromDollars(d: Double) = Money((d * 100).toLong())

    }
}
