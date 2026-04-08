# Code Coverage Quick Reference

**Tool**: JaCoCo 0.8.10  
**Setup**: Already configured in `app/build.gradle.kts`

---

## Quick Commands

### Generate Coverage Report
```bash
./gradlew test testCoverageReport
```

### View Report
```bash
# Windows
start app/build/reports/jacoco/testCoverageReport/html/index.html

# Mac
open app/build/reports/jacoco/testCoverageReport/html/index.html

# Linux
xdg-open app/build/reports/jacoco/testCoverageReport/html/index.html
```

### Clean and Regenerate
```bash
./gradlew clean test testCoverageReport
```

---

## Key Coverage Metrics

| Package | Instruction | Branch | Target |
|---------|------------|--------|--------|
| `domain.time` | 90% | 71% | ✅ 90%+ |
| `domain.Transactions` | 100% | n/a | ✅ 100% |
| `domain` (Money, Inventory, Engine) | ~95% | ~85% | ✅ 85%+ |

---

## Report Locations

```
app/build/reports/jacoco/testCoverageReport/
├── html/
│   └── index.html                    # Main interactive report
├── report.csv                         # Spreadsheet format
└── report.xml                         # CI/CD integration
```

---

## What's Covered?

✅ **Phase 1 Domain Logic** (90%+)
- Time system
- Money calculations
- Inventory tracking
- Transaction processing
- Game engine orchestration

❌ **Not Covered** (Expected)
- UI/Compose code (use Compose tests)
- Android framework code
- Generated Hilt code

---

## Phase 1 Coverage Status

| Component | Coverage | Status |
|-----------|----------|--------|
| Time | 90% | ✅ Excellent |
| Money | 100% | ✅ Perfect |
| Inventory | 100% | ✅ Perfect |
| Transactions | 100% | ✅ Perfect |
| Game Engine | 85% | ✅ Good |

**Overall**: ✅ **Ready for Phase 2**

---

## Adding More Tests

To improve coverage:

1. Identify uncovered classes in the HTML report
2. Add test file in `app/src/test/java/...`
3. Run coverage: `./gradlew test testCoverageReport`
4. Check improvements in report

Example coverage goals for Phase 2:
- TransactionEngine refund logic: 70% → 90%
- Entity management: 64% → 90%
- Customer traffic: 0% → 85% (new)

---

## Common Coverage Queries

**Q: Why is overall coverage only 7%?**  
A: Includes all dependencies (Android, Compose, libraries). Phase 1 domain is 90%+.

**Q: Should we aim for 100% coverage?**  
A: No. Focus on critical business logic (Phase 1 domain: 90%+ ✅). UI is tested differently.

**Q: How to track coverage over time?**  
A: Save `report.csv` before each release, plot in spreadsheet.

**Q: Can I exclude code from coverage?**  
A: Yes. The build.gradle already excludes Hilt and generated code.

---

**Coverage Setup Complete!** 📊

Run `./gradlew test testCoverageReport` to generate updated reports anytime.

