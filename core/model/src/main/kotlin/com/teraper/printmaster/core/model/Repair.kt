package com.teraper.printmaster.core.model

/**
 * One line of work or goods in a repair. Name, price and cost are copied from the price list
 * ([partId], a service) or the catalog ([itemId], goods) when added, so later price changes don't
 * rewrite finished jobs.
 */
data class RepairLine(
    val partId: Long?,
    val name: String,
    val price: Money,
    val cost: Money = Money.ZERO,
    val quantity: Int = 1,
    val itemId: Long? = null,
    /** Goods that must be bought first: the order can't be finished until they are. */
    val toBuy: Boolean = false,
) {
    val total: Money get() = price * quantity
    val costTotal: Money get() = cost * quantity
}

/**
 * Goods an order still lacks: some marked "to buy", or more needed than the warehouse has.
 * [missing] is how many to buy so that both are covered.
 */
data class GoodsShortage(val itemId: Long, val name: String, val needed: Int, val inStock: Int, val toBuy: Int) {
    val missing: Int get() = maxOf(toBuy, needed - inStock.coerceAtLeast(0))
}

/** What was worked on: a client's item, or one of its parts. */
data class RepairDevice(val item: ClientItem, val part: ClientItemPart? = null) {
    /** "CF283A · HP LaserJet M125", or "… (Office)" so two of one model read differently. */
    val name: String get() = part?.let { "${it.title} · ${item.label}" } ?: item.label

    /** The type of what was worked on, to offer its price-list group first. */
    val typeId: Long get() = (part?.type ?: item.type).id
}

/** The work done during a visit on one device ([device] null = not a listed device). */
data class Repair(
    val id: Long,
    val orderId: Long,
    val device: RepairDevice? = null,
    val note: String = "",
    val lines: List<RepairLine> = emptyList(),
) {
    val total: Money get() = lines.map { it.total }.sum()
    val cost: Money get() = lines.map { it.costTotal }.sum()
    val profit: Money get() = total - cost
}

/** Everything done on an order, and whether it was already billed to the client. */
data class OrderWork(
    val repairs: List<Repair> = emptyList(),
    /** True once the order was finished with work: the client was charged for it. */
    val isBilled: Boolean = false,
    /** Cash taken when the order was finished. */
    val paidCash: Money = Money.ZERO,
) {
    val total: Money get() = repairs.map { it.total }.sum()
    val cost: Money get() = repairs.map { it.cost }.sum()
    val profit: Money get() = total - cost
    val hasWork: Boolean get() = repairs.any { it.lines.isNotEmpty() }

    /** How many of each catalog item (goods) the whole order uses. */
    val goodsNeeded: Map<Long, Int>
        get() = repairs.flatMap { it.lines }.filter { it.itemId != null }
            .groupBy { it.itemId!! }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }

    /** Goods to buy before finishing, given the warehouse's [stock] counts by item id. */
    fun goodsShortages(stock: Map<Long, Int>): List<GoodsShortage> =
        repairs.flatMap { it.lines }.filter { it.itemId != null }.groupBy { it.itemId!! }.map { (itemId, lines) ->
            GoodsShortage(itemId, lines.first().name, lines.sumOf { it.quantity }, stock[itemId] ?: 0, lines.filter { it.toBuy }.sumOf { it.quantity })
        }.filter { it.missing > 0 }
}

/** The repair form. [id] 0 = new. [itemPartId] is a part of client item [itemId]. */
data class RepairDraft(
    val id: Long = 0,
    val orderId: Long,
    val itemId: Long? = null,
    val itemPartId: Long? = null,
    val note: String = "",
    val lines: List<RepairLine> = emptyList(),
) {
    val isNew: Boolean get() = id == 0L
    val total: Money get() = lines.map { it.total }.sum()
    val cost: Money get() = lines.map { it.costTotal }.sum()
    val profit: Money get() = total - cost

    fun validate(): Set<RepairDraftError> = buildSet {
        if (lines.isEmpty()) add(RepairDraftError.LINES_REQUIRED)
    }

    /** Picking a part also picks its item; null item = no device. */
    fun withDevice(itemId: Long?, itemPartId: Long? = null) =
        copy(itemId = itemId, itemPartId = itemPartId.takeIf { itemId != null })

    /** Adding the same price-list item again counts it twice instead of adding a second line. */
    fun plus(item: PriceItem): RepairDraft {
        val index = lines.indexOfFirst { it.partId == item.id }
        return if (index >= 0) {
            withQuantity(index, lines[index].quantity + 1)
        } else {
            copy(lines = lines + RepairLine(item.id, item.name, item.price, item.cost))
        }
    }

    /**
     * Goods from the catalog: the same item again counts twice. With [inStock] known, a line that
     * needs more than the warehouse has is marked "to buy" (never unmarked by itself).
     */
    fun plus(goods: CatalogItem, inStock: Int? = null): RepairDraft {
        val index = lines.indexOfFirst { it.itemId == goods.id }
        val added = if (index >= 0) {
            withQuantity(index, lines[index].quantity + 1)
        } else {
            copy(lines = lines + RepairLine(partId = null, name = goods.title, price = goods.price, cost = goods.cost, itemId = goods.id))
        }
        val line = added.lines.indexOfFirst { it.itemId == goods.id }
        return if (inStock != null && added.lines[line].quantity > inStock) added.withToBuy(line, true) else added
    }

    /** Only goods lines can be "to buy". */
    fun withToBuy(index: Int, toBuy: Boolean) =
        copy(lines = lines.mapIndexed { i, line -> if (i == index && line.itemId != null) line.copy(toBuy = toBuy) else line })

    fun plus(line: RepairLine) = copy(lines = lines + line)

    /** Zero or less removes the line. */
    fun withQuantity(index: Int, quantity: Int) = copy(
        lines = if (quantity <= 0) {
            lines.filterIndexed { i, _ -> i != index }
        } else {
            lines.mapIndexed { i, line -> if (i == index) line.copy(quantity = quantity.coerceAtMost(MAX_QUANTITY)) else line }
        },
    )

    /** A different price for this job only, e.g. a discount. */
    fun withPrice(index: Int, price: Money) =
        copy(lines = lines.mapIndexed { i, line -> if (i == index) line.copy(price = price) else line })

    /** How many of each price-list item is already added, to show on the picker. */
    fun quantities(): Map<Long, Int> = lines.filter { it.partId != null }.associate { it.partId!! to it.quantity }

    /** How many of each catalog item (goods) is already added. */
    fun goodsQuantities(): Map<Long, Int> = lines.filter { it.itemId != null }.associate { it.itemId!! to it.quantity }

    companion object {
        const val MAX_QUANTITY = 999

        fun from(repair: Repair) = RepairDraft(
            id = repair.id,
            orderId = repair.orderId,
            itemId = repair.device?.item?.id,
            itemPartId = repair.device?.part?.id,
            note = repair.note,
            lines = repair.lines,
        )
    }
}

enum class RepairDraftError { LINES_REQUIRED }
