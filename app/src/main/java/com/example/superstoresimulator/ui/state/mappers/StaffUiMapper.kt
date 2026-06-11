package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.ui.state.StaffScheduleEntryUI

fun buildStaffScheduleEntries(domain: GameState): List<StaffScheduleEntryUI> {
    val currentHour = domain.currentTime.hour
    val shiftMap = domain.staffSchedules.associateBy { it.entityId }

    val cashierRegisterMap = domain.registers
        .filter { it.assignedCashierId != null }
        .associate { it.assignedCashierId!! to it.registerId }

    return domain.hiredEntityRegistry.hiredEntities.map { entity ->
        val shift = shiftMap[entity.id]
        val isOnShift = shift?.isOnShift(currentHour) ?: true
        val tierLabel = when (entity.tier) {
            Tier.BASE -> ""
            Tier.FAST -> "Fast"
            Tier.MANAGER -> "Dept. Mgr"
        }

        StaffScheduleEntryUI(
            entityId = entity.id,
            entityName = entity.name,
            entityTypeName = entity.entityDefinition.displayName,
            entityDefKey = entity.entityDefinition.key,
            startHour = shift?.startHour,
            endHour = shift?.endHour,
            isOnShift = isOnShift,
            tierLabel = tierLabel,
            level = entity.level,
            assignedRegisterId = cashierRegisterMap[entity.id],
        )
    }
}

fun countActiveStaff(domain: GameState): Int {
    val currentHour = domain.currentTime.hour
    val shiftMap = domain.staffSchedules.associateBy { it.entityId }
    return domain.hiredEntityRegistry.hiredEntities.count { entity ->
        shiftMap[entity.id]?.isOnShift(currentHour) ?: true
    }
}
