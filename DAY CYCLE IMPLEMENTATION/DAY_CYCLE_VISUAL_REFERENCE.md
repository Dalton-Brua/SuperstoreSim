# Day Cycle System - Visual Reference & State Diagrams

## 1. TIME PROGRESSION VISUALIZATION

### Daily Timeline
```
┌─────────────────────────────────────────────────────────────────┐
│                          ONE GAME DAY                           │
└─────────────────────────────────────────────────────────────────┘

6:00 AM ━━━┳━━━ STORE OPENS (OPENING PROCEDURES)
           ┃   [Staff arrives, stock checked]
           ┃   LOW TRAFFIC: 0-3 customers/hour
           ┃
8:00 AM ━━━╋━━━ MORNING RUSH BEGINS
           ┃   MEDIUM-HIGH: 5-8 customers/hour
           ┃   ⚠️  CRITICAL: Need 2+ cashiers
           ┃
10:00 AM ━━╋━━━ Mid-morning lull
           ┃   MEDIUM: 2-4 customers/hour
           ┃   Good time to: Restock, Order supplies
           ┃
12:00 PM ━━╋━━━ LUNCH RUSH (30 min surge)
           ┃   HIGH: 8-12 customers/hour
           ┃   ⚠️  CRITICAL: Peak stress time
           ┃
2:00 PM ━━━╋━━━ Afternoon quiet
           ┃   LOW-MEDIUM: 1-3 customers/hour
           ┃   Can: Manually restock, take break
           ┃
3:00 PM ━━━╋━━━ Typical delivery time
           ┃   📦 Orders from morning arrive
           ┃   ✅ Stock items in backroom
           ┃
5:00 PM ━━━╋━━━ EVENING RUSH (CRITICAL)
           ┃   VERY HIGH: 8-15 customers/hour
           ┃   ⚠️  PEAK STRESS: Need full staff
           ┃   👥 Queues form if understaffed
           ┃
7:00 PM ━━━╋━━━ Rush subsides
           ┃   MEDIUM: 3-6 customers/hour
           ┃
8:00 PM ━━━╋━━━ Wind down
           ┃   LOW: 1-2 customers/hour
           ┃   Staff prepare for close
           ┃
9:00 PM ━━━┻━━━ STORE CLOSES
               [Closing procedures: cleanup, count]
               
NIGHT (9 PM - 6 AM):
- 🌙 Overnight stocking (stockers only)
- 📊 Daily report generated
- 💤 Staff off-duty
- 📦 Can still receive early deliveries
```

---

## 2. STORE STATE MACHINE

```
                    ┌──────────────┐
                    │   CLOSED     │
                    │  (Midnight)  │
                    └──────┬───────┘
                           │
                    (Time reaches 6 AM)
                           │
                    ┌──────▼───────┐
                    │  OPENING     │
                    │ (5 min setup) │
                    └──────┬───────┘
                           │
                  (Setup complete, 6:05 AM)
                           │
        ┌──────────────────▼──────────────────┐
        │            OPEN                     │
        │      (6:05 AM - 8:45 PM)            │
        │    ✅ Customers arrive             │
        │    ✅ Cashiers work                │
        │    ✅ Stockers work                │
        │    ✅ Orders can arrive            │
        └──────────────────┬──────────────────┘
                           │
                 (Time reaches 8:45 PM)
                           │
        ┌──────────────────▼──────────────────┐
        │            CLOSING                  │
        │    (Last 15 min before close)       │
        │    ✓ Existing customers finish     │
        │    ✗ No new customers enter        │
        │    ⚠️  Can still ring up           │
        └──────────────────┬──────────────────┘
                           │
                (Time reaches 9:00 PM)
                           │
        ┌──────────────────▼──────────────────┐
        │     CLOSING_PROCEDURES              │
        │      (9:00 PM - 10:00 PM)           │
        │    ✅ Stockers restock overnight   │
        │    ✅ Money counting               │
        │    ✅ Inventory checks             │
        │    ✗ No customers                  │
        │    ✗ Cashiers unavailable          │
        └──────────────────┬──────────────────┘
                           │
              (Procedures complete, 10 PM)
                           │
        ┌──────────────────▼──────────────────┐
        │  CLOSED (NIGHT)                     │
        │  (10 PM - 6 AM next day)            │
        │  📊 Daily metrics saved             │
        │  🔄 Loop back to OPENING            │
        └──────────────────────────────────────┘
```

---

## 3. TRAFFIC PATTERN GRAPHS

### Weekday Traffic Pattern
```
Customers/Hour
     │
  15 │                    ╱╲
  14 │                   ╱  ╲
  13 │                  ╱    ╲      ╱╲
  12 │                 ╱      ╲    ╱  ╲
  11 │                ╱        ╲  ╱    ╲╲
  10 │               ╱          ╲╱      ╲╲
   9 │              ╱                    ╲╲
   8 │    ╱╲       ╱                      ╲╲
   7 │   ╱  ╲     ╱                        ╲╲
   6 │  ╱    ╲   ╱                          ╲
   5 │ ╱      ╲ ╱                            ╲
   4 │╱        ╱                              ╲
   3 │                                         ╲
   2 │                                          ╲
   1 │___________|___________|___________|_______╲___
   0 └─────────────────────────────────────────────
     6AM   8AM  10AM  12PM  2PM   4PM  5PM  8PM  9PM
     │      │     │     │    │     │    │    │    │
   OPEN  MORNING MIDDAY LUNCH QUIET WORK PEAK WIND DOWN CLOSE
         RUSH                       RUSH
         
Peak Hours: 8-9 AM, 12 PM, 5-7 PM
Slow Hours: 10-11 AM, 2-4 PM
```

### Weekend Traffic Pattern
```
Customers/Hour
     │
  15 │
  14 │
  13 │      ╭─────────────╮
  12 │     ╱               ╲
  11 │    ╱                 ╲
  10 │   ╱                   ╲
   9 │  ╱                     ╲
   8 │ ╱                       ╲
   7 │                          ╲
   6 │                           ╲
   5 │                            ╲
   4 │                             ╲
   3 │                              ╲
   2 │_______________________________╲___
   1 │                                ╲
   0 └──────────────────────────────────────
     8AM   10AM  12PM  2PM   4PM  5PM  8PM  9PM
     │      │     │     │    │    │    │    │
   OPEN  MORNING ALL-DAY SHOPPING WIND CLOSE
         RAMP    STEADY STATE                DOWN
         
Peak Hours: 10 AM - 6 PM (flat all day)
No distinct peaks like weekday
```

---

## 4. STAFF SCHEDULING VISUALIZATION

### Example: Good Schedule
```
WEEKLY SCHEDULE
(M = Cashier Morning, E = Cashier Evening, S = Stocker)

          MON    TUE    WED    THU    FRI    SAT    SUN
Alice     M──┐   M──┐   M──┐   OFF    M──┐   M──┐   OFF
(Cashier) E──┘   E──┘   E──┘         E──┘   E──┘
          
Bob       OFF    M──┐   M──┐   M──┐   OFF    M──┐   M──┐
(Cashier)       E──┘   E──┘   E──┘         E──┘   E──┘
         
Carlos    M──┐   M──┐   OFF    M──┐   M──┐   M──┐   M──┐
(Stocker) STOCK  STOCK        STOCK  STOCK  STOCK  STOCK
         
Coverage Check:
✅ Morning (6-12): All days have coverage
✅ Lunch (12-1): Extra person available
✅ Evening (5-9): Always 2+ people
✅ Overnight: Carlos stocks (6+ hours)
✅ No gaps detected

Weekly Cost: (3 people × 5 days × $45/day) = $675
Weekly Revenue (est): $3,500
```

### Example: Bad Schedule (Gaps!)
```
          MON    TUE    WED    THU    FRI    SAT    SUN
Alice     M──┐   M──┐   M──┐   OFF    OFF    M──┐   OFF
(Cashier) E──┘   E──┘   E──┘              E──┘
          
Bob       OFF    OFF    M──┐   M──┐   M──┐   OFF    M──┐
(Cashier)                E──┘   E──┘   E──┘        E──┘
         
Carlos    M──┐   M──┐   OFF    M──┐   OFF    M──┐   M──┐
(Stocker) STOCK  STOCK        STOCK        STOCK  STOCK

Coverage Check:
❌ MON 9-12: Only Alice (should have 2)
❌ THU 9-12: Only Carlos + Bob overlap (both hard workers, no breaks)
❌ SAT 5-9: Only Bob (evening is PEAK - too risky!)
❌ SUN 12-5: Only Carlos (no second cashier for lunch rush!)

⚠️  CRITICAL GAPS - Needs adjustment!
```

---

## 5. INVENTORY ORDERING FLOWCHART

```
                    PLAYER CLICKS "ORDER MILK"
                           │
                    Current time: 2:00 PM
                           │
                    Milk: 5 gallons in backroom
                    Traffic: Medium
                           │
        ┌──────────────────┴──────────────────┐
        │                                      │
    Morning order?                         Afternoon order?
    (6 AM - 11 AM)                         (12 PM - 5 PM)
    │                                      │
    │ YES                                  │ YES
    ▼                                      ▼
    
 Delivery: Same day                  Delivery: Tomorrow 9 AM
 3:00 PM                            
    │                                      │
    ├─ 6:00 Order                          ├─ 2:00 Order
    ├─ 9:00 In transit                     ├─ Tomorrow 9:00 Delivered
    └─ 3:00 PM Arrive ✓                   └─ Tomorrow 9:00 Arrive ✓
    
    1 hour wait                             15 hours wait
    SHORT-TERM planning                     LONG-TERM planning
    
              ┌──────────────────┘
              │
        BOTH REDUCE $$ IMMEDIATELY
              │
        ┌─────▼──────────┐
        │  DEDUCT COST   │
        │  Case pack: 6  │
        │  Cost: $11.94  │
        │  $$ Balance    │
        │  Reduced       │
        └─────┬──────────┘
              │
        ┌─────▼──────────┐
        │  ORDER QUEUED  │
        │  (IN_TRANSIT)  │
        │                │
        │  Player can    │
        │  now order     │
        │  other items   │
        └─────┬──────────┘
              │
        ┌─────▼─────────────────────┐
        │  DELIVERY ARRIVES         │
        │  Items go to backroom     │
        │  Status: DELIVERED        │
        │  Ready to stock!          │
        └───────────────────────────┘
```

---

## 6. DAILY METRICS REPORT

### Example: Good Day
```
╔════════════════════════════════════════╗
║        DAILY SUMMARY - MONDAY          ║
╠════════════════════════════════════════╣
║                                        ║
║  Time Open: 6:00 AM - 9:00 PM (15h)  ║
║  Store State: All hours OPEN ✓        ║
║                                        ║
║  ─── CUSTOMERS ───                    ║
║  Total Transactions: 24                ║
║  Peak Hour: 5:00 PM (8 customers)      ║
║  Avg Basket Size: 4.2 items            ║
║                                        ║
║  ─── REVENUE ───                       ║
║  Total Sales: $2,456.80                ║
║  Avg Transaction: $102.37              ║
║                                        ║
║  ─── EXPENSES ───                      ║
║  Staff Salaries:     $330.00           ║
║  Inventory Costs:    $1,200.00         ║
║  Utilities/Other:    $100.00           ║
║  Total Expenses:     $1,630.00         ║
║                                        ║
║  ─── PROFIT ───                        ║
║  Daily Profit:       $826.80 ✓✓✓      ║
║                                        ║
║  ─── ISSUES ───                        ║
║  ⚠️  Ran out of Bread (2-3 PM)        ║
║      Lost sale (~$20)                  ║
║                                        ║
║  💚 EXCELLENT DAY!                    ║
║     Beat yesterday by $50               ║
║                                        ║
║  [NEXT DAY] [REVIEW] [SAVE]            ║
╚════════════════════════════════════════╝
```

### Example: Challenging Day
```
╔════════════════════════════════════════╗
║     DAILY SUMMARY - WEDNESDAY          ║
╠════════════════════════════════════════╣
║                                        ║
║  Time Open: 6:00 AM - 9:00 PM (15h)  ║
║  ⚠️  CLOSED 12:30 - 1:45 PM           ║
║     (Equipment failure!)               ║
║                                        ║
║  ─── CUSTOMERS ───                    ║
║  Total Transactions: 18 (down 25%)     ║
║  Peak Hour: 8:00 AM (3 customers)      ║
║  Avg Basket Size: 3.1 items            ║
║                                        ║
║  ─── REVENUE ───                       ║
║  Total Sales: $1,284.50                ║
║  Lost Revenue: ~$400 (due to closure)  ║
║                                        ║
║  ─── EXPENSES ───                      ║
║  Staff Salaries:     $330.00           ║
║  Inventory Costs:    $900.00           ║
║  Utilities/Other:    $100.00           ║
║  Total Expenses:     $1,330.00         ║
║                                        ║
║  ─── PROFIT ───                        ║
║  Daily Profit:      -$45.50 ❌         ║
║  (Loss due to equipment!)              ║
║                                        ║
║  ─── ISSUES ───                        ║
║  ❌ Equipment breakdown (1.25 hours)  ║
║  ❌ Missed lunch rush entirely         ║
║  ⚠️  Too much staff scheduled          ║
║      (overstaffed during closure)      ║
║                                        ║
║  💔 ROUGH DAY.                         ║
║     Next day: reduce payroll,          ║
║               maintain equipment        ║
║                                        ║
║  [NEXT DAY] [REVIEW] [SAVE]            ║
╚════════════════════════════════════════╝
```

---

## 7. HOUR-BY-HOUR EXAMPLE: A TYPICAL MONDAY

```
6:00 AM
▪ Store opens
▪ Staff arrives: Alice (cashier), Carlos (stocker)
▪ Customers: 1-2
▪ Action: Begin restocking from morning delivery

7:00 AM
▪ Morning rush begins!
▪ Customers: 5-7
▪ Staff: Alice at register (busy), Carlos stocking
▪ Action: Can add Bob now? Or manage with Alice?

8:00 AM
▪ Peak morning continues
▪ Customers: 8-10
▪ ⚠️  CRITICAL - queues forming?
▪ Action: If understaffed, call Bob in early!

9:00 AM
▪ Morning rush subsiding
▪ Customers: 3-4
▪ Action: Catch up on stocking, take break

10:00 AM
▪ Quiet period
▪ Customers: 1-2
▪ Action: 📋 Order more milk? (will arrive 3 PM)

11:00 AM
▪ Still quiet
▪ Customers: 1-2
▪ Action: Manual restock, prep for lunch rush

12:00 PM (LUNCH RUSH)
▪ Surge! Many customers
▪ Customers: 10-12
▪ Staff: Need both Alice AND Bob!
▪ ⚠️  Action: Carlos helps bag items, extra busy!

1:00 PM
▪ Rush subsiding
▪ Customers: 2-3
▪ Action: Staff takes lunch break

2:00 PM
▪ Afternoon lull
▪ Customers: 0-1
▪ Action: Slow period - can schedule for maintenance

3:00 PM
▪ 📦 DELIVERY ARRIVES (from 10 AM order)
▪ 6 gallons of milk in backroom
▪ Action: Carlos stocks milk on shelf

4:00 PM
▪ Still quiet
▪ Customers: 1-2
▪ Action: Prep for evening rush
▪ Analyze: Do we have enough milk? Other items?

5:00 PM (EVENING RUSH BEGINS)
▪ PEAK TRAFFIC!
▪ Customers: 12-15
▪ ⚠️  CRITICAL - Bob must be here
▪ Action: Alice + Bob both on registers
▪        Carlos handling returns/complaints

6:00 PM
▪ Still very busy
▪ Customers: 10-12
▪ Action: Keep pace, prevent long queues

7:00 PM
▪ Rush subsiding
▪ Customers: 4-6
▪ Action: Final push

8:00 PM
▪ Winding down
▪ Customers: 2-3
▪ Action: Last calls, prepare to close

9:00 PM (CLOSING TIME)
▪ Store closes to new customers
▪ Customers: 0-1 (finals checkout)
▪ Action: Begin closing procedures
▪         Carlos restocks overnight
▪         Money counting
▪         Inventory verification

10:00 PM
▪ Closing procedures complete
▪ Daily Summary generated:
  - 24 transactions
  - $2,456.80 revenue
  - $826.80 profit
  - ✅ Good day!
▪ Next day begins in 8 hours...
```

---

## 8. DECISION TREE: TO ORDER OR NOT

```
                     CHECK INVENTORY
                           │
                ┌──────────┴──────────┐
                │                     │
         What hour is it?      How much in stock?
         (affects delivery time)  (affects urgency)
         │                     │
     ┌───┴────┐                └─ 0-2 items: CRITICAL
     │        │
   6-11 AM  12-5 PM            3-5 items: MEDIUM
     │        │
 Deliver   Deliver             6+ items: LOW
 TODAY     TOMORROW
 @ 3 PM    @ 9 AM              What's the forecast?
     │        │                │
  1h wait  15h wait            ├─ Lunch peak (12 PM) soon?
     │        │                │  YES → Order NOW
  If item  If item             │
  popular: popular:            ├─ Evening peak (5 PM) soon?
  ORDER!   Prepare!            │  YES → Order NOW
           Wait if             │
           low urgency         └─ Slow period?
                                  WAIT for cheaper time
```

---

## 9. PLAYER MENTAL MODEL (What They're Thinking)

```
"It's 4:30 PM. Evening rush in 30 min."
          │
          ├─ Staff check:
          │  ✓ Alice scheduled 5-10 PM
          │  ✓ Bob scheduled 5-10 PM
          │  ✓ Carlos will help
          │  → GOOD, enough coverage
          │
          ├─ Inventory check:
          │  ✓ Milk: 4 units (ok)
          │  ✓ Bread: 2 units (LOW!)
          │  ✓ Popular items stocked
          │  → BREAD is risky
          │
          ├─ Decision:
          │  "Should I order bread now?"
          │  
          │  Delay = 15 hours (to tomorrow 9 AM)
          │  Risk = Run out during evening peak
          │  Upside = More customers = more sales
          │  
          │  Decision: WAIT
          │  (Evening rush might not need that much)
          │
          └─ Prepare: Get ready for rush!
             (Make sure registers are stocked with cash)
```

---

This visual reference should help clarify the full scope and flow of the day cycle system!

