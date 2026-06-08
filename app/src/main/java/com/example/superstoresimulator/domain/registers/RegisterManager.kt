package com.example.superstoresimulator.domain.registers

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.findRegisterById
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.updateRegister
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegisterManager @Inject constructor() {

    fun purchaseRegister(state: GameState): GameState {
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

    fun performShiftCheckAndReassignment(state: GameState, currentHour: Int): GameState {
        val registersAfterShiftCheck = state.registers.map { reg ->
            val assignedId = reg.assignedCashierId ?: return@map reg
            if (reg.transactionActive) return@map reg
            val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
            if (shift != null && !shift.isOnShift(currentHour)) reg.copy(assignedCashierId = null)
            else reg
        }

        var registers = if (registersAfterShiftCheck != state.registers) registersAfterShiftCheck
        else state.registers

        val unassignedOnShiftCashiers = state.hiredEntityRegistry.hiredEntities.filter { cashier ->
            cashier.entityDefinition == EntityDef.CASHIER &&
                cashier.id !in state.manuallyUnassignedCashiers &&
                registers.none { reg -> reg.assignedCashierId == cashier.id } &&
                state.staffSchedules.firstOrNull { it.entityId == cashier.id }?.isOnShift(currentHour) != false
        }
        for (cashier in unassignedOnShiftCashiers) {
            val freeRegister = registers.firstOrNull { it.assignedCashierId == null }
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
    ): Boolean {
        val register = state.registers.findRegisterById(registerId) ?: return false

        if (state.playerAssignedRegisterId == registerId && state.playerRole == PlayerRole.CASHIER) {
            return true
        }

        val assignedId = register.assignedCashierId
        if (assignedId != null) {
            val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
            return shift?.isOnShift(currentHour) == true
        }

        if (state.registers.size == 1) {
            return StaffManager.activeWeightedCount(
                EntityDef.CASHIER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            ) > 0f || state.playerRole == PlayerRole.CASHIER
        }

        return false
    }

    fun getCashierWeightForRegister(
        registerId: Int,
        state: GameState,
        currentHour: Int,
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
            val onShift = shift?.isOnShift(currentHour) == true
            return if (onShift || register.transactionActive)
                cashier.throughputWeight * cashier.levelMultiplier * cashier.trait.throughputMultiplier
            else 0f
        }

        if (state.registers.size == 1) {
            return StaffManager.activeWeightedCount(
                EntityDef.CASHIER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )
        }

        return 0f
    }
}
