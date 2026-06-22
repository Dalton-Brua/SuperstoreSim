package com.example.superstoresimulator.ui.state

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.FreshAutoOrderConfig
import com.example.superstoresimulator.domain.IncompleteOrderRequest
import com.example.superstoresimulator.domain.NormalAutoOrderConfig
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.StoreManagerConfig
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.research.AnalystAssignment
import com.example.superstoresimulator.domain.research.ResearchableUpgrade
import com.example.superstoresimulator.domain.tutorial.TutorialStep
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.pricing.Markdown
import com.example.superstoresimulator.domain.pricing.PricingState
import com.example.superstoresimulator.domain.pricing.ResolvedPrice
import com.example.superstoresimulator.domain.staff.EmployeeActivity
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
    val research: ResearchUIState = ResearchUIState(),
    val tutorial: TutorialUIState = TutorialUIState(),
    val delivery: DeliveryUIState = DeliveryUIState(),
    /** Register system — per-register status, assignment, and purchase info. */
    val registers: RegistersUIState = RegistersUIState(),
    val pricing: PricingUIState = PricingUIState(),
    val vendors: VendorUIState = VendorUIState(),
    val reputation: ReputationUIState = ReputationUIState(),
    val empire: EmpireUIState = EmpireUIState(),
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
    val totalStaff: Int,
    /** Number of employees whose shift covers the current game hour (or always-on if no shift set). */
    val activeStaff: Int = 0,
    val avgZoneScore: Float = 1.0f,
)

data class TransactionUIState(
    val current: Transaction,
    val previous: Transaction? = null,
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
    val focusedItemId: Int? = null,
    val hasFastStocker: Boolean = false,
    val hasStockingManager: Boolean = false,
    val hasFreshHandler: Boolean = false,
    val freshAutoOrderConfig: FreshAutoOrderConfig = FreshAutoOrderConfig(),
    val normalAutoOrderConfig: NormalAutoOrderConfig = NormalAutoOrderConfig(),
    val incompleteFreshOrders: List<IncompleteOrderRequest> = emptyList(),
    val incompleteNormalOrders: List<IncompleteOrderRequest> = emptyList(),
    val incompleteFreshItemNames: Map<Int, String> = emptyMap(),
    val incompleteFreshItemCosts: Map<Int, Money> = emptyMap(),
    val incompleteNormalItemNames: Map<Int, String> = emptyMap(),
    val incompleteNormalItemCosts: Map<Int, Money> = emptyMap(),
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
    val description: String = "",
    val tierLabel: String = "",
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
    /** Zone score for this item (0.0–1.0). Affects purchase probability. */
    val zoneScore: Float = 1.0f,
    val effectivePrice: Money = price,
    val priceModifierPercent: Int = 0,
    val hasActiveMarkdown: Boolean = false,
    val soldByWeight: Boolean = false,
    val vendorName: String? = null,
)


data class StaffUIState(
    val registry: HiredEntityRegistry,
    /** All hired employees with their shift times and register assignments. */
    val scheduleEntries: List<StaffScheduleEntryUI> = emptyList(),
    /** Current game hour (0-23) — used to show on-shift status in schedule view. */
    val currentHour: Int = 8,
    val currentDayOfWeek: Int = 0,
    val employeeActivities: Map<Int, EmployeeActivity> = emptyMap(),
    val cashierUtilization: Float = 0f,
    val stockerUtilization: Float = 0f,
    val freshUtilization: Float = 0f,
    val hasManagerOnStaff: Boolean = false,
    val hasSeniorManager: Boolean = false,
    val hasStoreManager: Boolean = false,
    val autoHireBudget: Money = Money.ZERO,
    val storeManagerConfig: StoreManagerConfig = StoreManagerConfig(),
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
    val playerRole: PlayerRole = PlayerRole.MANAGE,
    val playerCashierProgress: Float = 0f,
    val playerStockerProgress: Float = 0f,
    // Phase 1.5: Store size and operating costs
    val currentStoreSize: com.example.superstoresimulator.domain.store.StoreSize = com.example.superstoresimulator.domain.store.StoreSize.MOM_AND_POP,
    val dailyRent: Money = Money.ZERO,
    val dailyWages: Money = Money.ZERO,
    val buildingOwned: Boolean = false,
)

// Phase 3: Daily metrics UI state
data class MetricsUIState(
    /** All completed day snapshots (in-memory + archived summaries), newest first. */
    val completedDays: List<DailyMetrics> = emptyList(),
    /** Live snapshot of the current in-progress day (null before first tick). */
    val activeDay: DailyMetrics? = null,
    /** Whether the end-of-day summary dialog should be shown. */
    val showEndOfDayReport: Boolean = false,
    /** The most recently completed day's snapshot (shown in the dialog). */
    val lastReport: DailyMetrics? = null,
    val showEndOfWeekReport: Boolean = false,
    val weeklyReport: com.example.superstoresimulator.domain.metrics.WeeklyReport? = null,
    /** Item ID → display name, for resolving names in metric events. */
    val itemNames: Map<Int, String> = emptyMap(),
    /** Day numbers that are archived in Room (scalar-only, events need on-demand load). */
    val archivedDayNumbers: Set<Int> = emptySet(),
    /** Full DailyMetrics loaded on-demand from Room for an archived day. */
    val loadedArchivedDay: DailyMetrics? = null,
)

// Research system UI state
data class ResearchUIState(
    val totalRevenue: Money = Money.ZERO,
    val researchedUpgrades: Set<String> = emptySet(),
    val researchProgress: Map<String, Float> = emptyMap(),
    val totalPointsEarned: Float = 0f,
    val analystAssignments: Map<Int, AnalystAssignment> = emptyMap(),
    val visibleUpgrades: List<ResearchableUpgrade> = emptyList(),
    /** Hired market analysts and their current assignment (for the assignment UI). */
    val analysts: List<AnalystUiInfo> = emptyList(),
)

/** A hired market analyst as shown in the research assignment UI. */
data class AnalystUiInfo(
    val id: Int,
    val name: String,
    val assignment: AnalystAssignment? = null,
)

data class TutorialUIState(
    val tutorialComplete: Boolean = false,
    val currentStep: TutorialStep = TutorialStep.WELCOME,
    val displayTitle: String = "",
    val instruction: String = "",
    val hintText: String = "",
    /** Per-feature gating flags keyed by TutorialManager.FEATURE_* constants. */
    val featureVisibility: Map<String, Boolean> = emptyMap(),
    /** Bottom-nav screen the current tutorial step wants the player to visit (null when complete). */
    val hintScreen: Screen? = null,
)

/** Convenience lookup; unknown keys default to visible so partial maps never hide UI. */
fun TutorialUIState.isFeatureVisible(feature: String): Boolean =
    featureVisibility[feature] ?: true

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
    val isVendorTruck: Boolean = false,
    val vendorName: String? = null,
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
    val truckConfig: TruckConfig = TruckConfig(),
    val vendorTrucks: List<TruckUIState> = emptyList(),
    // Fleet upgrade
    val currentFleetTierName: String = "Standard Fleet",
    val nextFleetUpgradeCost: Money? = null,
    val nextFleetTierName: String? = null,
    val fleetUpgradeResearched: Boolean = false,
)

// ── Register System UI State ─────────────────────────────────────────────────

/**
 * UI-layer snapshot of a single register's current state.
 *
 * [isManned] is true when a cashier is on-shift and assigned OR the player is assigned.
 * [cashierOnShift] reflects only the hired cashier's shift status (false if no cashier assigned).
 */
data class RegisterUIState(
    val registerId: Int,
    /** Name of the hired cashier assigned to this register, or null if none. */
    val assignedCashierName: String? = null,
    /** Entity ID of the assigned cashier, or null. */
    val assignedCashierId: Int? = null,
    /** True when the player has claimed this register. */
    val isPlayerAssigned: Boolean = false,
    /** True when a transaction is currently being processed on this register. */
    val transactionActive: Boolean = false,
    /** True when this register is staffed and can accept new customers. */
    val isManned: Boolean = false,
    /** True when the assigned cashier's shift covers the current game hour. */
    val cashierOnShift: Boolean = false,
    val dailyTransactions: Int = 0,
    val dailyRevenue: Money = Money.ZERO,
)

/** Aggregate register information used by [RegistersCard]. */
data class RegistersUIState(
    val registers: List<RegisterUIState> = listOf(RegisterUIState(registerId = 0)),
    val ownedCount: Int = 1,
    val maxRegisters: Int = 1,
    /** Cost to buy the next register. */
    val nextRegisterCost: Money = Money(20_000L),
    /** True when the player can afford and is below the store-size cap. */
    val canPurchase: Boolean = false,
    val playerAssignedRegisterId: Int? = null,
)

// ── Staff Schedule UI State ───────────────────────────────────────────────────

/**
 * Schedule and assignment info for a single hired employee.
 *
 * [startHour] and [endHour] are null when no explicit shift has been set (employee is always on).
 * [assignedRegisterId] is non-null only for cashiers assigned to a specific register.
 */
data class StaffScheduleEntryUI(
    val entityId: Int,
    val entityName: String,
    val entityTypeName: String,
    val entityDefKey: String = "",
    /** Inclusive start of shift (6–13), or null if no schedule defined (always on). */
    val startHour: Int? = null,
    /** Exclusive end of shift (startHour + 8), or null if no schedule defined. */
    val endHour: Int? = null,
    /** Whether the employee is currently on shift based on the current game hour and day. */
    val isOnShift: Boolean = true,
    val tierLabel: String = "",
    val level: Int = 1,
    /** For cashiers: the register they are assigned to, or null if unassigned. */
    val assignedRegisterId: Int? = null,
    /** Days of the week this employee works (0=Mon..6=Sun). Empty = every day. */
    val workDays: Set<Int> = emptySet(),
    val daysPerWeek: Int = 7,
)

// ── Pricing System UI State ─────────────────────────────────────────────────

data class PricingUIState(
    val pricingState: PricingState = PricingState(),
    val priceIndex: Float = 1.0f,
    val trafficMultiplier: Float = 1.0f,
    val basketMultiplier: Float = 1.0f,
    val reputationLabel: String = "Standard",
    val itemsMarkedDown: Int = 0,
    /** `default_markup` researched — show the store-wide Default Markup slider. */
    val defaultMarkupUnlocked: Boolean = false,
    /** `category_pricing` researched — show the per-category markup sliders. */
    val categoryPricingUnlocked: Boolean = false,
    /** `item_pricing` researched — show the per-item price override slider. */
    val itemPricingUnlocked: Boolean = false,
)

// ── Vendor System UI State ──────────────────────────────────────────────────

data class VendorItemInfo(
    val name: String,
    val price: Money,
    val vendorTier: Int,
)

data class VendorCardUI(
    val vendorId: String,
    val vendorName: String,
    val reputation: Int,
    val maxReputation: Int,
    val tierName: String,
    val commissionPercent: Int,
    val restockIntervalDays: Int,
    val investCost: Money,
    val canInvest: Boolean,
    val atMaxRep: Boolean,
    val items: List<VendorItemInfo> = emptyList(),
)

data class VendorUIState(
    val vendors: List<VendorCardUI> = emptyList(),
    val currentVendorTier: Int = 0,
    val nextTierCost: Money? = null,
    val canUnlockNextTier: Boolean = false,
    val maxTierReached: Boolean = false,
)

// ── Reputation System UI State ──────────────────────────────────────────────

data class ReputationUIState(
    val isActive: Boolean = false,
    val reputationScore: Float = 100f,
    val trafficMultiplier: Float = 1.0f,
    val priceToleranceMultiplier: Float = 1.0f,
    val supplierDiscountBonus: Float = 0f,
    val currentRevenueTarget: Money = Money.ZERO,
    val lastRevenueScore: Float = 0f,
    val lastStockScore: Float = 0f,
    val lastAppearanceScore: Float = 0f,
    val lastDailyComposite: Float = 0f,
    val consecutiveTargetHits: Int = 0,
    val consecutiveTargetMisses: Int = 0,
    val goobSaleActiveToday: Boolean = false,
)

