package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.StaffShift

object StaffWageCalculator {

    /**
     * @param dayOfWeek 0–6 to charge only employees scheduled that day;
     *                  -1 to compute the average daily wage bill (weekly total / 7).
     */
    fun calculateTotalWages(
        registry: HiredEntityRegistry,
        schedules: List<StaffShift> = emptyList(),
        dayOfWeek: Int = -1,
    ): Money {
        return registry.hiredEntities.fold(Money.ZERO) { acc, entity ->
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            val hours = shift?.durationHours ?: 8

            if (dayOfWeek >= 0) {
                val scheduledToday = shift == null || shift.workDays.isEmpty() || dayOfWeek in shift.workDays
                if (!scheduledToday) return@fold acc
            }

            val traitMultiplier = if (entity.trait == EntityTrait.EFFICIENT) 0.9 else 1.0
            val dailyWage = entity.hourlyWage * hours * traitMultiplier

            if (dayOfWeek < 0) {
                val daysPerWeek = shift?.daysPerWeek ?: 7
                acc + dailyWage * (daysPerWeek.toDouble() / 7.0)
            } else {
                acc + dailyWage
            }
        }
    }
}
