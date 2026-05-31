package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Money

/**
 * Calculates daily wages based on hired staff.
 *
 * Wage formula per employee:
 *   dailyWage = hourlyWage × 8 × traitMultiplier
 *
 * Trait multipliers:
 *   EFFICIENT → 0.9 (−10% wage cost)
 *   all others → 1.0
 *
 * Hourly base wages (defined on each [EntityDef]):
 *   CASHIER / STOCKER / FRESH_HANDLER      : $6.25/hr  (Money(625))
 *   FAST_CASHIER / FAST_STOCKER / FAST_FH  : $12.50/hr (Money(1250))
 */
object StaffWageCalculator {

    /**
     * Calculate total daily wages for all hired staff in the registry.
     *
     * Uses [EntityDef.hourlyWage] × 8 hours, then applies the EFFICIENT trait discount.
     */
    fun calculateTotalWages(registry: HiredEntityRegistry): Money {
        return registry.hiredEntities.fold(Money.ZERO) { acc, entity ->
            val traitMultiplier = if (entity.trait == EntityTrait.EFFICIENT) 0.9 else 1.0
            val dailyWage = entity.hourlyWage * 8 * traitMultiplier
            acc + dailyWage
        }
    }
}
