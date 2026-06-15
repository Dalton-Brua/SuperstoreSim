package com.example.superstoresimulator.domain.persistence

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.FreshAutoOrderConfig
import com.example.superstoresimulator.domain.IncompleteOrderRequest
import com.example.superstoresimulator.domain.PendingOrderLine
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionLine
import java.time.Instant
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.RefundLine
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.metrics.DailyMetrics

import com.example.superstoresimulator.domain.metrics.OutOfStockEvent
import com.example.superstoresimulator.domain.metrics.SoldItemEvent
import com.example.superstoresimulator.domain.metrics.ExpiredItemEvent
import com.example.superstoresimulator.domain.metrics.AutoOrderLineItem
import com.example.superstoresimulator.domain.metrics.IncompleteAutoOrderLineItem
import com.example.superstoresimulator.domain.metrics.DeliveredTruckRecord
import com.example.superstoresimulator.domain.metrics.DeliveredItemLine
import com.example.superstoresimulator.domain.metrics.AutoHireEvent
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.pricing.Markdown
import com.example.superstoresimulator.domain.pricing.MarkdownReason
import com.example.superstoresimulator.domain.pricing.PriceChangeEvent
import com.example.superstoresimulator.domain.pricing.PricingState
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.time.GameTime
import org.json.JSONArray
import org.json.JSONObject

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

@Deprecated("Legacy save format only — used to load pre-saveVersion saves")
object LegacyGameStateDeserializer {

    fun deserialize(jsonString: String): GameState? {
        return try {
            val json = JSONObject(jsonString)

            GameState(
                storeName = json.getString("storeName"),
                money = Money(json.getLong("money")),
                totalTransactionsCompleted = json.getInt("totalTransactionsCompleted"),
                totalTaxCollected = Money(json.getLong("totalTaxCollected")),
                nextRefundId = json.getInt("nextRefundId"),
                playerPausedTime = json.getBoolean("playerPausedTime"),
                totalRevenue = Money(json.getLong("totalRevenue")),
                currentStoreSize = StoreSize.valueOf(json.getString("currentStoreSize")),
                playerRole = PlayerRole.fromLegacyName(json.getString("playerRole")),
                playerCashierProgress = json.getDouble("playerCashierProgress").toFloat(),
                playerStockerProgress = json.getDouble("playerStockerProgress").toFloat(),
                pendingCustomers = json.getInt("pendingCustomers"),
                showEndOfDayReport = json.getBoolean("showEndOfDayReport"),
                pausedByEndOfDay = json.getBoolean("pausedByEndOfDay"),
                objectiveBonusEarned = Money(json.getLong("objectiveBonusEarned")),
                currentTime = GameTime(json.getLong("currentTime")),
                storeState = StoreState.valueOf(json.getString("storeState")),
                storeConfig = deserializeStoreConfig(json.getJSONObject("storeConfig")),
                salesHistory = deserializeTransactionList(json.getJSONArray("salesHistory")),
                pendingRefunds = deserializeRefundRequestList(json.getJSONArray("pendingRefunds")),
                inventory = deserializeInventory(json.getJSONObject("inventory")),
                hiredEntityRegistry = deserializeHiredEntityRegistry(json.getJSONObject("hiredEntityRegistry")),
                currentDayMetrics = if (json.has("currentDayMetrics")) {
                    deserializeCurrentDayMetrics(json.getJSONObject("currentDayMetrics"))
                } else {
                    DailyMetrics()
                },
                completedDayMetrics = if (json.has("completedDayMetrics")) {
                    deserializeDailyMetricsList(json.getJSONArray("completedDayMetrics"))
                } else {
                    emptyList()
                },
                lastEndOfDayReport = if (json.has("lastEndOfDayReport")) {
                    deserializeDailyMetrics(json.getJSONObject("lastEndOfDayReport"))
                } else null,
                scheduledTrucks = if (json.has("scheduledTrucks")) {
                    val arr = json.getJSONArray("scheduledTrucks")
                    (0 until arr.length()).map { deserializeScheduledTruck(arr.getJSONObject(it)) }
                } else emptyList(),
                truckConfig = if (json.has("truckConfig")) {
                    deserializeTruckConfig(json.getJSONObject("truckConfig"))
                } else TruckConfig(),
                nextTruckId = if (json.has("nextTruckId")) json.getInt("nextTruckId") else 1,
                staffSchedules = if (json.has("staffSchedules")) {
                    val arr = json.getJSONArray("staffSchedules")
                    (0 until arr.length()).mapNotNull { i ->
                        val shiftJson = arr.getJSONObject(i)
                        val startHour = shiftJson.getInt("startHour")
                        val duration = if (shiftJson.has("durationHours")) shiftJson.getInt("durationHours") else 8
                        if (startHour in 6..20 && duration in 2..8 && startHour + duration <= 21) {
                            StaffShift(
                                entityId = shiftJson.getInt("entityId"),
                                startHour = startHour,
                                durationHours = duration,
                            )
                        } else null
                    }
                } else emptyList(),
                registers = if (json.has("registers")) {
                    val arr = json.getJSONArray("registers")
                    (0 until arr.length()).map { deserializeRegisterState(arr.getJSONObject(it)) }
                } else {
                    listOf(
                        RegisterState(
                            registerId = 0,
                            assignedCashierId = null,
                            currentTransaction = deserializeTransaction(
                                json.optJSONObject("currentTransaction") ?: JSONObject()
                            ),
                            transactionActive = json.optBoolean("transactionActive", false),
                        )
                    )
                },
                nextTransactionId = if (json.has("nextTransactionId")) {
                    json.getInt("nextTransactionId")
                } else {
                    val history = json.optJSONArray("salesHistory")
                    val maxPositiveId = if (history != null) {
                        (0 until history.length()).maxOfOrNull {
                            history.getJSONObject(it).optInt("id", 0)
                        } ?: 0
                    } else 0
                    maxPositiveId + 1
                },
                playerAssignedRegisterId = if (json.has("playerAssignedRegisterId") &&
                    !json.isNull("playerAssignedRegisterId")
                ) json.getInt("playerAssignedRegisterId") else null,
                manuallyUnassignedCashiers = if (json.has("manuallyUnassignedCashiers")) {
                    val arr = json.getJSONArray("manuallyUnassignedCashiers")
                    (0 until arr.length()).map { arr.getInt(it) }.toSet()
                } else emptySet(),
                autoHireBudget = if (json.has("autoHireBudget")) Money(json.getLong("autoHireBudget")) else StoreSize.MOM_AND_POP.dailyRent,
                freshAutoOrderConfig = if (json.has("freshAutoOrderConfig")) {
                    val cfg = json.getJSONObject("freshAutoOrderConfig")
                    FreshAutoOrderConfig(
                        enabled = cfg.optBoolean("enabled", true),
                        minStockThreshold = cfg.optInt("minStockThreshold", 5),
                        casePacksPerItem = cfg.optInt("casePacksPerItem", 1),
                    )
                } else FreshAutoOrderConfig(),
                incompleteFreshOrders = if (json.has("incompleteFreshOrders")) {
                    val arr = json.getJSONArray("incompleteFreshOrders")
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        IncompleteOrderRequest(
                            itemId = o.getInt("itemId"),
                            casePacksRequested = o.getInt("casePacksRequested"),
                            requestedOnDay = o.getInt("requestedOnDay"),
                            reason = o.optString("reason", ""),
                        )
                    }
                } else emptyList(),
                pricingState = if (json.has("pricingState")) {
                    deserializePricingState(json.getJSONObject("pricingState"))
                } else PricingState(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Register System ────────────────────────────────────────────────────────

    private fun deserializeRegisterState(json: JSONObject): RegisterState =
        RegisterState(
            registerId = json.getInt("registerId"),
            assignedCashierId = if (json.has("assignedCashierId") && !json.isNull("assignedCashierId"))
                json.getInt("assignedCashierId") else null,
            currentTransaction = deserializeTransaction(
                json.optJSONObject("currentTransaction") ?: JSONObject()
            ),
            transactionActive = json.optBoolean("transactionActive", false),
        )

    private fun deserializeStoreConfig(json: JSONObject): StoreConfig {
        return StoreConfig(
            openTimeMinutes = json.getInt("openTimeMinutes"),
            closeTimeMinutes = json.getInt("closeTimeMinutes"),
            closingProcedureDuration = json.getInt("closingProcedureDuration"),
            allowTransactionsDuringClosing = json.getBoolean("allowTransactionsDuringClosing"),
            gameSpeedMultiplier = json.getDouble("gameSpeedMultiplier").toFloat(),
            backroomCapPerItem = json.getInt("backroomCapPerItem")
        )
    }

    private fun deserializeTransaction(json: JSONObject): Transaction {
        val lines = mutableListOf<TransactionLine>()
        val linesArray = json.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until linesArray.length()) {
            lines.add(deserializeTransactionLine(linesArray.getJSONObject(i)))
        }
        return Transaction(
            id = json.optInt("id", 0),
            lines = lines,
            subtotal = Money(json.optLong("subtotal", 0L)),
            tax = Money(json.optLong("tax", 0L)),
            totalEarned = Money(json.optLong("totalEarned", 0L)),
            completedAt = if (json.has("completedAt")) Instant.ofEpochMilli(json.getLong("completedAt")) else null,
            registerId = json.optInt("registerId", 0),
            gameDayNumber = json.optInt("gameDayNumber", 0),
        )
    }

    private fun deserializeTransactionList(jsonArray: JSONArray): List<Transaction> {
        val list = mutableListOf<Transaction>()
        for (i in 0 until jsonArray.length()) {
            list.add(deserializeTransaction(jsonArray.getJSONObject(i)))
        }
        return list
    }

    private fun deserializeTransactionLine(json: JSONObject): TransactionLine {
        val unitPrice = Money(json.getLong("unitPrice"))
        return TransactionLine(
            itemId = json.getInt("itemId"),
            quantity = json.getInt("quantity"),
            rungQty = json.getInt("rungQty"),
            unitPrice = unitPrice,
            lineTotal = Money(json.getLong("lineTotal")),
            lostToOutOfStock = json.getBoolean("lostToOutOfStock"),
            basePrice = if (json.has("basePrice")) Money(json.getLong("basePrice")) else unitPrice,
            priceModifier = json.optInt("priceModifier", 0),
            weight = if (json.has("weight") && !json.isNull("weight")) json.getDouble("weight").toFloat() else null,
        )
    }

    private fun deserializeRefundRequest(json: JSONObject): RefundRequest {
        val lines = mutableListOf<RefundLine>()
        val linesArray = json.getJSONArray("lines")
        for (i in 0 until linesArray.length()) {
            lines.add(deserializeRefundLine(linesArray.getJSONObject(i)))
        }
        return RefundRequest(
            id = json.getInt("id"),
            timestamp = json.getLong("timestamp"),
            originalTransactionId = json.getInt("originalTransactionId"),
            lines = lines,
            subtotal = Money(json.getLong("subtotal")),
            tax = Money(json.getLong("tax"))
        )
    }

    private fun deserializeRefundRequestList(jsonArray: JSONArray): List<RefundRequest> {
        val list = mutableListOf<RefundRequest>()
        for (i in 0 until jsonArray.length()) {
            list.add(deserializeRefundRequest(jsonArray.getJSONObject(i)))
        }
        return list
    }

    private fun deserializeRefundLine(json: JSONObject): RefundLine {
        return RefundLine(
            itemId = json.getInt("itemId"),
            quantity = json.getInt("quantity"),
            unitPrice = Money(json.getLong("unitPrice"))
        )
    }

    private fun deserializeInventory(json: JSONObject): Map<Int, InventoryState> {
        val map = mutableMapOf<Int, InventoryState>()
        json.keys().forEach { key ->
            val itemId = key.toInt()
            val invJson = json.getJSONObject(key)

            val shelfBatches = mutableListOf<com.example.superstoresimulator.domain.inventory.ItemBatch>()
            if (invJson.has("shelfBatches")) {
                val shelfBatchesArray = invJson.getJSONArray("shelfBatches")
                for (i in 0 until shelfBatchesArray.length()) {
                    shelfBatches.add(deserializeBatch(shelfBatchesArray.getJSONObject(i)))
                }
            }

            val backroomBatches = mutableListOf<com.example.superstoresimulator.domain.inventory.ItemBatch>()
            if (invJson.has("backroomBatches")) {
                val backroomBatchesArray = invJson.getJSONArray("backroomBatches")
                for (i in 0 until backroomBatchesArray.length()) {
                    backroomBatches.add(deserializeBatch(backroomBatchesArray.getJSONObject(i)))
                }
            }

            map[itemId] = InventoryState(
                shelfBatches = shelfBatches,
                backroomBatches = backroomBatches,
                zoneScore = invJson.optDouble("zoneScore", 1.0).toFloat(),
            )
        }
        return map
    }

    private fun deserializeBatch(json: JSONObject): com.example.superstoresimulator.domain.inventory.ItemBatch {
        return com.example.superstoresimulator.domain.inventory.ItemBatch(
            receivedDay = json.getInt("receivedDay"),
            quantity = json.getInt("quantity"),
            expirationDay = json.getInt("expirationDay")
        )
    }

    private fun deserializeHiredEntityRegistry(json: JSONObject): HiredEntityRegistry {
        val entities = mutableListOf<HiredEntity>()
        val entitiesArray = json.getJSONArray("entities")
        for (i in 0 until entitiesArray.length()) {
            entities.add(deserializeHiredEntity(entitiesArray.getJSONObject(i)))
        }
        val fallbackNextId = (entities.maxOfOrNull { it.id } ?: 0) + 1
        val nextEntityId = json.optInt("nextEntityId", fallbackNextId)
        return HiredEntityRegistry(entities = entities, nextEntityId = nextEntityId)
    }

    private fun deserializeHiredEntity(json: JSONObject): HiredEntity {
        val defKey = json.getString("entityDefKey")
        val entityDef = EntityDef.allEntities.find { it.key == defKey }
            ?: throw IllegalStateException("Unknown entity definition: $defKey")
        val trait = try {
            EntityTrait.valueOf(json.getString("trait"))
        } catch (_: IllegalArgumentException) {
            EntityTrait.EFFICIENT
        }
        val tier = try {
            Tier.valueOf(json.optString("tier", Tier.BASE.name))
        } catch (_: IllegalArgumentException) {
            Tier.BASE
        }
        return HiredEntity(
            id = json.getInt("id"),
            name = json.getString("name"),
            entityDefinition = entityDef,
            trait = trait,
            tier = tier,
            xp = json.optInt("xp", 0),
            level = json.optInt("level", 1),
        )
    }

    private class CommonMetrics(
        val revenue: Money, val subtotal: Money, val taxCollected: Money,
        val transactionsCompleted: Int,
        val rentPaid: Money, val wagesPaid: Money,
        val refundsProcessed: Int, val refundAmount: Money,
        val customersServed: Int,
        val itemsSold: Int, val itemsStocked: Int, val itemsOrdered: Int,
        val lostRevenue: Money, val itemsLostToOutOfStock: Int,
        val outOfStockEvents: List<OutOfStockEvent>,
        val soldItemEvents: List<SoldItemEvent>,
        val itemsExpired: Int, val expiredWasteCost: Money,
        val expiredItemEvents: List<ExpiredItemEvent>,
        val autoOrderedFreshItems: List<AutoOrderLineItem>,
        val incompleteOrderedFreshItems: List<IncompleteAutoOrderLineItem>,
        val deliveredTrucks: List<DeliveredTruckRecord>,
        val autoHireEvents: List<AutoHireEvent>,
        val markdownsSaved: Money, val markupExtraRevenue: Money, val itemsMarkedDown: Int,
    )

    private fun optionalList(json: JSONObject, key: String) =
        if (json.has(key)) json.getJSONArray(key) else null

    private fun deserializeCommonMetrics(json: JSONObject): CommonMetrics = CommonMetrics(
        revenue = Money(json.getLong("revenue")),
        subtotal = Money(json.getLong("subtotal")),
        taxCollected = Money(json.getLong("taxCollected")),
        transactionsCompleted = json.getInt("transactionsCompleted"),
        rentPaid = Money(json.getLong("rentPaid")),
        wagesPaid = Money(json.getLong("wagesPaid")),
        refundsProcessed = json.getInt("refundsProcessed"),
        refundAmount = Money(json.getLong("refundAmount")),
        customersServed = json.getInt("customersServed"),
        itemsSold = json.getInt("itemsSold"),
        itemsStocked = json.getInt("itemsStocked"),
        itemsOrdered = json.getInt("itemsOrdered"),
        lostRevenue = Money(json.getLong("lostRevenue")),
        itemsLostToOutOfStock = json.getInt("itemsLostToOutOfStock"),
        outOfStockEvents = json.getJSONArray("outOfStockEvents").mapObjects { deserializeOutOfStockEvent(it) },
        soldItemEvents = json.getJSONArray("soldItemEvents").mapObjects { deserializeSoldItemEvent(it) },
        itemsExpired = json.optInt("itemsExpired", 0),
        expiredWasteCost = if (json.has("expiredWasteCost")) Money(json.getLong("expiredWasteCost")) else Money.ZERO,
        expiredItemEvents = optionalList(json, "expiredItemEvents")?.mapObjects { deserializeExpiredItemEvent(it) } ?: emptyList(),
        autoOrderedFreshItems = optionalList(json, "autoOrderedFreshItems")?.mapObjects { deserializeAutoOrderLineItem(it) } ?: emptyList(),
        incompleteOrderedFreshItems = optionalList(json, "incompleteOrderedFreshItems")?.mapObjects { deserializeIncompleteAutoOrderLineItem(it) } ?: emptyList(),
        deliveredTrucks = optionalList(json, "deliveredTrucks")?.mapObjects { deserializeDeliveredTruckRecord(it) } ?: emptyList(),
        autoHireEvents = optionalList(json, "autoHireEvents")?.mapObjects { deserializeAutoHireEvent(it) } ?: emptyList(),
        markdownsSaved = if (json.has("markdownsSaved")) Money(json.getLong("markdownsSaved")) else Money.ZERO,
        markupExtraRevenue = if (json.has("markupExtraRevenue")) Money(json.getLong("markupExtraRevenue")) else Money.ZERO,
        itemsMarkedDown = json.optInt("itemsMarkedDown", 0),
    )

    private fun deserializeCurrentDayMetrics(json: JSONObject): DailyMetrics {
        val common = deserializeCommonMetrics(json)
        return DailyMetrics(
            dayNumber = if (json.has("dayNumber")) json.getInt("dayNumber") else 0,
            subtotal = common.subtotal, taxCollected = common.taxCollected,
            transactionsCompleted = common.transactionsCompleted,
            rentPaid = common.rentPaid, wagesPaid = common.wagesPaid,
            refundsProcessed = common.refundsProcessed, refundAmount = common.refundAmount,
            customersServed = common.customersServed,
            itemsSold = common.itemsSold, itemsStocked = common.itemsStocked, itemsOrdered = common.itemsOrdered,
            lostRevenue = common.lostRevenue, itemsLostToOutOfStock = common.itemsLostToOutOfStock,
            outOfStockEvents = common.outOfStockEvents, soldItemEvents = common.soldItemEvents,
            itemsExpired = common.itemsExpired, expiredWasteCost = common.expiredWasteCost,
            expiredItemEvents = common.expiredItemEvents,
            autoOrderedFreshItems = common.autoOrderedFreshItems,
            incompleteOrderedFreshItems = common.incompleteOrderedFreshItems,
            deliveredTrucks = common.deliveredTrucks,
            autoHireEvents = common.autoHireEvents,
            markdownsSaved = common.markdownsSaved,
            markupExtraRevenue = common.markupExtraRevenue,
            itemsMarkedDown = common.itemsMarkedDown,
        )
    }

    private fun deserializeDailyMetrics(json: JSONObject): DailyMetrics {
        val common = deserializeCommonMetrics(json)
        return DailyMetrics(
            dayNumber = json.getInt("dayNumber"),
            dayOfWeek = json.getInt("dayOfWeek"),
            subtotal = common.subtotal, taxCollected = common.taxCollected,
            transactionsCompleted = common.transactionsCompleted,
            rentPaid = common.rentPaid, wagesPaid = common.wagesPaid,
            refundsProcessed = common.refundsProcessed, refundAmount = common.refundAmount,
            customersServed = common.customersServed,
            itemsSold = common.itemsSold, itemsStocked = common.itemsStocked, itemsOrdered = common.itemsOrdered,
            lostRevenue = common.lostRevenue, itemsLostToOutOfStock = common.itemsLostToOutOfStock,
            outOfStockEvents = common.outOfStockEvents, soldItemEvents = common.soldItemEvents,
            itemsExpired = common.itemsExpired, expiredWasteCost = common.expiredWasteCost,
            expiredItemEvents = common.expiredItemEvents,
            autoOrderedFreshItems = common.autoOrderedFreshItems,
            incompleteOrderedFreshItems = common.incompleteOrderedFreshItems,
            deliveredTrucks = common.deliveredTrucks,
            autoHireEvents = common.autoHireEvents,
            markdownsSaved = common.markdownsSaved,
            markupExtraRevenue = common.markupExtraRevenue,
            itemsMarkedDown = common.itemsMarkedDown,
        )
    }

    private fun deserializeDailyMetricsList(jsonArray: JSONArray): List<DailyMetrics> =
        jsonArray.mapObjects { deserializeDailyMetrics(it) }

    private fun deserializeOutOfStockEvent(json: JSONObject): OutOfStockEvent {
        return OutOfStockEvent(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            quantityLost = json.getInt("quantityLost"),
            revenueLost = Money(json.getLong("revenueLost"))
        )
    }

    private fun deserializeSoldItemEvent(json: JSONObject): SoldItemEvent {
        return SoldItemEvent(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            quantitySold = json.getInt("quantitySold"),
            revenue = Money(json.getLong("revenue")),
            effectivePrice = if (json.has("effectivePrice")) Money(json.getLong("effectivePrice")) else Money.ZERO,
            basePrice = if (json.has("basePrice")) Money(json.getLong("basePrice")) else Money.ZERO,
        )
    }

    private fun deserializeExpiredItemEvent(json: JSONObject): ExpiredItemEvent {
        return ExpiredItemEvent(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            quantity = json.getInt("quantity"),
            valueLost = Money(json.getLong("valueLost"))
        )
    }

    private fun deserializeAutoHireEvent(json: JSONObject): AutoHireEvent =
        AutoHireEvent(
            entityDefName = json.getString("entityDefName"),
            reason = json.getString("reason"),
            blocked = json.optBoolean("blocked", false),
            blockReason = json.optString("blockReason", ""),
        )

    private fun deserializeAutoOrderLineItem(json: JSONObject): AutoOrderLineItem =
        AutoOrderLineItem(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            casePacksOrdered = json.getInt("casePacksOrdered"),
            costPerCasePack = Money(json.getLong("costPerCasePack")),
            totalCost = Money(json.getLong("totalCost")),
        )

    private fun deserializeIncompleteAutoOrderLineItem(json: JSONObject): IncompleteAutoOrderLineItem =
        IncompleteAutoOrderLineItem(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            casePacksRequested = json.getInt("casePacksRequested"),
            costPerCasePack = Money(json.getLong("costPerCasePack")),
            totalCost = Money(json.getLong("totalCost")),
            reason = json.getString("reason"),
        )

    private fun deserializeDeliveredItemLine(json: JSONObject): DeliveredItemLine =
        DeliveredItemLine(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            casePacks = json.getInt("casePacks"),
            quantity = json.getInt("quantity"),
        )

    private fun deserializeDeliveredTruckRecord(json: JSONObject): DeliveredTruckRecord {
        val linesArray = json.getJSONArray("lines")
        val lines = (0 until linesArray.length()).map { deserializeDeliveredItemLine(linesArray.getJSONObject(it)) }
        return DeliveredTruckRecord(
            truckId = json.getInt("truckId"),
            arrivalDay = json.getInt("arrivalDay"),
            isFreshTruck = json.optBoolean("isFreshTruck", false),
            isEarlyTruck = json.optBoolean("isEarlyTruck", false),
            totalCasePacks = json.getInt("totalCasePacks"),
            lines = lines,
        )
    }

    private fun deserializeScheduledTruck(json: JSONObject): ScheduledTruck {
        val ordersArray = json.getJSONArray("orders")
        val orders = (0 until ordersArray.length()).map {
            deserializePendingOrderLine(ordersArray.getJSONObject(it))
        }
        return ScheduledTruck(
            truckId = json.getInt("truckId"),
            scheduledArrivalDay = json.getInt("scheduledArrivalDay"),
            capacityCasePacks = json.getInt("capacityCasePacks"),
            isFreshTruck = json.optBoolean("isFreshTruck", false),
            isEarlyTruck = json.optBoolean("isEarlyTruck", false),
            orders = orders,
        )
    }

    private fun deserializePendingOrderLine(json: JSONObject): PendingOrderLine {
        return PendingOrderLine(
            itemId = json.getInt("itemId"),
            quantity = json.getInt("quantity"),
            casePacksCount = json.getInt("casePacksCount"),
            unitCost = Money(json.getLong("unitCost")),
            orderedOnDay = json.getInt("orderedOnDay"),
            isFresh = json.optBoolean("isFresh", false),
        )
    }

    private fun deserializeTruckConfig(json: JSONObject): TruckConfig {
        val daysArray = json.getJSONArray("deliveryDays")
        val days = (0 until daysArray.length()).map { daysArray.getInt(it) }.toSet()
        return TruckConfig(
            deliveryDays = days.ifEmpty { setOf(0, 3) },
            regularTruckCapacityCasePacks = json.optInt("regularTruckCapacityCasePacks", TruckConfig.DEFAULT_REGULAR_TRUCK_CAPACITY),
            freshTruckCapacityCasePacks = json.optInt("freshTruckCapacityCasePacks", TruckConfig.DEFAULT_FRESH_TRUCK_CAPACITY),
            extraTruckSlotsUnlocked = json.optInt("extraTruckSlotsUnlocked", 0),
        )
    }

    // ── Pricing System ──────────────────────────────────────────────────────

    private fun deserializePricingState(json: JSONObject): PricingState {
        val markups = mutableMapOf<ItemCategory, Int>()
        if (json.has("categoryMarkups")) {
            val obj = json.getJSONObject("categoryMarkups")
            obj.keys().forEach { key ->
                try { markups[ItemCategory.valueOf(key)] = obj.getInt(key) } catch (_: Exception) {}
            }
        }

        val overrides = mutableMapOf<Int, Int>()
        if (json.has("itemOverrides")) {
            val obj = json.getJSONObject("itemOverrides")
            obj.keys().forEach { key ->
                overrides[key.toInt()] = obj.getInt(key)
            }
        }

        val markdowns = mutableMapOf<Int, Markdown>()
        if (json.has("activeMarkdowns")) {
            val arr = json.getJSONArray("activeMarkdowns")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val itemId = o.getInt("itemId")
                markdowns[itemId] = Markdown(
                    percentOff = o.getInt("percentOff"),
                    reason = try { MarkdownReason.valueOf(o.getString("reason")) } catch (_: Exception) { MarkdownReason.PLAYER_SALE },
                    appliedOnDay = o.getInt("appliedOnDay"),
                )
            }
        }

        val history = mutableListOf<PriceChangeEvent>()
        if (json.has("priceHistory")) {
            val arr = json.getJSONArray("priceHistory")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                history.add(PriceChangeEvent(
                    dayNumber = o.getInt("dayNumber"),
                    itemId = if (o.has("itemId") && !o.isNull("itemId")) o.getInt("itemId") else null,
                    category = if (o.has("category") && !o.isNull("category")) {
                        try { ItemCategory.valueOf(o.getString("category")) } catch (_: Exception) { null }
                    } else null,
                    oldPercent = o.getInt("oldPercent"),
                    newPercent = o.getInt("newPercent"),
                    source = if (o.has("source") && !o.isNull("source")) {
                        try { MarkdownReason.valueOf(o.getString("source")) } catch (_: Exception) { null }
                    } else null,
                ))
            }
        }

        return PricingState(
            categoryMarkups = markups,
            itemOverrides = overrides,
            activeMarkdowns = markdowns,
            defaultMarkup = json.optInt("defaultMarkup", 0),
            smoothedPriceIndex = json.optDouble("smoothedPriceIndex", 1.0).toFloat(),
            lastPriceIndexHour = json.optInt("lastPriceIndexHour", -1),
            priceHistory = history,
        )
    }
}
