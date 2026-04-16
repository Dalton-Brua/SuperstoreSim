package com.example.superstoresimulator.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.components.cards.InventoryItemCard
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.dialogs.BulkOrderDialog
import com.example.superstoresimulator.ui.state.InventoryUIState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@Composable
fun InventoryScreen(
    state: InventoryUIState,
    money: Money,
    itemMetadataCache: ItemMetadataCache,
    currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1,
    onBuyItem: (Int) -> Unit,
    onSelectCategory: (ItemCategory?) -> Unit,
    onBulkOrder: (maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // State to hold item names from cache
    val itemNames = remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    
    // State to hold search query
    val searchQuery = remember { mutableStateOf("") }
    
    // State for debounced search query
    val debouncedSearchQuery = remember { mutableStateOf("") }
    
    // Dialog state
    var showBulkOrderDialog by remember { mutableStateOf(false) }
    
    // Debounce search query with 300ms delay
    LaunchedEffect(searchQuery.value) {
        val job: Job? = null
        if (searchQuery.value.isEmpty()) {
            debouncedSearchQuery.value = ""
        } else {
            delay(300)  // Wait 300ms before applying search
            debouncedSearchQuery.value = searchQuery.value
        }
    }
    
    // Load item names from cache instead of database query
    LaunchedEffect(Unit) {
        try {
            // Get cached item names (no database access)
            itemNames.value = itemMetadataCache.getAllItemNames()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val listState = rememberLazyListState()
    
    // Recompute filtered items whenever category, focused item, or debounced search query changes
    val filteredItems = remember(state.items, state.selectedCategory, state.focusedItemId, debouncedSearchQuery.value, itemNames.value) {
        var filtered = if (state.focusedItemId != null) {
            // If an item is focused, show only that item
            state.items.filter { item -> item.id == state.focusedItemId }
        } else {
            // Otherwise filter by category
            state.items.filter { item ->
                state.selectedCategory == null || item.category == state.selectedCategory
            }
        }
        
        // Apply search filter if debounced search query is not empty
        if (debouncedSearchQuery.value.isNotEmpty()) {
            filtered = filtered.filter { item ->
                val itemName = itemNames.value[item.id] ?: item.name
                itemName.contains(debouncedSearchQuery.value, ignoreCase = true)
            }
        }
        
        filtered
    }

    // Scroll to focused item when it becomes available
    LaunchedEffect(state.focusedItemId, filteredItems) {
        val targetId = state.focusedItemId ?: return@LaunchedEffect
        val index = filteredItems.indexOfFirst { it.id == targetId }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    // Show bulk order dialog when requested
    if (showBulkOrderDialog) {
        BulkOrderDialog(
            allItems = state.items,
            money = money,
            onConfirm = { maxQty, casePacks, category ->
                onBulkOrder(maxQty, casePacks, category)
                showBulkOrderDialog = false
            },
            onDismiss = { showBulkOrderDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .padding(24.dp),
    ) {
        // ── Header row: title + bulk order button ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ScreenHeader(
                title = "Inventory",
                money = money
            )

            // Only show Bulk Order button if TIER_2 or above is unlocked
            if (currentTier.unlockAmount >= ItemUnlockTier.TIER_2.unlockAmount) {
                Button(
                    onClick = { showBulkOrderDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = "Bulk Order",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Bulk Order",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        InventoryCategoryBar(
            categories = ItemCategory.entries,
            selected = state.selectedCategory,
            onSelect = onSelectCategory
        )

        Spacer(Modifier.height(16.dp))

        // Search bar
        TextField(
            value = searchQuery.value,
            onValueChange = { searchQuery.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            placeholder = { Text("Search items...") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color(0xFF1E40AF),
                unfocusedIndicatorColor = Color(0xFFE2E8F0)
            )
        )

        Spacer(Modifier.height(16.dp))

        // Item List
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredItems) { item ->
                InventoryItemCard(
                    item = item.copy(name = itemNames.value[item.id] ?: item.name),
                    canAffordBuy = money >= item.unitCost,
                    onBuy = { onBuyItem(item.id) }
                )

            }
        }
    }
}

@Composable
fun InventoryCategoryBar(
    categories: List<ItemCategory>,
    selected: ItemCategory?,
    onSelect: (ItemCategory?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {

        item {
            CategoryChip(
                label = "All",
                isSelected = selected == null,
                onClick = { onSelect(null) }
            )
        }

        items(categories) { category ->
            CategoryChip(
                label = category.displayName,
                isSelected = selected == category,
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFF1E40AF) else Color(0xFFE2E8F0)
    val fg = if (isSelected) Color.White else Color(0xFF1E293B)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        tonalElevation = if (isSelected) 4.dp else 0.dp,
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

