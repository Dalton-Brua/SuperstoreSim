package com.example.superstoresimulator.domain.helpers

import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.Item

class FakeItemDao(private val items: List<Item>) : ItemDao {
    override suspend fun getAllItems(): List<Item> = items
    override suspend fun getItemById(itemId: String): Item? = items.firstOrNull { it.id == itemId }
    override suspend fun insertItem(item: Item) {}
    override suspend fun deleteItem(itemId: String) {}
    override suspend fun deleteAll() {}
    override suspend fun getItemName(itemId: String): String? = items.firstOrNull { it.id == itemId }?.name
    override suspend fun getAllItemsWithNames(): List<ItemWithName> = items.map { ItemWithName(it.id, it.name) }
    override suspend fun getItemsByIds(itemIds: List<String>): List<Item> = items.filter { it.id in itemIds }
    override suspend fun insertBatch(items: List<Item>) {}
    override suspend fun getItemCount(): Int = items.size
    override suspend fun getItemsByCategory(category: String): List<Item> = items.filter { it.category.name == category }
    override suspend fun searchItemsByName(searchTerm: String): List<Item> = items.filter { it.name.contains(searchTerm, ignoreCase = true) }
    override suspend fun getItemsForInventory(itemIds: List<String>): List<Item> = items.filter { it.id in itemIds }
}
