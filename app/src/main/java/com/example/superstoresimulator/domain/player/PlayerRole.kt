package com.example.superstoresimulator.domain.player

/**
 * Represents what job the player is currently performing.
 *
 * Only one role is active at a time (mutually exclusive).
 * Toggling the same role again returns to NONE.
 *
 * NONE    → Player is managing the store (observing, buying inventory, hiring staff, etc.)
 * CASHIER → Player is working the register; items in the active transaction ring up automatically
 * STOCKER → Player is in the stockroom; backroom items are moved to shelves automatically
 */
enum class PlayerRole {
    NONE,
    CASHIER,
    STOCKER
}

