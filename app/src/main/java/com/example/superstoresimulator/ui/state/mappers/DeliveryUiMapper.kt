package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.ui.state.DeliveryUIState
import com.example.superstoresimulator.ui.state.TruckOrderLineUI
import com.example.superstoresimulator.ui.state.TruckUIState

fun buildDeliveryUiState(domain: GameState, cache: ItemMetadataCache): DeliveryUIState {
    val currentDay = domain.currentTime.dayNumber
    val nextDay = currentDay + 1

    fun makeTruckUI(truck: ScheduledTruck): TruckUIState {
        return TruckUIState(
            truckId = truck.truckId,
            arrivalDay = truck.scheduledArrivalDay,
            arrivalDayOfWeek = truck.scheduledArrivalDay % 7,
            capacityUsed = truck.usedCapacityCasePacks,
            capacityTotal = truck.capacityCasePacks,
            isFreshTruck = truck.isFreshTruck,
            isEarlyTruck = truck.isEarlyTruck,
            orderLines = truck.orders
                .groupBy { it.itemId }
                .map { (itemId, lines) ->
                    TruckOrderLineUI(
                        itemId = itemId,
                        itemName = cache.get(itemId)?.name ?: "Item $itemId",
                        casePacks = lines.sumOf { it.casePacksCount },
                        quantity = lines.sumOf { it.quantity },
                        canCancel = truck.scheduledArrivalDay > currentDay,
                        truckId = truck.truckId,
                    )
                }
        )
    }

    val freshTruck = domain.scheduledTrucks
        .firstOrNull { it.isFreshTruck && it.scheduledArrivalDay == nextDay }
        ?.let { makeTruckUI(it) }

    val regularTrucks = domain.scheduledTrucks
        .filter { !it.isFreshTruck }
        .sortedBy { it.scheduledArrivalDay }
        .map { makeTruckUI(it) }

    val earlyTruckAlreadyExists = domain.scheduledTrucks.any {
        it.isEarlyTruck && it.scheduledArrivalDay == nextDay
    }

    val freeTrucks = TruckConfig.BASE_FREE_SLOTS + domain.currentStoreSize.ordinal
    val maxTrucks = freeTrucks + domain.truckConfig.extraTruckSlotsUnlocked

    return DeliveryUIState(
        regularTrucks = regularTrucks,
        freshTruck = freshTruck,
        earlyTruckAvailable = !earlyTruckAlreadyExists,
        earlyTruckCost = Money(10_000L),
        maxTrucksPerWeek = maxTrucks,
        freeTrucksPerWeek = freeTrucks,
        extraTruckSlotsUnlocked = domain.truckConfig.extraTruckSlotsUnlocked,
        extraTruckSlotCost = TruckConfig.EXTRA_SLOT_COST,
    )
}
