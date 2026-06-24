package com.example.superstoresimulator.domain.Entities

import kotlinx.serialization.Serializable

@Serializable
enum class EntityTrait(val description: String) {
    EFFICIENT("-10% wage cost"),
    HARDWORKER("+10% action speed"),
    QUICK_LEARNER("1.5× XP gain"),
    VETERAN("Starts at level 3");

    val throughputMultiplier: Float get() = when (this) {
        HARDWORKER -> 1.10f
        else -> 1.0f
    }

    val xpMultiplier: Float get() = when (this) {
        QUICK_LEARNER -> 1.5f
        else -> 1.0f
    }
}