package com.example.superstoresimulator.domain.Entities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.superstoresimulator.domain.Money

enum class Tier {
    BASE, FAST, MANAGER;

    fun next(): Tier {
        val values = entries
        val nextIndex = ordinal + 1
        require(nextIndex < values.size) { "Already at max tier" }
        return values[nextIndex]
    }
}

data class EntityDef(
    val key: String,
    val displayName: String,
    val cost: Money,
    val description: String,
    val roleDescription: String,
    val icon: ImageVector,
    val baseWage: Money = Money(725),
) {
    companion object {

        val CASHIER = EntityDef(
            key = "cashier",
            displayName = "Cashier",
            cost = Money(1500),
            description = "Hires a cashier to auto-ring items.",
            roleDescription = "Rings up items",
            icon = Icons.Default.Person,
        )

        val STOCKER = EntityDef(
            key = "stocker",
            displayName = "Stocker",
            cost = Money(1500),
            description = "Hires a stocker to restock shelves.",
            roleDescription = "Stocks shelves",
            icon = Icons.Default.Build,
        )

        val FRESH_HANDLER = EntityDef(
            key = "fresh_handler",
            displayName = "Fresh Handler",
            cost = Money(1500),
            description = "Hires a fresh handler to restock perishable items.",
            roleDescription = "Stocks fresh items",
            icon = Icons.Default.Build,
        )

        val MANAGER = EntityDef(
            key = "manager",
            displayName = "Manager",
            cost = Money(5_000),
            description = "Boosts all employee throughput by 15%. Promote at level 3 for Senior Manager (25%).",
            roleDescription = "Throughput bonus + auto-hire",
            icon = Icons.Default.Star,
            baseWage = Money(1_200),
        )

        val allEntities: List<EntityDef> = listOf(
            CASHIER,
            STOCKER,
            FRESH_HANDLER,
            MANAGER,
        )

        val managerEntities: List<EntityDef> = listOf(MANAGER)
    }
}