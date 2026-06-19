package com.example.superstoresimulator.domain.registers

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.findRegisterById
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.research.ResearchGates
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.updateRegister
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegisterManager @Inject constructor() {

    fun purchaseRegister(state: GameState): GameState {
        if (!ResearchGates.isResearched(state.researchState.researchedUpgrades, ResearchGates.REGISTER_EXPANSION)) return state
        if (state.ownedRegisterCount >= state.currentStoreSize.maxRegisters) return state
        val cost = StoreSize.nextRegisterCost(state.ownedRegisterCount)
        if (state.money < cost) return state
        val newRegisterId = (state.registers.maxOfOrNull { it.registerId } ?: 0) + 1
        return state.copy(
            money = state.money - cost,
            registers = state.registers + RegisterState(registerId = newRegisterId),
        )
    }

    fun assignCashierToRegister(state: GameState, cashierId: Int?, registerId: Int): GameState {
        val register = state.registers.findRegisterById(registerId) ?: return state
        if (cashierId != null) {
            try { state.hiredEntityRegistry.getById(cashierId) } catch (e: NoSuchElementException) { return state }
            if (state.registers.any { it.registerId != registerId && it.assignedCashierId == cashierId }) return state
        }
        val previousCashierId = register.assignedCashierId
        val updatedUnassigned = if (cashierId == null && previousCashierId != null) {
            state.manuallyUnassignedCashiers + previousCashierId
        } else if (cashierId != null) {
            state.manuallyUnassignedCashiers - cashierId
        } else {
            state.manuallyUnassignedCashiers
        }
        return state.copy(
            registers = state.registers.updateRegister(register.copy(assignedCashierId = cashierId)),
            manuallyUnassignedCashiers = updatedUnassigned,
        )
    }

    fun assignPlayerToRegister(state: GameState, registerId: Int?): GameState {
        if (registerId != null) {
            val register = state.registers.findRegisterById(registerId) ?: return state
            if (register.assignedCashierId != null) return state
        }
        if (registerId == null && state.playerRole == PlayerRole.CASHIER) {
            return state.copy(
                playerAssignedRegisterId = null,
                playerRole = PlayerRole.MANAGE,
                playerCashierProgress = 0f,
            )
        }
        return state.copy(playerAssignedRegisterId = registerId)
    }

    fun autoAssignUnassignedCashiers(state: GameState): GameState {
        var registers = state.registers
        val unassigned = state.hiredEntityRegistry.hiredEntities.filter { entity ->
            entity.entityDefinition == EntityDef.CASHIER &&
                entity.id !in state.manuallyUnassignedCashiers &&
                registers.none { reg -> reg.assignedCashierId == entity.id }
        }
        for (cashier in unassigned) {
            val free = registers.firstOrNull { it.assignedCashierId == null && state.playerAssignedRegisterId != it.registerId } ?: break
            registers = registers.updateRegister(free.copy(assignedCashierId = cashier.id))
        }
        return if (registers != state.registers) state.copy(registers = registers) else state
    }

    fun unassignEntity(state: GameState, entityId: Int): GameState {
        val updated = state.registers.map { reg ->
            if (reg.assignedCashierId == entityId) reg.copy(assignedCashierId = null) else reg
        }
        return if (updated != state.registers) state.copy(registers = updated) else state
    }

    fun performShiftCheckAndReassignment(state: GameState, currentHour: Int, dayOfWeek: Int = -1): GameState {
        val registersAfterShiftCheck = state.registers.map { reg ->
            val assignedId = reg.assignedCashierId ?: return@map reg
            if (reg.transactionActive) return@map reg
            val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
            val offShift = if (dayOfWeek >= 0)
                shift != null && !shift.isOnShift(currentHour, dayOfWeek)
            else
                shift != null && !shift.isOnShift(currentHour)
            if (offShift) reg.copy(assignedCashierId = null) else reg
        }

        var registers = if (registersAfterShiftCheck != state.registers) registersAfterShiftCheck
        else state.registers

        val unassignedOnShiftCashiers = state.hiredEntityRegistry.hiredEntities.filter { cashier ->
            cashier.entityDefinition == EntityDef.CASHIER &&
                cashier.id !in state.manuallyUnassignedCashiers &&
                registers.none { reg -> reg.assignedCashierId == cashier.id } &&
                run {
                    val shift = state.staffSchedules.firstOrNull { it.entityId == cashier.id }
                    if (dayOfWeek >= 0) shift?.isOnShift(currentHour, dayOfWeek) != false
                    else shift?.isOnShift(currentHour) != false
                }
        }
        for (cashier in unassignedOnShiftCashiers) {
            val freeRegister = registers.firstOrNull { it.assignedCashierId == null && state.playerAssignedRegisterId != it.registerId }
            if (freeRegister != null) {
                registers = registers.updateRegister(freeRegister.copy(assignedCashierId = cashier.id))
            }
        }

        return if (registers != state.registers) state.copy(registers = registers) else state
    }

    fun isRegisterMannedAndOnShift(
        registerId: Int,
        state: GameState,
        currentHour: Int,
        dayOfWeek: Int = -1,
    ): Boolean {
        val register = state.registers.findRegisterById(registerId) ?: return false

        if (state.playerAssignedRegisterId == registerId && state.playerRole == PlayerRole.CASHIER) {
            return true
        }

        val assignedId = register.assignedCashierId
        if (assignedId != null) {
            val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
            return if (dayOfWeek >= 0) shift?.isOnShift(currentHour, dayOfWeek) == true
            else shift?.isOnShift(currentHour) == true
        }

        if (state.registers.size == 1) {
            return StaffManager.activeWeightedCount(
                EntityDef.CASHIER, currentHour, state.staffSchedules, state.hiredEntityRegistry, dayOfWeek
            ) > 0f || state.playerRole == PlayerRole.CASHIER
        }

        return false
    }

    fun getCashierWeightForRegister(
        registerId: Int,
        state: GameState,
        currentHour: Int,
        dayOfWeek: Int = -1,
    ): Float {
        val register = state.registers.findRegisterById(registerId) ?: return 0f
        val assignedId = register.assignedCashierId

        if (assignedId != null) {
            val cashier = try {
                state.hiredEntityRegistry.getById(assignedId)
            } catch (e: NoSuchElementException) {
                return 0f
            }
            val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
            val onShift = if (dayOfWeek >= 0)
                shift?.isOnShift(currentHour, dayOfWeek) == true
            else
                shift?.isOnShift(currentHour) == true
            return if (onShift || register.transactionActive)
                cashier.throughputWeight * cashier.levelMultiplier * cashier.trait.throughputMultiplier
            else 0f
        }

        if (state.registers.size == 1) {
            return StaffManager.activeWeightedCount(
                EntityDef.CASHIER, currentHour, state.staffSchedules, state.hiredEntityRegistry, dayOfWeek
            )
        }

        return 0f
    }
}
