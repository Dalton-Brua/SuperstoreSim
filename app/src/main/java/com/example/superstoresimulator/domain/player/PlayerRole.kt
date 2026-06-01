package com.example.superstoresimulator.domain.player

/**
 * Represents what job the player is currently performing.
 *
 * Only one role is active at a time (mutually exclusive).
 * Toggling the same role again returns to MANAGE.
 *
 * MANAGE  → Player is managing the store: boosts all employee throughput by 10%
 * CASHIER → Player is working the register; items in the active transaction ring up automatically
 * STOCKER → Player is in the stockroom; backroom items are moved to shelves automatically
 */
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
