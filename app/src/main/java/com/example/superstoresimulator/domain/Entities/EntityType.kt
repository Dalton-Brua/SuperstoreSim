package com.example.superstoresimulator.domain.Entities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.superstoresimulator.domain.Entities.EntityDef


data class EntityType(
    val key: String,  // Unique identifier for serialization
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val entities: List<EntityDef>,

    ) {
    companion object {
        val NONE = EntityType(
            key = "NONE",
            displayName = "",
            description = "",
            icon = Icons.Default.Person,
            entities = emptyList()
        )

        val CASHIERS = EntityType(
            key = "CASHIERS",
            displayName = "Cashiers",
            description = "Rings up items",
            icon = Icons.Default.Person,
            entities = listOf(
                EntityDef.CASHIER,
                EntityDef.FAST_CASHIER,
            )
        )

        val STOCKERS = EntityType(
            key = "STOCKERS",
            displayName = "Stockers",
            description = "Stocks items",
            icon = Icons.Default.Build,
            entities = listOf(
                EntityDef.STOCKER,
                EntityDef.FAST_STOCKER,
            )
        )

        val FRESH_HANDLERS = EntityType(
            key = "FRESH_HANDLERS",
            displayName = "Fresh Handlers",
            description = "Stocks perishable items",
            icon = Icons.Default.Build,
            entities = listOf(
                EntityDef.FRESH_HANDLER,
                EntityDef.FAST_FRESH_HANDLER,
            )
        )

        /*val CUSTOMER_SERVICE_REPRESENTATIVES = EntityType(
            key = "CUSTOMER_SERVICE_REPRESENTATIVES",
            displayName = "Customer Service",
            description = "Handles refunds",
            icon = Icons.Default.Person,
            entities = listOf(
                EntityDef.CUSTOMER_SERVICE_REP,
            )
        )
         */
        val allEntityTypes: List<EntityType> = listOf(
            CASHIERS,
            STOCKERS,
            FRESH_HANDLERS,
            //CUSTOMER_SERVICE_REPRESENTATIVES,
        )
    }
}
