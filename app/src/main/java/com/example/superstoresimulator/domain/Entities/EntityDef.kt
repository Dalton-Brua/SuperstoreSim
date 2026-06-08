package com.example.superstoresimulator.domain.Entities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.superstoresimulator.domain.Money
import kotlinx.serialization.Serializable

@Serializable
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
    val xpThresholds: List<Int> = DEFAULT_XP_THRESHOLDS,
    val tierPerks: Map<Tier, String> = emptyMap(),
) {
    companion object {
        val DEFAULT_XP_THRESHOLDS = listOf(100, 300, 700, 1500)
        val MANAGER_XP_THRESHOLDS = listOf(250, 750, 1750, 4000)

        val CASHIER = EntityDef(
            key = "cashier",
            displayName = "Cashier",
            cost = Money(1500),
            description = "Hires a cashier to auto-ring items.",
            roleDescription = "Rings up items",
            icon = Icons.Default.Person,
            tierPerks = mapOf(
                Tier.BASE to "Rings up customers at registers",
                Tier.FAST to "Faster checkout speed",
                Tier.MANAGER to "Front-End Manager — cashier productivity bonus",
            ),
        )

        val STOCKER = EntityDef(
            key = "stocker",
            displayName = "Stocker",
            cost = Money(1500),
            description = "Hires a stocker to restock shelves.",
            roleDescription = "Stocks shelves",
            icon = Icons.Default.Build,
            tierPerks = mapOf(
                Tier.BASE to "Stocks items from backroom to shelves + zones shelves",
                Tier.FAST to "Faster stocking + unlocks daily auto-reorder for out-of-stock items",
                Tier.MANAGER to "Stocking Manager — proactive auto-ordering + stocker productivity bonus",
            ),
        )

        val FRESH_HANDLER = EntityDef(
            key = "fresh_handler",
            displayName = "Fresh Handler",
            cost = Money(1500),
            description = "Hires a fresh handler to restock perishable items.",
            roleDescription = "Stocks fresh items",
            icon = Icons.Default.Build,
            tierPerks = mapOf(
                Tier.BASE to "Stocks fresh items + marks down expiring products",
                Tier.FAST to "Faster fresh stocking + markdown speed",
                Tier.MANAGER to "Fresh Manager — fresh handler productivity bonus",
            ),
        )

        val MANAGER = EntityDef(
            key = "manager",
            displayName = "Manager",
            cost = Money(5_000),
            description = "Boosts all employee throughput by 15%. Promote to Senior (25%) then Store Manager (30%, auto-manages store).",
            roleDescription = "Throughput bonus + auto-hire",
            icon = Icons.Default.Star,
            baseWage = Money(1_200),
            xpThresholds = MANAGER_XP_THRESHOLDS,
            tierPerks = mapOf(
                Tier.BASE to "15% throughput bonus to all employees on shift + auto-hire",
                Tier.FAST to "Senior Manager — 25% throughput bonus + staff efficiency tracking",
                Tier.MANAGER to "Store Manager — 30% bonus + full store automation",
            ),
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