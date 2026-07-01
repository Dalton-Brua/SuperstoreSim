package com.example.superstoresimulator.domain.items

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemMetadataCache @Inject constructor(private val itemDao: ItemDao) {

    private var metadataCache: Map<Int, ItemMetadata> = emptyMap()
    private var itemsCache: Map<Int, Item> = emptyMap()
    private var itemNamesCache: Map<Int, String> = emptyMap()
    private var initialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val items = itemDao.getAllItems()

        val metadata = mutableMapOf<Int, ItemMetadata>()
        val fullItems = mutableMapOf<Int, Item>()
        val names = mutableMapOf<Int, String>()

        items.forEach { item ->
            val itemId = item.id.removePrefix("item_").toIntOrNull() ?: 0
            val groups = item.affinityGroups
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            metadata[itemId] = ItemMetadata(
                id = itemId,
                name = item.name,
                price = item.getPriceAsMoney(),
                unitCost = item.getUnitCostAsMoney(),
                category = item.category,
                casePack = item.casePack,
                casePackCost = item.getCasePackCostAsMoney(),
                purchaseWeight = item.purchaseWeight,
                researchGate = item.researchGate,
                affinityGroups = groups,
                substitutionGroup = item.substitutionGroup,
                shelfLifeDays = item.shelfLifeDays,
                soldByWeight = item.soldByWeight,
                vendorId = item.vendorId,
                vendorTier = item.vendorTier,
            )
            fullItems[itemId] = item
            names[itemId] = item.name
        }

        metadataCache = metadata
        itemsCache = fullItems
        itemNamesCache = names
        initialized = true
    }

    fun get(itemId: Int): ItemMetadata? = metadataCache[itemId]

    fun getItem(itemId: Int): Item? = itemsCache[itemId]

    fun getAllItemNames(): Map<Int, String> = itemNamesCache

    fun getAllItems(): Map<Int, Item> = itemsCache

    fun isItemAccessible(itemId: Int, researchedUpgrades: Set<String>): Boolean {
        val meta = get(itemId) ?: return false
        return meta.researchGate == null || meta.researchGate in researchedUpgrades
    }

    fun sharesAffinityGroup(id1: Int, id2: Int): Boolean {
        val g1 = get(id1)?.affinityGroups ?: return false
        val g2 = get(id2)?.affinityGroups ?: return false
        return g1.any { it in g2 }
    }

    fun sharesSubstitutionGroup(id1: Int, id2: Int): Boolean {
        val s1 = get(id1)?.substitutionGroup ?: return false
        val s2 = get(id2)?.substitutionGroup ?: return false
        return s1 == s2
    }

    fun getVendorItems(vendorId: String): List<ItemMetadata> =
        metadataCache.values.filter { it.vendorId == vendorId }

    fun getVendorItemsByTier(vendorId: String, maxTier: Int): List<ItemMetadata> =
        metadataCache.values.filter { it.vendorId == vendorId && it.vendorTier <= maxTier }

    fun isInitialized(): Boolean = initialized
}
