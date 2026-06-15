package com.example.superstoresimulator.domain.items

import kotlinx.serialization.Serializable

@Serializable
enum class ItemCategory(val displayName: String) {
    GROCERY("Grocery"),
    DAIRY("Dairy"),
    BAKERY("Bakery"),
    PRODUCE("Produce"),
    MEAT("Meat"),
    FROZEN("Frozen"),
    SNACKS("Snacks"),
    DRINKS("Drinks"),
    HOUSEHOLD("Household"),
    HEALTH("Health"),
    PHARMACY("Pharmacy"),
    ELECTRONICS("Electronics"),
    DELI("Deli"),
    PET("Pet"),
    BABY("Baby"),
    OFFICE("Office"),
    TOYS("Toys"),
    AUTO("Auto"),
    GARDEN("Garden"),
    SEASONAL("Seasonal"),
}
