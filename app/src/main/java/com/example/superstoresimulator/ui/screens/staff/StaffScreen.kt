package com.example.superstoresimulator.ui.screens.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.research.AnalystAssignment
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.StoreManagerConfig
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.staff.EmployeeActivity
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.dialogs.StoreManagerConfigDialog
import com.example.superstoresimulator.ui.state.ResearchUIState
import com.example.superstoresimulator.ui.state.StaffUIState
import com.example.superstoresimulator.ui.state.VendorUIState
import com.example.superstoresimulator.ui.theme.Amber
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.CautionDark
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.CriticalRed
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.ErrorSurface
import com.example.superstoresimulator.ui.theme.InfoChipSurface
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.PlaceholderSurface
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.ui.theme.Violet
import com.example.superstoresimulator.ui.theme.WarningChipSurface
import com.example.superstoresimulator.ui.theme.WarningTextDark
import com.yourapp.ui.theme.GameButtonStyles
import kotlinx.coroutines.launch

/**
 * Hiring criteria gate. A staff type only appears in the list (and is hireable in the
 * detail screen) once its requirement is met:
 *  - fresh_handler: requires the Fresh tab unlock ("prod_fresh_basics").
 *  - manager: requires store size of at least Small Grocery.
 * All other types are always available.
 */
fun canHireEntity(
    def: EntityDef,
    researchedUpgrades: Set<String>,
    currentStoreSize: StoreSize,
): Boolean = when (def.key) {
    "fresh_handler" -> "prod_fresh_basics" in researchedUpgrades
    "manager" -> currentStoreSize.ordinal >= StoreSize.SMALL_GROCERY.ordinal
    else -> true
}

@Composable
fun StaffScreen(
    state: StaffUIState,
    money: Money,
    researchedUpgrades: Set<String>,
    currentStoreSize: StoreSize,
    onHire: (EntityDef) -> Unit,
    onFire: (Int) -> Unit,
    onUpgrade: (Int) -> Unit,
    onUpdateStoreManagerConfig: (StoreManagerConfig) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showManagerConfig by remember { mutableStateOf(false) }
    var selectedDef by remember { mutableStateOf<EntityDef?>(null) }

    // Drill into a staff type in-place (back button keeps us on the same screen).
    if (selectedDef != null) {
        EntityTypeDetailScreen(
            state = state,
            money = money,
            def = selectedDef,
            researchedUpgrades = researchedUpgrades,
            currentStoreSize = currentStoreSize,
            onHire = onHire,
            onFire = onFire,
            onUpgrade = onUpgrade,
            onBack = { selectedDef = null },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Staff Management",
            money = money
        )

        Spacer(Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(EntityDef.allEntities.filter { canHireEntity(it, researchedUpgrades, currentStoreSize) }) { def ->
                StaffTypeCard(
                    def = def,
                    count = state.registry.countByDef(def),
                    onClick = { selectedDef = def }
                )
            }

            // Efficiency display — visible when senior manager (promoted) is on staff
            if (state.hasSeniorManager) {
                item {
                    StaffEfficiencySection(
                        cashierUtil = state.cashierUtilization,
                        stockerUtil = state.stockerUtilization,
                        freshUtil = state.freshUtilization,
                    )
                }
            }


            if (state.hasStoreManager) {
                item {
                    OutlinedButton(
                        onClick = { showManagerConfig = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryDark),
                    ) {
                        Text("Store Manager Settings", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    if (showManagerConfig) {
        StoreManagerConfigDialog(
            config = state.storeManagerConfig,
            onConfigChanged = onUpdateStoreManagerConfig,
            onDismiss = { showManagerConfig = false },
        )
    }
}

@Composable
fun StaffTypeCard(
    def: EntityDef,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = def.icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = def.displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hired: $count", fontSize = 14.sp, color = SubtleText)
                Text(def.roleDescription, fontSize = 14.sp, color = SubtleText)
            }
        }
    }
}

@Composable
fun EntityTypeDetailScreen(
    state: StaffUIState,
    money: Money,
    def: EntityDef?,
    researchedUpgrades: Set<String> = emptySet(),
    currentStoreSize: StoreSize = StoreSize.MOM_AND_POP,
    onHire: (EntityDef) -> Unit,
    onFire: (Int) -> Unit,
    onUpgrade: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (def == null) return

    val entities = state.registry.getByDef(def)
    val isFreshHandlers = def.key == "fresh_handler"
    val canHireFreshHandlers = "prod_fresh_basics" in researchedUpgrades
    val isManager = def.key == "manager"
    val canHireManagers = currentStoreSize.ordinal >= StoreSize.SMALL_GROCERY.ordinal

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .statusBarsPadding()
            .padding(16.dp)
    ) {

        Text(
            text = "‹ Back",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryDark,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 4.dp, horizontal = 2.dp)
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(def.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Primary)
            Spacer(Modifier.width(12.dp))
            Text(def.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PrimaryDark)
        }

        if (def.tierPerks.isNotEmpty()) {
            val highestTier = state.registry.getByDef(def)
                .maxOfOrNull { it.tier } ?: Tier.BASE
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((tier, perk) in def.tierPerks) {
                    val isActive = highestTier.ordinal >= tier.ordinal
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        color = if (isActive) SuccessChipSurface else InfoChipSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val tierLabel = when (tier) {
                                Tier.BASE -> "T1"
                                Tier.FAST -> "T2"
                                Tier.MANAGER -> "T3"
                            }
                            Text(
                                text = tierLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) SuccessTextDark else PrimaryDark,
                            )
                            Text(
                                text = perk,
                                fontSize = 11.sp,
                                color = if (isActive) SuccessTextDark else PrimaryDark,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Show tier requirement message for Fresh Handlers
        if (isFreshHandlers && !canHireFreshHandlers) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = ErrorSurface)
            ) {
                Text(
                    text = "Unlock the Fresh tab (Tier 2) to hire Fresh Handlers",
                    fontSize = 14.sp,
                    color = DestructiveDark,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Show tier requirement message for Managers
        if (isManager && !canHireManagers) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = ErrorSurface)
            ) {
                Text(
                    text = "Upgrade to Small Grocery to hire Managers",
                    fontSize = 14.sp,
                    color = DestructiveDark,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Button(
            onClick = { onHire(def) },
            enabled = (!isFreshHandlers || canHireFreshHandlers) && (!isManager || canHireManagers),
            colors = GameButtonStyles.primaryBlueColor(),
            shape = GameButtonStyles.Shape,
            border = GameButtonStyles.PrimaryBorder,
            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            )
        ) {
            Text("Hire ${def.displayName}", color = TextWhite)
        }

        Spacer(Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(entities) { entity ->
                HiredEntityCard(
                    entity = entity,
                    activity = state.employeeActivities[entity.id] ?: EmployeeActivity.OFF_SHIFT,
                    onFire = { onFire(entity.id) },
                    canAffordUpgrade = entity.canPromote && money >= entity.upgradeCost &&
                        !(entity.entityDefinition == EntityDef.MANAGER &&
                            entity.tier == com.example.superstoresimulator.domain.Entities.Tier.FAST &&
                            state.registry.getByDef(EntityDef.MANAGER).any { it.isStoreManager }),
                    onUpgrade = { onUpgrade(entity.id) }
                )
            }
        }
    }
}

@Composable
fun HiredEntityCard(
    entity: HiredEntity,
    activity: EmployeeActivity = EmployeeActivity.OFF_SHIFT,
    onFire: (Int) -> Unit,
    canAffordUpgrade: Boolean,
    onUpgrade: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tierLabel = when (entity.tier) {
        com.example.superstoresimulator.domain.Entities.Tier.BASE -> entity.entityDefinition.displayName
        com.example.superstoresimulator.domain.Entities.Tier.FAST -> if (entity.entityDefinition == EntityDef.MANAGER) "Senior Manager"
            else "Fast ${entity.entityDefinition.displayName}"
        com.example.superstoresimulator.domain.Entities.Tier.MANAGER -> if (entity.isStoreManager) "Store Manager"
            else "Dept. Manager"
    }
    val tierColor = when (entity.tier) {
        com.example.superstoresimulator.domain.Entities.Tier.BASE -> Primary
        com.example.superstoresimulator.domain.Entities.Tier.FAST -> Violet
        com.example.superstoresimulator.domain.Entities.Tier.MANAGER -> CautionDark
    }
    val thresholds = entity.entityDefinition.xpThresholds
    val xpForNextLevel = thresholds.getOrNull(entity.level - 1) ?: Int.MAX_VALUE
    val xpAtCurrentLevel = if (entity.level > 1) thresholds[entity.level - 2] else 0
    val xpProgress = if (entity.level >= HiredEntity.MAX_LEVEL) 1f else {
        ((entity.xp - xpAtCurrentLevel).toFloat() / (xpForNextLevel - xpAtCurrentLevel)).coerceIn(0f, 1f)
    }
    val canPromote = entity.canPromote && canAffordUpgrade
    val atMaxTier = entity.tier == com.example.superstoresimulator.domain.Entities.Tier.MANAGER

    Card(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = entity.entityDefinition.icon,
                    contentDescription = null,
                    tint = tierColor,
                    modifier = Modifier.size(36.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = entity.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            color = tierColor.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = tierLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tierColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = "Lv ${entity.level}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SubtleText,
                        )
                    }
                }
            }

            // Activity status chip
            ActivityChip(activity)

            // XP bar
            if (!atMaxTier || entity.level < HiredEntity.MAX_LEVEL) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (entity.level >= HiredEntity.MAX_LEVEL) "Max level" else "XP: ${entity.xp} / $xpForNextLevel",
                            fontSize = 11.sp,
                            color = SubtleText,
                        )
                        Text(text = entity.trait.description, fontSize = 11.sp, color = SubtleText)
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { xpProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = tierColor,
                        trackColor = ProgressBarTrack,
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val promoteLabel = when {
                    atMaxTier -> "Max Tier"
                    !entity.canPromote -> "Promote (Lv ${HiredEntity.PROMOTE_UNLOCK_LEVEL} req.)"
                    else -> "Promote (${entity.upgradeCost})"
                }
                Button(
                    onClick = { onUpgrade(entity.id) },
                    modifier = Modifier.weight(1f),
                    colors = GameButtonStyles.primaryBlueColor(),
                    shape = GameButtonStyles.Shape,
                    border = GameButtonStyles.PrimaryBorder,
                    enabled = canPromote && !atMaxTier
                ) {
                    Text(promoteLabel, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onFire(entity.id) },
                    modifier = Modifier.weight(1f),
                    colors = GameButtonStyles.outlinedDestructiveColors(),
                    shape = GameButtonStyles.Shape,
                    border = GameButtonStyles.DestructiveBorder
                ) {
                    Text("Fire")
                }
            }
        }
    }
}

@Composable
private fun ActivityChip(activity: EmployeeActivity) {
    val label = when (activity) {
        EmployeeActivity.CASHIERING -> "Cashiering"
        EmployeeActivity.WAITING_FOR_CUSTOMER -> "Waiting for customer"
        EmployeeActivity.STOCKING -> "Stocking"
        EmployeeActivity.ZONING -> "Zoning shelves"
        EmployeeActivity.HANDLING_FRESH -> "Handling fresh"
        EmployeeActivity.RESEARCHING -> "Researching"
        EmployeeActivity.CONSULTING -> "Consulting"
        EmployeeActivity.IDLE -> "Idle"
        EmployeeActivity.OFF_SHIFT -> "Off shift"
    }
    com.example.superstoresimulator.ui.components.common.ActivityChip(label, activity)
}

@Composable
fun StaffEfficiencySection(
    cashierUtil: Float,
    stockerUtil: Float,
    freshUtil: Float,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Staff Efficiency", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ChipTextDark)
            UtilizationBar("Cashiers", cashierUtil)
            UtilizationBar("Stockers", stockerUtil)
            UtilizationBar("Fresh Handlers", freshUtil)
        }
    }
}

@Composable
private fun UtilizationBar(label: String, utilization: Float) {
    val pct = (utilization * 100).toInt().coerceIn(0, 100)
    val barColor = when {
        pct >= 90 -> Destructive
        pct >= 70 -> Amber
        else -> Secondary
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.sp, color = SubtleText)
            Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ChipTextDark)
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { utilization.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = barColor,
            trackColor = ProgressBarTrack,
        )
    }
}


/**
 * Container that hosts the Staff, Schedule, and Unlocks tabs together so the bottom
 * nav bar stays at exactly 5 items.
 */
@Composable
fun StaffAndUnlocksScreen(
    staffState: StaffUIState,
    research: ResearchUIState,
    vendorState: VendorUIState,
    money: Money,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    onTabChanged: (Int) -> Unit,
    currentStoreSize: StoreSize,
    onHireStaff: (EntityDef) -> Unit,
    onFireStaff: (Int) -> Unit,
    onPromoteStaff: (Int) -> Unit,
    onUnlockNextVendorTier: () -> Unit,
    onInvestInVendor: (String) -> Unit,
    onUpdateShift: (entityId: Int, newStartHour: Int, newDuration: Int) -> Unit = { _, _, _ -> },
    onUpdateStoreManagerConfig: (StoreManagerConfig) -> Unit = {},
    onAssignAnalyst: (entityId: Int, assignment: AnalystAssignment?) -> Unit = { _, _ -> },
) {    val tabs = listOf("Staff", "Schedule", "Unlocks", "Vendors")

    // Pager state for tab navigation
    val pagerState = rememberPagerState(
        pageCount = { 4 },
        initialPage = initialTab
    )
    val coroutineScope = rememberCoroutineScope()

    // Track selected tab - start with initialTab and update from pager or external changes
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // Immediately update selectedTab when initialTab changes (e.g., from tier card navigation)
    if (selectedTab != initialTab) {
        selectedTab = initialTab
    }

    // Sync pager to selectedTab when it changes
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    // Update selectedTab when user swipes to a different page
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && selectedTab != pagerState.currentPage) {
            selectedTab = pagerState.currentPage
            onTabChanged(pagerState.currentPage)
        }
    }

    Column(modifier = modifier
        .fillMaxSize()
        .background(LightBackground)
        .statusBarsPadding()
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardWhite,
            contentColor = PrimaryDark,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        onTabChanged(index)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextSecondary
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> StaffScreen(
                    state = staffState,
                    money = money,
                    researchedUpgrades = research.researchedUpgrades,
                    currentStoreSize = currentStoreSize,
                    onHire = onHireStaff,
                    onFire = onFireStaff,
                    onUpgrade = onPromoteStaff,
                    onUpdateStoreManagerConfig = onUpdateStoreManagerConfig,
                )
                1 -> ScheduleScreen(
                    scheduleEntries = staffState.scheduleEntries,
                    onUpdateShift = onUpdateShift,
                )
                2 -> UnlocksScreen(
                    research = research,
                    money = money,
                    onAssignAnalyst = onAssignAnalyst,
                )
                3 -> VendorsScreen(
                    vendorState = vendorState,
                    money = money,
                    onUnlockNextTier = onUnlockNextVendorTier,
                    onInvestInVendor = onInvestInVendor,
                )
            }
        }
    }
}
