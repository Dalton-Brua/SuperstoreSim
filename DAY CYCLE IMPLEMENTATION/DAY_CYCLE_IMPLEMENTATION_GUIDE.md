# Day Cycle System - Implementation Guide

This document provides concrete code examples for implementing the day cycle feature.

---

## 1. TIME SYSTEM DATA CLASSES

### GameTime.kt
```kotlin
package com.example.superstoresimulator.domain.time

import kotlin.math.floor

data class GameTime(
    val totalMinutesElapsed: Long = 0  // Total game minutes since start
) {
    // Properties
    val hour: Int
        get() = ((totalMinutesElapsed / 60) % 24).toInt()
    
    val minute: Int
        get() = (totalMinutesElapsed % 60).toInt()
    
    val dayNumber: Int
        get() = (totalMinutesElapsed / 1440).toInt()  // 1440 minutes per day
    
    val dayOfWeek: Int
        get() = dayNumber % 7  // 0 = Monday, 6 = Sunday
    
    val weekNumber: Int
        get() = dayNumber / 7
    
    // Methods
    fun getTotalMinutesOfDay(): Int = hour * 60 + minute
    
    fun isOpen(openTimeMinutes: Int, closeTimeMinutes: Int): Boolean {
        val currentMin = getTotalMinutesOfDay()
        return currentMin >= openTimeMinutes && currentMin < closeTimeMinutes
    }
    
    fun addMinutes(minutes: Long): GameTime {
        return copy(totalMinutesElapsed = totalMinutesElapsed + minutes)
    }
    
    fun addSeconds(seconds: Long): GameTime {
        val minutes = seconds / 60
        return addMinutes(minutes)
    }
    
    fun getFormattedTime(): String {
        val hourStr = hour.toString().padStart(2, '0')
        val minStr = minute.toString().padStart(2, '0')
        val dayStr = when (dayOfWeek) {
            0 -> "Mon"; 1 -> "Tue"; 2 -> "Wed"; 3 -> "Thu"
            4 -> "Fri"; 5 -> "Sat"; 6 -> "Sun"
            else -> "???"
        }
        return "$dayStr $hourStr:$minStr"
    }
    
    fun getDayOfWeekName(): String = when (dayOfWeek) {
        0 -> "Monday"; 1 -> "Tuesday"; 2 -> "Wednesday"; 3 -> "Thursday"
        4 -> "Friday"; 5 -> "Saturday"; 6 -> "Sunday"
        else -> "Unknown"
    }
}
```

### StoreConfig.kt
```kotlin
package com.example.superstoresimulator.domain.time

data class StoreConfig(
    val openTimeMinutes: Int = 360,         // 6:00 AM
    val closeTimeMinutes: Int = 1260,       // 9:00 PM (21:00)
    val closingProcedureDuration: Int = 30, // 30 minutes
    val allowTransactionsDuringClosing: Boolean = false,
    val gameSpeedMultiplier: Float = 1.0f   // 1 real second = X game minutes
) {
    fun isOpen(gameTime: GameTime): Boolean {
        return gameTime.isOpen(openTimeMinutes, closeTimeMinutes)
    }
    
    fun isClosing(gameTime: GameTime): Boolean {
        val currentMin = gameTime.getTotalMinutesOfDay()
        return currentMin >= closeTimeMinutes && 
               currentMin < closeTimeMinutes + closingProcedureDuration
    }
    
    fun isClosed(gameTime: GameTime): Boolean {
        val currentMin = gameTime.getTotalMinutesOfDay()
        return currentMin < openTimeMinutes || 
               currentMin >= closeTimeMinutes + closingProcedureDuration
    }
}

enum class StoreState {
    CLOSED,              // Before opening time
    OPENING,             // Opening procedures
    OPEN,                // Normal operations
    CLOSING,             // Grace period (existing customers finish)
    CLOSING_PROCEDURES   // After close (staff cleanup)
}
```

---

## 2. TRAFFIC PATTERN SYSTEM

### TrafficPattern.kt
```kotlin
package com.example.superstoresimulator.domain.traffic

data class TrafficPattern(
    val hour: Int,
    val baseCustomerRate: Float,  // Customers per game minute
    val averageBasketSize: Int,
    val peakMultiplier: Float = 1.0f
)

object TrafficSchedule {
    // Weekday traffic (Monday-Friday)
    val WEEKDAY = listOf(
        TrafficPattern(6, 0.05f, 2, 0.5f),
        TrafficPattern(7, 0.15f, 3, 0.8f),
        TrafficPattern(8, 0.40f, 4, 1.2f),
        TrafficPattern(9, 0.25f, 3, 0.9f),
        TrafficPattern(10, 0.10f, 2, 0.7f),
        TrafficPattern(11, 0.08f, 2, 0.6f),
        TrafficPattern(12, 0.35f, 5, 1.3f),  // Lunch
        TrafficPattern(13, 0.20f, 3, 0.9f),
        TrafficPattern(14, 0.10f, 2, 0.7f),
        TrafficPattern(15, 0.08f, 2, 0.6f),
        TrafficPattern(16, 0.12f, 2, 0.7f),
        TrafficPattern(17, 0.50f, 5, 1.5f),  // After work
        TrafficPattern(18, 0.60f, 6, 1.6f),  // Peak evening
        TrafficPattern(19, 0.30f, 4, 1.1f),
        TrafficPattern(20, 0.15f, 3, 0.8f),
        TrafficPattern(21, 0.05f, 2, 0.5f),
    )
    
    // Weekend traffic (Saturday-Sunday)
    val WEEKEND = listOf(
        TrafficPattern(7, 0.02f, 1, 0.3f),
        TrafficPattern(8, 0.05f, 2, 0.5f),
        TrafficPattern(9, 0.15f, 3, 0.8f),
        TrafficPattern(10, 0.40f, 5, 1.4f),  // Weekend morning
        TrafficPattern(11, 0.50f, 6, 1.5f),
        TrafficPattern(12, 0.60f, 7, 1.6f),  // All-day shopping
        TrafficPattern(13, 0.55f, 6, 1.5f),
        TrafficPattern(14, 0.50f, 5, 1.4f),
        TrafficPattern(15, 0.45f, 5, 1.3f),
        TrafficPattern(16, 0.50f, 6, 1.4f),
        TrafficPattern(17, 0.40f, 5, 1.2f),
        TrafficPattern(18, 0.25f, 4, 0.9f),
        TrafficPattern(19, 0.15f, 3, 0.8f),
        TrafficPattern(20, 0.10f, 2, 0.7f),
        TrafficPattern(21, 0.05f, 2, 0.5f),
    )
    
    fun getPattern(gameTime: GameTime): TrafficPattern? {
        val pattern = if (gameTime.dayOfWeek in 0..4) WEEKDAY else WEEKEND
        return pattern.find { it.hour == gameTime.hour }
    }
    
    fun getBaseCustomerRate(gameTime: GameTime): Float {
        return getPattern(gameTime)?.baseCustomerRate ?: 0.0f
    }
}
```

### TrafficManager.kt
```kotlin
package com.example.superstoresimulator.domain.traffic

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import kotlin.random.Random

class TrafficManager {
    private var fractionalCustomers: Float = 0f
    private var lastHour: Int = -1
    
    fun update(gameState: GameState, deltaSeconds: Long): List<TransactionRequest> {
        val gameTime = gameState.currentTime
        val config = gameState.storeConfig
        
        // Only generate customers if store is open
        if (!config.isOpen(gameTime)) {
            fractionalCustomers = 0f
            return emptyList()
        }
        
        // Reset counter when hour changes
        if (gameTime.hour != lastHour) {
            lastHour = gameTime.hour
        }
        
        val baseRate = TrafficSchedule.getBaseCustomerRate(gameTime)
        val customersPerSecond = baseRate / 60f
        val deltaMinutes = deltaSeconds / 60f
        
        fractionalCustomers += customersPerSecond * deltaSeconds
        
        val transactionRequests = mutableListOf<TransactionRequest>()
        
        // Generate whole customers
        while (fractionalCustomers >= 1.0f) {
            val basketSize = generateRandomBasketSize(gameTime)
            transactionRequests.add(
                TransactionRequest(
                    itemCount = basketSize,
                    createdAt = gameTime
                )
            )
            fractionalCustomers -= 1.0f
        }
        
        return transactionRequests
    }
    
    private fun generateRandomBasketSize(gameTime: GameTime): Int {
        val pattern = TrafficSchedule.getPattern(gameTime) ?: return 3
        val avgSize = pattern.averageBasketSize
        
        // Normal distribution around average
        val variance = (avgSize * 0.5).toInt()
        return Random.nextInt(
            maxOf(1, avgSize - variance),
            avgSize + variance + 1
        )
    }
}

data class TransactionRequest(
    val itemCount: Int,
    val createdAt: GameTime
)
```

---

## 3. TIME MANAGER

### TimeManager.kt
```kotlin
package com.example.superstoresimulator.domain.time

class TimeManager(
    val config: StoreConfig = StoreConfig()
) {
    var currentTime = GameTime(0)
        private set
    
    fun update(deltaSeconds: Long) {
        // Convert real seconds to game minutes based on speed multiplier
        val gameMinutes = (deltaSeconds / 60f * config.gameSpeedMultiplier).toLong()
        currentTime = currentTime.addMinutes(gameMinutes)
    }
    
    fun reset() {
        currentTime = GameTime(0)
    }
    
    fun jumpToTime(hour: Int, minute: Int = 0): GameTime {
        val currentDayMin = currentTime.getTotalMinutesOfDay()
        val targetDayMin = hour * 60 + minute
        
        val currentTime = if (targetDayMin <= currentDayMin) {
            // Jump to next day
            this.currentTime.copy(
                totalMinutesElapsed = this.currentTime.totalMinutesElapsed + 
                    ((24 - currentTime.hour) * 60 - currentTime.minute) +
                    (targetDayMin)
            )
        } else {
            // Jump to later today
            this.currentTime.copy(
                totalMinutesElapsed = this.currentTime.totalMinutesElapsed +
                    (targetDayMin - currentDayMin)
            )
        }
        
        this.currentTime = currentTime
        return this.currentTime
    }
    
    fun getStoreState(): StoreState {
        return when {
            config.isClosed(currentTime) -> StoreState.CLOSED
            config.isOpen(currentTime) -> StoreState.OPEN
            config.isClosing(currentTime) -> StoreState.CLOSING
            else -> StoreState.CLOSED
        }
    }
}
```

---

## 4. UPDATED GAMESTATE

### GameState.kt (Additions)
```kotlin
data class GameState(
    // ...existing fields...
    
    // New time-related fields
    val currentTime: GameTime = GameTime(0),
    val storeConfig: StoreConfig = StoreConfig(),
    val storeState: StoreState = StoreState.CLOSED,
    
    // Daily metrics
    val dailyMetrics: DailyMetrics = DailyMetrics(),
    
    // Staff schedules
    val staffSchedules: Map<Int, StaffSchedule> = emptyMap(),
    val pendingOrders: List<InventoryOrder> = emptyList(),
)

data class DailyMetrics(
    val dateString: String = "",
    val openingMoney: Money = Money(0),
    val totalRevenue: Money = Money(0),
    val totalExpenses: Money = Money(0),
    val transactionCount: Int = 0,
    val customerCount: Int = 0,
    val peakHour: Int = 0,
    val peakHourCustomers: Int = 0
) {
    val profit: Money
        get() = totalRevenue - totalExpenses
}

data class InventoryOrder(
    val orderId: Int,
    val itemId: Int,
    val quantity: Int,
    val orderedAt: GameTime,
    val deliveryTime: GameTime,
    val casePacksOrdered: Int,
    val status: OrderStatus = OrderStatus.PENDING,
    val cost: Money = Money(0)
)

enum class OrderStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED,
    STOCKED
}

data class StaffSchedule(
    val employeeId: Int,
    val shifts: List<Shift> = emptyList(),
    val daysOff: Set<Int> = emptySet()
)

data class Shift(
    val startTimeMinutes: Int,  // 360 = 6 AM
    val endTimeMinutes: Int,    // 1260 = 9 PM
    val dayOfWeek: Int          // 0-6
)
```

---

## 5. UPDATED GAME ENGINE

### GameEngine.kt (Additions)
```kotlin
class GameEngine(private val itemDao: ItemDao) {
    private val timeManager = TimeManager()
    private val trafficManager = TrafficManager()
    
    private var state = GameState(
        inventory = mutableMapOf(),
        currentTime = GameTime(0)
    )
    
    // ...existing code...
    
    fun tick(deltaMilliseconds: Long) {
        // Update time first
        timeManager.update(deltaMilliseconds / 1000)
        state = state.copy(currentTime = timeManager.currentTime)
        
        // Update store state
        val newStoreState = timeManager.getStoreState()
        if (newStoreState != state.storeState) {
            handleStoreStateChange(newStoreState)
        }
        state = state.copy(storeState = newStoreState)
        
        // Only process traffic/customers if open
        if (state.storeState == StoreState.OPEN) {
            // Generate new customers based on traffic
            val transactionRequests = trafficManager.update(state, deltaMilliseconds / 1000)
            for (request in transactionRequests) {
                state = txEngine.startNewTransaction(state)
            }
        }
        
        // Existing staff automation
        processCashierActions(deltaMilliseconds)
        processStocker Actions(deltaMilliseconds)
        
        // Process inventory deliveries
        processInventoryDeliveries()
        
        // Update daily metrics
        updateDailyMetrics()
    }
    
    private fun handleStoreStateChange(newState: StoreState) {
        when (newState) {
            StoreState.OPENING -> {
                // Prepare for day
                // Reset daily metrics
                state = state.copy(
                    dailyMetrics = DailyMetrics(
                        dateString = state.currentTime.getFormattedTime(),
                        openingMoney = state.money
                    )
                )
            }
            StoreState.CLOSING -> {
                // Starting closing procedures
                // Stop accepting new transactions
            }
            StoreState.CLOSED -> {
                // Night time
                // Archive daily metrics
                // Process overnight stocking
            }
            else -> {}
        }
    }
    
    private fun processInventoryDeliveries() {
        val deliveredOrders = state.pendingOrders
            .filter { it.status == OrderStatus.IN_TRANSIT }
            .filter { it.deliveryTime <= state.currentTime }
        
        for (order in deliveredOrders) {
            val dyn = state.inventory[order.itemId]?.copy(
                backroomStock = (state.inventory[order.itemId]?.backroomStock ?: 0) + 
                    (order.casePacksOrdered * (getDbItem(order.itemId)?.casePack ?: 1))
            )
            
            if (dyn != null) {
                state = state.copy(
                    inventory = state.inventory + (order.itemId to dyn),
                    pendingOrders = state.pendingOrders.map { o ->
                        if (o.orderId == order.orderId) {
                            o.copy(status = OrderStatus.DELIVERED)
                        } else o
                    }
                )
            }
        }
    }
    
    private fun updateDailyMetrics() {
        // Called every tick to accumulate daily stats
        // This would track hourly customer counts, peak times, etc.
    }
    
    fun orderItemCasePacks(itemId: Int, casePacks: Int) {
        val dbItem = getDbItem(itemId) ?: return
        val cost = dbItem.getCasePackCostAsMoney() * casePacks
        
        if (state.money < cost) return
        
        // Calculate delivery time
        val currentHour = state.currentTime.hour
        val deliveryHour = if (currentHour > 18) {
            // Evening order → deliver tomorrow at 9 AM
            val hoursUntilTomorrow9Am = (9 + (24 - currentHour)) % 24
            state.currentTime.addMinutes((hoursUntilTomorrow9Am * 60).toLong())
        } else {
            // Morning order → deliver same day at 3 PM
            val hoursDifference = 15 - currentHour
            if (hoursDifference > 0) {
                state.currentTime.addMinutes((hoursDifference * 60).toLong())
            } else {
                // Too late today, deliver tomorrow
                state.currentTime.addMinutes(((24 - currentHour + 15) * 60).toLong())
            }
        }
        
        val order = InventoryOrder(
            orderId = state.pendingOrders.size + 1,
            itemId = itemId,
            quantity = dbItem.casePack * casePacks,
            orderedAt = state.currentTime,
            deliveryTime = deliveryHour,
            casePacksOrdered = casePacks,
            status = OrderStatus.IN_TRANSIT,
            cost = cost
        )
        
        state = state.copy(
            money = state.money - cost,
            pendingOrders = state.pendingOrders + order
        )
    }
}
```

---

## 6. UI STATE UPDATES

### GameUIState.kt (Additions)
```kotlin
data class TimeUIState(
    val currentTime: GameTime,
    val storeState: StoreState,
    val dayMetrics: DailyMetrics,
    val speedMultiplier: Float = 1.0f
)

// Updated AppUIState
data class AppUIState(
    // ...existing...
    val timeUI: TimeUIState? = null
)
```

---

## 7. UI COMPOSABLE EXAMPLE

### TimeDisplayBar.kt
```kotlin
@Composable
fun TimeDisplayBar(
    timeUI: TimeUIState,
    onSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2C3E50))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time display
        Column {
            Text(
                text = timeUI.currentTime.getFormattedTime(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = timeUI.storeState.name,
                fontSize = 12.sp,
                color = when (timeUI.storeState) {
                    StoreState.OPEN -> Color(0xFF2ECC71)
                    StoreState.CLOSED -> Color(0xFFE74C3C)
                    else -> Color(0xFFF39C12)
                }
            )
        }
        
        // Daily profit
        Text(
            text = "Daily: ${timeUI.dayMetrics.profit}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (timeUI.dayMetrics.profit >= Money(0)) 
                Color(0xFF2ECC71) else Color(0xFFE74C3C)
        )
        
        // Speed controls
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SpeedButton("1x", 1.0f, timeUI.speedMultiplier, onSpeedChanged)
            SpeedButton("2x", 2.0f, timeUI.speedMultiplier, onSpeedChanged)
            SpeedButton("4x", 4.0f, timeUI.speedMultiplier, onSpeedChanged)
        }
    }
}

@Composable
private fun SpeedButton(
    label: String,
    speed: Float,
    current: Float,
    onSpeedChanged: (Float) -> Unit
) {
    Button(
        onClick = { onSpeedChanged(speed) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (current == speed) Color(0xFF3498DB) else Color(0xFF34495E)
        ),
        modifier = Modifier.size(40.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 10.sp)
    }
}
```

---

## INTEGRATION CHECKLIST

- [ ] Add GameTime, StoreConfig, TrafficPattern data classes
- [ ] Create TrafficManager and TimeManager
- [ ] Update GameState to include time fields
- [ ] Update GameEngine.tick() to call time and traffic updates
- [ ] Update InventoryOrder and order processing
- [ ] Create TimeUIState
- [ ] Build TimeDisplayBar composable
- [ ] Add time display to main screen
- [ ] Create schedule management screen
- [ ] Add daily summary screen
- [ ] Implement speed controls
- [ ] Test time progression and traffic generation
- [ ] Balance customer rates and difficulty
- [ ] Save/load time state
- [ ] Polish audio/visual cues for time of day

This provides the foundation to build out the day cycle system incrementally.

