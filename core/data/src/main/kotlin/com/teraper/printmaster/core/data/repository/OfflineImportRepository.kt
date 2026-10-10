package com.teraper.printmaster.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.data.excel.XlsxReader
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.ImportDao
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.database.entity.ClientAliasEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ImportBatchEntity
import com.teraper.printmaster.core.database.entity.MatchRejectionEntity
import com.teraper.printmaster.core.database.entity.PayerAccountEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyCheck
import com.teraper.printmaster.core.model.EntryKind
import com.teraper.printmaster.core.model.ImportBatch
import com.teraper.printmaster.core.model.ImportedEntryDetail
import com.teraper.printmaster.core.model.ImportFile
import com.teraper.printmaster.core.model.ImportFiles
import com.teraper.printmaster.core.model.ImportKind
import com.teraper.printmaster.core.model.ImportPreview
import com.teraper.printmaster.core.model.ImportPreviewResult
import com.teraper.printmaster.core.model.ImportResult
import com.teraper.printmaster.core.model.ImportedInvoice
import com.teraper.printmaster.core.model.ImportedPayment
import com.teraper.printmaster.core.model.MatchMemory
import com.teraper.printmaster.core.model.MatchReason
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PayerNames
import com.teraper.printmaster.core.model.PaymentMatch
import com.teraper.printmaster.core.model.PaymentMatchState
import com.teraper.printmaster.core.model.PaymentMatcher
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.PendingPayment
import com.teraper.printmaster.core.model.RelatedEntry
import com.teraper.printmaster.core.model.sum
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OfflineImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PrintMasterDatabase,
    private val dao: ImportDao,
    private val companyDao: CompanyDao,
    private val companies: CompaniesRepository,
    private val clock: Clock,
    private val analytics: AppAnalytics,
) : ImportRepository {

    /** The file shown in the last preview, waiting for "Import". */
    private var previewed: Pair<ImportFile, ImportPreview>? = null

    override suspend fun preview(uri: String, fileName: String): ImportPreviewResult = withContext(Dispatchers.IO) {
        previewed = null
        val rows = try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use(XlsxReader::read) ?: return@withContext ImportPreviewResult.NotExcel
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext ImportPreviewResult.NotExcel
        }
        previewRows(rows, fileName)
    }

    /** [preview] after reading the file; separate so tests can give rows directly. */
    internal suspend fun previewRows(rows: List<List<String?>>, fileName: String): ImportPreviewResult {
        val file = ImportFiles.parse(rows) ?: return ImportPreviewResult.UnknownLayout
        val company = companies.observeActiveCompany().first() ?: return ImportPreviewResult.NoCompany
        val all = companies.observeCompanies().first()
        val preview = when (file) {
            is ImportFile.Invoices -> previewInvoices(file, fileName, company, all)
            is ImportFile.BankStatement -> previewBank(file, fileName, company, all)
        }
        previewed = file to preview
        return ImportPreviewResult.Ready(preview)
    }

    override suspend fun importPreviewed(saveCompanyIdentity: Boolean): ImportResult? = withContext(Dispatchers.IO) {
        val (file, preview) = previewed ?: return@withContext null
        previewed = null
        val companyId = preview.company.id
        db.withTransaction {
            // Saving only fills what's missing: a matching file can add the account of a company known by ՀՎՀՀ.
            val save = preview.companyCheck == CompanyCheck.MATCHES || (saveCompanyIdentity && preview.companyCheck == CompanyCheck.COMPANY_HAS_NONE)
            if (save) saveIdentity(preview)
            val batchId = dao.insertBatch(
                ImportBatchEntity(
                    companyId = companyId,
                    kind = preview.kind,
                    fileName = preview.fileName,
                    importedAt = clock.millis(),
                    rowCount = preview.rows,
                    addedCount = 0,
                    skippedCount = 0,
                ),
            )
            val result = when (file) {
                is ImportFile.Invoices -> importInvoices(file, companyId, batchId)
                is ImportFile.BankStatement -> importBank(file, companyId, batchId)
            }
            dao.updateBatchCounts(batchId, result.added, preview.rows - result.added - result.updated)
            // New invoices and clients can tell who earlier unmatched payments were from.
            rematchPending(companyId)
            analytics.log("excel_import", mapOf("kind" to preview.kind.name, "added" to result.added))
            result.copy(pending = dao.getPending(companyId).size)
        }
    }

    // Company guard

    private fun invoiceCheck(file: ImportFile.Invoices, company: Company, all: List<Company>): Pair<CompanyCheck, Company?> = when {
        file.issuerTaxId == null -> CompanyCheck.FILE_HAS_NONE to null
        company.taxId == null -> CompanyCheck.COMPANY_HAS_NONE to null
        ImportFiles.sameTaxId(company.taxId, file.issuerTaxId) -> CompanyCheck.MATCHES to null
        else -> CompanyCheck.DIFFERENT to all.firstOrNull { ImportFiles.sameTaxId(it.taxId, file.issuerTaxId) }
    }

    private fun bankCheck(file: ImportFile.BankStatement, company: Company, all: List<Company>): Pair<CompanyCheck, Company?> {
        fun owns(c: Company) = c.bankAccounts.any { ImportFiles.sameAccount(it, file.account) } || ImportFiles.sameTaxId(c.taxId, file.ownerTaxId)
        return when {
            file.account == null && file.ownerTaxId == null -> CompanyCheck.FILE_HAS_NONE to null
            owns(company) -> CompanyCheck.MATCHES to null
            company.bankAccounts.isEmpty() && company.taxId == null -> CompanyCheck.COMPANY_HAS_NONE to null
            else -> CompanyCheck.DIFFERENT to all.firstOrNull(::owns)
        }
    }

    /** Saves the file's ՀՎՀՀ and account on a company that had none. */
    private suspend fun saveIdentity(preview: ImportPreview) {
        val entity = companyDao.getCompany(preview.company.id) ?: return
        val taxId = entity.taxId ?: preview.fileTaxId?.takeLast(8)
        val accounts = entity.bankAccounts.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val withFile = if (preview.fileAccount != null && accounts.none { ImportFiles.sameAccount(it, preview.fileAccount) }) accounts + preview.fileAccount else accounts
        companyDao.updateCompany(entity.copy(taxId = taxId, bankAccounts = withFile.joinToString(", ")))
    }

    // Invoices

    private suspend fun previewInvoices(file: ImportFile.Invoices, fileName: String, company: Company, all: List<Company>): ImportPreview {
        val (check, belongsTo) = invoiceCheck(file, company, all)
        val existing = dao.getChargeKeys(company.id).associateBy { it.fingerprint }
        val known = dao.getClientTaxIds().map { it.taxId }.toSet()
        val live = file.invoices.filterNot { it.cancelled }
        val new = live.filter { it.fingerprint !in existing }
        val changed = file.invoices.count { invoice ->
            val old = existing[invoice.fingerprint] ?: return@count false
            invoice.cancelled || old.amountMinor != invoice.amount.minor || old.epochDay != invoice.issued.toEpochDay()
        }
        return ImportPreview(
            kind = ImportKind.INVOICES,
            fileName = fileName,
            company = company,
            companyCheck = check,
            belongsTo = belongsTo,
            fileTaxId = file.issuerTaxId,
            fileOwner = file.issuerName,
            from = file.invoices.minOfOrNull { it.issued },
            to = file.invoices.maxOfOrNull { it.issued },
            rows = file.invoices.size,
            newRows = new.size,
            alreadyImported = file.invoices.size - new.size - changed,
            changed = changed,
            newAmount = new.map { it.amount }.sum(),
            newClients = new.mapNotNull { it.clientTaxId }.toSet().count { it !in known },
        )
    }

    private suspend fun importInvoices(file: ImportFile.Invoices, companyId: Long, batchId: Long): ImportResult {
        val existing = dao.getChargeKeys(companyId).associateBy { it.fingerprint }
        val clientByTax = dao.getClientTaxIds().associate { it.taxId to it.id }.toMutableMap()
        var added = 0
        var updated = 0
        var created = 0
        for (invoice in file.invoices) {
            val old = existing[invoice.fingerprint]
            if (old != null) {
                when {
                    // Cancelled in the tax system after it was imported.
                    invoice.cancelled -> { dao.deleteCharge(old.id); updated++ }
                    old.amountMinor != invoice.amount.minor || old.epochDay != invoice.issued.toEpochDay() -> {
                        dao.updateCharge(old.id, invoice.amount.minor, invoice.issued.toEpochDay())
                        updated++
                    }
                }
                continue
            }
            if (invoice.cancelled) continue
            val clientId = invoice.clientTaxId?.let { tax ->
                clientByTax[tax] ?: dao.insertClient(newClient(invoice)).also { clientByTax[tax] = it; created++ }
            }
            dao.insertCharge(
                ChargeEntity(
                    companyId = companyId,
                    clientId = clientId,
                    source = ChargeSource.INVOICE_IMPORT,
                    documentNumber = invoice.serial,
                    amountMinor = invoice.amount.minor,
                    dateEpochDay = invoice.issued.toEpochDay(),
                    rawName = invoice.clientName,
                    rawTaxId = invoice.clientTaxId,
                    repairId = null,
                    importBatchId = batchId,
                    createdAt = clock.millis(),
                    fingerprint = invoice.fingerprint,
                    rawData = RawFields.encode(invoice.fields),
                ),
            )
            added++
        }
        return ImportResult(batchId, added, updated, created, pending = 0)
    }

    /** "«ԱՅ ԹԻ ԳՐՈՒՊ» (ՍՊԸ)" becomes the client «ԱՅ ԹԻ ԳՐՈՒՊ» ՍՊԸ. */
    private fun newClient(invoice: ImportedInvoice): ClientEntity {
        var name = invoice.clientName.replace(Regex("""\s*\(([^()]+)\)\s*$"""), " $1").trim()
        val words = name.split(' ')
        if (words.size >= 2 && words.last() == words[words.size - 2]) name = words.dropLast(1).joinToString(" ")
        // A firm's ՀՎՀՀ has 8 digits; a person (ՀԾՀ) has 10.
        val isFirm = invoice.clientTaxId?.length == 8
        return ClientEntity(
            name = name.ifEmpty { invoice.clientTaxId.orEmpty() },
            type = if (isFirm) ClientType.FIRM else ClientType.PRIVATE,
            taxId = invoice.clientTaxId.takeIf { isFirm },
            createdAt = clock.millis(),
        )
    }

    // Bank

    private suspend fun previewBank(file: ImportFile.BankStatement, fileName: String, company: Company, all: List<Company>): ImportPreview {
        val (check, belongsTo) = bankCheck(file, company, all)
        val known = dao.getPaymentFingerprints(company.id).toSet()
        val new = file.payments.filter { it.fingerprint !in known }
        val matcher = matcher(company.id)
        val auto = new.count { matcher.match(it) is PaymentMatch.Auto }
        return ImportPreview(
            kind = ImportKind.BANK_STATEMENT,
            fileName = fileName,
            company = company,
            companyCheck = check,
            belongsTo = belongsTo,
            fileTaxId = file.ownerTaxId,
            fileAccount = file.account,
            fileOwner = file.ownerName,
            from = file.payments.minOfOrNull { it.date },
            to = file.payments.maxOfOrNull { it.date },
            rows = file.payments.size,
            newRows = new.size,
            alreadyImported = file.payments.size - new.size,
            newAmount = new.map { it.amount }.sum(),
            autoMatched = auto,
            toConfirm = new.size - auto,
            skipped = file.skipped,
        )
    }

    private suspend fun importBank(file: ImportFile.BankStatement, companyId: Long, batchId: Long): ImportResult {
        val known = dao.getPaymentFingerprints(companyId).toMutableSet()
        val matcher = matcher(companyId)
        var added = 0
        for (payment in file.payments) {
            if (!known.add(payment.fingerprint)) continue
            val match = matcher.match(payment)
            val id = dao.insertPayment(payment.toEntity(companyId, batchId, match))
            if (id > 0) {
                added++
                if (match is PaymentMatch.Auto) rememberAccount(payment.payerAccount, match.clientId)
            }
        }
        return ImportResult(batchId, added, updated = 0, createdClients = 0, pending = 0)
    }

    private fun ImportedPayment.toEntity(companyId: Long, batchId: Long, match: PaymentMatch) = PaymentEntity(
        companyId = companyId,
        clientId = (match as? PaymentMatch.Auto)?.clientId,
        method = PaymentMethod.BANK,
        amountMinor = amount.minor,
        dateEpochDay = date.toEpochDay(),
        reference = documentNumber,
        rawPayerName = payerName,
        orderId = null,
        importBatchId = batchId,
        note = purpose,
        createdAt = clock.millis(),
        fingerprint = fingerprint,
        payerAccount = payerAccount,
        matchState = if (match is PaymentMatch.Auto) PaymentMatchState.AUTO else PaymentMatchState.PENDING,
        suggestedClientId = (match as? PaymentMatch.Suggested)?.clientId,
        matchReason = (match as? PaymentMatch.Auto)?.reason,
        rawData = RawFields.encode(fields),
    )

    private suspend fun matcher(companyId: Long): PaymentMatcher {
        val clients = dao.getClientNames().associate { it.clientId to it.number }
        val invoices = dao.getInvoiceClients(companyId)
        val memory = MatchMemory(
            aliases = dao.getAliases().associate { it.normalizedName to it.clientId },
            accounts = dao.getPayerAccounts().associate { it.account to it.clientId },
            invoiceClients = invoices.associate { it.documentNumber.uppercase() to it.clientId },
            invoiceAmounts = invoices.groupBy({ it.clientId }, { Money(it.amountMinor) }).mapValues { it.value.toSet() },
            rejected = dao.getRejections().groupBy({ it.nameKey }, { it.clientId }).mapValues { it.value.toSet() },
        )
        return PaymentMatcher(clients, memory)
    }

    /** An account that paid for two different clients is no longer trusted. */
    private suspend fun rememberAccount(account: String?, clientId: Long) {
        if (account == null) return
        val known = dao.getPayerAccounts().firstOrNull { it.account == account }
        when {
            known == null -> dao.upsertPayerAccount(PayerAccountEntity(account, clientId))
            known.clientId != null && known.clientId != clientId -> dao.upsertPayerAccount(PayerAccountEntity(account, null))
        }
    }

    private suspend fun rematchPending(companyId: Long) {
        val pending = dao.getPending(companyId)
        if (pending.isEmpty()) return
        val matcher = matcher(companyId)
        for (row in pending) {
            val payment = ImportedPayment(
                LocalDate.ofEpochDay(row.dateEpochDay), row.reference.orEmpty(), row.payerAccount, row.note, Money(row.amountMinor), row.rawPayerName.orEmpty(),
            )
            when (val match = matcher.match(payment)) {
                is PaymentMatch.Auto -> {
                    dao.setPaymentClient(row.id, match.clientId, PaymentMatchState.AUTO, null, match.reason)
                    rememberAccount(row.payerAccount, match.clientId)
                }
                is PaymentMatch.Suggested -> dao.setPaymentClient(row.id, null, PaymentMatchState.PENDING, match.clientId)
                PaymentMatch.None -> dao.setPaymentClient(row.id, null, PaymentMatchState.PENDING, null)
            }
        }
    }

    // History and review

    override fun observeBatches(): Flow<List<ImportBatch>> = companies.forActiveCompany(emptyList()) { companyId ->
        dao.observeBatches(companyId).map { rows ->
            rows.map {
                ImportBatch(
                    it.id, it.kind, it.fileName,
                    Instant.ofEpochMilli(it.importedAt).atZone(clock.zone).toLocalDateTime(),
                    it.rowCount, it.addedCount, it.skippedCount,
                )
            }
        }
    }

    override suspend fun undo(batchId: Long) {
        dao.deleteBatch(batchId)
    }

    override fun observePending(): Flow<List<PendingPayment>> = companies.forActiveCompany(emptyList()) { companyId ->
        dao.observePending(companyId).map { rows ->
            rows.map {
                PendingPayment(it.id, LocalDate.ofEpochDay(it.epochDay), Money(it.amountMinor), it.payerName.orEmpty(), it.note, it.suggestedClientId, it.suggestedName)
            }
        }
    }

    override suspend fun assign(paymentId: Long, clientId: Long) {
        db.withTransaction {
            val payment = dao.getPayment(paymentId) ?: return@withTransaction
            // Moved away from a wrong client: never match this payer to that client again.
            payment.clientId?.takeIf { it != clientId }?.let { rejectFor(payment, it) }
            dao.setPaymentClient(paymentId, clientId, PaymentMatchState.CONFIRMED, null, MatchReason.MANUAL)
            payment.rawPayerName?.takeIf { it.isNotBlank() }?.let { name ->
                val key = PayerNames.normalize(name).key
                if (key.isNotEmpty()) dao.upsertAlias(ClientAliasEntity(clientId = clientId, normalizedName = key, rawName = name))
            }
            // The user said so: this account is this client's from now on.
            payment.payerAccount?.let { dao.upsertPayerAccount(PayerAccountEntity(it, clientId)) }
            rematchPending(payment.companyId)
        }
    }

    override suspend fun ignore(paymentId: Long) {
        db.withTransaction {
            val payment = dao.getPayment(paymentId) ?: return@withTransaction
            payment.clientId?.let { rejectFor(payment, it) }
            dao.setPaymentClient(paymentId, null, PaymentMatchState.IGNORED, null, MatchReason.MANUAL)
        }
    }

    override suspend fun detachPayment(paymentId: Long) {
        db.withTransaction {
            val payment = dao.getPayment(paymentId) ?: return@withTransaction
            val wrongClient = payment.clientId ?: return@withTransaction
            rejectFor(payment, wrongClient)
            dao.setPaymentClient(paymentId, null, PaymentMatchState.PENDING, null)
            // Maybe the name now points at someone else; never back at the rejected client.
            rematchPending(payment.companyId)
        }
    }

    /** Remembers that this payment's payer is not [clientId]: by name, alias and account. */
    private suspend fun rejectFor(payment: PaymentEntity, clientId: Long) {
        payment.rawPayerName?.takeIf { it.isNotBlank() }?.let { name ->
            val key = PayerNames.normalize(name).key
            if (key.isNotEmpty()) {
                dao.insertRejection(MatchRejectionEntity(key, clientId))
                dao.deleteAlias(key, clientId)
            }
        }
        payment.payerAccount?.let { dao.forgetAccount(it, clientId) }
    }

    override suspend fun moveInvoice(chargeId: Long, clientId: Long) {
        val charge = dao.getCharge(chargeId) ?: return
        if (charge.source == ChargeSource.INVOICE_IMPORT) dao.setChargeClient(chargeId, clientId)
    }

    override fun observeChargeDetail(chargeId: Long): Flow<ImportedEntryDetail?> = dao.observeChargeDetail(chargeId).map { row ->
        row ?: return@map null
        val charge = row.charge
        val payments = charge.documentNumber?.let { dao.getPaymentsNaming(charge.companyId, it) }.orEmpty()
        ImportedEntryDetail(
            kind = when (charge.source) {
                ChargeSource.INVOICE_IMPORT -> EntryKind.INVOICE
                ChargeSource.REPAIR -> EntryKind.REPAIR_CHARGE
                ChargeSource.MANUAL -> EntryKind.MANUAL_CHARGE
            },
            id = charge.id,
            date = LocalDate.ofEpochDay(charge.dateEpochDay),
            amount = Money(charge.amountMinor),
            clientId = charge.clientId,
            clientName = row.clientName,
            documentNumber = charge.documentNumber,
            fileName = row.fileName,
            importedAt = row.importedAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDateTime() },
            fields = RawFields.decode(charge.rawData).ifEmpty { listOfNotNull(charge.rawName?.let { "Name" to it }, charge.rawTaxId?.let { "ՀՎՀՀ" to it }) },
            note = charge.note,
            createdAt = Instant.ofEpochMilli(charge.createdAt).atZone(clock.zone).toLocalDateTime(),
            orderId = row.orderId,
            related = payments.map {
                RelatedEntry(it.id, EntryKind.BANK_PAYMENT, LocalDate.ofEpochDay(it.dateEpochDay), Money(it.amountMinor), it.rawPayerName.orEmpty())
            },
        )
    }

    override fun observePaymentDetail(paymentId: Long): Flow<ImportedEntryDetail?> = dao.observePaymentDetail(paymentId).map { row ->
        row ?: return@map null
        val payment = row.payment
        val numbers = INVOICE_NUMBER.findAll(payment.note).map { it.value.replace(" ", "").uppercase() }.distinct().toList()
        val invoices = if (numbers.isEmpty()) emptyList() else dao.getInvoicesByNumber(payment.companyId, numbers)
        ImportedEntryDetail(
            kind = if (payment.method == PaymentMethod.BANK) EntryKind.BANK_PAYMENT else EntryKind.CASH_PAYMENT,
            id = payment.id,
            date = LocalDate.ofEpochDay(payment.dateEpochDay),
            amount = Money(payment.amountMinor),
            clientId = payment.clientId,
            clientName = row.clientName,
            documentNumber = payment.reference,
            payerName = payment.rawPayerName,
            payerAccount = payment.payerAccount,
            purpose = payment.note,
            matchState = payment.matchState,
            matchReason = payment.matchReason,
            suggestedClientName = row.suggestedName,
            fileName = row.fileName,
            importedAt = row.importedAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDateTime() },
            fields = RawFields.decode(payment.rawData),
            note = payment.note,
            createdAt = Instant.ofEpochMilli(payment.createdAt).atZone(clock.zone).toLocalDateTime(),
            orderId = payment.orderId,
            related = invoices.map {
                RelatedEntry(it.id, EntryKind.INVOICE, LocalDate.ofEpochDay(it.dateEpochDay), Money(it.amountMinor), it.documentNumber.orEmpty())
            },
        )
    }

    private companion object {
        val INVOICE_NUMBER = Regex("""[A-Za-z]\s?\d{10}""")
    }
}
