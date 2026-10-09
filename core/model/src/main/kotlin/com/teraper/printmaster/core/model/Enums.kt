package com.teraper.printmaster.core.model

enum class ClientType { FIRM, PRIVATE }

enum class PrintType { LASER, INK }

enum class ColorType { COLOR, MONO }

enum class OrderStatus { NEW, IN_PROGRESS, DONE, CANCELLED }

/** Price-list categories, same three as the PrintMaster web version. */
enum class RepairCategory { CARTRIDGE, PRINTER, OTHER }

/** Where a charge (something the client owes) came from. */
enum class ChargeSource { INVOICE_IMPORT, REPAIR, MANUAL }

enum class PaymentMethod { CASH, BANK }

/** Where an imported bank payment stands. */
enum class PaymentMatchState {
    /** Attached by the app (account, remembered name, invoice number or a clear name match). */
    AUTO,

    /** Attached or changed by the user. */
    CONFIRMED,

    /** Waiting for the user: maybe a suggested client, maybe none. Not counted for any client yet. */
    PENDING,

    /** The user said it's not a client payment. */
    IGNORED,
}

/** The two Excel files the app imports. */
enum class ImportKind { INVOICES, BANK_STATEMENT }
