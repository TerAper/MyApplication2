package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.model.ImportBatch
import com.teraper.printmaster.core.model.ImportedEntryDetail
import com.teraper.printmaster.core.model.ImportPreviewResult
import com.teraper.printmaster.core.model.ImportResult
import com.teraper.printmaster.core.model.PendingPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [previewResult] is what any file turns out to be; calls are recorded. */
class FakeImportRepository(
    var previewResult: ImportPreviewResult = ImportPreviewResult.UnknownLayout(),
    var importResult: ImportResult? = ImportResult(1, added = 3, updated = 0, createdClients = 1, pending = 0),
) : ImportRepository {
    val batches = MutableStateFlow<List<ImportBatch>>(emptyList())
    val pending = MutableStateFlow<List<PendingPayment>>(emptyList())
    val imported = mutableListOf<Boolean>()
    val assigned = mutableListOf<Pair<Long, Long>>()
    val ignored = mutableListOf<Long>()
    val undone = mutableListOf<Long>()
    val details = MutableStateFlow<Map<Long, ImportedEntryDetail>>(emptyMap())
    val movedInvoices = mutableListOf<Pair<Long, Long>>()
    val detached = mutableListOf<Long>()

    override suspend fun preview(uri: String, fileName: String) = previewResult

    override suspend fun importPreviewed(saveCompanyIdentity: Boolean): ImportResult? {
        imported += saveCompanyIdentity
        return importResult
    }

    override fun observeBatches(): Flow<List<ImportBatch>> = batches

    override suspend fun undo(batchId: Long) {
        undone += batchId
        batches.value = batches.value.filterNot { it.id == batchId }
    }

    override fun observePending(): Flow<List<PendingPayment>> = pending

    override suspend fun assign(paymentId: Long, clientId: Long) {
        assigned += paymentId to clientId
        pending.value = pending.value.filterNot { it.id == paymentId }
    }

    override suspend fun ignore(paymentId: Long) {
        ignored += paymentId
        pending.value = pending.value.filterNot { it.id == paymentId }
    }

    override fun observeChargeDetail(chargeId: Long): Flow<ImportedEntryDetail?> = details.map { it[chargeId] }

    override fun observePaymentDetail(paymentId: Long): Flow<ImportedEntryDetail?> = details.map { it[paymentId] }

    override suspend fun moveInvoice(chargeId: Long, clientId: Long) {
        movedInvoices += chargeId to clientId
    }

    override suspend fun detachPayment(paymentId: Long) {
        detached += paymentId
    }
}
