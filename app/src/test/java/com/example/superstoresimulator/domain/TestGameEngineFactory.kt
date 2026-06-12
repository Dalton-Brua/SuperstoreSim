package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.progression.ProgressionManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.helpers.FakeTransactionDao
import com.example.superstoresimulator.domain.tick.DayRolloverProcessor
import com.example.superstoresimulator.domain.tick.PlayerTickProcessor
import com.example.superstoresimulator.domain.tick.StaffTickProcessor
import com.example.superstoresimulator.domain.tick.TickOrchestrator
import com.example.superstoresimulator.domain.tick.TrafficProcessor
import com.example.superstoresimulator.domain.tick.UtilizationTracker
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
import com.example.superstoresimulator.domain.vendor.VendorManager

fun createTestGameEngine(cache: ItemMetadataCache): GameEngine {
    val pricingManager = PricingManager(cache)
    val transactionEngine = TransactionEngine(cache = cache, pricingManager = pricingManager)
    val timeManager = TimeManager()
    val trafficManager = TrafficManager()
    val staffManager = StaffManager()
    val dayManager = DayManager()
    val storeController = StoreController()
    val playerActionHandler = PlayerActionHandler()
    val progressionManager = ProgressionManager(cache)
    val truckManager = TruckManager(cache)
    val inventoryManager = InventoryManager(cache, truckManager)
    val spoilageManager = SpoilageManager(cache)
    val registerManager = RegisterManager()
    val vendorManager = VendorManager(cache)
    val dayRolloverProcessor = DayRolloverProcessor(staffManager, dayManager, truckManager, inventoryManager, FakeTransactionDao(), vendorManager)
    val trafficProcessor = TrafficProcessor(trafficManager, transactionEngine, registerManager)
    val staffTickProcessor = StaffTickProcessor(
        staffManager, inventoryManager, transactionEngine, registerManager, pricingManager, cache,
    )
    val playerTickProcessor = PlayerTickProcessor(
        playerActionHandler, transactionEngine, inventoryManager, cache,
    )
    val utilizationTracker = UtilizationTracker(staffManager, inventoryManager, cache)
    val tickOrchestrator = TickOrchestrator(
        timeManager, spoilageManager, storeController, pricingManager,
        registerManager, transactionEngine, trafficManager,
        dayRolloverProcessor, trafficProcessor, staffTickProcessor,
        playerTickProcessor, utilizationTracker,
        staffManager, dayManager,
    )
    val engine = GameEngine(
        itemMetadataCache = cache,
        tickOrchestrator = tickOrchestrator,
        transactionEngine = transactionEngine,
        inventoryManager = inventoryManager,
        registerManager = registerManager,
        staffManager = staffManager,
        storeController = storeController,
        playerActionHandler = playerActionHandler,
        progressionManager = progressionManager,
        pricingManager = pricingManager,
        dayManager = dayManager,
        timeManager = timeManager,
        trafficManager = trafficManager,
        truckManager = truckManager,
        vendorManager = vendorManager,
    )
    engine.seedNewGame()
    return engine
}
