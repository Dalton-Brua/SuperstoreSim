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
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Money
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
    onSelectStaffType: (EntityType) -> Unit,
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
            items(EntityType.allEntityTypes.filter { it != EntityType.NONE }) { type ->
                StaffTypeCard(
                    type = type,
                    count = state.registry.countByType(type),
                    onClick = { onSelectStaffType(type) }
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
    type: EntityType,
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

            // Title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = type.displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark
                )
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hired: $count", fontSize = 14.sp, color = Color(0xFF475569))
                Text(type.description, fontSize = 14.sp, color = Color(0xFF475569))
            }
        }
    }
}

@Composable
fun EntityTypeDetailScreen(
    state: StaffUIState,
    money: Money,
    type: EntityType,
    onHire: (EntityDef) -> Unit,
    onFire: (Int) -> Unit,
    onUpgrade: (Int) -> Unit,
    onBack: () -> Unit
) {
    // Guard: selectedType hasn't been set yet (brief first-frame race with NONE default)
    val hireable = type.entities.firstOrNull()
    if (type == EntityType.NONE || hireable == null) return

    val entities = state.registry.getByType(type)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .statusBarsPadding()
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(type.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Primary)
            Spacer(Modifier.width(12.dp))
            Text(type.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PrimaryDark)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onHire(hireable) },
            enabled = money >= hireable.cost,
            colors = GameButtonStyles.primaryBlueColor(),
            shape = GameButtonStyles.Shape,
            border = GameButtonStyles.PrimaryBorder,
            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            )
        ) {
            Text("Hire ${hireable.displayName}", color = Color.White)
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
                    canAffordUpgrade = money > (entity.entityDefinition.nextUpgrade?.cost ?: Money.ZERO),
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Title row (icon + name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = entity.entityType.icon,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(36.dp)
                )

                Column {
                    Text(
                        text = entity.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "ID: ${entity.id}",
                        fontSize = 14.sp,
                        color = Color(0xFF475569)
                    )

                    // ⭐ NEW: Entity type label (Cashier, Fast Cashier, etc.)
                    Text(
                        text = entity.entityDefinition.displayName,   // or entity.entityType.displayName
                        fontSize = 14.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Stats row (trait + type description)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entity.trait.description,
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
                Text(
                    text = entity.entityType.description,
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onUpgrade(entity.id) },
                    modifier = Modifier.weight(1f),
                    colors = GameButtonStyles.primaryBlueColor(),
                    shape = GameButtonStyles.Shape,
                    border = GameButtonStyles.PrimaryBorder,
                    enabled = canAffordUpgrade
                ) {
                    Text("Upgrade")
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
 * Container that hosts the Staff and Unlocks tabs together so the bottom
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
    onSelectStaffType: (EntityType) -> Unit,
    onUnlockNextTier: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("Staff", "Unlocks")
    
    // Pager state for tab navigation
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    
    // Sync pager with selectedTab
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    
    // Sync selectedTab with pager
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) {
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
                    onSelectStaffType = onSelectStaffType
                )
                1 -> UnlocksScreen(
                    progression = progression,
                    money = money,
                    onUnlockNextTier = onUnlockNextTier,
                )
            }
        }
    }
}
