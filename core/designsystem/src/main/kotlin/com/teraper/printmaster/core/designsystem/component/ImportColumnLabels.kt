package com.teraper.printmaster.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.designsystem.R
import com.teraper.printmaster.core.model.ImportColumn

/** What the column holds, in the app's language. */
@Composable
fun ImportColumn.label(): String = stringResource(
    when (this) {
        ImportColumn.INVOICE_NUMBER -> R.string.core_designsystem_column_invoice_number
        ImportColumn.INVOICE_CLIENT_TAX_ID -> R.string.core_designsystem_column_invoice_client_tax_id
        ImportColumn.INVOICE_CLIENT_PERSON_ID -> R.string.core_designsystem_column_invoice_client_person_id
        ImportColumn.INVOICE_CLIENT_NAME -> R.string.core_designsystem_column_invoice_client_name
        ImportColumn.INVOICE_STATUS -> R.string.core_designsystem_column_invoice_status
        ImportColumn.INVOICE_DATE -> R.string.core_designsystem_column_invoice_date
        ImportColumn.INVOICE_AMOUNT -> R.string.core_designsystem_column_invoice_amount
        ImportColumn.INVOICE_ISSUER_TAX_ID -> R.string.core_designsystem_column_invoice_issuer_tax_id
        ImportColumn.INVOICE_ISSUER_NAME -> R.string.core_designsystem_column_invoice_issuer_name
        ImportColumn.BANK_DATE -> R.string.core_designsystem_column_bank_date
        ImportColumn.BANK_DOCUMENT -> R.string.core_designsystem_column_bank_document
        ImportColumn.BANK_TYPE -> R.string.core_designsystem_column_bank_type
        ImportColumn.BANK_PAYER_ACCOUNT -> R.string.core_designsystem_column_bank_payer_account
        ImportColumn.BANK_PURPOSE -> R.string.core_designsystem_column_bank_purpose
        ImportColumn.BANK_INCOMING -> R.string.core_designsystem_column_bank_incoming
        ImportColumn.BANK_PAYER -> R.string.core_designsystem_column_bank_payer
        ImportColumn.BANK_OWN_ACCOUNT -> R.string.core_designsystem_column_bank_own_account
        ImportColumn.BANK_OWN_TAX_ID -> R.string.core_designsystem_column_bank_own_tax_id
    },
)

/** What the import uses the column for. */
@Composable
fun ImportColumn.use(): String = stringResource(
    when (this) {
        ImportColumn.INVOICE_NUMBER -> R.string.core_designsystem_column_invoice_number_use
        ImportColumn.INVOICE_CLIENT_TAX_ID -> R.string.core_designsystem_column_invoice_client_tax_id_use
        ImportColumn.INVOICE_CLIENT_PERSON_ID -> R.string.core_designsystem_column_invoice_client_person_id_use
        ImportColumn.INVOICE_CLIENT_NAME -> R.string.core_designsystem_column_invoice_client_name_use
        ImportColumn.INVOICE_STATUS -> R.string.core_designsystem_column_invoice_status_use
        ImportColumn.INVOICE_DATE -> R.string.core_designsystem_column_invoice_date_use
        ImportColumn.INVOICE_AMOUNT -> R.string.core_designsystem_column_invoice_amount_use
        ImportColumn.INVOICE_ISSUER_TAX_ID -> R.string.core_designsystem_column_invoice_issuer_tax_id_use
        ImportColumn.INVOICE_ISSUER_NAME -> R.string.core_designsystem_column_invoice_issuer_name_use
        ImportColumn.BANK_DATE -> R.string.core_designsystem_column_bank_date_use
        ImportColumn.BANK_DOCUMENT -> R.string.core_designsystem_column_bank_document_use
        ImportColumn.BANK_TYPE -> R.string.core_designsystem_column_bank_type_use
        ImportColumn.BANK_PAYER_ACCOUNT -> R.string.core_designsystem_column_bank_payer_account_use
        ImportColumn.BANK_PURPOSE -> R.string.core_designsystem_column_bank_purpose_use
        ImportColumn.BANK_INCOMING -> R.string.core_designsystem_column_bank_incoming_use
        ImportColumn.BANK_PAYER -> R.string.core_designsystem_column_bank_payer_use
        ImportColumn.BANK_OWN_ACCOUNT -> R.string.core_designsystem_column_bank_own_account_use
        ImportColumn.BANK_OWN_TAX_ID -> R.string.core_designsystem_column_bank_own_tax_id_use
    },
)
