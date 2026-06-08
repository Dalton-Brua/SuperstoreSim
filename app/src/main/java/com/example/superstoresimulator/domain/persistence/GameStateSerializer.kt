package com.example.superstoresimulator.domain.persistence

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.SAVE_VERSION
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.json.JSONObject

object GameStateSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(state: GameState): String =
        json.encodeToString(state)

    @Suppress("DEPRECATION")
    fun deserialize(raw: String): GameState? = runCatching {
        val probe = JSONObject(raw)
        if (!probe.has("saveVersion")) {
            return LegacyGameStateDeserializer.deserialize(raw)
                ?.copy(saveVersion = SAVE_VERSION)
        }
        json.decodeFromString<GameState>(raw)
    }.getOrNull()
}
