package com.example.superstoresimulator.domain.persistence

import android.content.Context
import android.content.SharedPreferences
import com.example.superstoresimulator.domain.GameState

/**
 * Repository for saving and loading GameState using SharedPreferences.
 * Uses GameStateSerializer to convert state to/from JSON.
 * 
 * Pattern: All save/load operations are synchronous but lightweight (JSON string I/O).
 * Called from ViewModel on background thread via coroutine.
 */
class GameStateRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    /**
     * Saves the current game state to persistent storage.
     * Returns true if successful, false otherwise.
     */
    fun saveGameState(state: GameState): Boolean {
        return try {
            val json = GameStateSerializer.serialize(state)
            prefs.edit()
                .putString(KEY_GAME_STATE, json)
                .putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Loads the saved game state from persistent storage.
     * Returns null if no save exists or if deserialization fails.
     */
    fun loadGameState(): GameState? {
        return try {
            val json = prefs.getString(KEY_GAME_STATE, null) ?: return null
            GameStateSerializer.deserialize(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Checks if a saved game exists.
     */
    fun hasSavedGame(): Boolean {
        return prefs.contains(KEY_GAME_STATE)
    }
    
    /**
     * Gets the timestamp of the last save (in milliseconds since epoch).
     * Returns null if no save exists.
     */
    fun getLastSaveTime(): Long? {
        if (!hasSavedGame()) return null
        val time = prefs.getLong(KEY_LAST_SAVE_TIME, 0)
        return if (time > 0) time else null
    }
    
    /**
     * Clears all saved game data.
     * Use with caution - this cannot be undone.
     */
    fun clearSave(): Boolean {
        return try {
            prefs.edit()
                .remove(KEY_GAME_STATE)
                .remove(KEY_LAST_SAVE_TIME)
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    companion object {
        private const val PREFS_NAME = "superstore_save_data"
        private const val KEY_GAME_STATE = "game_state_json"
        private const val KEY_LAST_SAVE_TIME = "last_save_timestamp"
    }
}

