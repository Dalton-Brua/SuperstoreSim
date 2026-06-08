package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.findRegisterById
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.traffic.TrafficManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficProcessor @Inject constructor(
    private val trafficManager: TrafficManager,
    private val transactionEngine: TransactionEngine,
    private val registerManager: RegisterManager,
) {
    fun process(state: GameState, delta: Double, currentHour: Int): GameState {
        var s = state
        if (s.storeState == StoreState.OPEN) {
            val newCustomers = trafficManager.update(s, delta)
            if (newCustomers.isNotEmpty()) {
                s = s.copy(pendingCustomers = s.pendingCustomers + newCustomers.size)
            }
        }

        if (s.storeState == StoreState.OPEN && s.pendingCustomers > 0) {
            for (reg in s.registers) {
                if (s.pendingCustomers <= 0) break
                if (reg.transactionActive) continue
                if (!registerManager.isRegisterMannedAndOnShift(reg.registerId, s, currentHour)) continue
                val baseBasket = (2..5).random()
                val basketSize = (baseBasket * s.currentStoreSize.basketSizeMultiplier).toInt().coerceAtLeast(1)
                s = transactionEngine.generateRandomTransaction(s, basketSize, reg.registerId)
                if (s.registers.findRegisterById(reg.registerId)?.transactionActive == true) {
                    s = s.copy(pendingCustomers = (s.pendingCustomers - 1).coerceAtLeast(0))
                }
            }
        }
        return s
    }
}
