package com.teraper.printmaster.core.model

/** One line of the price list: what the client pays ([price]) and what it costs the master ([cost]). */
data class PriceItem(
    val id: Long,
    val category: RepairCategory,
    val name: String,
    val description: String = "",
    val price: Money,
    val cost: Money = Money.ZERO,
) {
    val profit: Money get() = price - cost
}

/** The price-list item form. [id] 0 = new. Amounts are whole dram as typed. */
data class PriceItemDraft(
    val id: Long = 0,
    val category: RepairCategory = RepairCategory.CARTRIDGE,
    val name: String = "",
    val description: String = "",
    val priceDigits: String = "",
    val costDigits: String = "",
) {
    val isNew: Boolean get() = id == 0L
    val price: Money get() = Money.ofDram(priceDigits.toLongOrNull() ?: 0L)
    val cost: Money get() = Money.ofDram(costDigits.toLongOrNull() ?: 0L)
    val profit: Money get() = price - cost

    fun validate(): Set<PriceItemError> = buildSet {
        if (name.isBlank()) add(PriceItemError.NAME_REQUIRED)
        if (!price.isPositive) add(PriceItemError.PRICE_REQUIRED)
    }

    fun withPrice(text: String) = copy(priceDigits = amountDigits(text))

    fun withCost(text: String) = copy(costDigits = amountDigits(text))

    companion object {
        fun from(item: PriceItem) = PriceItemDraft(
            id = item.id,
            category = item.category,
            name = item.name,
            description = item.description,
            priceDigits = item.price.dram.toString(),
            costDigits = item.cost.dram.takeIf { it > 0 }?.toString().orEmpty(),
        )

        /** Keeps only digits, without leading zeros, at most [MoneyEntryDraft.MAX_DIGITS]. */
        fun amountDigits(text: String): String =
            text.filter { it.isDigit() }.trimStart('0').take(MoneyEntryDraft.MAX_DIGITS)
    }
}

enum class PriceItemError { NAME_REQUIRED, PRICE_REQUIRED }

object PriceListSearch {
    /** Same name in the same category counts as a duplicate: "Заправка  HP" = "заправка hp". */
    fun key(name: String): String = ClientSearch.normalizeText(name)

    /** True when every word of [query] is in the item's name or description. */
    fun matches(item: PriceItem, query: String): Boolean {
        val words = ClientSearch.normalizeText(query).split(' ').filter { it.isNotEmpty() }
        val haystack = ClientSearch.normalizeText(item.name + " " + item.description)
        return words.all { it in haystack }
    }
}
