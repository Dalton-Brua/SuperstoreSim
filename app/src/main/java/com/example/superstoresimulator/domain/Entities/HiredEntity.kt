package com.example.superstoresimulator.domain.Entities

data class HiredEntity(
    val id: Int,
    val name: String,
    val entityDefinition: EntityDef,
    val entityType: EntityType,
    val trait: EntityTrait,
)
