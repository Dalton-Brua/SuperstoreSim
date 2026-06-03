package com.example.superstoresimulator.domain.Entities

data class HiredEntityRegistry(
    private val entities: List<HiredEntity> = emptyList(),
    private var nextEntityId: Int = 1
) {
    val hiredEntities: List<HiredEntity> get() = entities

    private val firstNames = listOf(
        "Alex", "Jordan", "Taylor", "Morgan", "Casey", "Riley", "Jamie", "Avery", "Parker", "Reese",
        "Quinn", "Rowan", "Skyler", "Dakota", "Phoenix", "River", "Jules", "Cameron", "Logan", "Hayden",
        "Finley", "Emerson", "Marley", "Lennox", "Sage", "Remy", "Ellis", "Rory", "Blake", "Drew", "Charlie",
        "Sam", "Kai", "Nico", "Arden", "Blair", "Devon", "Lane", "Tatum", "Hollis", "Perry", "Sutton", "Wynn",
        "Ethan", "Noah", "Liam", "Mason", "Oliver", "Henry", "Lucas", "Isaac", "Owen", "Caleb", "Levi",
        "Miles", "Julian", "Adrian", "Xavier", "Dominic", "Vincent", "Marcus", "Felix", "Jasper",
        "Silas", "Damian", "Elias", "Roman", "Matteo", "Jonah", "Wesley", "Grant", "Everett", "Brooks",
        "Colton", "Hunter", "Easton", "Sawyer", "Bennett", "Graham", "Maxwell", "Theodore", "Harrison",
        "Emma", "Olivia", "Ava", "Mia", "Sophia", "Amelia", "Harper", "Chloe", "Ella", "Grace",
        "Nora", "Hazel", "Violet", "Stella", "Lucy", "Aria", "Zoe", "Maya", "Ivy", "Clara",
        "Elise", "Naomi", "Ruby", "Alice", "Audrey", "Lila", "Sadie", "Piper", "Willow", "Luna",
        "Isla", "Freya", "Eliza", "Juliet", "Margo", "Daphne", "Tessa", "Mira", "Celeste"
    )

    private val lastNames = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones",
        "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
        "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
        "Thomas", "Taylor", "Moore", "Jackson", "Martin",
        "Lee", "Perez", "Thompson", "White", "Harris",
        "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson"
    )

    private fun randomName(): String = "${firstNames.random()} ${lastNames.random()}"

    fun countByDef(def: EntityDef): Int = entities.count { it.entityDefinition == def }

    fun totalCount(): Int = entities.size

    fun hireEntity(definition: EntityDef): HiredEntityRegistry {
        val trait = EntityTrait.entries.random()
        val startLevel = if (trait == EntityTrait.VETERAN) 3 else 1
        val startXp = if (trait == EntityTrait.VETERAN) definition.xpThresholds[1] else 0
        val newEntity = HiredEntity(
            id = nextEntityId,
            name = randomName(),
            entityDefinition = definition,
            trait = trait,
            level = startLevel,
            xp = startXp,
        )
        return copy(entities = entities + newEntity, nextEntityId = nextEntityId + 1)
    }

    fun promoteEntity(entityId: Int): HiredEntityRegistry {
        val updatedList = entities.map { entity ->
            if (entity.id == entityId) entity.upgrade() else entity
        }
        return copy(entities = updatedList)
    }

    fun fireEntity(entityId: Int): HiredEntityRegistry =
        copy(entities = entities.filterNot { it.id == entityId })

    fun getById(entityId: Int): HiredEntity = entities.first { it.id == entityId }

    fun getByDef(def: EntityDef): List<HiredEntity> =
        entities.filter { it.entityDefinition == def }

    fun getNextEntityId(): Int = nextEntityId

    fun grantXp(entityId: Int, rawAmount: Int): HiredEntityRegistry {
        val updatedList = entities.map { entity ->
            if (entity.id != entityId) return@map entity
            val multiplier = entity.trait.xpMultiplier
            val gained = (rawAmount * multiplier).toInt().coerceAtLeast(1)
            val newXp = entity.xp + gained
            val newLevel = computeLevel(newXp, entity.entityDefinition.xpThresholds)
            entity.copy(xp = newXp, level = newLevel)
        }
        return copy(entities = updatedList)
    }

    fun grantXpToAll(def: EntityDef, rawAmount: Int): HiredEntityRegistry {
        var updated = this
        entities.filter { it.entityDefinition == def }.forEach { entity ->
            updated = updated.grantXp(entity.id, rawAmount)
        }
        return updated
    }

    private fun computeLevel(xp: Int, thresholds: List<Int> = HiredEntity.XP_THRESHOLDS): Int {
        var level = 1
        for (threshold in thresholds) {
            if (xp >= threshold) level++ else break
        }
        return level.coerceAtMost(HiredEntity.MAX_LEVEL)
    }
}