package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Money

/**
 * Calculates daily wages based on hired staff.
 *
 * Wage rates (daily):
 * - CASHIER: $50
 * - FAST_CASHIER: $100
 * - STOCKER: $50
 * - FAST_STOCKER: $100
 * - FRESH_HANDLER: $50
 * - FAST_FRESH_HANDLER: $100
 * - CUSTOMER_SERVICE_REP: $75
 */
object StaffWageCalculator {

    private val WAGE_TABLE = mapOf(
        EntityDef.CASHIER.key to Money(5_000),              // $50/day
        EntityDef.FAST_CASHIER.key to Money(10_000),        // $100/day
        EntityDef.STOCKER.key to Money(5_000),              // $50/day
        EntityDef.FAST_STOCKER.key to Money(10_000),        // $100/day
        EntityDef.FRESH_HANDLER.key to Money(5_000),        // $50/day
        EntityDef.FAST_FRESH_HANDLER.key to Money(10_000),  // $100/day
        //EntityDef.CUSTOMER_SERVICE_REP.key to Money(7_500),  // $75/day
    )

    /**
     * Calculate total wages for all hired staff in the registry.
     */
    fun calculateTotalWages(registry: HiredEntityRegistry): Money {
        return registry.hiredEntities.fold(Money.ZERO) { acc, entity ->
            val wage = WAGE_TABLE[entity.entityDefinition.key] ?: Money.ZERO
            acc + wage
        }
    }

    /**
     * Get the daily wage for a specific entity definition.
     */
    fun getWageForEntity(entityDef: EntityDef): Money {
        return WAGE_TABLE[entityDef.key] ?: Money.ZERO
    }
}

