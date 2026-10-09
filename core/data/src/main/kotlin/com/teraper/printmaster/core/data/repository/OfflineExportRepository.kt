package com.teraper.printmaster.core.data.repository

import android.content.Context
import android.net.Uri
import com.teraper.printmaster.core.data.export.Cell
import com.teraper.printmaster.core.data.export.XlsxWriter
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.sum
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal class OfflineExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clients: ClientsRepository,
    private val companies: CompaniesRepository,
    private val clock: Clock,
) : ExportRepository {

    override suspend fun debtReportFileName(): String {
        val company = companies.observeActiveCompany().first()?.name.orEmpty()
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
        return listOf("Debts", company, LocalDate.now(clock).toString()).filter { it.isNotEmpty() }.joinToString("-") + ".xlsx"
    }

    override suspend fun saveDebtReport(uri: String, onlyDebtors: Boolean, labels: DebtReportLabels): Boolean =
        withContext(Dispatchers.IO) {
            writing {
                val out = context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: return@writing false
                out.use { writeDebtReport(it, onlyDebtors, labels) }
                true
            }
        }

    override suspend fun cacheDebtReport(onlyDebtors: Boolean, labels: DebtReportLabels): String? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
            val file = File(dir, debtReportFileName())
            val written = writing {
                file.outputStream().use { writeDebtReport(it, onlyDebtors, labels) }
                true
            }
            if (written) file.path else null
        }

    private suspend fun writeDebtReport(out: OutputStream, onlyDebtors: Boolean, labels: DebtReportLabels) {
        val company = companies.observeActiveCompany().first()
        val rows = clients.observeClientSummaries().first()
            .filter { if (onlyDebtors) it.balance.isPositive else true }
            .sortedWith(compareByDescending<ClientSummary> { it.balance }.thenBy { it.client.name })
        XlsxWriter.write(
            out = out,
            sheetName = labels.title,
            columnWidths = listOf(34, 12, 16, 14, 14, 14, 14),
            rows = buildList {
                add(listOf(Cell.Text(labels.title, bold = true)))
                add(listOf(Cell.Text(listOfNotNull(company?.name, LocalDate.now(clock).format(DATE)).joinToString(" · "))))
                add(emptyList())
                add(
                    listOf(labels.client, labels.taxId, labels.phone, labels.charged, labels.paid, labels.balance, labels.lastPayment)
                        .map { Cell.Text(it, bold = true) },
                )
                rows.forEach { row ->
                    add(
                        listOf(
                            Cell.Text(row.client.name),
                            row.client.taxId?.let { Cell.Text(it) } ?: Cell.Empty,
                            row.client.phones.firstOrNull()?.let { Cell.Text(it.number) } ?: Cell.Empty,
                            Cell.Amount(row.charged.dram),
                            Cell.Amount(row.paid.dram),
                            Cell.Amount(row.balance.dram),
                            row.lastPaymentDate?.let { Cell.Text(it.format(DATE)) } ?: Cell.Empty,
                        ),
                    )
                }
                add(
                    listOf(
                        Cell.Text(labels.total, bold = true), Cell.Empty, Cell.Empty,
                        Cell.Amount(rows.map { it.charged }.sum().dram, bold = true),
                        Cell.Amount(rows.map { it.paid }.sum().dram, bold = true),
                        Cell.Amount(rows.map { it.balance }.sum().dram, bold = true),
                    ),
                )
            },
        )
    }

    private inline fun writing(block: () -> Boolean): Boolean = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
