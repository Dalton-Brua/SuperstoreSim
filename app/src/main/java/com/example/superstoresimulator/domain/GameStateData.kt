package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.metrics.WeeklyReport
import com.example.superstoresimulator.domain.reputation.ReputationState
import com.example.superstoresimulator.domain.research.ResearchState
import com.example.superstoresimulator.domain.tutorial.TutorialState

import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.pricing.PricingState
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.vendor.VendorSystemState
import com.example.superstoresimulator.domain.empire.EmpireClock
import com.example.superstoresimulator.domain.empire.Region
import com.example.superstoresimulator.domain.empire.RegionalManager
import com.example.superstoresimulator.domain.empire.SecondaryStore
import kotlinx.serialization.Serializable
import java.util.Locale

const val SAVE_VERSION = 1

// ── Register System ────────────────────────────────────────────────────────────

@Serializable
data class RegisterState(
    val registerId: Int,
    val assignedCashierId: Int? = null,
    val currentTransaction: Transaction = Transaction(),
    val transactionActive: Boolean = false,
    val dailyTransactions: Int = 0,
    val dailyRevenue: Money = Money.ZERO,
)

/** ID-based lookup — never use index arithmetic on the registers list. */
fun List<RegisterState>.findRegisterById(registerId: Int): RegisterState? =
    firstOrNull { it.registerId == registerId }

/** Returns a new list with the matching register replaced by [updated]. */
fun List<RegisterState>.updateRegister(updated: RegisterState): List<RegisterState> =
    map { if (it.registerId == updated.registerId) updated else it }

// ── Staff Scheduling ─────────────────────────────────────────────────────────

@Serializable
data class StaffShift(
    val entityId: Int,
    val startHour: Int,
    val durationHours: Int = 8,
    val workDays: Set<Int> = emptySet(),
) {
    init {
        require(startHour in 6..20) {
            "startHour must be between 6 and 20 (inclusive), got $startHour"
        }
        require(durationHours in 2..8) {
            "durationHours must be between 2 and 8 (inclusive), got $durationHours"
        }
        require(startHour + durationHours <= 21) {
            "shift must end by 21:00, got startHour=$startHour + duration=$durationHours = ${startHour + durationHours}"
        }
    }

    val endHour: Int get() = startHour + durationHours

    val daysPerWeek: Int get() = if (workDays.isEmpty()) 7 else workDays.size

    fun isOnShift(hour: Int): Boolean = hour >= startHour && hour < endHour

    fun isOnShift(hour: Int, dayOfWeek: Int): Boolean =
        (workDays.isEmpty() || dayOfWeek in workDays) && hour >= startHour && hour < endHour
}

// ── Truck Delivery System ─────────────────────────────────────────────────────

@Serializable
data class PendingOrderLine(
    val itemId: Int,
    val quantity: Int,         // total units in this line
    val casePacksCount: Int,   // number of case packs (for capacity tracking)
    val unitCost: Money,       // per-unit cost (used for refund on cancellation)
    val orderedOnDay: Int,
    val isFresh: Boolean,
)

@Serializable
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

@Serializable
data class TruckCapacityTier(
    val tier: Int,
    val regularCapacity: Int,
    val freshCapacity: Int,
    val upgradeCost: Money?,
    val displayName: String,
)

val TRUCK_CAPACITY_TIERS = listOf(
    TruckCapacityTier(0, 2000,  500, null,               "Standard Fleet"),
    TruckCapacityTier(1, 3000,  750, Money(10_000_000L),  "Enhanced Fleet"),   // $100K
    TruckCapacityTier(2, 5000, 1250, Money(20_000_000L),  "Heavy Fleet"),      // $200K
)

@Serializable
data class TruckConfig(
    /** Day-of-week indices for regular trucks (0 = Monday … 6 = Sunday). */
    val deliveryDays: Set<Int> = setOf(0, 3),  // Monday and Thursday by default
    val regularTruckCapacityCasePacks: Int = DEFAULT_REGULAR_TRUCK_CAPACITY,
    val freshTruckCapacityCasePacks: Int = DEFAULT_FRESH_TRUCK_CAPACITY,
    /** Number of extra delivery-day slots purchased beyond the store-size-based free limit. */
    val extraTruckSlotsUnlocked: Int = 0,
    val truckCapacityTier: Int = 0,
) {
    val effectiveRegularCapacity: Int
        get() = TRUCK_CAPACITY_TIERS.getOrNull(truckCapacityTier)?.regularCapacity ?: DEFAULT_REGULAR_TRUCK_CAPACITY
    val effectiveFreshCapacity: Int
        get() = TRUCK_CAPACITY_TIERS.getOrNull(truckCapacityTier)?.freshCapacity ?: DEFAULT_FRESH_TRUCK_CAPACITY

    companion object {
        const val DEFAULT_REGULAR_TRUCK_CAPACITY = 2000
        const val DEFAULT_FRESH_TRUCK_CAPACITY = 500
        /** One-time cost to unlock one additional weekly delivery slot beyond the free limit. */
        val EXTRA_SLOT_COST = Money(100_000L) // $1,000
        /** Base free delivery days at the smallest store size. */
        const val BASE_FREE_SLOTS = 2
    }
}

@Serializable
data class AutoOrderConfig(
    val enabled: Boolean = true,
    val minStockThreshold: Int = 5,
    val casePacksPerItem: Int = 1,
)

typealias FreshAutoOrderConfig = AutoOrderConfig
typealias NormalAutoOrderConfig = AutoOrderConfig

@Serializable
data class StoreManagerConfig(
    // Auto-hire: master toggle
    val autoHireEnabled: Boolean = true,
    // Auto-hire: per-department toggles
    val autoHireCashiers: Boolean = true,
    val autoHireStockers: Boolean = true,
    val autoHireFreshHandlers: Boolean = true,
    // Auto-hire: stocker criteria
    val hireStockerOnBackroomFull: Boolean = true,
    val hireStockerOnLowZoneScore: Boolean = true,
    val zoneScoreHireThreshold: Int = 80,
    // Truck management
    val autoFillDeliverySlots: Boolean = true,
    val autoEarlyTruckEnabled: Boolean = true,
    val earlyTruckOosPercent: Int = 3,
    val autoBuyTruckSlotEnabled: Boolean = true,
    val buySlotOosThreshold: Int = 10,
    // Registers
    val autoBuyRegistersEnabled: Boolean = true,
    // Shifts
    val autoRebalanceShiftsEnabled: Boolean = true,
    val autoPromoteEnabled: Boolean = true,
    val autoTerminateEnabled: Boolean = true,
    // Weekly scheduling
    val autoOptimizeWeeklySchedule: Boolean = true,
    val minDaysPerWeek: Int = 3,
    val maxDaysPerWeek: Int = 5,
)

@Serializable
data class ZoningState(
    val targetItemId: Int? = null,
    val progress: Float = 0f,
)

@Serializable
data class SimAccumulators(
    val timeAccumulatorMs: Long = 0L,
    val trafficAccumulator: Double = 0.0,
    val lastKnownDayNumber: Int = 0,
    val cashierProgressByRegister: Map<Int, Float> = emptyMap(),
    val stockerProgress: Float = 0f,
    val stockingManagerProgress: Float = 0f,
    val freshHandlerProgress: Float = 0f,
    val zoningByStockerId: Map<Int, ZoningState> = emptyMap(),
)

@Serializable
data class IncompleteOrderRequest(
    val itemId: Int,
    val casePacksRequested: Int,
    val requestedOnDay: Int,
    val reason: String,
)

@Serializable
data class DailyStaffMetrics(
    val peakPendingCustomers: Int = 0,
    val avgHourlyPendingCustomers: Float = 0f,
    val avgCashierUtilization: Float = 0f,
    val avgStockerUtilization: Float = 0f,
    val avgFreshUtilization: Float = 0f,
    val hasUnstaffedRegisters: Boolean = false,
    val freshItemsOutOfStock: Int = 0,
    val freshOrdersAttempted: Int = 0,
)

@Serializable
data class GameState(

    val saveVersion: Int = SAVE_VERSION,

    val storeName: String = "Grocery Store",

    val money: Money = Money(0), // (Dollars, Cents)

    val hiredEntityRegistry: HiredEntityRegistry = HiredEntityRegistry(),

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

    val buildingOwned: Boolean = false,

    val playerRole: PlayerRole = PlayerRole.MANAGE,
    val playerCashierProgress: Float = 0f,  // Fractional ring-up accumulator
    val playerStockerProgress: Float = 0f,  // Fractional stock accumulator

    val pendingCustomers: Int = 0,

    // Progression: cumulative revenue (never decreases)
    val totalRevenue: Money = Money.ZERO,

    // Research system
    val researchState: ResearchState = ResearchState(),

    // Tutorial system
    val tutorialState: TutorialState = TutorialState(),

    val currentDayMetrics: DailyMetrics = DailyMetrics(),
    val completedDayMetrics: List<DailyMetrics> = emptyList(),
    val showEndOfDayReport: Boolean = false,
    val lastEndOfDayReport: DailyMetrics? = null,
    // True when the game engine paused time on behalf of the player for the end-of-day report.
    // Allows dismissEndOfDayReport() to restore the correct paused/running state.
    val pausedByEndOfDay: Boolean = false,

    // Cumulative totals from days archived to Room (not in completedDayMetrics)
    val archivedCumulativeRevenueCents: Long = 0L,
    val archivedCumulativeExpiredItems: Int = 0,
    val archivedCumulativeExpiredWasteCostCents: Long = 0L,

    @kotlinx.serialization.Transient
    val showEndOfWeekReport: Boolean = false,
    @kotlinx.serialization.Transient
    val lastEndOfWeekReport: WeeklyReport? = null,

    val objectiveBonusEarned: Money = Money.ZERO,  // Bonus from completed objectives today

    // Fresh item auto-ordering
    val freshAutoOrderConfig: FreshAutoOrderConfig = FreshAutoOrderConfig(),
    val incompleteFreshOrders: List<IncompleteOrderRequest> = emptyList(),

    // Normal item auto-ordering (Stocking Manager)
    val normalAutoOrderConfig: NormalAutoOrderConfig = NormalAutoOrderConfig(),
    val incompleteNormalOrders: List<IncompleteOrderRequest> = emptyList(),

    // Store Manager autonomous action config
    val storeManagerConfig: StoreManagerConfig = StoreManagerConfig(),
    // Day number when the store manager last terminated each entity type (keyed by EntityDef.key)
    val lastTerminationDayByType: Map<String, Int> = emptyMap(),

    // Truck delivery system
    val scheduledTrucks: List<ScheduledTruck> = emptyList(),
    val truckConfig: TruckConfig = TruckConfig(),
    val nextTruckId: Int = 1,

    // Staff scheduling
    val staffSchedules: List<StaffShift> = emptyList(),

    // ── Register System ───────────────────────────────────────────────────────
    /** All owned registers.  Starts with a single register (id=0). */
    val registers: List<RegisterState> = listOf(RegisterState(registerId = 0)),
    /** Global monotonic counter for unique transaction IDs across all registers. */
    val nextTransactionId: Int = 1,
    /** Register id the player has claimed as cashier, or null if unassigned. */
    val playerAssignedRegisterId: Int? = null,
    /** Cashier entity IDs manually unassigned from registers today — blocked from auto-reassignment until midnight. */
    val manuallyUnassignedCashiers: Set<Int> = emptySet(),

    // Auto-hire budget: managers will not hire if doing so would bring money below this threshold
    val autoHireBudget: Money = StoreSize.MOM_AND_POP.dailyRent,

    // Pricing system
    val pricingState: PricingState = PricingState(),

    // Vendor system
    val vendorSystem: VendorSystemState = VendorSystemState(),

    // Reputation system
    val reputationState: ReputationState = ReputationState(),

    // Simulation accumulators — fractional progress, traffic, day tracking
    val simAccumulators: SimAccumulators = SimAccumulators(),

    // ── Empire mode (loop 2) — additive, all defaulted; off until the player goes corporate ──
    /** One-way flag: once true, the home store is an abstract directed store and loop 1 is retired. */
    val empireModeActive: Boolean = false,
    /** Unlocked/known markets (seeded from RegionRegistry on entering empire mode). */
    val regions: List<Region> = emptyList(),
    val secondaryStores: List<SecondaryStore> = emptyList(),
    val nextSecondaryStoreId: Int = 1,
    val regionalManager: RegionalManager? = null,
    val empireClock: EmpireClock = EmpireClock(),
    /** When non-null, the player is hands-on operating this store; loop-1 fields hold its state. */
    val operatingStoreId: Int? = null,
) {
    val ownedRegisterCount: Int get() = registers.size

    val avgZoneScore: Float
        get() {
            var sum = 0.0
            var count = 0
            for (inv in inventory.values) {
                if (inv.shelfStock > 0) {
                    sum += inv.zoneScore
                    count++
                }
            }
            return if (count == 0) 1.0f else (sum / count).toFloat()
        }

    val currentTransaction: Transaction
        get() = registers.firstOrNull()?.currentTransaction ?: Transaction()
    val transactionActive: Boolean
        get() = registers.firstOrNull()?.transactionActive ?: false
}


@Serializable
@JvmInline
value class Money(val cents: Long) {

    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun compareTo(other: Money) = cents.compareTo(other.cents)
    operator fun times(multiplier: Int) = Money(cents * multiplier)
    operator fun times(multiplier: Double) = Money((cents * multiplier).toLong())
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
