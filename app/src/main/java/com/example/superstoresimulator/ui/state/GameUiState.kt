package com.example.superstoresimulator.ui.state

import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.store.StoreState

data class GameUiState(

    val app: AppUIState,
    val dashboard: DashboardUIState,
    val transactions: TransactionUIState,
    val inventory: InventoryUIState,
    val staff: StaffUIState,
    val history: HistoryUIState,
    val time: TimeUIState? = null,
    val metrics: MetricsUIState = MetricsUIState(),
    val progression: ProgressionUIState = ProgressionUIState(),
    val delivery: DeliveryUIState = DeliveryUIState(),
)

data class AppUIState(
    val storeName: String,
    val transactionActive: Boolean = false,
    val pendingRefunds: Int,
    val showTransactionDialog: Boolean = false,
    val showPendingRefundsDialog: Boolean = false,
    val money: Money,
    val playerHasOpenedStore: Boolean = false,
)

data class DashboardUIState(
    val money: Money,
    val totalStaff: Int
)

data class TransactionUIState(
    val current: Transaction,
    val totalCompleted: Int,
    val isActive: Boolean,
    val isDialogOpen: Boolean,
    val pendingRefunds: List<RefundRequest>,
    val pendingCustomers: Int = 0,  // customers waiting for an open register
    val completedToday: Int = 0,    // transactions completed in the current game day
)

data class InventoryUIState(
    val items: List<InventoryItemUI>,
    val selectedCategory: ItemCategory? = null,
    val focusedItemId: Int? = null
)

data class InventoryItemUI(
    val id: Int,
    val name: String,
    val price: Money,
    val unitCost: Money,
    val shelfStock: Int,
    val backroomStock: Int,
    val category: ItemCategory,
    val casePack: Int,
    val casePackCost: Money,
    /**
     * True when the backroom holds so many units that even one more full case pack
     * would exceed the per-item backroom cap. The "Order" button is disabled when this is true.
     */
    val backroomFull: Boolean = false,
    /** Number of days until item expires (null = non-perishable). */
    val shelfLifeDays: Int? = null,
    /** The closest expiration day among all batches (null if no stock or non-perishable). */
    val closestExpirationDay: Int? = null,
    /** Total case packs across all scheduled trucks for this item (0 = no pending deliveries). */
    val pendingCasePacks: Int = 0,
    /** Earliest scheduled arrival day among all trucks carrying this item (null if none). */
    val earliestArrivalDay: Int? = null,
)


data class StaffUIState(
    val registry: HiredEntityRegistry,
    val selectedType: EntityType = EntityType.NONE,
)

data class HistoryUIState(
    val salesHistory: List<Transaction>,
    val totalTaxCollected: Money
)

// Phase 1: Time UI State
data class TimeUIState(
    val currentTime: GameTime,
    val storeState: StoreState,
    val speedMultiplier: Float = 1.0f,
    val playerPausedTime: Boolean = false,
    // Phase 2: Player role
    val playerRole: PlayerRole = PlayerRole.NONE,
    val playerCashierProgress: Float = 0f,
    val playerStockerProgress: Float = 0f,
    // Phase 1.5: Store size and operating costs
    val currentStoreSize: com.example.superstoresimulator.domain.store.StoreSize = com.example.superstoresimulator.domain.store.StoreSize.MOM_AND_POP,
    val dailyRent: Money = Money.ZERO,
    val dailyWages: Money = Money.ZERO,
)

// Phase 3: Daily metrics UI state
data class MetricsUIState(
    /** All completed day snapshots, newest first. */
    val completedDays: List<DailyMetrics> = emptyList(),
    /** Whether the end-of-day summary dialog should be shown. */
    val showEndOfDayReport: Boolean = false,
    /** The most recently completed day's snapshot (shown in the dialog). */
    val lastReport: DailyMetrics? = null,
)

// Progression: revenue tier unlock progress
data class ProgressionUIState(
    /** The player's current active tier (paid and unlocked). */
    val currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1,
    /** Cumulative revenue earned — never decreases. */
    val totalRevenue: Money = Money.ZERO,
    /** The next tier to unlock, or null if at the top tier. */
    val nextTier: ItemUnlockTier? = ItemUnlockTier.TIER_2,
    /** How much more revenue is needed to reach the next tier's revenue gate, or null at top tier. */
    val revenueToNextTier: Money? = Money(500_000L),
    /** Progress fraction (0.0–1.0) within the current tier band. */
    val tierProgressFraction: Float = 0f,
    /** Non-null for one UI frame after a tier unlock — cleared by DismissTierUnlock. */
    val justUnlockedTier: ItemUnlockTier? = null,
    /**
     * The next tier whose revenue gate has been met and is ready to be purchased,
     * or null if the revenue gate for the next tier hasn't been crossed yet.
     */
    val availableTier: ItemUnlockTier? = null,
)

// ── Truck Delivery UI State ───────────────────────────────────────────────────

data class TruckOrderLineUI(
    val itemId: Int,
    val itemName: String,
    val casePacks: Int,
    val quantity: Int,
    val canCancel: Boolean,
    val truckId: Int = 0,
)

data class TruckUIState(
    val truckId: Int,
    val arrivalDay: Int,
    val arrivalDayOfWeek: Int,    // 0=Mon…6=Sun  (arrivalDay % 7)
    val capacityUsed: Int,        // used case packs
    val capacityTotal: Int,       // max case packs
    val isFreshTruck: Boolean,
    val isEarlyTruck: Boolean,
    val orderLines: List<TruckOrderLineUI>,
)

data class DeliveryUIState(
    val regularTrucks: List<TruckUIState> = emptyList(),  // sorted by arrival day
    val freshTruck: TruckUIState? = null,                 // today's+1 fresh truck, if any
    val earlyTruckAvailable: Boolean = true,              // false when one already scheduled for tomorrow
    val earlyTruckCost: Money = Money(10_000L),           // always $100
    /** Maximum delivery days allowed per week (free limit + purchased extra slots). */
    val maxTrucksPerWeek: Int = 2,
    /** Base free delivery days at the current store size (2 + storeSize.ordinal). */
    val freeTrucksPerWeek: Int = 2,
    /** Number of extra delivery-day slots purchased beyond the free limit. */
    val extraTruckSlotsUnlocked: Int = 0,
    /** Cost to purchase one additional weekly delivery slot. */
    val extraTruckSlotCost: Money = Money(10_000L),       // always $100
)
