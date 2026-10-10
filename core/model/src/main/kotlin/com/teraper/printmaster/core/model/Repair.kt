package com.teraper.printmaster.core.model

/**
 * One line of work or parts in a repair. Name, price and cost are copied from the price
 * list ([partId]) when added, so later price changes don't rewrite finished jobs.
 */
data class RepairLine(
    val partId: Long?,
    val name: String,
    val price: Money,
    val cost: Money = Money.ZERO,
    val quantity: Int = 1,
) {
    val total: Money get() = price * quantity
    val costTotal: Money get() = cost * quantity
}

/** What was worked on: a client's printer, or one of its cartridges. */
data class RepairDevice(val printer: ClientPrinter, val cartridge: ClientPrinterCartridge? = null) {
    /** "CF283A · HP LaserJet M125", or "… (Office)" so two printers of one model read differently. */
    val name: String get() {
        val model = printer.model.fullName + if (printer.location.isBlank()) "" else " (${printer.location})"
        return cartridge?.let { "${it.cartridge.name} · $model" } ?: model
    }
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
}

/** The repair form. [id] 0 = new. [cartridgeId] is a cartridge of printer [printerId]. */
data class RepairDraft(
    val id: Long = 0,
    val orderId: Long,
    val printerId: Long? = null,
    val cartridgeId: Long? = null,
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

    /** Picking a cartridge also picks its printer; null printer = no device. */
    fun withDevice(printerId: Long?, cartridgeId: Long? = null) =
        copy(printerId = printerId, cartridgeId = cartridgeId.takeIf { printerId != null })

    /** Adding the same price-list item again counts it twice instead of adding a second line. */
    fun plus(item: PriceItem): RepairDraft {
        val index = lines.indexOfFirst { it.partId == item.id }
        return if (index >= 0) {
            withQuantity(index, lines[index].quantity + 1)
        } else {
            copy(lines = lines + RepairLine(item.id, item.name, item.price, item.cost))
        }
    }

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

    companion object {
        const val MAX_QUANTITY = 999

        fun from(repair: Repair) = RepairDraft(
            id = repair.id,
            orderId = repair.orderId,
            printerId = repair.device?.printer?.id,
            cartridgeId = repair.device?.cartridge?.id,
            note = repair.note,
            lines = repair.lines,
        )
    }
}

enum class RepairDraftError { LINES_REQUIRED }

/** Which price-list group fits a device: cartridge work for a cartridge, printer work for a printer. */
fun RepairDraft.suggestedCategory(): RepairCategory? = when {
    cartridgeId != null -> RepairCategory.CARTRIDGE
    printerId != null -> RepairCategory.PRINTER
    else -> null
}
