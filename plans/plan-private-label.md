# Plan B: Private Label Brand + Loyalty Buildup

## Context

Player at Supercenter scale has ~41% margins on national brands. Private label (store brand) items offer ~55% margin but require sustained multi-analyst R&D investment per product line. Loyalty mechanic makes PL demand grow over time as customers buy more store-brand products.

---

## Program Launch

### Research
```kotlin
"private_label_program" — "Private Label Feasibility Study"
researchCost: 200, prereq: ["store_supercenter"], requiredStoreSize: SUPERCENTER
gateCheck: { s -> s.totalRevenue.cents >= 200_000_000L }  // $2M total revenue
```

### Launch fee + Brand naming
After research: player pays $500K (`Money(50_000_000L)`). On purchase, a dialog prompts for a **brand name** (e.g., "Great Value", "Kirkland", "Market Pantry"). This name is stored in state and used as the prefix for all private label item display names.

- `GameStateData.kt`: `val privateLabelBrandName: String = ""` (empty = program not launched)
- `privateLabelLaunched` replaced by `privateLabelBrandName.isNotEmpty()` — brand name doubles as the launch flag
- `GameEvent.LaunchPrivateLabelProgram(brandName: String)` — carries player's chosen name
- `GameEngine.launchPrivateLabelProgram(brandName)` — guards: research done, not yet launched, money >= $500K. Deducts cost, sets `privateLabelBrandName`

### Brand name in item display

PL items in `items.json` use a generic base name (e.g., `"Cereal"`, `"Ketchup"`). At display time, prefix with the brand:
- `ItemMetadataCache` or UI layer: `"${state.privateLabelBrandName} Cereal"`, `"${state.privateLabelBrandName} Ketchup"`
- Items store the **base** name only — brand is resolved at render time so renaming works retroactively
- Player can rename brand later from settings/PL dashboard (optional, low priority)

---

## Product Lines (~8 lines, ~35 items total)

Each line is a research upgrade under new `PRIVATE_LABEL` category:
```
pl_grocery_basics  — Store Brand pasta, rice, beans        (prereq: prod_breakfast or prod_canned)
pl_condiments      — Store Brand ketchup, mustard, etc.    (prereq: prod_condiments)
pl_dairy           — Store Brand milk, butter, cheese       (prereq: prod_dairy_basics)
pl_snacks          — Store Brand chips, pretzels            (prereq: prod_snack_variety)
pl_frozen          — Store Brand frozen vegs, pizza         (prereq: prod_frozen_basics)
pl_drinks          — Store Brand water, cola, juice         (prereq: prod_soft_drinks)
pl_bakery          — Store Brand bread, rolls               (prereq: prod_bakery)
pl_cleaning        — Store Brand soap, detergent            (prereq: prod_cleaning)
```

Each: researchCost 80–150, prerequisite = corresponding national brand line + `private_label_program` launched.

---

## Private Label Item Properties

| Property | National Brand | Private Label |
|----------|---------------|---------------|
| Unit cost | baseline | ~60% of national |
| Shelf price | baseline | ~90% of national |
| Margin | ~41% | ~55% |
| Purchase weight | baseline | 0.6× baseline (lower initial demand) |
| Substitution group | shared | **same group as national equivalent** |

Items added to `items.json` at id range `item_200+` with `"isPrivateLabel": true`.

### Substitution group rule

**Every PL item MUST share the `substitutionGroup` of its national brand counterpart.** The existing substitution system suppresses purchase weight for items in the same group once one is already in the basket — a customer who picks up Store Brand Ketchup will not also pick up Name Brand Ketchup (and vice versa).

Example from `items.json`:
```json
{ "id": "item_007", "name": "Peanut Butter", "substitutionGroup": "sub_nut_butter", ... }
{ "id": "item_207", "name": "Peanut Butter",  "substitutionGroup": "sub_nut_butter", "isPrivateLabel": true, ... }
```

For items that currently have **no** substitution group (standalone items), adding a PL variant means both items need a shared group. Either:
- Add a `substitutionGroup` to the national brand item when the PL counterpart is created, or
- Pre-assign substitution groups to all items that will get PL variants

Items without a PL counterpart keep their existing (or null) substitution group — no change needed.

---

## Loyalty Buildup Mechanic

**Private Label Loyalty score per product line.**

### How it works
- Each PL product line has a `loyalty: Float` (0.0 → 1.0) tracked in state
- Every time a customer buys a PL item, loyalty for that line increases slightly
- Loyalty acts as a **multiplier on purchaseWeight** for PL items in that line:
  - At loyalty 0.0: effective weight = base × 0.6 (initial low demand)
  - At loyalty 1.0: effective weight = base × 1.2 (exceeds national brand demand)
  - Formula: `effectiveWeight = basePurchaseWeight * (0.6 + 0.6 * loyalty)`
- Loyalty grows slowly: `+0.001` per unit sold, capped at 1.0
- Loyalty decays if PL items are out of stock: `-0.002` per day with empty shelves

### State

`GameStateData.kt`:
```kotlin
val privateLabelBrandName: String = ""  // empty = not launched; non-empty = brand name chosen + program active
val privateLabelLoyalty: Map<String, Float> = emptyMap()  // lineId → loyalty (0.0–1.0)
```

### Integration points

- **TransactionEngine** (or wherever basket items are consumed): after selling a PL item, increment loyalty for its line
- **TrafficProcessor / basket building**: when selecting items for customer basket, apply loyalty multiplier to PL item purchase weights
- **SpoilageManager / DayManager**: on day rollover, decay loyalty for lines with empty PL shelves

---

## UI

- **Launch dialog**: text input for brand name + confirmation ("Launch [BrandName] for $500,000?"). Validates non-empty, trims whitespace.
- **Research screen**: new `PRIVATE_LABEL` category with program research + per-line R&D
- **Inventory screen**: PL items show brand name badge (e.g., "[BrandName] Cereal")
- **Private Label dashboard** (new section or screen): brand name header, loyalty meters per line, revenue breakdown PL vs national, margin comparison. Optional: rename brand button.
- **Item detail**: "[BrandName]" label, margin highlight

## Lateral Changes

- **ResearchCategory** enum: add `PRIVATE_LABEL("Private Label")`
- **DailyMetrics**: add `privateLabelRevenue: Money = Money.ZERO`, `privateLabelUnitsSold: Int = 0`
- **End-of-day report**: PL revenue line item
- **ItemMetadata**: add `isPrivateLabel: Boolean = false`, `privateLabelLine: String? = null`

---

## File Summary

| File | Changes |
|------|---------|
| `items.json` | ~35 new PL items (id 200+) |
| `ItemDataLoader.kt` | Parse `isPrivateLabel`, `privateLabelLine` |
| `ItemMetadata.kt` | Add `isPrivateLabel`, `privateLabelLine` |
| `ItemMetadataCache.kt` | Pass through new fields |
| `GameStateData.kt` | `privateLabelBrandName`, `privateLabelLoyalty` |
| `ResearchUpgradeRegistry.kt` | ~9 new upgrades (program + 8 lines) |
| `ResearchCategory` enum | Add `PRIVATE_LABEL` |
| `ResearchGates.kt` | Add PL program constant |
| `GameEngine.kt` | `launchPrivateLabelProgram(brandName)`, loyalty tick |
| `GameEvent.kt` | New events |
| `TransactionEngine.kt` | Increment loyalty on PL sale |
| `TrafficProcessor.kt` | Apply loyalty multiplier to PL purchase weights |
| `DailyMetrics.kt` | PL revenue/units tracking |
| `DayManager.kt` | Loyalty decay for OOS lines |
| UI: research screen, inventory badges, PL dashboard | New category, badges, dashboard |

## Verification

- Unit: PL items load with correct `isPrivateLabel` flag and margins
- Unit: loyalty increments on PL sale, decays on OOS day
- Unit: effective purchase weight scales with loyalty
- Emulator: full flow — research program → launch ($500K) → research a line → items appear → sell → watch loyalty grow
- Verify PL revenue shows in EOD report
