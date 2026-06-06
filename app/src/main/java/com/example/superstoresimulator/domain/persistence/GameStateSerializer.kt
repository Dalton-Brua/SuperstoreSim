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
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.RefundLine
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.metrics.DailyMetricsAccumulator
import com.example.superstoresimulator.domain.metrics.OutOfStockEvent
import com.example.superstoresimulator.domain.metrics.SoldItemEvent
import com.example.superstoresimulator.domain.metrics.ExpiredItemEvent
import com.example.superstoresimulator.domain.metrics.FreshOrderLineItem
import com.example.superstoresimulator.domain.metrics.IncompleteOrderLineItem
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

/**
 * Serializes and deserializes GameState to/from JSON for persistence.
 * Uses org.json (built into Android) to avoid additional dependencies.
 */
object GameStateSerializer {

    fun serialize(state: GameState): String {
        val json = JSONObject()
        
        // Basic fields
        json.put("storeName", state.storeName)
        json.put("money", state.money.cents)
        // Legacy flat fields kept so old builds can still open new save files.
        json.put("transactionActive", state.transactionActive)
        json.put("totalTransactionsCompleted", state.totalTransactionsCompleted)
        json.put("totalTaxCollected", state.totalTaxCollected.cents)
        json.put("nextRefundId", state.nextRefundId)
        json.put("playerPausedTime", state.playerPausedTime)
        json.put("totalRevenue", state.totalRevenue.cents)
        json.put("currentTier", state.currentTier.name)
        json.put("currentStoreSize", state.currentStoreSize.name)
        json.put("playerRole", state.playerRole.name)
        json.put("playerCashierProgress", state.playerCashierProgress.toDouble())
        json.put("playerStockerProgress", state.playerStockerProgress.toDouble())
        json.put("pendingCustomers", state.pendingCustomers)
        json.put("showEndOfDayReport", state.showEndOfDayReport)
        json.put("pausedByEndOfDay", state.pausedByEndOfDay)
        json.put("objectiveBonusEarned", state.objectiveBonusEarned.cents)
        
        // Time and store
        json.put("currentTime", state.currentTime.totalMinutesElapsed)
        json.put("storeState", state.storeState.name)
        json.put("storeConfig", serializeStoreConfig(state.storeConfig))
        
        // Legacy flat current transaction (register 0) for old-build compat.
        json.put("currentTransaction", serializeTransaction(state.currentTransaction))
        
        // Sales history
        val historyArray = JSONArray()
        state.salesHistory.forEach { tx ->
            historyArray.put(serializeTransaction(tx))
        }
        json.put("salesHistory", historyArray)
        
        // Pending refunds
        val refundsArray = JSONArray()
        state.pendingRefunds.forEach { refund ->
            refundsArray.put(serializeRefundRequest(refund))
        }
        json.put("pendingRefunds", refundsArray)
        
        // Inventory
        val inventoryObj = JSONObject()
        state.inventory.forEach { (itemId, invState) ->
            inventoryObj.put(itemId.toString(), serializeInventoryState(invState))
        }
        json.put("inventory", inventoryObj)
        
        // Hired entity registry
        json.put("hiredEntityRegistry", serializeHiredEntityRegistry(state.hiredEntityRegistry))
        
        // Daily metrics
        json.put("currentDayMetrics", serializeDailyMetricsAccumulator(state.currentDayMetrics))
        val completedDaysArray = JSONArray()
        state.completedDayMetrics.forEach { day ->
            completedDaysArray.put(serializeDailyMetrics(day))
        }
        json.put("completedDayMetrics", completedDaysArray)
        
        // Last end of day report
        state.lastEndOfDayReport?.let {
            json.put("lastEndOfDayReport", serializeDailyMetrics(it))
        }

        // Truck delivery system
        val trucksArray = JSONArray()
        state.scheduledTrucks.forEach { truck ->
            trucksArray.put(serializeScheduledTruck(truck))
        }
        json.put("scheduledTrucks", trucksArray)
        json.put("truckConfig", serializeTruckConfig(state.truckConfig))
        json.put("nextTruckId", state.nextTruckId)

        // Staff schedules
        val schedulesArray = JSONArray()
        state.staffSchedules.forEach { shift ->
            schedulesArray.put(JSONObject().apply {
                put("entityId", shift.entityId)
                put("startHour", shift.startHour)
                put("durationHours", shift.durationHours)
            })
        }
        json.put("staffSchedules", schedulesArray)

        // ── Register system ──────────────────────────────────────────────────
        val registersArray = JSONArray()
        state.registers.forEach { reg ->
            registersArray.put(serializeRegisterState(reg))
        }
        json.put("registers", registersArray)
        json.put("ownedRegisterCount", state.ownedRegisterCount)
        if (state.playerAssignedRegisterId != null) {
            json.put("playerAssignedRegisterId", state.playerAssignedRegisterId)
        }
        if (state.manuallyUnassignedCashiers.isNotEmpty()) {
            val unassignedArray = JSONArray()
            state.manuallyUnassignedCashiers.forEach { unassignedArray.put(it) }
            json.put("manuallyUnassignedCashiers", unassignedArray)
        }

        // ── Auto-hire budget (Phase 5B) ──────────────────────────────────────
        json.put("autoHireBudget", state.autoHireBudget.cents)

        // ── Fresh auto-order system ──────────────────────────────────────────
        json.put("freshAutoOrderConfig", JSONObject().apply {
            put("enabled", state.freshAutoOrderConfig.enabled)
            put("minStockThreshold", state.freshAutoOrderConfig.minStockThreshold)
            put("casePacksPerItem", state.freshAutoOrderConfig.casePacksPerItem)
        })
        val incompleteOrdersArray = JSONArray()
        state.incompleteFreshOrders.forEach { req ->
            incompleteOrdersArray.put(JSONObject().apply {
                put("itemId", req.itemId)
                put("casePacksRequested", req.casePacksRequested)
                put("requestedOnDay", req.requestedOnDay)
                put("reason", req.reason)
            })
        }
        json.put("incompleteFreshOrders", incompleteOrdersArray)

        // ── Pricing system ──────────────────────────────────────────────────
        json.put("pricingState", serializePricingState(state.pricingState))

        return json.toString()
    }

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
                currentTier = ItemUnlockTier.valueOf(json.getString("currentTier")),
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
                    deserializeDailyMetricsAccumulator(json.getJSONObject("currentDayMetrics"))
                } else {
                    DailyMetricsAccumulator()
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
                // ── Register system (3.5 backward-compat migration) ──────────
                registers = if (json.has("registers")) {
                    val arr = json.getJSONArray("registers")
                    (0 until arr.length()).map { deserializeRegisterState(arr.getJSONObject(it)) }
                } else {
                    // Old save: reconstruct register 0 from legacy flat fields.
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
                playerAssignedRegisterId = if (json.has("playerAssignedRegisterId") &&
                    !json.isNull("playerAssignedRegisterId")
                ) json.getInt("playerAssignedRegisterId") else null,
                manuallyUnassignedCashiers = if (json.has("manuallyUnassignedCashiers")) {
                    val arr = json.getJSONArray("manuallyUnassignedCashiers")
                    (0 until arr.length()).map { arr.getInt(it) }.toSet()
                } else emptySet(),
                // ── Auto-hire budget (Phase 5B) ──────────────────────────────
                autoHireBudget = if (json.has("autoHireBudget")) Money(json.getLong("autoHireBudget")) else StoreSize.MOM_AND_POP.dailyRent,
                // ── Fresh auto-order system ──────────────────────────────────
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

    private fun serializeRegisterState(reg: RegisterState): JSONObject =
        JSONObject().apply {
            put("registerId", reg.registerId)
            if (reg.assignedCashierId != null) put("assignedCashierId", reg.assignedCashierId)
            put("currentTransaction", serializeTransaction(reg.currentTransaction))
            put("transactionActive", reg.transactionActive)
        }

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

    private fun serializeStoreConfig(config: StoreConfig): JSONObject {
        return JSONObject().apply {
            put("openTimeMinutes", config.openTimeMinutes)
            put("closeTimeMinutes", config.closeTimeMinutes)
            put("closingProcedureDuration", config.closingProcedureDuration)
            put("allowTransactionsDuringClosing", config.allowTransactionsDuringClosing)
            put("gameSpeedMultiplier", config.gameSpeedMultiplier.toDouble())
            put("backroomCapPerItem", config.backroomCapPerItem)
        }
    }

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

    private fun serializeTransaction(tx: Transaction): JSONObject {
        return JSONObject().apply {
            put("id", tx.id)
            put("subtotal", tx.subtotal.cents)
            put("tax", tx.tax.cents)
            put("totalEarned", tx.totalEarned.cents)
            put("registerId", tx.registerId)
            put("gameDayNumber", tx.gameDayNumber)
            val linesArray = JSONArray()
            tx.lines.forEach { line ->
                linesArray.put(serializeTransactionLine(line))
            }
            put("lines", linesArray)
        }
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
            completedAt = null,
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

    private fun serializeTransactionLine(line: TransactionLine): JSONObject {
        return JSONObject().apply {
            put("itemId", line.itemId)
            put("quantity", line.quantity)
            put("rungQty", line.rungQty)
            put("unitPrice", line.unitPrice.cents)
            put("lineTotal", line.lineTotal.cents)
            put("lostToOutOfStock", line.lostToOutOfStock)
            put("basePrice", line.basePrice.cents)
            put("priceModifier", line.priceModifier)
            if (line.weight != null) put("weight", line.weight.toDouble())
        }
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

    private fun serializeRefundRequest(refund: RefundRequest): JSONObject {
        return JSONObject().apply {
            put("id", refund.id)
            put("timestamp", refund.timestamp)
            put("originalTransactionId", refund.originalTransactionId)
            put("subtotal", refund.subtotal.cents)
            put("tax", refund.tax.cents)
            val linesArray = JSONArray()
            refund.lines.forEach { line ->
                linesArray.put(serializeRefundLine(line))
            }
            put("lines", linesArray)
        }
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

    private fun serializeRefundLine(line: RefundLine): JSONObject {
        return JSONObject().apply {
            put("itemId", line.itemId)
            put("quantity", line.quantity)
            put("unitPrice", line.unitPrice.cents)
        }
    }

    private fun deserializeRefundLine(json: JSONObject): RefundLine {
        return RefundLine(
            itemId = json.getInt("itemId"),
            quantity = json.getInt("quantity"),
            unitPrice = Money(json.getLong("unitPrice"))
        )
    }

    private fun serializeInventoryState(invState: InventoryState): JSONObject {
        return JSONObject().apply {
            // Serialize shelf batches
            val shelfBatchesArray = JSONArray()
            invState.shelfBatches.forEach { batch ->
                shelfBatchesArray.put(serializeBatch(batch))
            }
            put("shelfBatches", shelfBatchesArray)

            // Serialize backroom batches
            val backroomBatchesArray = JSONArray()
            invState.backroomBatches.forEach { batch ->
                backroomBatchesArray.put(serializeBatch(batch))
            }
            put("backroomBatches", backroomBatchesArray)

            put("zoneScore", invState.zoneScore.toDouble())
        }
    }
    
    private fun serializeBatch(batch: com.example.superstoresimulator.domain.inventory.ItemBatch): JSONObject {
        return JSONObject().apply {
            put("receivedDay", batch.receivedDay)
            put("quantity", batch.quantity)
            put("expirationDay", batch.expirationDay)
        }
    }

    private fun deserializeInventory(json: JSONObject): Map<Int, InventoryState> {
        val map = mutableMapOf<Int, InventoryState>()
        json.keys().forEach { key ->
            val itemId = key.toInt()
            val invJson = json.getJSONObject(key)
            
            // Deserialize shelf batches
            val shelfBatches = mutableListOf<com.example.superstoresimulator.domain.inventory.ItemBatch>()
            if (invJson.has("shelfBatches")) {
                val shelfBatchesArray = invJson.getJSONArray("shelfBatches")
                for (i in 0 until shelfBatchesArray.length()) {
                    shelfBatches.add(deserializeBatch(shelfBatchesArray.getJSONObject(i)))
                }
            }
            
            // Deserialize backroom batches
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

    private fun serializeHiredEntityRegistry(registry: HiredEntityRegistry): JSONObject {
        return JSONObject().apply {
            val entitiesArray = JSONArray()
            registry.hiredEntities.forEach { entity ->
                entitiesArray.put(serializeHiredEntity(entity))
            }
            put("entities", entitiesArray)
            put("nextEntityId", registry.getNextEntityId())
        }
    }

    private fun deserializeHiredEntityRegistry(json: JSONObject): HiredEntityRegistry {
        val entities = mutableListOf<HiredEntity>()
        val entitiesArray = json.getJSONArray("entities")
        for (i in 0 until entitiesArray.length()) {
            entities.add(deserializeHiredEntity(entitiesArray.getJSONObject(i)))
        }

        // Preserve exact saved employee identity (name/trait/id), and keep backward compatibility
        // with older saves that did not persist nextEntityId.
        val fallbackNextId = (entities.maxOfOrNull { it.id } ?: 0) + 1
        val nextEntityId = json.optInt("nextEntityId", fallbackNextId)
        return HiredEntityRegistry(entities = entities, nextEntityId = nextEntityId)
    }

    private fun serializeHiredEntity(entity: HiredEntity): JSONObject {
        return JSONObject().apply {
            put("id", entity.id)
            put("name", entity.name)
            put("entityDefKey", entity.entityDefinition.key)
            put("trait", entity.trait.name)
            put("tier", entity.tier.name)
            put("xp", entity.xp)
            put("level", entity.level)
        }
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

    private fun JSONObject.putCommonMetrics(
        revenue: Money, subtotal: Money, taxCollected: Money, transactionsCompleted: Int,
        rentPaid: Money, wagesPaid: Money, refundsProcessed: Int, refundAmount: Money,
        customersServed: Int, itemsSold: Int, itemsStocked: Int, itemsOrdered: Int,
        lostRevenue: Money, itemsLostToOutOfStock: Int,
        outOfStockEvents: List<OutOfStockEvent>, soldItemEvents: List<SoldItemEvent>,
        itemsExpired: Int, expiredWasteCost: Money, expiredItemEvents: List<ExpiredItemEvent>,
        autoOrderedFreshItems: List<FreshOrderLineItem>,
        incompleteOrderedFreshItems: List<IncompleteOrderLineItem>,
        deliveredTrucks: List<DeliveredTruckRecord>, autoHireEvents: List<AutoHireEvent>,
        markdownsSaved: Money, markupExtraRevenue: Money, itemsMarkedDown: Int,
    ) {
        put("revenue", revenue.cents); put("subtotal", subtotal.cents); put("taxCollected", taxCollected.cents)
        put("transactionsCompleted", transactionsCompleted)
        put("rentPaid", rentPaid.cents); put("wagesPaid", wagesPaid.cents)
        put("refundsProcessed", refundsProcessed); put("refundAmount", refundAmount.cents)
        put("customersServed", customersServed)
        put("itemsSold", itemsSold); put("itemsStocked", itemsStocked); put("itemsOrdered", itemsOrdered)
        put("lostRevenue", lostRevenue.cents); put("itemsLostToOutOfStock", itemsLostToOutOfStock)
        put("outOfStockEvents", JSONArray().apply { outOfStockEvents.forEach { put(serializeOutOfStockEvent(it)) } })
        put("soldItemEvents", JSONArray().apply { soldItemEvents.forEach { put(serializeSoldItemEvent(it)) } })
        put("itemsExpired", itemsExpired); put("expiredWasteCost", expiredWasteCost.cents)
        put("expiredItemEvents", JSONArray().apply { expiredItemEvents.forEach { put(serializeExpiredItemEvent(it)) } })
        put("autoOrderedFreshItems", JSONArray().apply { autoOrderedFreshItems.forEach { put(serializeFreshOrderLineItem(it)) } })
        put("incompleteOrderedFreshItems", JSONArray().apply { incompleteOrderedFreshItems.forEach { put(serializeIncompleteOrderLineItem(it)) } })
        put("deliveredTrucks", JSONArray().apply { deliveredTrucks.forEach { put(serializeDeliveredTruckRecord(it)) } })
        put("autoHireEvents", JSONArray().apply { autoHireEvents.forEach { put(serializeAutoHireEvent(it)) } })
        put("markdownsSaved", markdownsSaved.cents); put("markupExtraRevenue", markupExtraRevenue.cents)
        put("itemsMarkedDown", itemsMarkedDown)
    }

    private fun serializeDailyMetricsAccumulator(acc: DailyMetricsAccumulator): JSONObject {
        return JSONObject().apply {
            put("dayNumber", acc.dayNumber)
            putCommonMetrics(
                acc.revenue, acc.subtotal, acc.taxCollected, acc.transactionsCompleted,
                acc.rentPaid, acc.wagesPaid, acc.refundsProcessed, acc.refundAmount,
                acc.customersServed, acc.itemsSold, acc.itemsStocked, acc.itemsOrdered,
                acc.lostRevenue, acc.itemsLostToOutOfStock,
                acc.outOfStockEvents, acc.soldItemEvents,
                acc.itemsExpired, acc.expiredWasteCost, acc.expiredItemEvents,
                acc.autoOrderedFreshItems, acc.incompleteOrderedFreshItems,
                acc.deliveredTrucks, acc.autoHireEvents,
                acc.markdownsSaved, acc.markupExtraRevenue, acc.itemsMarkedDown,
            )
        }
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
        val autoOrderedFreshItems: List<FreshOrderLineItem>,
        val incompleteOrderedFreshItems: List<IncompleteOrderLineItem>,
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
        autoOrderedFreshItems = optionalList(json, "autoOrderedFreshItems")?.mapObjects { deserializeFreshOrderLineItem(it) } ?: emptyList(),
        incompleteOrderedFreshItems = optionalList(json, "incompleteOrderedFreshItems")?.mapObjects { deserializeIncompleteOrderLineItem(it) } ?: emptyList(),
        deliveredTrucks = optionalList(json, "deliveredTrucks")?.mapObjects { deserializeDeliveredTruckRecord(it) } ?: emptyList(),
        autoHireEvents = optionalList(json, "autoHireEvents")?.mapObjects { deserializeAutoHireEvent(it) } ?: emptyList(),
        markdownsSaved = if (json.has("markdownsSaved")) Money(json.getLong("markdownsSaved")) else Money.ZERO,
        markupExtraRevenue = if (json.has("markupExtraRevenue")) Money(json.getLong("markupExtraRevenue")) else Money.ZERO,
        itemsMarkedDown = json.optInt("itemsMarkedDown", 0),
    )

    private fun deserializeDailyMetricsAccumulator(json: JSONObject): DailyMetricsAccumulator {
        val common = deserializeCommonMetrics(json)
        return DailyMetricsAccumulator(
            dayNumber = if (json.has("dayNumber")) json.getInt("dayNumber") else 0,
            revenue = common.revenue, subtotal = common.subtotal, taxCollected = common.taxCollected,
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

    private fun serializeDailyMetrics(metrics: DailyMetrics): JSONObject {
        return JSONObject().apply {
            put("dayNumber", metrics.dayNumber)
            put("dayOfWeek", metrics.dayOfWeek)
            putCommonMetrics(
                metrics.revenue, metrics.subtotal, metrics.taxCollected, metrics.transactionsCompleted,
                metrics.rentPaid, metrics.wagesPaid, metrics.refundsProcessed, metrics.refundAmount,
                metrics.customersServed, metrics.itemsSold, metrics.itemsStocked, metrics.itemsOrdered,
                metrics.lostRevenue, metrics.itemsLostToOutOfStock,
                metrics.outOfStockEvents, metrics.soldItemEvents,
                metrics.itemsExpired, metrics.expiredWasteCost, metrics.expiredItemEvents,
                metrics.autoOrderedFreshItems, metrics.incompleteOrderedFreshItems,
                metrics.deliveredTrucks, metrics.autoHireEvents,
                metrics.markdownsSaved, metrics.markupExtraRevenue, metrics.itemsMarkedDown,
            )
        }
    }

    private fun deserializeDailyMetrics(json: JSONObject): DailyMetrics {
        val common = deserializeCommonMetrics(json)
        return DailyMetrics(
            dayNumber = json.getInt("dayNumber"),
            dayOfWeek = json.getInt("dayOfWeek"),
            revenue = common.revenue, subtotal = common.subtotal, taxCollected = common.taxCollected,
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

    private fun serializeOutOfStockEvent(event: OutOfStockEvent): JSONObject {
        return JSONObject().apply {
            put("itemId", event.itemId)
            put("itemName", event.itemName)
            put("quantityLost", event.quantityLost)
            put("revenueLost", event.revenueLost.cents)
        }
    }

    private fun deserializeOutOfStockEvent(json: JSONObject): OutOfStockEvent {
        return OutOfStockEvent(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            quantityLost = json.getInt("quantityLost"),
            revenueLost = Money(json.getLong("revenueLost"))
        )
    }

    private fun serializeSoldItemEvent(event: SoldItemEvent): JSONObject {
        return JSONObject().apply {
            put("itemId", event.itemId)
            put("itemName", event.itemName)
            put("quantitySold", event.quantitySold)
            put("revenue", event.revenue.cents)
            put("effectivePrice", event.effectivePrice.cents)
            put("basePrice", event.basePrice.cents)
        }
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
    
    private fun serializeExpiredItemEvent(event: ExpiredItemEvent): JSONObject {
        return JSONObject().apply {
            put("itemId", event.itemId)
            put("itemName", event.itemName)
            put("quantity", event.quantity)
            put("valueLost", event.valueLost.cents)
        }
    }

    private fun deserializeExpiredItemEvent(json: JSONObject): ExpiredItemEvent {
        return ExpiredItemEvent(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            quantity = json.getInt("quantity"),
            valueLost = Money(json.getLong("valueLost"))
        )
    }

    // ── Auto-Hire Events ────────────────────────────────────────────────────

    private fun serializeAutoHireEvent(event: AutoHireEvent): JSONObject =
        JSONObject().apply {
            put("entityDefName", event.entityDefName)
            put("reason", event.reason)
            put("blocked", event.blocked)
            put("blockReason", event.blockReason)
        }

    private fun deserializeAutoHireEvent(json: JSONObject): AutoHireEvent =
        AutoHireEvent(
            entityDefName = json.getString("entityDefName"),
            reason = json.getString("reason"),
            blocked = json.optBoolean("blocked", false),
            blockReason = json.optString("blockReason", ""),
        )

    // ── Truck Delivery System ─────────────────────────────────────────────────

    private fun serializeFreshOrderLineItem(item: FreshOrderLineItem): JSONObject =
        JSONObject().apply {
            put("itemId", item.itemId)
            put("itemName", item.itemName)
            put("casePacksOrdered", item.casePacksOrdered)
            put("costPerCasePack", item.costPerCasePack.cents)
            put("totalCost", item.totalCost.cents)
        }

    private fun deserializeFreshOrderLineItem(json: JSONObject): FreshOrderLineItem =
        FreshOrderLineItem(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            casePacksOrdered = json.getInt("casePacksOrdered"),
            costPerCasePack = Money(json.getLong("costPerCasePack")),
            totalCost = Money(json.getLong("totalCost")),
        )

    private fun serializeIncompleteOrderLineItem(item: IncompleteOrderLineItem): JSONObject =
        JSONObject().apply {
            put("itemId", item.itemId)
            put("itemName", item.itemName)
            put("casePacksRequested", item.casePacksRequested)
            put("costPerCasePack", item.costPerCasePack.cents)
            put("totalCost", item.totalCost.cents)
            put("reason", item.reason)
        }

    private fun deserializeIncompleteOrderLineItem(json: JSONObject): IncompleteOrderLineItem =
        IncompleteOrderLineItem(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            casePacksRequested = json.getInt("casePacksRequested"),
            costPerCasePack = Money(json.getLong("costPerCasePack")),
            totalCost = Money(json.getLong("totalCost")),
            reason = json.getString("reason"),
        )

    private fun serializeDeliveredItemLine(line: DeliveredItemLine): JSONObject =
        JSONObject().apply {
            put("itemId", line.itemId)
            put("itemName", line.itemName)
            put("casePacks", line.casePacks)
            put("quantity", line.quantity)
        }

    private fun deserializeDeliveredItemLine(json: JSONObject): DeliveredItemLine =
        DeliveredItemLine(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            casePacks = json.getInt("casePacks"),
            quantity = json.getInt("quantity"),
        )

    private fun serializeDeliveredTruckRecord(record: DeliveredTruckRecord): JSONObject =
        JSONObject().apply {
            put("truckId", record.truckId)
            put("arrivalDay", record.arrivalDay)
            put("isFreshTruck", record.isFreshTruck)
            put("isEarlyTruck", record.isEarlyTruck)
            put("totalCasePacks", record.totalCasePacks)
            val linesArray = JSONArray()
            record.lines.forEach { linesArray.put(serializeDeliveredItemLine(it)) }
            put("lines", linesArray)
        }

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

    private fun serializeScheduledTruck(truck: ScheduledTruck): JSONObject {
        return JSONObject().apply {
            put("truckId", truck.truckId)
            put("scheduledArrivalDay", truck.scheduledArrivalDay)
            put("capacityCasePacks", truck.capacityCasePacks)
            put("isFreshTruck", truck.isFreshTruck)
            put("isEarlyTruck", truck.isEarlyTruck)
            val ordersArray = JSONArray()
            truck.orders.forEach { line -> ordersArray.put(serializePendingOrderLine(line)) }
            put("orders", ordersArray)
        }
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

    private fun serializePendingOrderLine(line: PendingOrderLine): JSONObject {
        return JSONObject().apply {
            put("itemId", line.itemId)
            put("quantity", line.quantity)
            put("casePacksCount", line.casePacksCount)
            put("unitCost", line.unitCost.cents)
            put("orderedOnDay", line.orderedOnDay)
            put("isFresh", line.isFresh)
        }
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

    private fun serializeTruckConfig(config: TruckConfig): JSONObject {
        return JSONObject().apply {
            val daysArray = JSONArray()
            config.deliveryDays.forEach { daysArray.put(it) }
            put("deliveryDays", daysArray)
            put("regularTruckCapacityCasePacks", config.regularTruckCapacityCasePacks)
            put("freshTruckCapacityCasePacks", config.freshTruckCapacityCasePacks)
            put("extraTruckSlotsUnlocked", config.extraTruckSlotsUnlocked)
        }
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

    private fun serializePricingState(pricing: PricingState): JSONObject =
        JSONObject().apply {
            val markupsObj = JSONObject()
            pricing.categoryMarkups.forEach { (cat, pct) -> markupsObj.put(cat.name, pct) }
            put("categoryMarkups", markupsObj)

            val overridesObj = JSONObject()
            pricing.itemOverrides.forEach { (id, pct) -> overridesObj.put(id.toString(), pct) }
            put("itemOverrides", overridesObj)

            val markdownsArr = JSONArray()
            pricing.activeMarkdowns.forEach { (id, md) ->
                markdownsArr.put(JSONObject().apply {
                    put("itemId", id)
                    put("percentOff", md.percentOff)
                    put("reason", md.reason.name)
                    put("appliedOnDay", md.appliedOnDay)
                })
            }
            put("activeMarkdowns", markdownsArr)

            put("defaultMarkup", pricing.defaultMarkup)
            put("smoothedPriceIndex", pricing.smoothedPriceIndex.toDouble())

            val historyArr = JSONArray()
            pricing.priceHistory.forEach { evt ->
                historyArr.put(JSONObject().apply {
                    put("dayNumber", evt.dayNumber)
                    if (evt.itemId != null) put("itemId", evt.itemId)
                    if (evt.category != null) put("category", evt.category.name)
                    put("oldPercent", evt.oldPercent)
                    put("newPercent", evt.newPercent)
                    if (evt.source != null) put("source", evt.source.name)
                })
            }
            put("priceHistory", historyArr)
        }

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
            priceHistory = history,
        )
    }
}
