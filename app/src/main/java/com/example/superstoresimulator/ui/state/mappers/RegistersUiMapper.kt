package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.ui.state.RegisterUIState
import com.example.superstoresimulator.ui.state.RegistersUIState

fun buildRegistersUiState(domain: GameState): RegistersUIState {
    val currentHour = domain.currentTime.hour
    val playerAssigReg = domain.playerAssignedRegisterId

    val registerUiList = domain.registers.map { reg ->
        val cashierId = reg.assignedCashierId
        val cashier = cashierId?.let { id ->
            domain.hiredEntityRegistry.hiredEntities.firstOrNull { it.id == id }
        }
        val cashierShift = cashierId?.let { id ->
            domain.staffSchedules.firstOrNull { it.entityId == id }
        }
        val cashierOnShift = cashierShift?.isOnShift(currentHour) ?: (cashierId != null)
        val isPlayerAssigned = reg.registerId == playerAssigReg

        RegisterUIState(
            registerId = reg.registerId,
            assignedCashierName = cashier?.name,
            assignedCashierId = cashierId,
            isPlayerAssigned = isPlayerAssigned,
            transactionActive = reg.transactionActive,
            isManned = (cashierId != null && cashierOnShift) || isPlayerAssigned,
            cashierOnShift = cashierOnShift,
            dailyTransactions = reg.dailyTransactions,
            dailyRevenue = reg.dailyRevenue,
        )
    }

    val maxRegs = domain.currentStoreSize.maxRegisters
    val canPurchase = domain.ownedRegisterCount < maxRegs &&
        domain.money >= StoreSize.nextRegisterCost(domain.ownedRegisterCount)

    return RegistersUIState(
        registers = registerUiList,
        ownedCount = domain.ownedRegisterCount,
        maxRegisters = maxRegs,
        nextRegisterCost = StoreSize.nextRegisterCost(domain.ownedRegisterCount),
        canPurchase = canPurchase,
        playerAssignedRegisterId = playerAssigReg,
    )
}
