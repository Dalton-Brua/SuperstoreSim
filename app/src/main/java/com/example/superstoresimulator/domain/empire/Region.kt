package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Money
import kotlinx.serialization.Serializable

/** Per-region demand shape — multipliers on the base sim. */
@Serializable
data class DemandProfile(
    val baseSpendingPower: Float,  // multiplier on avgBasket (0.8 rural … 1.4 affluent)
    val baseTraffic: Float,        // multiplier on base customer count
    val growthRate: Float,         // per-day drift in capacity/traffic (boom vs declining)
)

/** A market the player can open stores in. Saturation is computed per region per day. */
@Serializable
data class Region(
    val regionId: Int,
    val name: String,
    val demandProfile: DemandProfile,
    val capacity: Float,           // total store-weight absorbed before saturation bites
    val unlockCost: Money,         // one-time entry fee to open the first store here
    val unlocked: Boolean = false,
)

/** Authored fixed region list. The home region (id 0) is unlocked on entering empire mode. */
object RegionRegistry {
    const val HOME_REGION_ID = 0

    val authored: List<Region> = listOf(
        Region(
            regionId = HOME_REGION_ID,
            name = "Hometown",
            demandProfile = DemandProfile(baseSpendingPower = 1.0f, baseTraffic = 1.0f, growthRate = 0.0f),
            capacity = 20f,
            unlockCost = Money.ZERO,
            unlocked = true,
        ),
        Region(
            regionId = 1,
            name = "Rural County",
            demandProfile = DemandProfile(baseSpendingPower = 0.8f, baseTraffic = 0.9f, growthRate = 0.0f),
            capacity = 8f,
            unlockCost = Money(20_000_000L),  // $200K cheap unlock, saturates fast
        ),
        Region(
            regionId = 2,
            name = "Metro Heights",
            demandProfile = DemandProfile(baseSpendingPower = 1.4f, baseTraffic = 1.3f, growthRate = 0.0f),
            capacity = 40f,
            unlockCost = Money(150_000_000L), // $1.5M dense/affluent
        ),
        Region(
            regionId = 3,
            name = "Sunbelt Boom",
            demandProfile = DemandProfile(baseSpendingPower = 1.0f, baseTraffic = 1.0f, growthRate = 0.01f),
            capacity = 15f,
            unlockCost = Money(75_000_000L),  // $750K growing — rewards early entry
        ),
    )

    fun byId(id: Int): Region? = authored.firstOrNull { it.regionId == id }
}
