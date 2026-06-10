package com.example.superstoresimulator.domain.items

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemById(itemId: String): Item?

    suspend fun getItemById(itemId: Int) : Item? {
        return getItemById(itemId.toString())
    }
    @Query("SELECT * FROM items")
    suspend fun getAllItems(): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item)

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    // Query for item name by itemId
    @Query("SELECT name FROM items WHERE id = :itemId")
    suspend fun getItemName(itemId: String): String?

    @Query("SELECT * FROM items WHERE id IN (:itemIds)")
    suspend fun getItemsByIds(itemIds: List<String>): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(items: List<Item>)

    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemCount(): Int

    @Query("SELECT * FROM items WHERE category = :category ORDER BY name ASC")
    suspend fun getItemsByCategory(category: String): List<Item>

    @Query("SELECT * FROM items WHERE name LIKE :searchTerm ORDER BY name ASC LIMIT 20")
    suspend fun searchItemsByName(searchTerm: String): List<Item>

    @Query("""
        SELECT items.* FROM items 
        WHERE items.id IN (:itemIds)
    """)
    suspend fun getItemsForInventory(itemIds: List<String>): List<Item>
}
