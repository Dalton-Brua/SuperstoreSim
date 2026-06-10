package com.example.superstoresimulator.domain.items

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Metadata Cache Layer
 * 
 * SINGLE SOURCE OF TRUTH for all item data. Loads from database ONCE at startup.
 * - Caches item metadata (name, price, category) for 60 FPS inventory rendering
 * - Also caches full Item objects for GameEngine and other components
 * - Replaces 3+ redundant database calls with a single load
 * 
 * Architecture: All components that need item data should call initialize() once,
 * then use get() or getItem() for metadata/full data lookups.
 */
class ItemMetadataCache(private val itemDao: ItemDao) {
    
    private var metadataCache: Map<Int, ItemMetadata> = emptyMap()
    private var itemsCache: Map<Int, Item> = emptyMap()
    private var itemNamesCache: Map<Int, String> = emptyMap()
    private var initialized = false
    
    /**
     * Initialize cache from database (call once on app startup)
     * SINGLE entry point - all components should use this
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        
        val items = itemDao.getAllItems()
        
        // Build all three caches from a single database load
        val metadata = mutableMapOf<Int, ItemMetadata>()
        val fullItems = mutableMapOf<Int, Item>()
        val names = mutableMapOf<Int, String>()
        
        items.forEach { item ->
            val itemId = item.id.removePrefix("item_").toIntOrNull() ?: 0
            
            metadata[itemId] = ItemMetadata(
                id = itemId,
                name = item.name,
                price = item.getPriceAsMoney(),
                unitCost = item.getUnitCostAsMoney(),
                category = item.category,
                casePack = item.casePack,
                casePackCost = item.getCasePackCostAsMoney(),
                purchaseWeight = item.purchaseWeight,
                tier = runCatching { ItemUnlockTier.valueOf(item.tier) }.getOrDefault(ItemUnlockTier.TIER_1),
                shelfLifeDays = item.shelfLifeDays,
                soldByWeight = item.soldByWeight,
            )
            fullItems[itemId] = item
            names[itemId] = item.name
        }
        
        metadataCache = metadata
        itemsCache = fullItems
        itemNamesCache = names
        initialized = true
    }
    
    /**
     * Get cached metadata for an item (O(1) lookup)
     * Used by inventory UI for fast name/price/category display
     */
    fun get(itemId: Int): ItemMetadata? = metadataCache[itemId]
    
    /**
     * Get full Item object for an item (O(1) lookup)
     * Used by GameEngine and other components that need full item data
     */
    fun getItem(itemId: Int): Item? = itemsCache[itemId]
    
    /**
     * Get all cached item names as Map<Int, String>
     * Used by InventoryScreen instead of separate database query
     */
    fun getAllItemNames(): Map<Int, String> = itemNamesCache
    
    /**
     * Get all cached items as Map<Int, Item>
     * Used by GameEngine initialization instead of separate database query
     */
    fun getAllItems(): Map<Int, Item> = itemsCache
    
    /**
     * Check if cache is initialized
     */
    fun isInitialized(): Boolean = initialized
}

