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
) {
    companion object {

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
            nextUpgrade = null
        )

        val CASHIER = EntityDef(
            key = "cashier",
            displayName = "Cashier",
            cost = Money(1500),
            description = "Hires a cashier to auto-ring items.",
            icon = Icons.Default.Person,
            nextUpgrade = FAST_CASHIER
        )

        val FAST_STOCKER = EntityDef(
            key = "fast_stocker",
            displayName = "Fast Stocker",
            cost = Money(10000),
            description = "Better training increases your stocker's speed.",
            icon = Icons.Default.Build
        )

        val STOCKER = EntityDef(
            key = "stocker",
            displayName = "Stocker",
            cost = Money(1500),
            description = "Hires a stocker to restock shelves.",
            icon = Icons.Default.Build,
            nextUpgrade = FAST_STOCKER
        )

        val CUSTOMER_SERVICE_REP = EntityDef(
            key = "customer_service_rep",
            displayName = "Customer Service Rep",
            cost = Money(5000),
            description = "Hires a customer service rep to handle refunds.",
            icon = Icons.Default.Person
        )

        val allEntities: List<EntityDef> = listOf(
            CASHIER,
            FAST_CASHIER,
            STOCKER,
            FAST_STOCKER,
            CUSTOMER_SERVICE_REP,
        )

    }
}

