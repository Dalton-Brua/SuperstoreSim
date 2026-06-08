package com.example.superstoresimulator.domain.player

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerRole {
    MANAGE,
    CASHIER,
    STOCKER;

    companion object {
        @JvmStatic
        fun fromLegacyName(name: String): PlayerRole = when (name) {
            "NONE" -> MANAGE
            else -> valueOf(name)
        }
    }
}
