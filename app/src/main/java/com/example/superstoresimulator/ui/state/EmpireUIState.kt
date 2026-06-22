package com.example.superstoresimulator.ui.state

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.empire.EmpireSpeed
import com.example.superstoresimulator.domain.empire.ManagerPersonality
import com.example.superstoresimulator.domain.empire.SimStoreMetrics
import com.example.superstoresimulator.domain.empire.StoreDirection
import com.example.superstoresimulator.domain.empire.StoreUpgrade
import com.example.superstoresimulator.domain.store.StoreSize

/**
 * Empire-mode (loop 2) UI contract. Frozen in Wave 0 so Track E renders against it while
 * Tracks A–D land real data. Read-only — derived from GameState by [buildEmpireUiState].
 */
data class EmpireUIState(
    val active: Boolean = false,
    val money: Money = Money.ZERO,
    /** Whether the "go corporate" purchase is available (research + not yet empire). */
    val canEnterEmpire: Boolean = false,
    val regions: List<RegionUI> = emptyList(),
    val manager: ManagerUI? = null,
    val canHireManager: Boolean = false,
    val clock: EmpireClockUI = EmpireClockUI(),
    /** Non-null when the player is hands-on operating a store (loop 1 active). */
    val operatingStoreId: Int? = null,
)

data class RegionUI(
    val regionId: Int,
    val name: String,
    val unlocked: Boolean,
    val unlockCost: Money,
    val load: Float,              // totalWeight / capacity (saturation bar fill)
    val overCapacity: Boolean,
    val baseSpendingPower: Float,
    val baseTraffic: Float,
    val stores: List<StoreUI> = emptyList(),
)

data class StoreUI(
    val storeId: Int,
    val name: String,
    val size: StoreSize,
    val direction: StoreDirection,
    val managedByManager: Boolean,
    val upgrades: Set<StoreUpgrade>,
    val today: SimStoreMetrics,
    val history: List<SimStoreMetrics>,
    val operatingPerformance: Float,
    val daysSinceOperated: Int?,    // null = never operated
    val unprofitable: Boolean,
    /** Cost to expand to the next size tier, or null if already max. */
    val expandCost: Money?,
)

data class ManagerUI(
    val name: String,
    val personality: ManagerPersonality,
    val salaryPerDay: Money,
    val storesOnPreferred: Int,
    val storesDeviating: Int,
)

data class EmpireClockUI(
    val speed: EmpireSpeed = EmpireSpeed.PAUSED,
    val pendingDecisionReason: String? = null,
    val currentDayIndex: Int = 0,
)
