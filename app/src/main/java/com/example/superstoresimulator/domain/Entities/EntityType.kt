package com.example.superstoresimulator.domain.Entities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.superstoresimulator.domain.Entities.EntityDef.Companion.CASHIER
import com.example.superstoresimulator.domain.Entities.EntityDef.Companion.CUSTOMER_SERVICE_REP
import com.example.superstoresimulator.domain.Entities.EntityDef.Companion.FAST_CASHIER
import com.example.superstoresimulator.domain.Entities.EntityDef.Companion.FAST_STOCKER
import com.example.superstoresimulator.domain.Entities.EntityDef.Companion.STOCKER

data class EntityType(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val entities: List<EntityDef>,

    ) {
    companion object {
        val NONE = EntityType(
            "",
            description = "",
            Icons.Default.Person,
            emptyList()
        )

        val CASHIERS = EntityType(
            "Cashiers",
            description = "Rings up items",
            Icons.Default.Person,
            listOf(
                EntityDef.CASHIER,
                EntityDef.FAST_CASHIER,
            )
        )

        val STOCKERS = EntityType(
            "Stockers",
            description = "Stocks items from the backroom",
            Icons.Default.Build,
            listOf(
                EntityDef.STOCKER,
                EntityDef.FAST_STOCKER,
            )
        )

        val CUSTOMER_SERVICE_REPRESENTATIVES = EntityType(
            "Customer Service",
            description = "Handles refunds",
            Icons.Default.Person,
            listOf(
                EntityDef.CUSTOMER_SERVICE_REP,
            )
        )
        val allEntityTypes: List<EntityType> = listOf(
            CASHIERS,
            STOCKERS,
            CUSTOMER_SERVICE_REPRESENTATIVES,
        )
    }
}
