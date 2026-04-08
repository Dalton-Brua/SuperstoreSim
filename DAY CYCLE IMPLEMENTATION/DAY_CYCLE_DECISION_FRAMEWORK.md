# Day Cycle System - Decision Framework & Recommendations

## QUICK REFERENCE: Critical Decisions

### Decision 1: Time Progression Model
**Question**: How fast should game time pass?

**Options**:
- **A (Recommended)**: 1 real second = 6 game minutes (10x speed)
  - ✅ One 8-hour day = ~5 real minutes
  - ✅ Good balance between realism and playability
  - ✅ Player can control with speed multiplier
- **B**: 1 real second = 1 game minute (realistic)
  - ✅ Most realistic
  - ❌ 8-hour day = 8 real minutes (tedious)
  - ❌ Player gets bored waiting
- **C**: Player-controlled (variable)
  - ✅ Both realistic and fast modes
  - ✅ Player agency
  - ⚠️ More UI complexity

**Recommendation**: **Option A** (1s = 6 min) as default, with speed multiplier buttons (1x, 2x, 4x)

**Why**: Balances gameplay feel with time progression. Player can speed up for slow periods.

---

### Decision 2: Customer Generation
**Question**: How should customers arrive?

**Options**:
- **A (Recommended)**: Autonomous generation
  - Customer queue fills based on traffic pattern
  - Auto-starts transactions when cashiers available
  - ✅ More realistic
  - ✅ Demands staff management
  - ❌ Less player control
  
- **B**: Player-triggered
  - Click "process customer" button
  - Probability of success based on traffic pattern
  - ✅ Player controls pace
  - ✅ Closer to current game feel
  - ❌ Less realistic
  - ❌ Doesn't penalize bad scheduling

**Recommendation**: **Option A** (Autonomous)

**Why**: Drives the core new mechanic (staff scheduling becomes critical). Creates interesting tension during peak hours.

---

### Decision 3: Delivery Delays
**Question**: How long should inventory orders take?

**Options**:
- **A (Recommended)**: Time-based delivery windows
  - Order 6 PM → Deliver 9 AM next day
  - Order 9 AM → Deliver 3 PM same day
  - ✅ Adds strategic planning
  - ✅ Risk of stockouts becomes real
  - ✅ Creates different play styles
  
- **B**: Fixed delay
  - All orders take 2 hours
  - ❌ Less realistic
  - ❌ Less strategic variation

- **C**: Immediate (no change)
  - ❌ Defeats purpose of day cycle
  - ❌ No new mechanics

**Recommendation**: **Option A** (Time-based windows)

**Why**: Creates the most interesting strategic decision-making. Forces player to think ahead.

---

### Decision 4: Staff Scheduling Complexity
**Question**: How complex should scheduling be?

**Options**:
- **A (Recommended)**: Simple scheduling
  - Define shifts (start/end time)
  - Mark days off
  - System validates coverage
  - ✅ Manageable complexity
  - ✅ Clear feedback (red flags for gaps)
  - ✅ Strategic depth without overwhelming
  
- **B**: Advanced scheduling
  - Staff requests, preferences
  - Skill levels
  - Fatigue/morale tracking
  - ✅ Deep simulation
  - ❌ Tutorial burden
  - ❌ Complexity might overwhelm
  
- **C**: No scheduling
  - Staff work fixed hours automatically
  - ❌ Loses player agency
  - ❌ Less interesting

**Recommendation**: **Option A** (Simple scheduling) with future expansion to B

**Why**: Achieves player agency and strategy without overwhelming. Easy to expand later.

---

### Decision 5: Perishable Items
**Question**: Should items expire?

**Options**:
- **A**: Yes, with decay
  - Milk expires in 5 days
  - Bread in 3 days
  - ✅ Added urgency
  - ✅ Realistic
  - ❌ More bookkeeping
  - ❌ Can frustrate if not careful
  
- **B (Recommended)**: Yes, but generous
  - Most items last 30+ days
  - Only seasonal items expire quickly
  - ✅ Adds mechanics without excessive burden
  
- **C**: No expiration
  - ❌ Loses realism
  - ❌ No urgency to sell

**Recommendation**: **Option B** (Generous expiration)

**Why**: Adds mechanic without punishing player too harshly. Can be tuned based on playtesting feedback.

---

## IMPLEMENTATION PRIORITY MATRIX

### Priority 1: MVP (Minimum Viable Product)
**Effort**: 1-2 weeks | **Value**: 9/10 | **Required for**: Core feature to work

- [x] GameTime data structure
- [x] Time display UI
- [x] Open/close mechanics
- [x] Basic traffic pattern
- [x] Auto-customer generation (simple)
- [x] Staff presence checking (can't work when closed)

**Why These First**: Core gameplay loop won't work without these.

---

### Priority 2: Core Strategic Layer
**Effort**: 1-2 weeks | **Value**: 8/10 | **Required for**: Interesting gameplay

- [x] Traffic patterns (realistic hourly variation)
- [x] Peak hour surge logic
- [x] Delivery time windows
- [x] Customer queue visualization
- [x] Staff scheduling UI
- [x] Schedule validation (show coverage gaps)
- [x] Daily metrics/summary

**Why These Second**: Make the game strategically interesting, not just clicky.

---

### Priority 3: Polish & Balance
**Effort**: 1 week | **Value**: 7/10 | **Required for**: Playable feel

- [x] Speed multiplier buttons
- [x] Visual time indicators (sunrise/sunset)
- [x] Audio cues (opening bell, closing bell)
- [x] Animation/transitions for time of day
- [x] Balance customer rates
- [x] Balance staff salaries
- [x] Difficulty presets

**Why These Third**: Makes it fun to play, not tedious.

---

### Priority 4: Advanced Features
**Effort**: 2+ weeks | **Value**: 6/10 | **Optional**: Nice but not critical

- [ ] Perishable items with decay
- [ ] Random events (weather, sick staff, etc.)
- [ ] Staff fatigue/morale
- [ ] Multi-shift management
- [ ] Equipment breakdowns
- [ ] Seasonal patterns
- [ ] Day/night visual themes

**Why These Last**: Cool features that enhance depth but not essential for core loop.

---

## PHASED ROLLOUT PLAN

### Phase 1: Time Foundation (Week 1)
**Goal**: Basic time progression and store open/close

```
Mon-Wed:
- Implement GameTime, StoreConfig, StoreState
- Implement TimeManager
- Update GameEngine.tick() to update time
- Add time display to UI
- Test time progression

Thu-Fri:
- Implement store open/close logic
- Prevent transactions when closed
- Save/load time state
- Polish time display (format, colors)
```

**Success Criteria**:
- Time advances every tick
- Display updates correctly
- Store prevents transactions when closed
- Player can see current time in UI

---

### Phase 2: Traffic & Auto-Customers (Week 1-2)
**Goal**: Autonomous customer generation

```
Mon-Wed:
- Implement TrafficPattern and TrafficSchedule
- Implement TrafficManager
- Integrate into GameEngine.tick()
- Generate customers based on traffic pattern

Thu-Fri:
- Visualize customer queue
- Balance customer rates (not too many!)
- Add traffic variations (weekday vs weekend)
- Test playability
```

**Success Criteria**:
- Customers auto-generate during open hours
- Fewer customers during slow hours
- More customers during peak hours
- Customer arrival feels natural (not overwhelming)

---

### Phase 3: Scheduling System (Week 2)
**Goal**: Staff scheduling with coverage validation

```
Mon-Tue:
- Implement Shift, StaffSchedule data classes
- Create scheduling UI screen
- Allow player to create/edit shifts
- Validate schedule for gaps

Wed-Thu:
- Implement daily payroll calculation
- Show coverage warnings (red flags)
- Allow staff status checking
- Save schedules between days

Fri:
- Balance staff salaries
- Test various schedules
- Polish UI
```

**Success Criteria**:
- Player can create staff schedules
- System validates coverage
- Clear visual feedback on gaps
- Payroll calculated correctly

---

### Phase 4: Delivery System (Week 2)
**Goal**: Order delays and delivery windows

```
Mon-Tue:
- Implement InventoryOrder and OrderStatus
- Calculate delivery times based on order time
- Track pending orders in GameState
- Process order arrivals

Wed-Thu:
- Update inventory when orders arrive
- Show pending orders in UI
- Allow player to order with delivery preview
- Test stock-out mechanics

Fri:
- Balance delivery times
- Test edge cases (order Friday evening, etc.)
- Polish UI
```

**Success Criteria**:
- Orders take time to arrive
- Different delivery times based on order timing
- Player can see pending orders
- Stock-outs are possible

---

### Phase 5: Daily Metrics & Summary (Week 3)
**Goal**: Tracking and analysis

```
Mon-Tue:
- Track daily transactions, customers, revenue
- Calculate peak hours
- Generate daily summary

Wed-Thu:
- Create daily summary screen
- Show metrics visualization (graphs)
- Calculate profit/loss
- Archive historical data

Fri:
- Polish presentation
- Add daily goals/targets (optional)
```

**Success Criteria**:
- Daily metrics tracked accurately
- Summary screen informative
- Player can review past performance
- Encourages repeated plays (to beat yesterday's score)

---

### Phase 6: Polish & Tuning (Week 3)
**Goal**: Make it feel good to play

```
Mon-Tue:
- Add speed multiplier buttons (1x, 2x, 4x)
- Add visual time indicators (time of day colors)
- Add audio cues (opening bell, closing bell)

Wed-Thu:
- Playtest and balance
- Customer rates
- Staff salary costs
- Order delivery times

Fri:
- Polish any remaining issues
- Create tutorial for new systems
```

**Success Criteria**:
- Fun to play at different speeds
- Balanced difficulty
- Clear feedback for all actions

---

## BALANCE TARGETS

### Customer Traffic
```
Target: ~5-10 customers per 8-hour day (at baseline difficulty)
Weekday peak hours (5-7 PM):
- 8 AM - 9 AM: 2-3 customers
- 12 PM - 1 PM: 3-4 customers
- 5 PM - 7 PM: 5-8 customers
- Off-peak: 0-2 customers

Weekend:
- 10 AM - 6 PM: 4-6 customers per hour
- Off-hours: 1-2 per hour
```

### Staff Costs
```
Daily salaries (baseline):
- Cashier: $50/day (8 hours)
- Stocker: $40/day (8 hours)

Target: 1-2 cashiers, 1 stocker for sustainable profit
With optimal scheduling, player should profit ~$200-300/day at start
```

### Delivery Times
```
Morning order (6 AM - 11 AM): Deliver same day 3 PM
Midday order (12 PM - 5 PM): Deliver next day 9 AM
Evening order (6 PM - 9 PM): Deliver next day 9 AM
```

---

## RISK MITIGATION

### Risk 1: Overwhelming Complexity
**Mitigation**:
- Start with MVP (just time + traffic)
- Add scheduling in Phase 2 (optional at first)
- Provide tutorial overlays
- Separate "easy" and "hard" modes

### Risk 2: Tedious Waiting
**Mitigation**:
- Speed multiplier controls
- "Skip to closing" button
- "Resume time" button
- Optional auto-play during slow hours

### Risk 3: Unbalanced Difficulty
**Mitigation**:
- Early playtesting with speed variations
- Easy mode: 1 required cashier, high traffic
- Normal mode: 2 required cashiers, medium traffic  
- Hard mode: 3+ required cashiers, high traffic

### Risk 4: Save File Compatibility
**Mitigation**:
- Database version increment (already done for case packs)
- Migration from old saves: set time to 0 (start at morning)
- Clear notification if old save loaded

---

## SUCCESS METRICS

### For Implementation:
- ✅ Time advances smoothly without stuttering
- ✅ Customer generation matches traffic patterns within 10%
- ✅ Schedule validation works correctly
- ✅ Orders arrive at predicted times
- ✅ Save/load preserves time state

### For Gameplay:
- ✅ Game feels more strategic, less clicky
- ✅ Peak hours create interesting pressure
- ✅ Staff scheduling decisions matter
- ✅ Player plans ahead for inventory
- ✅ Repeated plays feel fresh (different traffic patterns)

### For Player Retention:
- ✅ New feature hooks players for 2-3 more plays
- ✅ Daily metrics motivate competitive replays
- ✅ Strategic depth encourages "just one more day"

---

## NEXT STEPS

1. **Review this design** with a focus on decision points
2. **Choose your trade-offs** (complexity vs realism)
3. **Start Phase 1** (time foundation)
4. **Early playtest** Phase 2 (traffic)
5. **Gather feedback** before committing to later phases
6. **Iterate based on feedback**

---

## APPENDIX: Common Questions

### Q: Will this make the game too slow?
**A**: No - with speed controls, players can keep 4x speed during slow periods. Feels dynamic, not tedious.

### Q: Should I implement perishables from the start?
**A**: No - add in Phase 4 as an optional complication. Start with simple inventory.

### Q: How do I keep games short for mobile?
**A**: One full day = ~5 real minutes at 1x speed. Sessions can be 15-30 minutes (multiple days).

### Q: What if players hate the new system?
**A**: Keep the old clicker mode as "Arcade Mode" alongside new "Manager Mode". Let players choose.

### Q: Should customers wait in a queue?
**A**: Yes! Adds visual feedback. Show queue length, customer frustration (impatient icon).

### Q: Can I add weather effects later?
**A**: Absolutely! Phase 4+ can add:
- Rain (fewer customers, increased accidents)
- Snow (more customers for supplies)
- Holidays (surges)

### Q: How do I prevent player frustration with stock-outs?
**A**: 
- Show inventory warnings when low
- Suggest reordering
- Only penalize slightly (loss of sales, not bankruptcy)
- Let player save before key decisions

