# Test Suite Analysis — Superstore Simulator
**Scope**: All 16 test files under `app/src/test/`
**Date**: April 7, 2026
**Purpose**: Comprehensive audit of every test for vacuousness, triviality, strength of assertion, and cross-file redundancy. No code was changed.

---

## Definitions Used in This Document

| Term | Definition |
|------|-----------|
| **Vacuous** | The test always passes regardless of what the implementation does. The assertion is either `assertTrue(true)`, comparing a value to itself after an operation, or placed inside a conditional branch that is provably never reached. |
| **Conditionally Vacuous** | Meaningful assertions exist but are guarded by a runtime `if` check that can legitimately evaluate to `false`, in which case no assertion runs and the test trivially passes. Common pattern: `if (state.pendingRefunds.isNotEmpty()) { ... }` with a random refund chance. |
| **Trivial** | The test passes by construction — it exercises only Kotlin/JVM language features (data class `copy()`, non-null types, arithmetic) rather than application logic. It would still pass even if all application code were deleted and replaced with stubs. |
| **Weak assertion** | The assertion allows a wide range of outputs (e.g. `>= 0`, `> initialValue`) when a concrete expected value is derivable, meaning many incorrect implementations would pass. |
| **Redundant** | An equivalent test already exists in another file, providing no additional coverage. |
| **Sound** | The test has a meaningful, concrete assertion that would fail if the implementation were wrong. |

---

## File-by-File Analysis

---

### 1. `ExampleUnitTest.kt`

**Total tests: 1**

| Test | Classification | Explanation |
|------|---------------|-------------|
| `addition_isCorrect` | **Trivial** | Android Studio boilerplate. Asserts `2 + 2 == 4`. Tests the JVM, not application logic. Should be deleted. |

---

### 2. `MoneyTest.kt`

**Total tests: 21**

#### Sound Tests
| Test | Notes |
|------|-------|
| `testMoneyFromDollars` | Tests the cents-conversion arithmetic (`10.50 → 1050`). |
| `testMoneyToString` | Tests formatting logic for `$0.00`, `$1.00`, `$10.50`, `$123.45`. |
| `testMoneyMultiplicationByDouble` | Tests rounding behaviour (`$10.00 × 1.5 = 1500¢`). |
| `testMoneyCentsAccuracy` | Important — verifies floating-point input `9.99` is stored as exactly `999` cents and `0.01` → `1` cent. |
| `testMoneySubtractionBelowZero` | Tests that subtraction producing a negative result stores `-50` correctly. |
| `testMoneyRealWorldScenario` | Simulates a full transaction (subtotal, tax, total) with concrete expected values. |
| `testMoneyBulkOperations` | 100 additions accumulate to exactly `$5000.00`. |
| `testMoneyDivisionByScale` | Documents the workaround pattern for division (`toDouble() / 4`). |

#### Trivial Tests
| Test | Classification | Explanation |
|------|---------------|-------------|
| `testMoneyCreation` | **Trivial** | Creates `Money(100)`, asserts `cents == 100`. Tests data class storage, not logic. |
| `testMoneyFromCents` | **Trivial** | `Money.fromCents(1000)` → `cents == 1000`. Factory method that stores the argument unchanged. |
| `testMoneyAddition` | **Trivial** | `100 + 50 = 150`. Operator arithmetic. |
| `testMoneySubtraction` | **Trivial** | `100 - 30 = 70`. |
| `testMoneyNegation` | **Trivial** | `-Money(100) = -100`. |
| `testMoneyMultiplicationByInt` | **Trivial** | `100 * 5 = 500`. |
| `testMoneyMultiplicationByLong` | **Trivial** | `100 * 10L = 1000`. |
| `testMoneyChaining` | **Trivial** | `100 + 50 + 25 = 175`. Pure arithmetic. |
| `testMoneyToDouble` | **Trivial** | Verifies `cents / 100.0` conversion for several values. |
| `testMoneyZeroConstant` | **Trivial** | Asserts `Money.ZERO.cents == 0`. Tests a declared constant. |
| `testMoneyPositiveAndNegative` | **Trivial** | `Money(100) + Money(-100) == 0`. Cancellation of opposites. |
| `testMoneyLargeAmounts` | **Trivial** | Creates `$1,000,000`, doubles it, checks no overflow. Tests Long capacity, not custom logic. |
| `testMoneyEquality` | **Trivial** | Asserts `==` and `!=`. Tests the `equals` generated for a value class. |
| `testMoneyRefundScenario` | **Trivial** (borderline) | The arithmetic is performed in the test itself (`100 × 0.10 = 10`, `100 - 10 = 90`). The test essentially asserts that `Money` arithmetic equals what we computed — the logic is in the test, not the subject under test. |
| `testMoneyComparison` | **Trivial** | Tests `compareTo` for equal, greater, and less cases. Exercises the Comparable contract. |

#### Weak Assertion
| Test | Issue |
|------|-------|
| `testMoneyFormattingEdgeCases` | The negative-money assertion is `negMoney.contains("-") \|\| negMoney.startsWith("$")`. Almost any non-null string satisfies this (it accepts both `"-$1.00"` and `"$-1.00"` and `"$1.00"` and `"$-100"` etc.). It cannot distinguish a correctly formatted value from a random string. |

---

### 3. `InventoryStateTest.kt`

**Total tests: 16**

`InventoryState` is a plain Kotlin data class with two `Int` fields (`shelfStock`, `backroomStock`) and no methods. Almost all tests in this file test Kotlin language semantics rather than application logic.

#### Vacuous Test
| Test | Classification | Explanation |
|------|---------------|-------------|
| `testStockingFromEmptyBackroom` | **Vacuous** | Sets `backroomStock = 0`, then does `if (inventory.backroomStock > 0) { fail(...) }`. The condition is always `false` (by construction), so `fail()` never runs. The test body has no effect; it trivially passes. |

#### Trivial Tests (the majority of this file)
| Test | Explanation |
|------|-------------|
| `testInventoryCreation` | Constructs object, asserts fields equal constructor arguments. Tests data class storage. |
| `testInventoryDefaults` | Same as above with zeros. |
| `testInventoryCopy` | Tests Kotlin's generated `copy()` function. This is a language feature, not business logic. |
| `testTotalStock` | Asserts `10 + 20 == 30`. The addition is computed in the test, not by any application code. |
| `testEmptyShelf` | Constructs with `shelfStock = 0`, asserts `shelfStock == 0` and `backroomStock > 0`. |
| `testEmptyBackroom` | Symmetric to above. |
| `testEmptyInventory` | Checks both fields are `0` after construction with `0`. |
| `testLargeStock` | Constructs with large values, asserts they were stored. |
| `testStockingFlow` | Performs `copy(shelfStock = s+1, backroomStock = b-1)`, checks result. Tests `copy()`. |
| `testConsumingStock` | Same — manual `copy()` arithmetic. |
| `testReorderingFlow` | Same. |
| `testShelfStockCannotGoNegative` | Asserts that `InventoryState` allows negative shelf stock after a manual `copy()`. The comment says GameEngine prevents this. This tests the data class has no validation — correct observation, but it doesn't test the guard that actually matters (GameEngine). |
| `testInitialStoreInventory` | Duplicate of `testInventoryCreation` with the same values. |
| `testComplexStockingSequence` | Multi-step arithmetic using `copy()`. Every intermediate assertion is just checking the result of explicit arithmetic in the test itself. |
| `testMultipleInventoryItems` | Creates three independent objects, asserts `shelfStock + backroomStock` equals constants entered in the constructor. |
| `testImmutability` | Identical to `testInventoryCopy` — tests that Kotlin data class `.copy()` does not mutate the original. |

**Summary**: This entire file essentially tests the Kotlin `data class` feature. Since `InventoryState` has no methods, every test in it should either be deleted or replaced with tests on `GameEngine` that exercise the same state transitions through the actual business logic path.

---

### 4. `GameEngineTest.kt`

**Total tests: 22**
Uses an empty-inventory `FakeItemDao` (no items). This means inventory-dependent paths (transactions, stocking) are structurally unreachable in every test in this file.

#### Sound Tests
| Test | Notes |
|------|-------|
| `testGameEngineInitialization` | Checks name, money, time, storeState on a fresh engine. |
| `testGameEngineStateImmutable` | Verifies that a snapshot captured before `updateStoreName()` is not mutated after the update. |
| `testGameTickProgressesTime` | `tick(1000)` then asserts `newTime > initialTime`. |
| `testToggleTimePaused` | Toggle twice, verify flag flips correctly. |
| `testGameSpeedMultiplier` | Compares time advancement at 1× vs 4×; asserts 4× advances more than 2× the 1× amount. |
| `testStoreStateTransitions` | Queries `storeConfig.isOpen(GameTime(360))`, verifies store is open at 6 AM. |
| `testMultipleTickCalls` | Three snapshots, asserts monotonic non-decreasing time. |
| `testStoreConfigPreservation` | After tick, open/close time fields unchanged. |
| `testCurrentStateMethod` | Verifies `currentState()` reflects latest mutation. |

#### Trivial Tests
| Test | Explanation |
|------|-------------|
| `testStoreNameUpdate` | Simple setter test: `updateStoreName("X")`, assert `storeName == "X"`. |
| `testSetGameSpeed` | Setter: `setGameSpeed(2.0f)`, assert `gameSpeedMultiplier == 2.0f`. |
| `testInventoryInitialization` | With no items, `inventory.isEmpty()`. Follows trivially from construction. |
| `testStartTransaction` | With no inventory, `transactionActive == false`. Follows trivially from construction. |
| `testNoTransactionActiveInitially` | Duplicate of `testStartTransaction`. |
| `testTotalTransactionsCompletedInitially` | Asserts initial count is 0. Checks a default field value. |
| `testSalesHistoryEmpty` | Asserts `salesHistory.isEmpty()` initially. Checks a default field value. |
| `testPendingRefundsEmpty` | Asserts `pendingRefunds.isEmpty()` initially. Checks a default field value. |
| `testRefundIdCounter` | Asserts `nextRefundId == 1` initially. Checks a default field value. |
| `testLongGameSession` | 900 × `tick(16)`, asserts `time > 0`. Given time starts at 0 and each tick advances it, `> 0` is trivially satisfied. This is a "does not crash" test with no meaningful assertion. |

#### Weak Assertion / Near-Trivial Tests
| Test | Issue |
|------|-------|
| `testGameStateConsistency` | After `tick(16)`, asserts `assertNotNull` on six fields. In Kotlin, non-nullable types (`GameState`, `Money`, `GameTime`, etc.) can never be null — the compiler guarantees this. The only assertion that could ever fail is for a nullable type, and none of these are. |
| `testGameStateStructure` | Same pattern as above — `assertNotNull` on non-nullable fields. |

**Structural Note**: Because this engine has no items, `testStartTransaction` and `testNoTransactionActiveInitially` are identical in effect (both test that `transactionActive == false` on a fresh empty engine) and `testInventoryInitialization` is trivially true by construction.

---

### 5. `GameEngineAdvancedTest.kt`

**Total tests: 31**
Uses a 3-item `FakeItemDao`. This is the most feature-complete GameEngine test file.

#### Vacuous Tests
| Test | Classification | Explanation |
|------|---------------|-------------|
| `testCompleteTransaction` | **Vacuous** | Rings up all items and then asserts `assertTrue("Operation should complete without error", true)`. `assertTrue(true)` always passes regardless of what the code does. If `ringUpItem` throws an exception it would fail via the exception, but the assertion itself provides zero checking. |
| `testProcessRefundLine` | **Vacuous** | Ends with `assertTrue("Operation should complete without error", true)`. Same issue — only exception would catch a bug. |
| `testUpgradeEntity` | **Vacuous** | After hiring and upgrading, asserts `assertTrue("Operation should complete without error", true)`. The entire assertion body is this line. No property is checked after the upgrade. |
| `testTickWithStocker` | **Vacuous** | Runs 10 ticks with a stocker, then `assertTrue("Engine should tick without error", true)`. |
| `testTickWithCashierProcessesTransactions` | **Near-Vacuous** | Asserts `totalTransactionsCompleted >= 0`. Any non-negative integer satisfies this, including the initial value of 0. Whether or not transactions occurred, this passes. |

#### Conditionally Vacuous Tests
| Test | Explanation |
|------|-------------|
| `testProcessRefund` | All meaningful assertions (`assertFalse`, `assertTrue(money < moneyBefore)`) are inside `if (stateAfterTx.pendingRefunds.isNotEmpty())`. The internal `TransactionEngine` defaults to 10% refund chance with no fixed seed, so this block may not execute. If no refund is generated the test silently passes with no assertions having run. |

#### Sound Tests
| Test | Notes |
|------|-------|
| `testGameEngineInitialization` | Checks non-null, inventory non-empty, time/state fields present. |
| `testInventoryInitialStock` | Verifies every item starts with `shelfStock = 10, backroomStock = 10`. |
| `testGetDbItem` | `getDbItem(1)` returns item with correct name. |
| `testGetDbItemNotFound` | `getDbItem(999)` returns null. |
| `testStockItemFromBackroom` | Shelf +1, backroom -1 after direct call. |
| `testStockItemWithNoBackroomStock` | After 50 drain calls, values non-negative. |
| `testBuyItemToBackroom` | Money decreased, backroom increased with sufficient funds. |
| `testBuyItemCasePacksSingle` | Backroom +6 (casePack size), money decreased. |
| `testBuyItemCasePacksMultiple` | 3 case packs, backroom increased. |
| `testBuyItemCasePacksInsufficientFunds` | With 0 money, backroom does not increase. |
| `testStartTransaction` | With inventory, transaction auto-starts. |
| `testRingUpItem` | `rungQty` increases after `ringUpItem(itemId)`. |
| `testRingUpItemRandom` | `ringUpItem()` (no ID) increases total rung quantity. |
| `testHireEntity` | Money decreases, registry count +1. |
| `testFireEntity` | Registry count -1 after fire. |
| `testToggleTimePaused` | Flag toggles correctly. |
| `testSetGameSpeedVariations` | Tests all four speed values (1×, 2×, 4×, 8×). |
| `testStateRemainsConsistent` | After mixed operations, inventory non-negative and fields non-null. |
| `testMoneyNeverNegative` | 100 hire-and-buy cycles, money stays ≥ 0. |
| `testInventoryNeverNegative` | 50 ring-up cycles, inventory stays ≥ 0. |
| `testCurrentStateReturnsLatestState` | Store names differ before/after update, latest reflects change. |

#### Trivial / Weak Tests
| Test | Issue |
|------|-------|
| `testHireEntityInsufficientFunds` | With 0 money, hire is rejected; asserts `money.cents >= 0`. The guard clause correctly prevents hiring, but the assertion `>= 0` is satisfied by any non-negative balance and cannot detect a case where the money was incorrectly decreased to some other non-negative value. |
| `testUpdateStoreName` | Simple setter test. |
| `testSetGameSpeed` | Simple setter test (duplicate of the one in `GameEngineTest`). |

---

### 6. `CashierTransactionTest.kt`

**Total tests: 9**

#### Sound Tests
| Test | Notes |
|------|-------|
| `testCashierProcessesTransaction` | Hires cashier, completes transaction, verifies `finalMoney > moneyAfterHire`. |
| `testTransactionClosesAfterCompletion` | `transactionActive == false` after completing all lines. |
| `testCannotStartTransactionAfterStoreClosed` | Verifies `startTransaction()` is rejected when store is CLOSED. |
| `testTransactionCounterIncrementsOnCompletion` | `totalTransactionsCompleted` increases after completion. |

#### Mislabeled / Weak Tests
| Test | Issue |
|------|-------|
| `testFastCashierProcessesFaster` | The name implies a speed comparison, but no speed is measured. The test actually verifies the upgrade path (CASHIER → FAST_CASHIER) and that `FAST_CASHIER.cost > CASHIER.cost`. Both are sound assertions, but the test name misrepresents what is tested. |
| `testCashierRequiredForTransactionProcessing` | The name implies that a cashier is *required*, but the test rings up an item without any cashier and only asserts `money.cents >= 0` and `money >= moneyBeforeRingUp`. The first is trivially true. The second would pass as long as ring-up does not decrease money. Neither assertion verifies that a cashier is required. **Mislabeled**. |
| `testCanStartNewTransactionAfterCompletion` | The name implies a new transaction is started, but the test only checks post-completion invariants (`transactionActive == false`, `salesHistory.isNotEmpty()`, `totalTransactionsCompleted > 0`). A new transaction is never started or asserted. **Mislabeled**. |
| `testTransactionProcessingRequiresOpenStore` | Rings up an item in the auto-started transaction (which began before store open), asserts `money >= moneyWithClosedStore`. Since ring-up only adds money, this is trivially true and doesn't test whether the store being closed prevents transactions. **Mislabeled**. |

---

### 7. `StockRandomItemFromBackroomTest.kt`

**Total tests: 10**

This is one of the strongest test files in the codebase. All 10 tests have meaningful, concrete assertions.

| Test | Classification | Notes |
|------|---------------|-------|
| `testStockItemFromBackroomIncreasesShelf` | Sound | Shelf +1, backroom -1. Exact values. |
| `testStockItemFromBackroomWithNoBackroomStock` | Sound | Drains to 0, verifies no-op on empty backroom. |
| `testStockItemFromBackroomMultipleItems` | Sound | Two items stocked independently, both increase correctly. |
| `testStockItemFromBackroomInventoryRemains` | Sound | Conservation law: total shelf+backroom unchanged. |
| `testTickWithStockerTriggersStocking` | Sound | 20 ticks with stocker; shelf increased, backroom decreased. |
| `testMultipleStockersStockFaster` | Sound | 1 stocker vs 2 stockers; second phase stocks at least as many. |
| `testStockerPrefersItemsWithLowestShelf` | Sound | Manipulates initial state, verifies preference ordering. |
| `testStockerStopsWhenNoBackroomStock` | Sound | All backrooms drained first; shelves unchanged during subsequent ticks. |
| `testNoStockingWithoutStocker` | Sound | No stocker hired; backrooms unchanged after 50 ticks. |
| `testStockingWithInventoryConsistency` | Sound | Conservation law: total inventory unchanged during stocking-only ticks. |

---

### 8. `UpgradeEntityTest.kt`

**Total tests: 20**

This is the highest-quality test file in the codebase. The file's own comments explicitly document the reasoning for non-vacuousness in section 4 ("MAX-LEVEL / NO FURTHER UPGRADE"). All money assertions use concrete expected values derived from an independently traced balance rather than comparing a value to itself.

| Test | Classification | Notes |
|------|---------------|-------|
| `testCashierUpgradesToFastCashier` | Sound | Full balance trace: 20,000 → −1,500 → −10,000 = 8,500. Both entity and money checked. |
| `testStockerUpgradesToFastStocker` | Sound | Same pattern for stocker. |
| `testUpgradeDeductsExactCashierCost` | Sound | Expected balance computed from `EntityDef.cost.cents` constants, not literals. |
| `testUpgradeDeductsExactStockerCost` | Sound | Same. |
| `testUpgradeBlockedWhenBalanceIsZero` | Sound | Exact balance 0 after hire; entity and balance both checked post-attempt. |
| `testUpgradeBlockedWhenOneCentShort` | Sound | Off-by-one boundary (9,999¢ when upgrade costs 10,000¢). |
| `testUpgradeSucceedsWithExactlyEnoughMoney` | Sound | Exact boundary: balance drains to 0 after upgrade. |
| `testUpgradingMaxLevelCashierIsNoOp` | Sound | Balance must be **exactly 18,500¢** — not just unchanged. Catches any spurious deduction. |
| `testUpgradingMaxLevelStockerIsNoOp` | Sound | Same. |
| `testCustomerServiceRepHasNoUpgradePath` | Sound | CSR has no upgrade from the start; balance exactly 5,000¢ post-attempt. |
| `testUpgradePreservesEntityName` | Sound | Name is non-blank before and identical after. |
| `testUpgradePreservesEntityTrait` | Sound | Trait is a valid `EntityTrait` value before and identical after. |
| `testUpgradePreservesEntityId` | Sound | ID field unchanged; getById still resolves to upgraded entity. |
| `testUpgradeDoesNotChangeRegistryCount` | Sound | Two entities hired; count remains 2 after upgrading one. |
| `testUpgradeOnlyAffectsTargetedEntity` | Sound | Three entities; only the targeted one changes definition. |
| `testUpgradingOneEntityDeductsExactBalanceForThatEntityOnly` | Sound | Full balance trace through 3 hires + 1 upgrade: exactly 85,500¢. |
| `testUpgradeNonExistentEntityThrows` | Sound | `NoSuchElementException` expected. |
| `testUpgradeFiredEntityThrows` | Sound | Fire then upgrade; `NoSuchElementException` expected. |
| `testFullCashierUpgradeSequence` | Sound | Step-by-step with concrete balance at each step; max-level no-op verified. |
| `testFullStockerUpgradeSequence` | Sound | Same for stocker. |

---

### 9. `TransactionTest.kt`

**Total tests: 17**

`Transaction` and `TransactionLine` are data classes. Many tests in this file are construction tests or arithmetic tests where all values are provided by the test itself.

#### Sound Tests
| Test | Notes |
|------|-------|
| `testTransactionLineConstructor` | Tests the convenience constructor's computed `rungQty = quantity` and `lineTotal = quantity × unitPrice`. |
| `testComplexTransaction` | Multi-line subtotal, cross-checked against an independently computed total. |
| `testHighValueTransaction` | High-value lines, checks computed subtotal and total. |
| `testSingleItemTransaction` | `tax.cents > 0` after applying tax rate to a non-zero subtotal. |

#### Trivial Tests
| Test | Explanation |
|------|-------------|
| `testTransactionCreation` | Constructs `Transaction` with explicit values, asserts they were stored. Tests data class storage. |
| `testDefaultTransaction` | Asserts default `Transaction()` has `id=0`, empty lines, zero money. Checks default values. |
| `testTransactionLineCreation` | Same pattern — constructs `TransactionLine`, asserts stored values. |
| `testMultipleTransactionLines` | Creates 3 lines and computes `subtotal` in the test using `fold`. The assert `assertEquals(Money.fromDollars(50.50), subtotal)` checks arithmetic the test itself performed. |
| `testTransactionTaxCalculation` | `Money.fromDollars(100.0 * 0.0825)` is computed in the test and asserted to equal `$8.25`. This tests `Money.fromDollars` with a pre-computed value, not any Transaction logic. |
| `testTransactionTotalWithTax` | `$50 + $4.125 = $54.125`. Tests Money addition with manually specified addends. |
| `testTransactionLineProgress` | Manually copies `rungQty` incrementally via `copy()`, asserts the copied values. Tests data class `copy()`. |
| `testTransactionCompletion` | Sets `completedAt = Instant.now()` via `copy()`, asserts it is non-null. Tests that `copy()` stored the value. |
| `testTransactionWithZeroTax` | `total.cents >= subtotal.cents` — trivially true because adding any non-negative tax to a subtotal can only increase it. |
| `testEmptyTransaction` | Constructs `Transaction` with explicit zeros, asserts they were stored. |
| `testTransactionImmutability` | Tests that `original.copy(id=2)` gives `id=2` and original retains `id=1`. Tests Kotlin `data class` copy semantics. |
| `testTransactionLinePartialRingUp` | Manually copies `rungQty = 3`, asserts `rungQty == 3` and `quantity - rungQty == 2`. Tests arithmetic in the test. |
| `testMultipleTransactions` | Creates 5 transactions with explicit positive values, asserts `totalEarned.cents > 0`. Trivially true since the test itself provides the positive values. |

---

### 10. `TransactionEngineTest.kt`

**Total tests: 35**

This file uses `Random(42)` (fixed seed) for most tests, which makes most outcome-dependent tests deterministic. However, the refund tests rely on a 10% chance, and with this seed the first transaction may or may not generate a refund — making several tests conditionally vacuous.

#### Conditionally Vacuous Tests
These tests have their only meaningful assertions inside `if (state.pendingRefunds.isNotEmpty())`. With a 10% refund chance and `Random(42)`, this block may or may not execute. If no refund is generated, the test body has no assertions and silently passes.

| Test | Risk |
|------|------|
| `testProcessRefundWithValidRefundId` | All assertions (money decrease, refund removal) inside `if`. No fallback assertion. |
| `testProcessRefundDecreasesTotalTaxCollected` | All assertions inside `if`. |
| `testProcessRefundAddsToSalesHistory` | All assertions inside `if`. |
| `testProcessRefundLineWithValidParameters` | Assertions inside `if`. Final assert is `assertTrue(state.pendingRefunds.size > 0)` — equivalent to asserting the empty-refund branch wasn't reached, which is tautological if the outer `if` condition was already true. |
| `testProcessRefundLineIncreasesShelfStock` | Assertion inside `if`. |
| `testProcessRefundLineDecreasesMoneyAndTax` | Inside `if`, and uses `<=` comparisons (allows no change). |
| `testProcessRefundLinePartiallyProcessesRefund` | Doubly conditional: outer `if` on refund existing, inner `if` on `originalQty > 1`. |
| `testProcessRefundLineWithExcessiveQuantityCoercesMax` | Triply conditional. |

#### Sound Tests
| Test | Notes |
|------|-------|
| `testStartNewTransactionCreatesValidTransaction` | `transactionActive == true`, lines non-empty, ID incremented. |
| `testStartNewTransactionHasCorrectLineCount` | Lines in range `1..5`. |
| `testStartNewTransactionCalculatesTaxCorrectly` | Tax within 2 cents of `subtotal × 0.0825`. |
| `testStartNewTransactionCalculatesTotalCorrectly` | `totalEarned == subtotal + tax`. |
| `testStartNewTransactionUsesShuffledItems` | Item IDs are all in `1..10`. |
| `testStartNewTransactionWithEmptyInventoryThrows` | `IllegalStateException` on empty inventory. |
| `testStartNewTransactionQuantitiesInRange` | All quantities in `1..3`. |
| `testStartNewTransactionLinesTotalCalculated` | `lineTotal == unitPrice × quantity` for every line. |
| `testStartNewTransactionRungQtyZero` | All lines start with `rungQty == 0`. |
| `testRingUpSingleItemIncrementsRungQty` | `rungQty` increases by 1. |
| `testRingUpSingleItemConsumesShelfStock` | `shelfStock` decreases by 1. |
| `testRingUpSingleItemWithNoShelfStockMarksLineAsLostToOutOfStock` | Forces OOS — verifies `lostToOutOfStock = true`, `rungQty` unchanged, `shelfStock` not consumed. Key edge case. |
| `testRingUpSingleItemCompleteTransaction` | All items rung; `transactionActive == false` and counter incremented. |
| `testRingUpSingleItemWithInvalidItemIdReturnsUnchanged` | Invalid ID is a no-op. |
| `testRingUpSingleItemIncreasesMoney` | Money increases by exactly `transaction.totalEarned`. |
| `testRingUpSingleItemIncrementsTaxCollected` | `totalTaxCollected` increases by `transaction.tax`. |
| `testRingUpSingleItemAddedToSalesHistory` | History grows by 1. |
| `testRingUpSingleItemAlreadyRungDoesNotRingUp` | Ringing up a fully-rung line does not consume more inventory. |
| `testProcessRefundWithInvalidRefundIdReturnsUnchanged` | Invalid refund ID is a no-op. |
| `testProcessRefundLineWithZeroQuantityReturnsUnchanged` | Zero quantity is a no-op. |
| `testTransactionEngineDoesNotMutateOriginalState` | Calling `startNewTransaction` does not mutate the passed-in state. |
| `testTaxCalculationAccuracy` | New engine with default rate; tax within 2 cents. |
| `testCustomTaxRate` | 10% rate applied correctly. |
| `testMultipleSequentialTransactions` | 3 transactions, money grows monotonically, count ≥ each iteration. |
| `testInventoryDecreasesWithMultipleTransactions` | Total shelf stock decreases after 2 transactions. |

#### Weak / Borderline Tests
| Test | Issue |
|------|-------|
| `testRingUpSingleItemPartialCompletion` | The meaningful assertion (`transactionActive == true` for partial ring-up) is inside `if (firstLine.quantity > 1)`. If the first line has `quantity == 1` (possible with `Random(42)`), no assertion runs. |
| `testStartNewTransactionUnitPriceConsistent` | Asserts all items cost exactly `$9.99`. This is an implementation detail of the placeholder price. If the placeholder price changes, this test breaks — and it only tests implementation detail, not a game rule. |

---

### 11. `TransactionEngineAdvancedTest.kt`

**Total tests: 25**

This file correctly uses `refundChance = 1.0` for all refund-path tests, eliminating the conditional-vacuousness problem present in `TransactionEngineTest`. Most tests here are sound.

#### Dead Code / Logic Bug
| Test | Issue |
|------|-------|
| `testRefundWithMultipleLines` | Contains `while (state.currentTransaction.lines.size < 3 && !state.transactionActive) { state = engine.startNewTransaction(state) }`. After `startNewTransaction`, `transactionActive` becomes `true`, so `!state.transactionActive` becomes `false` and the loop body never re-executes even on the first iteration. Because `startNewTransaction` was already called before the loop, `transactionActive` is `true` when the `while` condition is first evaluated, making the loop entirely dead code. The test then continues with a simple refund-not-null check. This is a logic bug in the test but does not make it vacuous — the post-loop assertions still run. |

#### Sound Tests
| Test | Notes |
|------|-------|
| `testAlwaysGenerateRefundWhenChanceIs100Percent` | Forces refund, checks it exists. Eliminates the conditional-vacuousness problem. |
| `testNeverGenerateRefundWhenChanceIs0Percent` | Forces no refund, checks empty list. |
| `testRefundRequestStructureIsValid` | Validates `id`, `timestamp`, `originalTransactionId`, `lines`, `subtotal`, `tax`. |
| `testRefundLinesHaveCorrectQuantities` | Each refund line has positive `quantity` and positive `unitPrice`. |
| `testMultipleRefundsCanGenerate` | 3 transactions, `pendingRefunds.size >= 1`. |
| `testSingleItemInventory` | Single-item inventory; all lines use `itemId == 1`. |
| `testLargeQuantityRingUp` | 20 ring-ups; stock decreased. |
| `testInventoryBackroomNotConsumed` | Ring-up does not touch backroom. |
| `testCompleteTransactionWithMultipleLines` | Multi-line transaction completes; counter == 1. |
| `testPartialRefundProcessing` | Processes 1 unit; money decreases; refund remains if qty > 1. |
| `testFullRefundThenNewTransaction` | Refund removed after full processing; new transaction can start. |
| `testInventoryReplenishmentAfterRefund` | Shelf stock restored after refund line processed. |
| `testTaxCalculationWithCustomRate` | 10% rate, within 2 cents. |
| `testZeroTaxRate` | Tax == 0 and `total == subtotal`. |
| `testHighTaxRate` | 25% rate, within 2 cents. |
| `testSalesHistoryAccumulatesCorrectly` | 5 transactions; counter == 5 and history non-empty. |
| `testStateConsistencyAfterComplexSequence` | After tx → refund → tx; all invariants hold. |
| `testNoMoneyLeakage` | Money increase exactly equals `transaction.totalEarned`. Concrete comparison. |
| `testRefundHigherThanSale` | Full refund: money after < money after sale. |
| `testProcessRefundLineMultipleTimes` | Process each refund line one unit at a time; refund removed at end. |

#### Weak Assertion
| Test | Issue |
|------|-------|
| `testMoneyAccumulatesAcrossTransactions` | Asserts `state.money > initialMoney` AND `(state.money - initialMoney).cents > 0`. These are equivalent; the second assertion adds nothing. |

---

### 12. `GameTimeTest.kt`

**Total tests: 14**

`GameTime` is a value class wrapping a single `Long` with computed properties. All tests here are testing the correctness of the computed properties.

| Test | Classification | Notes |
|------|---------------|-------|
| `testGameTimeCreation` | **Trivial** | Asserts `GameTime(0)` has `hour=0`, `minute=0`. These are the initial values by definition. |
| `testHourCalculation` | Sound | Tests `totalMinutesElapsed / 60 % 24` at multiple input values. |
| `testMinuteCalculation` | Sound | Tests `totalMinutesElapsed % 60` at multiple input values. |
| `testDayNumberCalculation` | Sound | Tests `totalMinutesElapsed / 1440` at boundary values. |
| `testDayOfWeekCalculation` | Sound | Tests `dayNumber % 7` at boundary values including the Monday wrap. |
| `testWeekNumberCalculation` | Sound | Tests `dayNumber / 7` at several values. |
| `testGetTotalMinutesOfDay` | Sound | Tests `totalMinutesElapsed % 1440`. |
| `testIsOpen` | Sound | Tests boundary logic at open, open–1, close, close+1. |
| `testAddMinutes` | Sound | Tests `GameTime(x).addMinutes(d).totalMinutesElapsed == x + d`. |
| `testAddSeconds` | Sound | Tests integer division: `addSeconds(3600) == 60 minutes`. |
| `testGetFormattedTime` | Sound | Tests string output at Monday 00:00, 06:00, Tuesday 09:05, Friday 21:30. |
| `testGetDayOfWeekName` | Sound | All seven day names. |
| `testDateProgression` | Sound | Loop asserting `dayNumber` and `dayOfWeek` for days 0–6. |
| `testRealisticGameDayFlow` | Sound | Simulates 5 AM → 6 AM → noon → 9 PM, checking `isOpen` at each step. |

---

### 13. `TimeManagerTest.kt`

**Total tests: 16**

Several tests in this file are partially redundant with `GameTimeTest` and `StoreConfigTest`.

#### Sound Tests
| Test | Notes |
|------|-------|
| `testResetTime` | Advances then resets; checks `totalMinutesElapsed == 0` and state CLOSED. |
| `testJumpToTime` | Jump to 6:00; checks totalMinutes == 360. |
| `testJumpToTimeWithMinutes` | Jump to 9:30; checks totalMinutes == 570. |
| `testJumpToTimePastCurrentTime` | From 6 AM, jump to 5 AM (earlier); expects next-day handling at minute 1740. |
| `testAccumulatedTimeRounding` | 100 × 16 ms frames; checks accumulated minutes within ±1. Tests fractional accumulation logic. |
| `testConfigCanBeChanged` | Replaces config; verifies new open boundary. |
| `testSpeedMultiplierAffectsTimeProgression` | `advanceAt4x == advanceAt1x × 4`. Exact ratio. |
| `testTimerDoesNotRegress` | 100 ticks; time is monotonically non-decreasing. |

#### Trivial / Redundant Tests
| Test | Issue |
|------|-------|
| `testInitialTime` | Asserts `totalMinutesElapsed == 0` and state CLOSED on a freshly constructed `TimeManager`. Checks default field values. |
| `testTimeProgression` | Calls `update(1000)`, asserts `totalMinutesElapsed > 0`. A `> 0` assertion on a value that starts at 0 and can only increase is near-trivial. The comment in the test says the expected value is 120 game-minutes but the assertion only checks `> 0`. |
| `testGameSpeedMultiplier` | Sets 2× multiplier, updates, asserts `advanceAt2x > 0`. This does not test that the speed is actually 2×; it only tests that time advanced. **Trivially weak** — time advances at 1× too. |
| `testStoreStateClosed` | Re-implements `StoreConfig.isClosed`/`isOpen`/`isClosing` logic directly in the test body. **Redundant** with `StoreConfigTest`. |
| `testStoreStateOpen` | Same — redundant with `StoreConfigTest`. |
| `testStoreStateClosing` | Same — redundant with `StoreConfigTest`. |
| `testStoreStateTransition` | Table-driven state transitions. Better than the above three, but still redundant with `StoreConfigTest.testMutualExclusivityOfStates` and `testClosingTransitionFlow`. |
| `testDayTransition` | Asserts `GameTime(1380).dayNumber == 0` and `GameTime(1440).dayNumber == 1`. This tests `GameTime`, not `TimeManager`. **Misplaced** (belongs in `GameTimeTest`). |
| `testLongGameSession` | Loops through 7 days of `GameTime` arithmetic. Tests `GameTime`, not `TimeManager`. **Misplaced**. Equivalent to `GameTimeTest.testDateProgression`. |

---

### 14. `StoreConfigTest.kt`

**Total tests: 10**

| Test | Classification | Notes |
|------|---------------|-------|
| `testDefaultConfig` | **Trivial** | Asserts constructor defaults. Tests declared initial values. |
| `testIsOpen` | Sound | Boundary tests at 0, 359, 360, 720, 1259, 1260, 1439. |
| `testIsClosing` | Sound | Boundary tests at 1259, 1260, 1275, 1289, 1290, 1439. |
| `testIsClosed` | Sound | Comprehensive coverage across all three time zones. |
| `testMutualExclusivityOfStates` | **Excellent** | Loops all 1,440 minutes; asserts exactly one of `{isOpen, isClosing, isClosed}` is true. This is the strongest structural test in the time subsystem. |
| `testClosingTransitionFlow` | Sound | Verifies full OPEN → CLOSING → CLOSED transition sequence. |
| `testCustomStoreHours` | Sound | Creates custom config with 8 AM–10 PM; tests boundary at 479, 480, 1319, 1320. |
| `testGameSpeedMultiplier` | **Trivial** | Creates `config.copy(gameSpeedMultiplier = 2.0f)`, asserts the value is 2.0f. Tests `copy()`. |
| `testConfigCopy` | **Trivial** | Tests `data class copy()` semantics — original unchanged, copy has new value. |
| `testClosingProcedureDuration` | Sound | Two configs with 10-min and 60-min closing durations; tests boundaries correctly. |

---

### 15. `ItemUnlockTierTest.kt`

**Total tests: 22**

This is a well-structured pure unit test file for `ItemUnlockTier` and `requiredTierForSection`. Most tests are sound.

| Test | Classification | Notes |
|------|---------------|-------|
| `fromTotalEarned returns TIER_1 at zero` | Sound | Boundary: 0¢ → TIER_1. |
| `fromTotalEarned returns TIER_1 just below TIER_2 threshold` | Sound | Off-by-one: 499,999¢ → TIER_1. |
| `fromTotalEarned returns TIER_2 exactly at its threshold` | Sound | Exact boundary: 500,000¢ → TIER_2. |
| `fromTotalEarned returns TIER_2 just below TIER_3 threshold` | Sound | 1,999,999¢ → TIER_2. |
| `fromTotalEarned returns TIER_3 exactly at its threshold` | Sound | 2,000,000¢ → TIER_3. |
| `fromTotalEarned returns TIER_3 just below TIER_GM threshold` | Sound | 9,999,999¢ → TIER_3. |
| `fromTotalEarned returns TIER_GM exactly at its threshold` | Sound | 10,000,000¢ → TIER_GM. |
| `fromTotalEarned returns TIER_GM well above max threshold` | Sound | 999,999,999¢ → TIER_GM. |
| `nextTier returns correct successor tiers` | Sound | All four tiers' successors including null for TIER_GM. |
| `TIER_1 unlocks at 0 cents` | **Trivial** | Asserts a compile-time constant (`unlockAmount == 0L`). |
| `TIER_2 unlocks at 5000 dollars (500000 cents)` | **Trivial** | Same — asserts constant. |
| `TIER_3 unlocks at 20000 dollars (2000000 cents)` | **Trivial** | Same. |
| `TIER_GM unlocks at 100000 dollars (10000000 cents)` | **Trivial** | Same. These four tests are valuable as documentation but add no falsifiability beyond reading the source. |
| `TIER_1 contains GROCERY SNACKS and DRINKS only` | Sound | Both membership and non-membership of all categories. |
| `TIER_2 adds DAIRY and keeps all TIER_1 sections` | Sound | |
| `TIER_3 adds PRODUCE FROZEN BAKERY to TIER_2 sections` | Sound | |
| `TIER_GM contains every ItemCategory` | Sound | Iterates all entries. |
| `each tier is a strict superset of the previous tier` | Sound | Structural invariant: `higher.unlockedSections.containsAll(lower.unlockedSections)`. |
| `requiredTierForSection maps GROCERY SNACKS DRINKS to TIER_1` | Sound | |
| `requiredTierForSection maps DAIRY to TIER_2` | Sound | |
| `requiredTierForSection maps PRODUCE FROZEN BAKERY to TIER_3` | Sound | |
| `requiredTierForSection maps specialty categories to TIER_GM` | Sound | |
| `requiredTierForSection is consistent with unlockedSections for all categories` | **Excellent** | Loops all categories; asserts `requiredTierForSection(c)` is the earliest tier containing `c`. |

---

### 16. `ProgressionTest.kt`

**Total tests: 16**

This file contains a set of integration tests (using `GameEngine`) plus a set of pure `ItemUnlockTier` tests that are **completely duplicated** from `ItemUnlockTierTest.kt`.

#### Unique / Sound Integration Tests
| Test | Notes |
|------|-------|
| `initial GameState starts at TIER_1` | Engine-level check: `currentState().currentTier == TIER_1`. |
| `initial GameState has zero totalRevenue` | Engine-level check: `totalRevenue == Money.ZERO`. |
| `totalRevenue increases after completing a transaction` | `revenueAfter > revenueBefore`. Concrete inequality. |
| `totalRevenue does not decrease when spending money on inventory` | Verifies that `buyItemToBackroom` does not reduce `totalRevenue`. Important separation-of-concerns test. |
| `tier advances to TIER_2 after crossing 5000 dollar threshold` | High-price item; single transaction crosses 500,000¢; tier advances. |

#### Trivial / Weak Integration Test
| Test | Issue |
|------|-------|
| `tier does not advance until threshold is crossed` | The assertion is inside `if (state.totalRevenue.cents < 500_000L)`. With a 10¢ item, this condition is always true (revenue ≈ 54¢), so the assertion always runs — but the conditional makes the structure look potentially vacuous. The condition should be removed and replaced with `assertTrue(state.totalRevenue.cents < 500_000L)` followed by the tier assertion. |

#### Duplicated Tests (exact duplicates of tests in `ItemUnlockTierTest.kt`)
All of the following replicate tests that exist identically in `ItemUnlockTierTest.kt`, providing zero additional coverage:

| Test in ProgressionTest | Duplicate of |
|------------------------|--------------|
| `fromTotalEarned returns TIER_1 at zero` | `ItemUnlockTierTest` |
| `fromTotalEarned returns TIER_1 just below TIER_2 threshold` | `ItemUnlockTierTest` |
| `fromTotalEarned returns TIER_2 exactly at its threshold` | `ItemUnlockTierTest` |
| `fromTotalEarned returns TIER_2 just below TIER_3 threshold` | `ItemUnlockTierTest` |
| `fromTotalEarned returns TIER_3 exactly at its threshold` | `ItemUnlockTierTest` |
| `fromTotalEarned returns TIER_GM exactly at its threshold` | `ItemUnlockTierTest` |
| `fromTotalEarned returns TIER_GM well above max threshold` | `ItemUnlockTierTest` |
| `TIER_1 contains GROCERY SNACKS and DRINKS only` | `ItemUnlockTierTest` |
| `TIER_2 adds DAIRY to TIER_1 sections` | `ItemUnlockTierTest` |
| `TIER_3 adds PRODUCE FROZEN BAKERY to TIER_2 sections` | `ItemUnlockTierTest` |
| `TIER_GM contains every ItemCategory` | `ItemUnlockTierTest` |
| `requiredTierForSection maps GROCERY SNACKS DRINKS to TIER_1` | `ItemUnlockTierTest` |
| `requiredTierForSection maps DAIRY to TIER_2` | `ItemUnlockTierTest` |
| `requiredTierForSection maps PRODUCE FROZEN BAKERY to TIER_3` | `ItemUnlockTierTest` |
| `requiredTierForSection maps specialty categories to TIER_GM` | `ItemUnlockTierTest` |
| `nextTier returns correct successor tiers` | `ItemUnlockTierTest` |

---

## Cross-Cutting Findings

### Pattern 1 — `assertTrue(true)` Vacuous Assertions
Found in **GameEngineAdvancedTest**: `testCompleteTransaction`, `testProcessRefundLine`, `testUpgradeEntity`, `testTickWithStocker`. These tests exist only to verify that the code does not throw an exception. While "does not crash" is a valid test goal, it should be named accordingly (e.g. `testCompleteTransaction_doesNotThrow`) and ideally augmented with a real assertion.

### Pattern 2 — Conditional Refund Assertions (10% chance)
The majority of refund tests in **TransactionEngineTest** guard their assertions with `if (state.pendingRefunds.isNotEmpty())`. With a 10% refund chance (the default), many test runs silently pass with zero assertions executed. **TransactionEngineAdvancedTest** correctly solves this by using `refundChance = 1.0` for all refund-path tests.

### Pattern 3 — Data Class Copy() Tests in InventoryStateTest
`InventoryStateTest` tests Kotlin's `data class` feature almost exclusively. Since `InventoryState` has no methods, all business logic lives in `GameEngine`. The file should be replaced with `GameEngine`-level tests that exercise the same inventory transitions.

### Pattern 4 — Initial State Cluster (GameEngineTest)
Five consecutive tests (`testNoTransactionActiveInitially`, `testTotalTransactionsCompletedInitially`, `testSalesHistoryEmpty`, `testPendingRefundsEmpty`, `testRefundIdCounter`) each check a single field's initial value. These provide essentially identical coverage to `testGameEngineInitialization` and could all be merged into it.

### Pattern 5 — Cross-File Redundancy
| Duplicate Cluster | Files Involved |
|-------------------|---------------|
| `ItemUnlockTier.fromTotalEarned` threshold tests | `ItemUnlockTierTest` + `ProgressionTest` (7 identical tests) |
| Tier `unlockedSections` membership tests | `ItemUnlockTierTest` + `ProgressionTest` (4 near-identical tests) |
| `requiredTierForSection` mapping tests | `ItemUnlockTierTest` + `ProgressionTest` (4 identical tests) |
| `nextTier` test | `ItemUnlockTierTest` + `ProgressionTest` |
| Store state logic tests | `StoreConfigTest` + `TimeManagerTest` (`testStoreStateClosed/Open/Closing/Transition`) |
| GameTime day progression | `GameTimeTest.testDateProgression` + `TimeManagerTest.testLongGameSession` |
| Game speed setter | `GameEngineTest.testSetGameSpeed` + `GameEngineAdvancedTest.testSetGameSpeed` |
| Time pause toggle | `GameEngineTest.testToggleTimePaused` + `GameEngineAdvancedTest.testToggleTimePaused` |

### Pattern 6 — Mislabeled Tests
| Test | Actual Behavior |
|------|----------------|
| `CashierTransactionTest.testFastCashierProcessesFaster` | Tests upgrade path and cost comparison, not processing speed |
| `CashierTransactionTest.testCashierRequiredForTransactionProcessing` | Tests that ring-up doesn't decrease money; does not verify a cashier is required |
| `CashierTransactionTest.testCanStartNewTransactionAfterCompletion` | Does not start a new transaction; verifies post-completion state fields |
| `CashierTransactionTest.testTransactionProcessingRequiresOpenStore` | Does not verify open-store requirement; only checks money is non-decreasing |

### Pattern 7 — Weak `>= 0` Guard Assertions
Several tests conclude with `assertTrue(money.cents >= 0)` or `assertTrue(inventory.shelfStock >= 0)`. While these test that the implementation does not produce nonsensical negative values, they cannot detect incorrect but technically positive values. For example, if `hireEntity` incorrectly subtracted twice the hire cost, `money.cents >= 0` would still pass as long as enough money remained.

---

## Summary Table

| File | Total Tests | Vacuous | Cond. Vacuous | Trivial | Weak Assertion | Redundant | Sound |
|------|-------------|---------|--------------|---------|----------------|-----------|-------|
| ExampleUnitTest | 1 | 0 | 0 | 1 | 0 | 0 | 0 |
| MoneyTest | 21 | 0 | 0 | 14 | 1 | 0 | 6 |
| InventoryStateTest | 16 | 1 | 0 | 14 | 0 | 0 | 1 |
| GameEngineTest | 22 | 0 | 0 | 10 | 2 | 0 | 10 |
| GameEngineAdvancedTest | 31 | 4 | 1 | 2 | 2 | 2 | 20 |
| CashierTransactionTest | 9 | 0 | 0 | 0 | 4 | 0 | 5 |
| StockRandomItemFromBackroomTest | 10 | 0 | 0 | 0 | 0 | 0 | 10 |
| UpgradeEntityTest | 20 | 0 | 0 | 0 | 0 | 0 | 20 |
| TransactionTest | 17 | 0 | 0 | 13 | 0 | 0 | 4 |
| TransactionEngineTest | 35 | 0 | 8 | 2 | 1 | 0 | 24 |
| TransactionEngineAdvancedTest | 25 | 0 | 0 | 0 | 1 | 0 | 24 |
| GameTimeTest | 14 | 0 | 0 | 1 | 0 | 0 | 13 |
| TimeManagerTest | 16 | 0 | 0 | 3 | 1 | 4 | 8 |
| StoreConfigTest | 10 | 0 | 0 | 3 | 0 | 0 | 7 |
| ItemUnlockTierTest | 22 | 0 | 0 | 4 | 0 | 0 | 18 |
| ProgressionTest | 16 | 0 | 1 | 0 | 0 | 16 | 5 |
| **Totals** | **285** | **5** | **10** | **65** | **12** | **22** | **175** |

*Note: Categories are not mutually exclusive. A test counted as "Redundant" may also be "Trivial". The Redundant count (22) reflects the 16 in ProgressionTest plus 4 in TimeManagerTest plus 2 in GameEngineAdvancedTest.*

---

## Prioritised Recommendations

### High Priority (affects correctness of the test suite)
1. **Fix conditionally-vacuous refund tests** in `TransactionEngineTest`: add `refundChance = 1.0` for the eight refund-path tests, mirroring the approach already used in `TransactionEngineAdvancedTest`.
2. **Replace `assertTrue(true)`** in `GameEngineAdvancedTest` (`testCompleteTransaction`, `testProcessRefundLine`, `testUpgradeEntity`, `testTickWithStocker`) with concrete assertions on the post-operation state.

### Medium Priority (test value)
3. **Delete or rewrite `InventoryStateTest`**: the entire file tests Kotlin's `data class` feature. Replace with `GameEngine`-level inventory tests.
4. **Remove duplicated tier tests from `ProgressionTest`**: the 16 tests duplicated from `ItemUnlockTierTest` add zero coverage. Keep only the integration tests unique to `ProgressionTest`.
5. **Remove duplicate GameTime / StoreConfig tests from `TimeManagerTest`**: `testStoreStateClosed`, `testStoreStateOpen`, `testStoreStateClosing`, `testDayTransition`, `testLongGameSession`.

### Low Priority (cleanup / documentation)
6. **Delete `ExampleUnitTest`**: Android Studio boilerplate with no project value.
7. **Rename mislabeled tests** in `CashierTransactionTest` to accurately describe what they assert.
8. **Merge initial-state tests** in `GameEngineTest` into `testGameEngineInitialization`.
9. **Replace weak `>= 0` assertions** with concrete expected values where derivable (e.g. `GameEngineAdvancedTest.testHireEntityInsufficientFunds` could assert `money == Money(0)` exactly).
10. **Fix the dead `while` loop** in `TransactionEngineAdvancedTest.testRefundWithMultipleLines`.

