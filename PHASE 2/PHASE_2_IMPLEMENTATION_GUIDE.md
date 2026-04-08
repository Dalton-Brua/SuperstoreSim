# Phase 2: Traffic Patterns & Player Roles - Complete Implementation Guide

> **Date Created**: March 31, 2026  
> **Status**: Ready for Implementation  
> **Prerequisites**: Phase 1 (Time System) must be completed  
> **Estimated Effort**: 10-15 hours

---

## Quick Reference: What Phase 2 Adds

### New Features
- ✅ **Autonomous customer generation** based on hourly traffic patterns
- ✅ **Player roles system** (Cashier/Stocker) - player can work alongside staff
- ✅ **Customer queue visualization** - see pending transactions
- ✅ **Automatic transaction processing** - based on player/staff work rate

### Removed Features (Finalization Step)
- ❌ Manual "Start Transaction" button
- ❌ Manual "Ring Up" button
- ❌ Manual ring-up item buttons in transaction dialog
- ❌ RingUp/RingUpItem/StartTransaction events

### New GameEvents
- ✨ `SetPlayerRole(role: PlayerRole)` - toggle player work mode

---

## Architecture Overview

```
GameEngine.tick(deltaMs)
    ├── TimeManager.update(deltaMs)
    │   └── Advances game time
    ├── TrafficManager.update(deltaMs)  [NEW Phase 2]
    │   └── Auto-generates customers/transactions
    ├── Process Hired Cashiers
    │   └── Ring up items automatically
    ├── Process Hired Stockers
    │   └── Stock items automatically
    ├── Process Player Work  [NEW Phase 2]
    │   ├── If PlayerRole == CASHIER → ring up automatically
    │   └── If PlayerRole == STOCKER → stock automatically
    └── Update UI State
        └── Render transaction queues, player role, etc.
```

---

## Phase 2 Implementation Checklist

### Step 1: Create Traffic Pattern System (2-3 hours)
- [ ] Create `domain/traffic/TrafficPattern.kt`
- [ ] Define hourly customer rates
- [ ] Create `TrafficSchedule` (weekday/weekend patterns)
- [ ] Add to `domain/traffic/TrafficManager.kt`
- [ ] Test accumulation logic

### Step 2: Integrate TrafficManager into GameEngine (1-2 hours)
- [ ] Add `TrafficManager` instance to `GameEngine`
- [ ] Call `trafficManager.update()` in `tick()`
- [ ] Auto-start transactions from customer arrivals
- [ ] Only generate when `storeState == OPEN`

### Step 3: Create Player Roles System (2-3 hours)
- [ ] Add `PlayerRole` enum to `domain/`
- [ ] Add fields to `GameState`: `playerRole`, `playerCashierProgress`, `playerStockerProgress`
- [ ] Create `SetPlayerRole` event
- [ ] Implement player work logic in `GameEngine.tick()`

### Step 4: UI Integration - Player Roles (2-3 hours)
- [ ] Add `PlayerRole` to `GameUiState`
- [ ] Create `PlayerRoleIndicator.kt` composable
- [ ] Add "Work as Cashier" button
- [ ] Add "Work as Stocker" button
- [ ] Add "Stop Working" button
- [ ] Wire to `GameViewModel`

### Step 5: UI Integration - Customer Queue (1-2 hours)
- [ ] Add pending transaction count to `GameUiState`
- [ ] Create `TransactionQueueIndicator.kt` composable
- [ ] Display on home screen
- [ ] Show visual feedback during peak hours

### Step 6: Remove Manual UI Elements (1-2 hours)
- [ ] Remove `RingUpButton.kt`
- [ ] Remove `StartTransaction` event handling
- [ ] Remove "Ring Up Item" button from `TransactionDetailDialog.kt`
- [ ] Remove related events from `GameEvent.kt`
- [ ] Delete related handlers from `GameViewModel.kt`

### Step 7: Playtest & Balance (2-3 hours)
- [ ] Test customer generation rates
- [ ] Test player cashier/stocker work
- [ ] Test hired staff still works
- [ ] Balance: is peak hour challenging?
- [ ] Adjust traffic rates if needed

---

## Detailed Implementation

### 1. TrafficPattern System

**File**: `domain/traffic/TrafficPattern.kt`

```kotlin
package com.example.superstoresimulator.domain.traffic

/**
 * Defines customer traffic pattern for a specific hour
 * @param hour Hour of day (6-21, corresponding to 6 AM - 9 PM)
 * @param baseCustomerRate Customers per minute at base speed (1x)
 * @param averageBasketSize Average items per transaction
 * @param peakMultiplier Multiplier during peak hours
 */
data class TrafficPattern(
    val hour: Int,
    val baseCustomerRate: Float,    // 0.5 = 1 customer per 2 minutes
    val averageBasketSize: Int,     // 3-5 items typical
    val peakMultiplier: Float = 1.0f
)

/**
 * Traffic schedule for entire day
 * Separate weekday/weekend patterns for realism
 */
object TrafficSchedule {
    // WEEKDAY PATTERN (Mon-Fri)
    val WEEKDAY = listOf(
        // Early Morning (6-7 AM): Very quiet
        TrafficPattern(6, baseCustomerRate = 0.3f, averageBasketSize = 3, peakMultiplier = 0.5f),
        TrafficPattern(7, baseCustomerRate = 0.5f, averageBasketSize = 3, peakMultiplier = 0.7f),
        
        // Morning (8-10 AM): Picking up
        TrafficPattern(8, baseCustomerRate = 1.0f, averageBasketSize = 4, peakMultiplier = 1.2f),
        TrafficPattern(9, baseCustomerRate = 1.2f, averageBasketSize = 4, peakMultiplier = 1.5f),
        TrafficPattern(10, baseCustomerRate = 0.8f, averageBasketSize = 3, peakMultiplier = 1.0f),
        
        // Mid-Morning to Noon (11-12 PM): Growing
        TrafficPattern(11, baseCustomerRate = 1.0f, averageBasketSize = 4, peakMultiplier = 1.2f),
        TrafficPattern(12, baseCustomerRate = 2.0f, averageBasketSize = 5, peakMultiplier = 2.5f), // LUNCH RUSH
        
        // Afternoon (1-4 PM): Quiet/Medium
        TrafficPattern(13, baseCustomerRate = 1.5f, averageBasketSize = 4, peakMultiplier = 1.8f),
        TrafficPattern(14, baseCustomerRate = 0.8f, averageBasketSize = 3, peakMultiplier = 1.0f),
        TrafficPattern(15, baseCustomerRate = 0.7f, averageBasketSize = 3, peakMultiplier = 0.9f),
        TrafficPattern(16, baseCustomerRate = 0.9f, averageBasketSize = 3, peakMultiplier = 1.1f),
        
        // Evening (5-7 PM): Second rush
        TrafficPattern(17, baseCustomerRate = 2.2f, averageBasketSize = 5, peakMultiplier = 2.8f), // EVENING RUSH PEAK
        TrafficPattern(18, baseCustomerRate = 1.8f, averageBasketSize = 4, peakMultiplier = 2.2f),
        TrafficPattern(19, baseCustomerRate = 1.2f, averageBasketSize = 4, peakMultiplier = 1.5f),
        
        // Late Evening (8-9 PM): Dying down
        TrafficPattern(20, baseCustomerRate = 0.6f, averageBasketSize = 3, peakMultiplier = 0.8f),
        TrafficPattern(21, baseCustomerRate = 0.2f, averageBasketSize = 2, peakMultiplier = 0.3f),
    )
    
    // WEEKEND PATTERN (Sat-Sun)
    val WEEKEND = listOf(
        // Morning (6-10 AM): Slower start
        TrafficPattern(6, baseCustomerRate = 0.2f, averageBasketSize = 2, peakMultiplier = 0.3f),
        TrafficPattern(7, baseCustomerRate = 0.3f, averageBasketSize = 3, peakMultiplier = 0.4f),
        TrafficPattern(8, baseCustomerRate = 0.6f, averageBasketSize = 3, peakMultiplier = 0.8f),
        TrafficPattern(9, baseCustomerRate = 0.9f, averageBasketSize = 4, peakMultiplier = 1.2f),
        TrafficPattern(10, baseCustomerRate = 1.1f, averageBasketSize = 4, peakMultiplier = 1.4f),
        
        // Late Morning to Afternoon (11-3 PM): Moderate, steady
        TrafficPattern(11, baseCustomerRate = 1.3f, averageBasketSize = 4, peakMultiplier = 1.6f),
        TrafficPattern(12, baseCustomerRate = 1.5f, averageBasketSize = 4, peakMultiplier = 1.8f),
        TrafficPattern(13, baseCustomerRate = 1.4f, averageBasketSize = 4, peakMultiplier = 1.7f),
        TrafficPattern(14, baseCustomerRate = 1.2f, averageBasketSize = 4, peakMultiplier = 1.5f),
        TrafficPattern(15, baseCustomerRate = 1.1f, averageBasketSize = 3, peakMultiplier = 1.3f),
        
        // Evening (4-7 PM): Evening shoppers
        TrafficPattern(16, baseCustomerRate = 1.2f, averageBasketSize = 4, peakMultiplier = 1.5f),
        TrafficPattern(17, baseCustomerRate = 1.4f, averageBasketSize = 4, peakMultiplier = 1.7f),
        TrafficPattern(18, baseCustomerRate = 1.1f, averageBasketSize = 4, peakMultiplier = 1.3f),
        TrafficPattern(19, baseCustomerRate = 0.7f, averageBasketSize = 3, peakMultiplier = 0.9f),
        
        // Late Evening (8-9 PM): Winding down
        TrafficPattern(20, baseCustomerRate = 0.4f, averageBasketSize = 3, peakMultiplier = 0.5f),
        TrafficPattern(21, baseCustomerRate = 0.1f, averageBasketSize = 2, peakMultiplier = 0.2f),
    )
    
    fun getPatternForTime(gameTime: GameTime): TrafficPattern {
        val hour = gameTime.hour
        val isWeekend = gameTime.dayOfWeek in 5..6  // Saturday=5, Sunday=6
        val schedule = if (isWeekend) WEEKEND else WEEKDAY
        
        // Find pattern for this hour (or return default if outside hours)
        return schedule.firstOrNull { it.hour == hour }
            ?: TrafficPattern(hour, 0.5f, 3, 1.0f)
    }
}
```

**File**: `domain/traffic/TrafficManager.kt`

```kotlin
package com.example.superstoresimulator.domain.traffic

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.time.StoreState

/**
 * Manages autonomous customer generation based on traffic patterns
 * Accumulates fractional customers similar to how TimeManager accumulates fractional time
 */
class TrafficManager {
    private var accumulatedCustomers = 0.0  // Fractional customer accumulation
    
    /**
     * Update customer traffic for this tick
     * @param state Current game state
     * @param deltaSeconds Time elapsed since last tick in real seconds
     * @return List of complete transactions ready to start
     */
    fun update(state: GameState, deltaSeconds: Long): List<TransactionRequest> {
        val transactions = mutableListOf<TransactionRequest>()
        
        // Only generate customers if store is open
        if (state.storeState != StoreState.OPEN) {
            accumulatedCustomers = 0.0
            return transactions
        }
        
        // Get traffic pattern for current hour
        val pattern = TrafficSchedule.getPatternForTime(state.currentTime)
        
        // Calculate customer rate considering game speed multiplier
        // baseCustomerRate is per minute, convert to per second
        val customerRatePerSecond = (pattern.baseCustomerRate / 60.0f) * 
                                    state.storeConfig.gameSpeedMultiplier
        
        // Accumulate customers
        accumulatedCustomers += customerRatePerSecond * deltaSeconds
        
        // Generate transactions for whole customers
        while (accumulatedCustomers >= 1.0) {
            // Generate random basket (3-7 items, centered on averageBasketSize)
            val basketSize = (pattern.averageBasketSize - 1 + Random.nextInt(3)).coerceIn(1, 7)
            
            transactions.add(TransactionRequest(
                itemCount = basketSize,
                customerPattern = pattern
            ))
            
            accumulatedCustomers -= 1.0
        }
        
        return transactions
    }
    
    fun reset() {
        accumulatedCustomers = 0.0
    }
}

/**
 * Request to generate a new transaction
 */
data class TransactionRequest(
    val itemCount: Int,
    val customerPattern: TrafficPattern
)
```

### 2. Player Role System

**File**: `domain/player/PlayerRole.kt`

```kotlin
package com.example.superstoresimulator.domain.player

/**
 * Represents what job the player is currently performing
 */
enum class PlayerRole {
    NONE,      // Player is managing/not working (default)
    CASHIER,   // Player is ringing up items automatically
    STOCKER    // Player is stocking items automatically
}
```

**Update**: `domain/GameStateData.kt`

```kotlin
// Add these fields to GameState:
val playerRole: PlayerRole = PlayerRole.NONE,
val playerCashierProgress: Float = 0f,  // Accumulate partial ring-ups
val playerStockerProgress: Float = 0f,  // Accumulate partial stocks
```

### 3. GameEngine Integration

**Update**: `domain/GameEngine.kt` - `tick()` method

```kotlin
fun tick(deltaMilliseconds: Long) {
    // ...existing time update code...
    
    val deltaSeconds = deltaMilliseconds / 1000.0
    
    // Phase 2: Generate autonomous customers
    if (state.storeState == StoreState.OPEN) {
        val newCustomers = trafficManager.update(state, deltaSeconds)
        newCustomers.forEach { request ->
            state = txEngine.generateRandomTransaction(state, request.itemCount)
        }
    }
    
    // Process hired staff work
    processHiredCashiers(deltaSeconds)
    processHiredStockers(deltaSeconds)
    
    // Phase 2: Process player work (mutually exclusive)
    when (state.playerRole) {
        PlayerRole.CASHIER -> state = performPlayerCashierWork(deltaSeconds)
        PlayerRole.STOCKER -> state = performPlayerStockerWork(deltaSeconds)
        PlayerRole.NONE -> {
            // Reset progress when not working
            state = state.copy(
                playerCashierProgress = 0f,
                playerStockerProgress = 0f
            )
        }
    }
    
    // ...rest of tick...
}

private fun performPlayerCashierWork(deltaSeconds: Double): GameState {
    val itemsPerSecond = 0.5f  // Same as hired cashier
    var currentState = state
    var progress = currentState.playerCashierProgress + (itemsPerSecond * deltaSeconds).toFloat()
    
    // Ring up whole items
    while (progress >= 1.0f) {
        // Ring up one item from current transaction
        if (currentState.currentTransaction.lines.isNotEmpty()) {
            val lines = currentState.currentTransaction.lines
            val ringable = lines.filter { it.rungQty < it.quantity }
            
            if (ringable.isNotEmpty()) {
                val lineToRing = ringable.first()
                val updatedLine = lineToRing.copy(rungQty = lineToRing.rungQty + 1)
                val updatedLines = lines.map { if (it == lineToRing) updatedLine else it }
                
                val allRung = updatedLines.all { it.rungQty == it.quantity }
                if (allRung) {
                    // Transaction complete
                    currentState = completeTransaction(currentState)
                    if (currentState.currentTransaction.lines.isEmpty()) {
                        // Start new transaction if there are pending customers
                        // (handled by transaction engine)
                    }
                } else {
                    currentState = currentState.copy(
                        currentTransaction = currentState.currentTransaction.copy(lines = updatedLines)
                    )
                }
            }
        }
        
        progress -= 1.0f
    }
    
    return currentState.copy(playerCashierProgress = progress)
}

private fun performPlayerStockerWork(deltaSeconds: Double): GameState {
    val actionsPerSecond = 0.3f  // Same as hired stocker
    var currentState = state
    var progress = currentState.playerStockerProgress + (actionsPerSecond * deltaSeconds).toFloat()
    
    // Stock one item per accumulated action
    while (progress >= 1.0f) {
        currentState = stockRandomItemFromBackroom(currentState)
        progress -= 1.0f
    }
    
    return currentState.copy(playerStockerProgress = progress)
}
```

### 4. UI State Integration

**Update**: `ui/state/GameUiState.kt`

```kotlin
// Add to TimeUIState:
val playerRole: PlayerRole = PlayerRole.NONE
```

### 5. New Event

**Update**: `ui/GameEvent.kt`

```kotlin
data class SetPlayerRole(val role: PlayerRole) : GameEvent
```

### 6. ViewModel Handler

**Update**: `ui/viewmodels/GameViewModel.kt`

```kotlin
is GameEvent.SetPlayerRole -> {
    gameEngine.state = gameEngine.state.copy(playerRole = event.role)
}
```

### 7. UI Components

**New File**: `ui/components/PlayerRoleIndicator.kt`

```kotlin
package com.example.superstoresimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.player.PlayerRole

@Composable
fun PlayerRoleIndicator(
    playerRole: PlayerRole,
    playerCashierProgress: Float,
    playerStockerProgress: Float,
    modifier: Modifier = Modifier
) {
    val roleColor = when (playerRole) {
        PlayerRole.CASHIER -> Color(0xFF2ECC71)  // Green
        PlayerRole.STOCKER -> Color(0xFF3498DB)  // Blue
        PlayerRole.NONE -> Color(0xFF95A5A6)     // Gray
    }
    
    val roleText = when (playerRole) {
        PlayerRole.CASHIER -> "CASHIER"
        PlayerRole.STOCKER -> "STOCKER"
        PlayerRole.NONE -> "MANAGING"
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2C3E50))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(roleColor, shape = RoundedCornerShape(4.dp))
                .padding(6.dp, 3.dp)
        ) {
            Text(
                text = roleText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Show progress if working
        if (playerRole != PlayerRole.NONE) {
            val progress = when (playerRole) {
                PlayerRole.CASHIER -> playerCashierProgress
                PlayerRole.STOCKER -> playerStockerProgress
                else -> 0f
            }
            
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 10.sp,
                color = Color.White
            )
        }
    }
}
```

**New File**: `ui/components/PlayerRoleButtons.kt`

```kotlin
package com.example.superstoresimulator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.player.PlayerRole

@Composable
fun PlayerRoleButtons(
    currentRole: PlayerRole,
    hasPendingTransactions: Boolean,
    hasBackroomItems: Boolean,
    onRoleChanged: (PlayerRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Work as Cashier button
        Button(
            onClick = { 
                onRoleChanged(if (currentRole == PlayerRole.CASHIER) PlayerRole.NONE else PlayerRole.CASHIER)
            },
            enabled = hasPendingTransactions,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentRole == PlayerRole.CASHIER) Color(0xFF2ECC71) else Color(0xFF34495E),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFBDC3C7)
            ),
            modifier = Modifier.weight(1f)
        ) {
            Text("Cashier", fontSize = 12.sp)
        }
        
        // Work as Stocker button
        Button(
            onClick = {
                onRoleChanged(if (currentRole == PlayerRole.STOCKER) PlayerRole.NONE else PlayerRole.STOCKER)
            },
            enabled = hasBackroomItems,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentRole == PlayerRole.STOCKER) Color(0xFF3498DB) else Color(0xFF34495E),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFBDC3C7)
            ),
            modifier = Modifier.weight(1f)
        ) {
            Text("Stocker", fontSize = 12.sp)
        }
        
        // Stop Working button (only show if working)
        if (currentRole != PlayerRole.NONE) {
            Button(
                onClick = { onRoleChanged(PlayerRole.NONE) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE74C3C),
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop", fontSize = 12.sp)
            }
        }
    }
}
```

---

## File Locations Reference

### Files to Create
```
domain/
  ├── traffic/
  │   ├── TrafficPattern.kt (NEW)
  │   └── TrafficManager.kt (NEW)
  └── player/
      └── PlayerRole.kt (NEW)

ui/
  └── components/
      ├── PlayerRoleIndicator.kt (NEW)
      └── PlayerRoleButtons.kt (NEW)
```

### Files to Modify
```
domain/
  ├── GameStateData.kt (ADD playerRole, playerCashierProgress, playerStockerProgress)
  └── GameEngine.kt (ADD traffic integration, player work logic in tick())

ui/
  ├── GameEvent.kt (ADD SetPlayerRole event)
  ├── viewmodels/GameViewModel.kt (ADD SetPlayerRole handler)
  └── state/GameUiState.kt (ADD playerRole to TimeUIState)
```

### Files to Delete (Final Step - Phase 2 Finalization)
```
ui/
  ├── components/RingUpButton.kt (DELETE)
  └── Remove RingUp/RingUpItem/StartTransaction from GameEvent.kt
```

---

## Testing Checklist

- [ ] Traffic generates customers at expected rates
- [ ] Customers don't generate when store is closed
- [ ] Player can toggle to CASHIER role
- [ ] Player cashier work processes items automatically
- [ ] Player can toggle to STOCKER role
- [ ] Player stocker work stocks items automatically
- [ ] Only one player role active at a time
- [ ] Player work accumulates fractional progress
- [ ] Hired staff still works alongside player
- [ ] Peak hours are noticeably busier
- [ ] Game is playable with no hired staff (player working)
- [ ] Manual buttons removed (final step)

---

## Balance Tuning Guidelines

### If Game is Too Easy
- Increase `baseCustomerRate` in traffic patterns
- Decrease `playerCashierProgress` accumulation rate
- Decrease hired staff efficiency

### If Game is Too Hard
- Decrease `baseCustomerRate` in traffic patterns
- Increase `playerCashierProgress` accumulation rate
- Increase hired staff efficiency

### If Peak Hours Don't Feel Hectic
- Increase `peakMultiplier` values in traffic patterns
- Consider visual feedback (queue visualization)

---

## Next Steps After Phase 2

- **Phase 3**: Delivery system & inventory restocking
- **Phase 4**: Staff scheduling & shift management
- **Phase 5**: Metrics & daily progression

---

## Contact/Reference

**Original Design**: DAY_CYCLE_IMPLEMENTATION_GUIDE.md  
**Player Roles Addition**: PHASE_2_PLAYER_ROLES_ADDITION.md  
**Questions?**: See DAY_CYCLE_DESIGN.md for system architecture

