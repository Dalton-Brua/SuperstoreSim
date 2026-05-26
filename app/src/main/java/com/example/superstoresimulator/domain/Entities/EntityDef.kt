package com.example.superstoresimulator.domain.Entities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.superstoresimulator.domain.Money

/**
 * Small immutable descriptor for purchasable upgrades (employees, features, etc.).
 *
 * Use the companion object for common defaults (CASHIER, STOCKER). This centralizes
 * cost/label data so UI and game logic can read from one place.
 */
data class EntityDef(
    val key: String,
    val displayName: String,
    val cost: Money,
    val description: String,
    val icon: ImageVector,
    val nextUpgrade: EntityDef? = null,
    val hourlyWage: Money = Money.ZERO,
) {
    companion object {

        // ── Wage multiplier constants ─────────────────────────────────────────
        /** Hourly wage multiplier for fast-tier staff (2× base). */
        const val FAST_STAFF_WAGE_MULTIPLIER = 2.0
        /** Hourly wage multiplier for department managers (3× base). */
        const val DEPT_MANAGER_WAGE_MULTIPLIER = 3.0

        // Upgrades must be declared BEFORE the base entities that reference them.
        // Kotlin/JVM initialises companion-object vals top-to-bottom; if CASHIER
        // were declared first its `nextUpgrade = EntityDef.FAST_CASHIER` would
        // resolve to null (FAST_CASHIER not yet initialised), silently breaking
        // the upgrade path.

        val FAST_CASHIER = EntityDef(
            key = "fast_cashier",
            displayName = "Fast Cashier",
            cost = Money(10000),
            description = "Better training increases your cashier's speed.",
            icon = Icons.Default.Person,
            nextUpgrade = null,
            hourlyWage = Money(1250),  // $12.50/hr
        )

        val CASHIER = EntityDef(
            key = "cashier",
            displayName = "Cashier",
            cost = Money(1500),
            description = "Hires a cashier to auto-ring items.",
            icon = Icons.Default.Person,
            nextUpgrade = FAST_CASHIER,
            hourlyWage = Money(625),   // $6.25/hr
        )

        val FAST_STOCKER = EntityDef(
            key = "fast_stocker",
            displayName = "Fast Stocker",
            cost = Money(10000),
            description = "Better training increases your stocker's speed.",
            icon = Icons.Default.Build,
            hourlyWage = Money(1250),  // $12.50/hr
        )

        val STOCKER = EntityDef(
            key = "stocker",
            displayName = "Stocker",
            cost = Money(1500),
            description = "Hires a stocker to restock shelves.",
            icon = Icons.Default.Build,
            nextUpgrade = FAST_STOCKER,
            hourlyWage = Money(625),   // $6.25/hr
        )

        val FAST_FRESH_HANDLER = EntityDef(
            key = "fast_fresh_handler",
            displayName = "Fast Fresh Handler",
            cost = Money(10000),
            description = "Better training increases your fresh handler's speed.",
            icon = Icons.Default.Build,
            nextUpgrade = null,
            hourlyWage = Money(1250),  // $12.50/hr
        )

        val FRESH_HANDLER = EntityDef(
            key = "fresh_handler",
            displayName = "Fresh Handler",
            cost = Money(1500),
            description = "Hires a fresh handler to restock perishable items.",
            icon = Icons.Default.Build,
            nextUpgrade = FAST_FRESH_HANDLER,
            hourlyWage = Money(625),   // $6.25/hr
        )

        /*
        val CUSTOMER_SERVICE_REP = EntityDef(
            key = "customer_service_rep",
            displayName = "Customer Service Rep",
            cost = Money(5000),
            description = "Hires a customer service rep to handle refunds.",
            icon = Icons.Default.Person
        )
        */

        val allEntities: List<EntityDef> = listOf(
            CASHIER,
            FAST_CASHIER,
            STOCKER,
            FAST_STOCKER,
            FRESH_HANDLER,
            FAST_FRESH_HANDLER,
        )

        /**
         * Returns the throughput weight for a given [EntityDef].
         *
         * Base staff (CASHIER, STOCKER, FRESH_HANDLER) contribute 1.0.
         * Fast-tier staff (FAST_CASHIER, FAST_STOCKER, FAST_FRESH_HANDLER) contribute 2.0.
         * Department managers (FRONT_END_MANAGER, STOCKING_MANAGER) contribute 3.0.
         * Unknown definitions default to 1.0.
         */
        fun baseThroughputWeight(def: EntityDef): Float = when (def.key) {
            "fast_cashier", "fast_stocker", "fast_fresh_handler" -> 2.0f
            "front_end_manager", "stocking_manager" -> 3.0f
            else -> 1.0f
        }

    }
}
