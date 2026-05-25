# Plan: Immediate Fresh Handler Order Processing

Currently, fresh handler auto-orders are **queued** into `queuedFreshOrders` and only processed at midnight. The request is to process them immediately when triggered, and show all orders (success + failure) in the end-of-day report. The report UI already renders `autoOrderedFreshItems` and `incompleteOrderedFreshItems` — we just need to populate them throughout the day instead of only at midnight.

### Steps

1. **Rewrite `attemptFreshHandlerAutoOrder()`** in [`GameEngine.kt`](app/src/main/java/com/example/superstoresimulator/domain/GameEngine.kt) — instead of calling `inventoryManager.queueFreshOrder()`, immediately check affordability and either call `inventoryManager.buyItemCasePacks()` (success path, update `currentDayMetrics.autoOrderedFreshItems` and emit `MoneyChanged`/`InventoryUpdated`) or log an `IncompleteOrderLineItem` and append to `incompleteFreshOrders` (failure path). Replace the `alreadyQueued` set with a check against `currentDayMetrics.autoOrderedFreshItems + incompleteOrderedFreshItems` to prevent re-attempting the same item each tick.

2. **Remove `processQueuedFreshOrders()`** from [`GameEngine.kt`](app/src/main/java/com/example/superstoresimulator/domain/GameEngine.kt) — delete this private function and its call at midnight in `tick()` (lines 259–261). Orders are now fully handled inside `attemptFreshHandlerAutoOrder()`.

3. **Remove `queuedFreshOrders` and `FreshOrderRequest`** from [`GameStateData.kt`](app/src/main/java/com/example/superstoresimulator/domain/GameStateData.kt) — delete the `FreshOrderRequest` data class and `queuedFreshOrders` field from `GameState` since nothing queues to it anymore.

4. **Remove `queueFreshOrder()`** from [`InventoryManager.kt`](app/src/main/java/com/example/superstoresimulator/domain/inventory/InventoryManager.kt) — this helper only existed to append to the now-deleted queue.

5. **Clean up `DayManager.rollOverDay()`** in [`DayManager.kt`](app/src/main/java/com/example/superstoresimulator/domain/metrics/DayManager.kt) — remove the unreachable dead-code block (lines 73–85) that looped over `queuedFreshOrders` and remove the stale `queuedFreshOrders = emptyList()` from the returned state.

6. **Add/update tests** — write a test verifying that when `attemptFreshHandlerAutoOrder()` fires, inventory is updated and money is deducted immediately, and the accumulator's `autoOrderedFreshItems` is populated before midnight; also test the failure path populates `incompleteOrderedFreshItems` on the same tick.

### Further Considerations

1. **Re-attempt on the following day**: Currently failed orders persist in `incompleteFreshOrders` across days. After this change, the "already-attempted" check resets with `currentDayMetrics` at each midnight rollover — so a previously-failed fresh item will be retried next day automatically (if funds are now available). Is this the desired behavior, or should failed items wait for manual re-order only?
2. **`GameStateSerializer` is unaffected**: `queuedFreshOrders` is not currently serialized, so removing it requires no serializer changes. `incompleteFreshOrders` is also not serialized (pre-existing gap) — worth considering whether to add it.

