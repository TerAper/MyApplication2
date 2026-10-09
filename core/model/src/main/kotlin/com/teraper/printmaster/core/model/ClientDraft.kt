package com.teraper.printmaster.core.model

/** What the add/edit form holds before saving. [id] 0 = new client. */
data class ClientDraft(
    val id: Long = 0,
    val type: ClientType = ClientType.FIRM,
    val name: String = "",
    val taxId: String = "",
    val note: String = "",
    val phones: List<ContactDraft> = listOf(ContactDraft()),
    val addresses: List<ContactDraft> = listOf(ContactDraft()),
) {
    /** One phone or address row. [id] 0 = not saved yet. */
    data class ContactDraft(val id: Long = 0, val value: String = "", val label: String = "", val mapLink: String? = null)

    val isNew: Boolean get() = id == 0L

    fun validate(): Set<ClientDraftError> = buildSet {
        if (name.isBlank()) add(ClientDraftError.NAME_REQUIRED)
        if (type == ClientType.FIRM) {
            val digits = normalizedTaxId()
            if (digits != null && !TAX_ID_REGEX.matches(digits)) add(ClientDraftError.TAX_ID_FORMAT)
        }
    }

    /** Tax ID with spaces removed; null when empty or the client is private. */
    fun normalizedTaxId(): String? =
        if (type == ClientType.PRIVATE) null else taxId.filterNot { it.isWhitespace() }.ifEmpty { null }

    /** Trimmed values, empty rows dropped. Call only when [validate] is empty. */
    fun normalized(): ClientDraft = copy(
        name = name.trim().replace(MULTI_SPACE, " "),
        taxId = normalizedTaxId().orEmpty(),
        note = note.trim(),
        phones = phones.cleaned(),
        addresses = addresses.cleaned(),
    )

    private fun List<ContactDraft>.cleaned() =
        map { it.copy(value = it.value.trim(), label = it.label.trim()) }.filter { it.value.isNotEmpty() }

    companion object {
        /** Armenian ՀՎՀՀ is 8 digits. */
        private val TAX_ID_REGEX = Regex("""\d{8}""")
        private val MULTI_SPACE = Regex("""\s+""")

        fun from(client: Client) = ClientDraft(
            id = client.id,
            type = client.type,
            name = client.name,
            taxId = client.taxId.orEmpty(),
            note = client.note,
            phones = client.phones.map { ContactDraft(it.id, it.number, it.label) }.ifEmpty { listOf(ContactDraft()) },
            addresses = client.addresses.map { ContactDraft(it.id, it.address, it.label, it.mapLink) }.ifEmpty { listOf(ContactDraft()) },
        )
    }
}

enum class ClientDraftError { NAME_REQUIRED, TAX_ID_FORMAT }
