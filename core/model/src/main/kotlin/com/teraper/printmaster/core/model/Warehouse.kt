package com.teraper.printmaster.core.model

/*
 * Stock is kept as movements: every receive, adjustment, use in an order or move between companies
 * is a row, and the count is their sum. Nothing is overwritten, so a count can always be explained.
 */

/** One warehouse for all own companies (the default), or one per company. */
enum class WarehouseMode { SHARED, PER_COMPANY }

enum class StockKind {
    /** Bought or brought in: +. */
    RECEIVE,

    /** Recount, lost, broken…: + or −, with a reason. */
    ADJUST,

    /** Used on an order: − (back + when the order is reopened). */
    ORDER,

    /** Moved to or from another company's warehouse: − here, + there. */
    MOVE,
}

/** Why a count was changed by hand. */
enum class AdjustReason { RECOUNT, LOST, BROKEN, RETURNED, OTHER }

/** One stock movement of a catalog item; [quantity] is signed. */
data class StockMove(
    val id: Long,
    val itemId: Long,
    val quantity: Int,
    val kind: StockKind,
    val at: Long,
    /** Paid per piece, for a receive. */
    val unitCost: Money = Money.ZERO,
    val reason: AdjustReason? = null,
    val note: String = "",
    val orderId: Long? = null,
    /** The other company of a move. */
    val otherCompanyId: Long? = null,
)

/** A catalog item in the warehouse being viewed: how many there are and how many to keep. */
data class StockLine(val item: CatalogItem, val count: Int, val keepAtLeast: Int = 0) {
    /** Fewer than [keepAtLeast]: time to buy. */
    val isLow: Boolean get() = keepAtLeast > 0 && count < keepAtLeast

    /** How many to buy to get back to [keepAtLeast]. */
    val toBuy: Int get() = if (isLow) keepAtLeast - count.coerceAtLeast(0) else 0

    /** What the pieces cost you (by the item's current cost). */
    val value: Money get() = item.cost * count.coerceAtLeast(0)
}

enum class StockFilter {
    ALL, IN_STOCK, LOW, OUT;

    fun accepts(line: StockLine): Boolean = when (this) {
        ALL -> true
        IN_STOCK -> line.count > 0
        LOW -> line.isLow
        OUT -> line.count <= 0
    }
}

/** A receive or adjustment typed in the stock sheet. */
data class StockDraft(
    val itemId: Long,
    val kind: StockKind = StockKind.RECEIVE,
    /** Receive: how many came in. Adjust: the new count. Move: how many go. */
    val quantityDigits: String = "",
    /** Receive: paid per piece, whole dram. */
    val costDigits: String = "",
    val reason: AdjustReason = AdjustReason.RECOUNT,
    val note: String = "",
    /** Move: the company it goes to. */
    val toCompanyId: Long? = null,
) {
    val quantity: Int get() = quantityDigits.toIntOrNull() ?: 0
    val unitCost: Money get() = Money.ofDram(costDigits.toLongOrNull() ?: 0L)

    /** Digits only; "0" stays (a recount to zero), other leading zeros go. */
    fun withQuantity(text: String): StockDraft {
        val digits = text.filter { it.isDigit() }.take(MAX_DIGITS)
        return copy(quantityDigits = digits.trimStart('0').ifEmpty { if (digits.isNotEmpty()) "0" else "" })
    }

    fun withCost(text: String) = copy(costDigits = PriceItemDraft.amountDigits(text))

    /** The signed change it makes to [current]. */
    fun change(current: Int): Int = when (kind) {
        StockKind.RECEIVE -> quantity
        StockKind.ADJUST -> quantity - current
        StockKind.MOVE -> -quantity
        StockKind.ORDER -> 0
    }

    fun validate(current: Int): Set<StockDraftError> = buildSet {
        when (kind) {
            StockKind.RECEIVE -> if (quantity <= 0) add(StockDraftError.QUANTITY_REQUIRED)
            StockKind.ADJUST -> if (quantityDigits.isEmpty()) add(StockDraftError.QUANTITY_REQUIRED) else if (quantity == current) add(StockDraftError.NO_CHANGE)
            StockKind.MOVE -> {
                if (quantity <= 0) add(StockDraftError.QUANTITY_REQUIRED) else if (quantity > current) add(StockDraftError.NOT_ENOUGH)
                if (toCompanyId == null) add(StockDraftError.COMPANY_REQUIRED)
            }
            StockKind.ORDER -> Unit
        }
    }

    companion object {
        const val MAX_DIGITS = 6
    }
}

enum class StockDraftError { QUANTITY_REQUIRED, NO_CHANGE, NOT_ENOUGH, COMPANY_REQUIRED }
