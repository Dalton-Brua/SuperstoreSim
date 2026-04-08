# Phase 2 Addition: Player Roles (Cashier/Stocker)

## Feature Overview

When the store is understaffed or during critical peak hours, the player can manually perform staff duties:
- **Player as Cashier**: Ring up items from the transaction queue automatically
- **Player as Stocker**: Stock items from backroom to shelves automatically
- **Toggle System**: Player can switch between roles or stop working at any time
- **Mutual Exclusivity**: Only one player role at a time (can't cashier AND stock simultaneously)

## Why This Feature?

**Problem**: With autonomous customer generation, if the player hires no cashiers, the store gets overwhelmed immediately and the game becomes frustrating/unplayable.

**Solution**: Allow player to work alongside (or in place of) hired staff to:
- Learn the game mechanics
- Handle understaffing gracefully
- Create strategic decisions ("Should I hire staff or do this myself?")
- Provide manual fallback during crisis moments

## Implementation Design

### 1. PlayerRole Enum
```kotlin
enum class PlayerRole {
    NONE,      // Player is managing only (default)
    CASHIER,   // Player is ringing up items
    STOCKER    // Player is stocking shelves
}
```

### 2. GameState Addition
```kotlin
data class GameState(
    // ... existing fields ...
    val playerRole: PlayerRole = PlayerRole.NONE,
    val playerCashierProgress: Float = 0f,  // Accumulate partial ring-ups
    val playerStockerProgress: Float = 0f,  // Accumulate partial stocks
)
```

### 3. GameEngine Changes

**In tick() method, add player work logic**:
```kotlin
// After staff actions, add player actions
if (state.playerRole == PlayerRole.CASHIER) {
    // Player rings up items at same rate as hired cashier
    val itemsPerSecond = 0.5f  // Same as hired cashier
    state = performPlayerCashierWork(itemsPerSecond, delta)
}

if (state.playerRole == PlayerRole.STOCKER) {
    // Player stocks items at same rate as hired stocker
    val stocksPerSecond = 0.3f  // Same as hired stocker
    state = performPlayerStockerWork(stocksPerSecond, delta)
}
```

### 4. New GameEvents
```kotlin
data class SetPlayerRole(val role: PlayerRole) : GameEvent
```

### 5. UI Updates

**Add to GameUiState**:
```kotlin
data class PlayerRoleUIState(
    val currentRole: PlayerRole,
    val showRoleButtons: Boolean = true
)
```

**Create PlayerRoleIndicator Composable**:
- Display current role (CASHIER, STOCKER, NONE)
- Show color-coded indicator (green = active, gray = inactive)
- Display accumulated progress for current role

**Add RoleToggle Buttons to Home Screen**:
- "Work as Cashier" button (only shown if there are pending transactions)
- "Work as Stocker" button (only shown if backroom has items)
- "Stop Working" button (when actively working)
- Buttons are mutually exclusive (only one can be active)

## Gameplay Implications

### Early Game Scenario
```
Player: "I'm starting with no staff"
Game: "Customers are arriving, but no one to ring them up!"
Player: "I'll click CASHIER and handle it myself"
Game: "Now you're ringing up items automatically"
Player Decision: "This is stressful. Should I hire a cashier now?"
```

### Strategic Depth Added
- **Cost vs Time**: "Hiring a cashier costs $X, but I can do the work for free (my time)"
- **Growth Path**: "As I hire staff, I gradually shift from doing work to managing"
- **Crisis Management**: "During peak hours, should I help staff or stay managing?"
- **Specialization**: "I'm better at one job, so I'll hire for the other"

## Progression of Gameplay

### Phase (Minimal Staff)
```
Player is CASHIER during peak hours
Player is STOCKER during quiet hours
No hired staff yet
```

### Phase (Growing)
```
Player hires 1 Cashier
Player works alongside during peak (both working)
Player can focus on stocking when needed
```

### Phase (Mature)
```
Player has 2 Cashiers + 1 Stocker
Player rarely works (only in emergencies)
Switches to pure management/strategy
```

## UI/UX Details

### Player Role Display
- Location: Above or next to time display
- Color: 
  - Green when active (player working)
  - Gray when inactive (NONE)
  - Fade effect showing progress accumulation

### Progress Bar (Optional)
- Show small bar under role indicator
- Fills as partial action accumulates
- Resets when whole action completes

### Button Layout
```
┌─────────────────────────────────────┐
│ Mon 06:00 | NONE (1x speed)         │
├─────────────────────────────────────┤
│ [Cashier] [Stocker] [Stop]          │
└─────────────────────────────────────┘
```

## Implementation Order (Phase 2)

1. Add PlayerRole enum and GameState fields
2. Create SetPlayerRole event
3. Add player work logic to GameEngine.tick()
4. Update GameViewModel to handle player role state
5. Create PlayerRoleIndicator composable
6. Add role toggle buttons to home screen
7. **REMOVE manual player actions** (see below)
8. Playtest and balance player work rates

## Phase 2: Removal of Manual Player Actions

With autonomous customer generation and player roles, the following manual player actions must be removed or disabled:

### 1. RingUpButton - REMOVE
**Location**: Home screen (StoreHomeScreen.kt)
**Why**: Players no longer manually ring up items. Instead:
- Customers arrive automatically
- Player can toggle "Work as Cashier" to ring up automatically
- OR hired cashiers handle it automatically

**Action**: Delete the RingUpButton component and all related code
- Remove from StoreHomeScreen layout
- Remove RingUpItem and RingUp events (no longer needed)
- Remove related GameEvent handlers from GameViewModel

### 2. TransactionDetailDialog - MODIFY
**Location**: Transaction detail dialog (TransactionDetailDialog.kt)
**Current Functionality**: 
- Shows transaction items
- Allows clicking "Ring Up Item" button to manually ring up individual items
- Allows clicking "View Item" to navigate to inventory

**New Functionality**:
- Display transaction items (read-only)
- Keep "View Item" button (still useful)
- REMOVE "Ring Up Item" button
- Show status: "Pending" or "Complete"
- Purpose: Informational/debugging view, not action center

**Action**: 
- Remove onRingUpItem parameter and callback
- Remove the "Ring Up Item" button from dialog UI
- Keep everything else as-is

### 3. StartTransaction Button - REMOVE
**Location**: Home screen (StoreHomeScreen.kt)
**Why**: Transactions are now auto-generated based on traffic patterns
**Action**: Remove StartTransaction event and button

### 4. TransactionSummaryCard - MODIFY
**Location**: Home screen (StoreHomeScreen.kt)
**Current**: Shows current transaction progress, can click to view details
**New**: 
- Still shows transaction details
- Click still opens dialog (for viewing)
- But the dialog is now read-only (no ring-up action)

### 5. Affected Events to Remove
From GameEvent.kt:
- `RingUp` - no longer needed
- `RingUpItem(itemId: Int)` - no longer needed
- `StartTransaction` - no longer needed

All these are replaced by:
- `SetPlayerRole(role: PlayerRole)` - new event for player work
- Auto-generated transactions from TrafficManager

## UI/UX Flow Change

### BEFORE Phase 2 (Current)
```
1. Player clicks "Start Transaction" button
2. Random transaction appears
3. Player clicks items to "Ring Up" one at a time
4. Transaction completes when all items rung up
5. Next transaction starts (player initiates again)
```

### AFTER Phase 2 (Autonomous)
```
1. Time flows, store opens
2. Customers arrive automatically (TrafficManager)
3. Transactions auto-generate
4. Player chooses: Work as Cashier OR let staff handle
5. Items auto-ring up based on player/staff work rate
6. Transactions complete automatically
7. Queue fills/empties based on customer flow vs processing speed
```

## Migration Strategy

**Don't delete immediately** - instead:
1. Implement Phase 2 traffic system FIRST
2. Implement Phase 2 player roles SECOND
3. Test that autonomous system works
4. THEN remove manual buttons (Phase 2 finalization)

This prevents breaking the game while we build the new system.

## Summary of Changes

| Feature | Current | Phase 2 | Reason |
|---------|---------|---------|--------|
| RingUpButton | Active | REMOVED | Auto-processing |
| StartTransaction | Active | REMOVED | Auto-generation |
| Ring Up Item button (dialog) | Active | REMOVED | Auto-processing |
| Work as Cashier button | N/A | NEW | Player control |
| Work as Stocker button | N/A | NEW | Player control |
| Transaction Detail Dialog | Functional | Read-only | Info only |
| Manual transaction flow | Primary | GONE | Replaced by autonomous |



## Balance Considerations

### Player Work Rates
- **Cashier**: 0.5 items/second (same as hired cashier)
- **Stocker**: 0.3 items/second (same as hired stocker)
- Rationale: Player work is equally effective, but requires attention (opportunity cost)

### When to Show Buttons
- "Work as Cashier" button only appears if:
  - Store is OPEN
  - PlayerRole is NONE or already CASHIER
  - There are pending transactions waiting
  
- "Work as Stocker" button only appears if:
  - Store is OPEN
  - PlayerRole is NONE or already STOCKER
  - Backroom has items available to stock
  
- "Stop Working" button only appears if:
  - PlayerRole is not NONE

## Future Enhancements

1. **Exhaustion System**: Player gets slower over time (optional)
2. **Shift System**: Player works designated hours only
3. **Multi-Task Indicator**: Show what player could be doing vs what they're doing
4. **Staff Training**: "Help my cashier learn" - player + cashier = faster
5. **Achievements**: "Cashier 100 items", "Stocker 50 items", etc.

## Summary

This feature transforms the player from pure manager into an active participant during crises. It:
- ✅ Prevents unplayable states (no staff crash)
- ✅ Adds strategic depth (hire vs do it myself)
- ✅ Creates engaging pacing (passive management → active work → passive again)
- ✅ Teaches mechanics (player learns by doing)
- ✅ Maintains agency (always optional)

