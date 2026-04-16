package com.example.superstoresimulator.domain.Entities

data class HiredEntityRegistry(
    private val entities: List<HiredEntity> = emptyList(),
    private var nextEntityId: Int = 1

) {
    
    /** Public accessor for the hired entities list (used by wage calculator and other systems). */
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
    private fun randomName(): String {
        return "${firstNames.random()} ${lastNames.random()}"
    }

    fun countByEntity(def: EntityDef): Int =
        entities.count { it.entityDefinition == def }

    fun countByType(type: EntityType): Int =
        entities.count { it.entityType == type }

    fun totalCount(): Int = entities.size

    fun hireEntity(definition: EntityDef, type: EntityType): HiredEntityRegistry {

        val newEntity = HiredEntity(
            id = nextEntityId,
            name = randomName(),
            entityDefinition = definition,
            entityType = type,
            trait = EntityTrait.entries.random()
        )

        return copy(
            entities = entities + newEntity,
            nextEntityId = nextEntityId + 1
        )
    }

    fun upgradeEntity(entityId: Int): HiredEntityRegistry {
        val updatedList = entities.map { entity ->
            if (entity.id == entityId) {
                entity.copy(entityDefinition = entity.entityDefinition.nextUpgrade?: entity.entityDefinition) // Use nextUpgrade unless null, then do nothing
            } else entity
        }

        return copy(entities = updatedList)
    }

    fun fireEntity(entityId: Int): HiredEntityRegistry =
        copy(entities = entities.filterNot { it.id == entityId })

    fun getById(entityId: Int) : HiredEntity =
        entities.first { it.id == entityId }
    fun getByType(type: EntityType): List<HiredEntity> =
        entities.filter { it.entityType == type }
    fun getNextEntityId() : Int {
        return nextEntityId
    }

}