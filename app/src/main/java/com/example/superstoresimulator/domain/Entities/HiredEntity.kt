package com.example.superstoresimulator.domain.Entities

import com.example.superstoresimulator.domain.Money

data class HiredEntity(
    val id: Int,
    val name: String,
    val entityDefinition: EntityDef,
    val trait: EntityTrait,
    val tier: Tier = Tier.BASE,
    val xp: Int = 0,
    val level: Int = 1,
) {
    val throughputWeight: Float
        get() = when (tier) {
            Tier.BASE -> 1.0f
            Tier.FAST -> 1.5f
            Tier.MANAGER -> 2.0f
        }

    val levelMultiplier: Float
        get() = 1.0f + (level - 1) * LEVEL_BONUS_PER_LEVEL

    val hourlyWage: Money
        get() = when (tier) {
            Tier.BASE -> entityDefinition.baseWage
            Tier.FAST -> entityDefinition.baseWage * 2.0
            Tier.MANAGER -> entityDefinition.baseWage * 3.0
        }

    val upgradeCost: Money
        get() = when (tier) {
            Tier.BASE -> Money(10_000)
            Tier.FAST -> Money(50_000)
            Tier.MANAGER -> error("Already at max tier")
        }

    val isStoreManager: Boolean get() = entityDefinition == EntityDef.MANAGER && tier == Tier.MANAGER
    val isDeptManager: Boolean get() = entityDefinition != EntityDef.MANAGER && tier == Tier.MANAGER
    val canPromote: Boolean get() = tier != Tier.MANAGER && level >= PROMOTE_UNLOCK_LEVEL

    fun upgrade(): HiredEntity {
        require(tier != Tier.MANAGER) { "Already at max tier" }
        return copy(tier = tier.next(), level = 1, xp = 0)
    }

    companion object {
        const val MAX_LEVEL = 5
        const val LEVEL_BONUS_PER_LEVEL = 0.10f
        const val PROMOTE_UNLOCK_LEVEL = 3
        val XP_THRESHOLDS = listOf(100, 300, 700, 1500)
    }
}