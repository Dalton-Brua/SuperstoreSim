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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.state.ProgressionUIState
import com.example.superstoresimulator.ui.state.StaffUIState
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Primary
import com.yourapp.ui.theme.GameButtonStyles
import kotlinx.coroutines.launch

@Composable
fun StaffScreen(
    state: StaffUIState,
    money: Money,
    onSelectStaffDef: (EntityDef?) -> Unit,
    modifier: Modifier = Modifier
) {
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
            items(EntityDef.allEntities) { def ->
                StaffTypeCard(
                    def = def,
                    count = state.registry.countByDef(def),
                    onClick = { onSelectStaffDef(def) }
                )
            }
            item {
                Spacer(Modifier.height(6.dp))
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    tint = Color(0xFF3B82F6),
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
                Text("Hired: $count", fontSize = 14.sp, color = Color(0xFF475569))
                Text(def.roleDescription, fontSize = 14.sp, color = Color(0xFF475569))
            }
        }
    }
}

@Composable
fun EntityTypeDetailScreen(
    state: StaffUIState,
    money: Money,
    def: EntityDef?,
    currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1,
    onHire: (EntityDef) -> Unit,
    onFire: (Int) -> Unit,
    onUpgrade: (Int) -> Unit,
    onBack: () -> Unit
) {
    if (def == null) return

    val entities = state.registry.getByDef(def)
    val isFreshHandlers = def.key == "fresh_handler"
    val canHireFreshHandlers = currentTier.unlockAmount >= ItemUnlockTier.TIER_2.unlockAmount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .statusBarsPadding()
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(def.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Primary)
            Spacer(Modifier.width(12.dp))
            Text(def.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PrimaryDark)
        }

        Spacer(Modifier.height(12.dp))
        
        // Show tier requirement message for Fresh Handlers
        if (isFreshHandlers && !canHireFreshHandlers) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
            ) {
                Text(
                    text = "Unlock the Fresh tab (Tier 2) to hire Fresh Handlers",
                    fontSize = 14.sp,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Button(
            onClick = { onHire(def) },
            enabled = money >= def.cost && (!isFreshHandlers || canHireFreshHandlers),
            colors = GameButtonStyles.primaryBlueColor(),
            shape = GameButtonStyles.Shape,
            border = GameButtonStyles.PrimaryBorder,
            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            )
        ) {
            Text("Hire ${def.displayName}", color = Color.White)
        }

        Spacer(Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(entities) { entity ->
                HiredEntityCard(
                    entity = entity,
                    onFire = { onFire(entity.id) },
                    canAffordUpgrade = entity.canPromote && money >= entity.upgradeCost,
                    onUpgrade = { onUpgrade(entity.id) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onBack,
            colors = GameButtonStyles.primaryBlueColor(),
            shape = GameButtonStyles.Shape,
            border = GameButtonStyles.PrimaryBorder
        ) {
            Text("Back", color = Color.White)
        }
    }
}

@Composable
fun HiredEntityCard(
    entity: HiredEntity,
    onFire: (Int) -> Unit,
    canAffordUpgrade: Boolean,
    onUpgrade: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tierLabel = when (entity.tier) {
        com.example.superstoresimulator.domain.Entities.Tier.BASE -> entity.entityDefinition.displayName
        com.example.superstoresimulator.domain.Entities.Tier.FAST -> "Fast ${entity.entityDefinition.displayName}"
        com.example.superstoresimulator.domain.Entities.Tier.MANAGER -> "Dept. Manager"
    }
    val tierColor = when (entity.tier) {
        com.example.superstoresimulator.domain.Entities.Tier.BASE -> Color(0xFF3B82F6)
        com.example.superstoresimulator.domain.Entities.Tier.FAST -> Color(0xFF8B5CF6)
        com.example.superstoresimulator.domain.Entities.Tier.MANAGER -> Color(0xFFD97706)
    }
    val xpForNextLevel = HiredEntity.XP_THRESHOLDS.getOrNull(entity.level - 1) ?: Int.MAX_VALUE
    val xpAtCurrentLevel = if (entity.level > 1) HiredEntity.XP_THRESHOLDS[entity.level - 2] else 0
    val xpProgress = if (entity.level >= HiredEntity.MAX_LEVEL) 1f else {
        ((entity.xp - xpAtCurrentLevel).toFloat() / (xpForNextLevel - xpAtCurrentLevel)).coerceIn(0f, 1f)
    }
    val canPromote = entity.canPromote && canAffordUpgrade
    val atMaxTier = entity.tier == com.example.superstoresimulator.domain.Entities.Tier.MANAGER

    Card(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                            color = Color(0xFF475569),
                        )
                    }
                }
            }

            // XP bar
            if (!atMaxTier || entity.level < HiredEntity.MAX_LEVEL) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (entity.level >= HiredEntity.MAX_LEVEL) "Max level" else "XP: ${entity.xp} / $xpForNextLevel",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                        )
                        Text(text = entity.trait.description, fontSize = 11.sp, color = Color(0xFF475569))
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { xpProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = tierColor,
                        trackColor = Color(0xFFE2E8F0),
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

/**
 * Container that hosts the Staff, Schedule, and Unlocks tabs together so the bottom
 * nav bar stays at exactly 5 items.
 */
@Composable
fun StaffAndUnlocksScreen(
    staffState: StaffUIState,
    progression: ProgressionUIState,
    money: Money,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    onTabChanged: (Int) -> Unit,
    onSelectStaffDef: (EntityDef?) -> Unit,
    onUnlockNextTier: () -> Unit,
    onUpdateShift: (entityId: Int, newStartHour: Int) -> Unit = { _, _ -> },
) {    val tabs = listOf("Staff", "Schedule", "Unlocks")

    // Pager state for tab navigation
    val pagerState = rememberPagerState(
        pageCount = { 3 },
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
            containerColor = Color.White,
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
                    unselectedContentColor = Color(0xFF64748B)
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
                    onSelectStaffDef = onSelectStaffDef
                )
                1 -> ScheduleScreen(
                    scheduleEntries = staffState.scheduleEntries,
                    onUpdateShift = onUpdateShift,
                )
                2 -> UnlocksScreen(
                    progression = progression,
                    money = money,
                    onUnlockNextTier = onUnlockNextTier,
                )
            }
        }
    }
}
