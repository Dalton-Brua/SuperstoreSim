# Port Superstore Simulator to Desktop (Compose Multiplatform)

## Context

The game is a single-module Android app (`:app`, Kotlin 2.0.0, Compose BOM 2024.12, Material3)
with clean domain/data/ui separation. Goal: ship a **Windows/macOS/Linux desktop build** (for
Steam or direct distribution) from the **same codebase** that still produces the Android app — so
features and saves can later sync across platforms.

Compose Multiplatform (CMP) lets the domain logic *and* the Compose UI be shared. Three things are
Android-only and block desktop today:

1. **Hilt** — DI annotation processor that only runs on Android. ~27 `@Singleton` classes, 2 `@Module`
   files, 2 `@HiltViewModel` ViewModels.
2. **Room** — `Room.databaseBuilder` needs an Android `Context`.
3. **Context-bound I/O** — `GameStateRepository` (SharedPreferences) and `ItemDataLoader`
   (`R.raw.items` via `context.resources`).

Decisions made with the user: **Koin** replaces Hilt everywhere; **Room KMP** (keep Room, port the
builder) for persistence; **MVP first** — get the existing phone-style UI running in a resizable
desktop window, defer the widescreen redesign to a follow-on phase.

Most code (domain managers, GameEngine, serialization, Compose screens, custom navigation) ports
with little change. The work is concentrated in build restructuring, the DI swap, and 4–5 platform
shims.

---

## Target architecture

Convert the single Android module into a 3-module CMP project:

```
:shared        KMP library — commonMain holds domain + data + UI + ViewModels + Koin defs.
               androidMain / desktopMain hold the platform actuals.
               jvmShared (intermediate set, android+desktop) holds JVM-only code (org.json legacy parser).
:androidApp    com.android.application — MainActivity, manifest, Application, starts Koin. Depends on :shared.
:desktopApp    Compose Desktop (JVM) — main()/Window, starts Koin, nativeDistributions. Depends on :shared.
```

Current `app/src/main/java/com/example/superstoresimulator/{domain,ui,di}` moves wholesale into
`shared/src/commonMain/kotlin/...`, with the platform-specific files peeled off into the
android/desktop/jvmShared source sets.

---

## Phase 1 — Module + build restructure

**Toolchain bumps (prerequisite):** jpackage (used to build installers) needs **JDK 17+**, and
Compose Desktop builds on 17+. Current `jvmTarget`/`sourceCompatibility` is 11. Bump the toolchain to
**JDK 17** across all modules.

- `settings.gradle.kts` — replace `include(":app")` with `include(":shared", ":androidApp", ":desktopApp")`.
  The JetBrains compose-dev maven repo is already in `pluginManagement`; keep it. Loosen the `google {}`
  `includeGroupByRegex` filters so the Compose/Kotlin-multiplatform plugins resolve.
- `gradle/libs.versions.toml` — add:
  - plugins: `org.jetbrains.kotlin.multiplatform`, `org.jetbrains.compose` (CMP ~1.7.x to match Kotlin
    2.0.0 — pin carefully), `com.android.library`, `androidx.room` (Room Gradle plugin).
  - libs: `io.insert-koin:koin-core`, `koin-android`, `koin-compose` / `koin-compose-viewmodel`;
    `androidx.room:room-runtime` (already 2.8.4 — KMP-capable), `androidx.sqlite:sqlite-bundled`;
    `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` (multiplatform ViewModel).
  - Remove the Hilt plugin/lib entries once the swap (Phase 2) lands.
- **New** `shared/build.gradle.kts` — `kotlin { androidTarget(); jvm("desktop") }`, apply
  `org.jetbrains.compose`, `kotlin.plugin.compose`, `room`, `ksp`, `kotlin.serialization`. Declare the
  `jvmShared` intermediate source set that both `androidMain` and `desktopMain` extend (for org.json
  code). Add `room { schemaDirectory(...) }` and the KSP Room compiler for both targets.
- **New** `androidApp/build.gradle.kts` — `com.android.application`, `compose`, depends on `:shared`.
  Carry over `namespace`, `applicationId`, `minSdk 24`, `targetSdk 36`, `compileSdk 36` from the old
  `app/build.gradle.kts`. Move the **JaCoCo** block here (its paths reference
  `intermediates/classes/debug`) or retarget it at the `:shared` android unit-test classes.
- **New** `desktopApp/build.gradle.kts` — `kotlin("jvm")` + `org.jetbrains.compose`, depends on
  `:shared`, plus the `compose.desktop.application` block (Phase 6).
- Delete the old `app/` module directory after sources are moved.

**Source moves (bulk, mechanical):** move `com/example/superstoresimulator/{domain,ui}` →
`shared/src/commonMain/kotlin/`. The DI folder is rewritten in Phase 2, not moved.

---

## Phase 2 — DI: Hilt → Koin

This is the largest mechanical change. Pattern, applied across ~30 files:

- Strip `@Inject constructor`, `@Singleton`, `@HiltViewModel`, `@AndroidEntryPoint`, `@HiltAndroidApp`,
  `@Module`, `@InstallIn`, `@Provides`, and the `@TickDelta` qualifier. Constructors stay — Koin calls
  them.
- **Delete** `di/DatabaseModule.kt` and `di/GameModule.kt`.
- **New** `shared/src/commonMain/.../di/AppModule.kt` — a Koin `module {}` that declares every former
  `@Singleton` as `single { ... }`: `GameEngine`, `TickOrchestrator`, `ItemMetadataCache`, the manager
  set (`InventoryManager`, `PricingManager`, `VendorManager`, `StaffManager`, `ResearchManager`,
  `ReputationManager`, `RegisterManager`, `TrafficManager`, `TutorialManager`, `DayManager`,
  `MetricsArchiver`, `TransactionEngine`, the tick processors, etc.), plus `tickDelta = 16L` as a named
  constant. ViewModels become `viewModel { GameViewModel(...) }` /
  `viewModel { ItemViewModel(...) }` via koin-compose-viewmodel.
- **New** platform Koin modules for the things that differ by OS:
  - `androidMain/.../di/PlatformModule.android.kt` — provides `AppDatabase` (Context builder),
    `GameStateRepository` save dir = `context.filesDir`.
  - `desktopMain/.../di/PlatformModule.desktop.kt` — provides `AppDatabase` (file-path builder),
    save dir = OS app-data dir.
- ViewModels: change base to multiplatform `androidx.lifecycle.ViewModel` (lifecycle-viewmodel
  artifact is multiplatform); `viewModelScope` works unchanged.

---

## Phase 3 — Persistence: Room KMP + file-based save

- `di/AppDatabase.kt` → move to `commonMain`. Keep `@Database(version = 11, ...)`. Add the KMP
  requirement: a `@Suppress("NO_ACTUAL_FOR_EXPECT") expect object` database constructor, or an
  `expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>` with actuals:
  - `androidMain` — `Room.databaseBuilder(context, AppDatabase::class.java, "superstore-database-v11")`.
  - `desktopMain` — `Room.databaseBuilder<AppDatabase>(name = <appDataDir>/superstore-database-v11.db)`.
  - Both `.setDriver(BundledSQLiteDriver())` + `.setQueryCoroutineContext(Dispatchers.IO)`, keep
    `fallbackToDestructiveMigration(true)`.
- Entities/DAOs (`Item`, `TransactionEntity`, `TransactionLineEntity`, `ArchivedDailyMetricsEntity`,
  `ItemDao`, `TransactionDao`, `ArchivedDailyMetricsDao`, `MoneyData`) move to `commonMain`
  **unchanged** — they use only Room annotations + suspend funcs, which are KMP-clean.
- **Rewrite** `domain/persistence/GameStateRepository.kt` — drop `Context`/`SharedPreferences`. Take a
  save directory (`java.io.File`, available in both JVM source sets) injected by the platform Koin
  module. `saveGameState` writes `GameStateSerializer.serialize(state)` to `save.json`; `loadGameState`
  reads it; `getLastSaveTime` uses file mtime; `clearSave` deletes it. The serializer
  (`GameStateSerializer.kt`) is already kotlinx.serialization → moves to `commonMain` untouched.
- `LegacyGameStateDeserializer.kt` uses `org.json` (JVM-only) → place in the **`jvmShared`** source
  set (both targets are JVM, so no rewrite needed). Alternatively migrate it to kotlinx
  `JsonElement` parsing to live in commonMain — larger effort, optional.

---

## Phase 4 — Resources: item catalog

- Move `app/src/main/res/raw/items.json` → `shared/src/commonMain/composeResources/files/items.json`
  (CMP resources).
- **Rewrite** `domain/items/ItemDataLoader.kt` — replace `context.resources.openRawResource(R.raw.items)`
  with the CMP resource reader (`Res.readBytes("files/items.json")`). Replace the `org.json` parsing
  with a `@Serializable ItemJson` DTO + `Json.decodeFromString` (kotlinx.serialization is already a
  dependency and is multiplatform) so the loader lives in `commonMain` with no `org.json` / `Context`.
  Drop the `context: Context` parameter; callers (`GameViewModel.init`) update accordingly.

---

## Phase 5 — UI + entry points (MVP: phone layout in a window)

- `ui/theme/Theme.kt` (root-package `SuperstoreSimulatorTheme`) → move to commonMain. **Remove** the
  dynamic-color branch (`Build.VERSION` + `dynamicColorScheme` + `LocalContext`) — it's already passed
  `dynamicColor = false`, and it's Android-only. Keep the light/dark `colorScheme` selection.
- `statusBarsPadding()` (6 files: `SuperstoreApp`, `SettingsPanel`, `InventoryScreen`, `StaffScreen`,
  `MetricsScreen`) — keep as-is; CMP's `androidx.compose.foundation.layout` provides it and it resolves
  to zero insets on desktop. Verify at first compile; if unresolved, wrap in an `expect`/no-op.
- `RegisterDetailDialog.kt` `ModalBottomSheet` and the `HorizontalPager` swipe nav work in CMP desktop
  via mouse drag. MVP keeps them; no rework now.
- **Android entry** — move `MainActivity.kt`, `SuperstoreSimulatorApp.kt` into `:androidApp`. Strip
  Hilt: `MainActivity` gets `itemDao`/`GameViewModel` from Koin (`koinViewModel()` /
  `get()`); keep the `onPause`/`onResume`/`onStop` save hooks. `SuperstoreSimulatorApp` drops
  `@HiltAndroidApp`, calls `startKoin { androidContext(this); modules(appModule, androidPlatformModule) }`
  in `onCreate`, keeps `ResearchGates.validate()`.
- **New desktop entry** `desktopApp/src/jvmMain/kotlin/Main.kt`:
  ```kotlin
  fun main() = application {
      startKoin { modules(appModule, desktopPlatformModule) }
      Window(onCloseRequest = { gameViewModel.saveGameState(); exitApplication() },
             title = "Superstore Simulator",
             state = rememberWindowState(width = 1280.dp, height = 800.dp)) {
          SuperstoreSimulatorTheme(dynamicColor = false) {
              val vm = koinViewModel<GameViewModel>()
              SuperstoreApp(viewModel = vm, itemDao = koinInject())
          }
      }
  }
  ```
  Window-close + a periodic/lifecycle save replace the Activity save callbacks.

---

## Phase 6 — Packaging / installers

In `desktopApp/build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        mainClass = "com.example.superstoresimulator.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Superstore Simulator"
            packageVersion = "1.0.0"
            windows { upgradeUuid = "<generate-a-stable-GUID>"; menuGroup = "Superstore Simulator" }
            macOS  { bundleID = "com.example.superstoresimulator" }
        }
    }
}
```

Local commands the dev runs (each builds only for the host OS — jpackage is not cross-OS):
- `./gradlew :desktopApp:run` — launch from source.
- `./gradlew :desktopApp:createDistributable` — app image (folder) for smoke testing.
- `./gradlew :desktopApp:packageMsi` (Windows) / `packageDmg` (macOS) / `packageDeb` (Linux).
- `./gradlew :desktopApp:packageReleaseDistributionForCurrentOS` for the optimized build.

Steam later: point Steamworks depot at `createDistributable` output (or ship the installer);
integrate the Steamworks SDK via a JVM binding as a separate task.

---

## Phase 7 — GitHub Actions

**New** `.github/workflows/desktop-build.yml` — matrix over the three host OSes so each produces its
native installer, plus an Android APK job:

```yaml
name: Build
on: { push: { branches: [ main, "feature/**" ] }, workflow_dispatch: {} }
jobs:
  desktop:
    strategy:
      matrix:
        include:
          - os: windows-latest
            task: packageReleaseMsi
          - os: macos-latest
            task: packageReleaseDmg
          - os: ubuntu-latest
            task: packageReleaseDeb
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :desktopApp:${{ matrix.task }}
      - uses: actions/upload-artifact@v4
        with:
          name: superstore-${{ matrix.os }}
          path: desktopApp/build/compose/binaries/main-release/**/*
  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
      - uses: actions/upload-artifact@v4
        with: { name: android-apk, path: androidApp/build/outputs/apk/debug/*.apk }
```

(Use a Windows `bash` shell or `./gradlew.bat` as needed; pin the `setup-gradle` cache.) A later
`release.yml` triggered on tags can attach the three installers to a GitHub Release.

---

## Explicitly changed / new files

| File | Action |
|------|--------|
| `settings.gradle.kts` | Rewrite includes → `:shared`, `:androidApp`, `:desktopApp` |
| `gradle/libs.versions.toml` | Add CMP/Koin/Room-KMP/multiplatform-lifecycle; drop Hilt |
| `app/build.gradle.kts` | Split into 3 new build files; delete `app/` |
| `shared/build.gradle.kts` | **New** — KMP + compose + room + ksp; `jvmShared` source set |
| `androidApp/build.gradle.kts` | **New** — android.application; move JaCoCo here |
| `desktopApp/build.gradle.kts` | **New** — compose.desktop + nativeDistributions |
| `di/DatabaseModule.kt`, `di/GameModule.kt` | **Delete** (Hilt) |
| `di/AppModule.kt` (+ android/desktop PlatformModule) | **New** — Koin modules |
| `di/AppDatabase.kt` | Move to commonMain; expect/actual DB builder + BundledSQLiteDriver |
| `domain/persistence/GameStateRepository.kt` | Rewrite — File-based save, no Context |
| `domain/items/ItemDataLoader.kt` | Rewrite — CMP resource + kotlinx.serialization, no Context/org.json |
| `domain/persistence/LegacyGameStateDeserializer.kt` | Move to `jvmShared` set (keeps org.json) |
| `ui/theme/Theme.kt` | Move to commonMain; remove dynamic-color branch |
| `MainActivity.kt`, `SuperstoreSimulatorApp.kt` | Move to `:androidApp`; strip Hilt, start Koin |
| `desktopApp/.../Main.kt` | **New** — desktop entry/window |
| ~27 `@Singleton`/`@Inject` classes + 2 ViewModels | Strip Hilt annotations; declared in Koin |
| `res/raw/items.json` | Move to `shared/.../composeResources/files/` |
| `.github/workflows/desktop-build.yml` | **New** — CI matrix + Android job |

---

## Verification

1. **Compiles per platform:** `./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid`.
2. **Unit tests still green:** existing JVM tests (FakeItemDao / JUnit4 / Mockito) run from the
   `jvmShared`/android-unit-test set — `./gradlew :shared:testDebugUnitTest`. Confirm the
   factory-seed + TransactionEngine-cache tests still pass (per project testing notes).
3. **Desktop runs:** `./gradlew :desktopApp:run` — window opens, item catalog loads from the moved
   `items.json`, a new game starts, ticks advance, save/quit/relaunch restores state from `save.json`.
4. **Android unaffected:** `./gradlew :androidApp:assembleDebug`, install to emulator, verify the
   game still loads its existing save (the SharedPreferences→file save format is the same JSON; note a
   one-time migration read may be needed on Android if you keep old saves).
5. **Installer builds:** `./gradlew :desktopApp:packageMsi` (on Windows) produces an `.msi`; install
   and launch it.
6. **CI green:** push the branch; confirm all matrix jobs upload artifacts.

---

## Risks / notes

- **Version alignment** is the sharpest risk: Kotlin 2.0.0 ↔ Compose Multiplatform ↔ Compose Compiler
  plugin ↔ Room KMP ↔ KSP must form a compatible set. Bumping Kotlin to 2.1.x + CMP 1.7.3 may be the
  smoother baseline — validate early before bulk-moving sources.
- **Room KMP + KSP** for two targets occasionally needs `room { generateKotlin = true }` and KSP2;
  budget time for the first clean build.
- The **widescreen UI redesign** (side nav, multi-panel dashboards, mouse/keyboard affordances) is a
  deliberate follow-on phase, not included here.
- **Steamworks SDK** integration (cloud saves, achievements, leaderboards) is separate downstream work.
- Effort estimate: build restructure + DI swap + shims ≈ the bulk; MVP desktop build reachable in
  roughly **1–2 focused weeks**, dominated by the Hilt→Koin pass and version-alignment debugging.
