package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.StaffShift

object StaffWageCalculator {

    fun calculateTotalWages(registry: HiredEntityRegistry, schedules: List<StaffShift> = emptyList()): Money {
        return registry.hiredEntities.fold(Money.ZERO) { acc, entity ->
            val traitMultiplier = if (entity.trait == EntityTrait.EFFICIENT) 0.9 else 1.0
            val hours = schedules.firstOrNull { it.entityId == entity.id }?.durationHours ?: 8
            val dailyWage = entity.hourlyWage * hours * traitMultiplier
            acc + dailyWage
        }
    }
}
