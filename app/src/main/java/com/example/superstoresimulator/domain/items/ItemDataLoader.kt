package com.example.superstoresimulator.domain.items

import android.content.Context
import com.example.superstoresimulator.R
import com.example.superstoresimulator.domain.Money
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for loading item data from JSON file into the database.
 */
object ItemDataLoader {
    /**
     * Load items from the items.json resource file and insert them into the database.
     * Always reloads from JSON so that any price/cost changes in items.json are applied on next launch.
     * Returns true if items were loaded successfully.
     */
    suspend fun loadItemsIfNeeded(context: Context, itemDao: ItemDao): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Always clear and reload — the Item table is a read-only catalog,
                // so wiping it ensures items.json is always the source of truth.
                itemDao.deleteAll()

                // Load JSON from raw resources
                val jsonString = context.resources.openRawResource(R.raw.items)
                    .bufferedReader()
                    .use { it.readText() }

                // Parse JSON
                val jsonObject = JSONObject(jsonString)
                val itemsArray = jsonObject.getJSONArray("items")
                val items = mutableListOf<Item>()

                for (i in 0 until itemsArray.length()) {
                    val itemJson = itemsArray.getJSONObject(i)
                    val item = Item(
                        id = itemJson.getString("id"),
                        name = itemJson.getString("name"),
                        price = MoneyData.fromMoney(Money.fromCents(itemJson.getInt("price").toLong())),
                        description = itemJson.getString("description"),
                        unitCost = MoneyData.fromMoney(Money.fromCents(itemJson.getInt("unitCost").toLong())),
                        category = ItemCategory.valueOf(itemJson.getString("category")),
                        casePack = if (itemJson.has("casePack")) itemJson.getInt("casePack") else 1,
                        purchaseWeight = if (itemJson.has("purchaseWeight")) itemJson.getDouble("purchaseWeight").toFloat() else 1.0f,
                        researchGate = if (itemJson.has("researchGate")) itemJson.getString("researchGate") else null,
                        affinityGroups = if (itemJson.has("affinityGroups")) itemJson.getString("affinityGroups") else null,
                        substitutionGroup = if (itemJson.has("substitutionGroup")) itemJson.getString("substitutionGroup") else null,
                        tier = if (itemJson.has("tier")) itemJson.getString("tier") else "TIER_1",
                        shelfLifeDays = if (itemJson.has("shelfLifeDays")) itemJson.getInt("shelfLifeDays") else null,
                        soldByWeight = if (itemJson.has("soldByWeight")) itemJson.getBoolean("soldByWeight") else false,
                        vendorId = if (itemJson.has("vendorId")) itemJson.getString("vendorId") else null,
                        vendorTier = if (itemJson.has("vendorTier")) itemJson.getInt("vendorTier") else -1,
                    )
                    items.add(item)
                }

                // Insert all items in a single batch operation
                if (items.isNotEmpty()) {
                    itemDao.insertBatch(items)
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}

