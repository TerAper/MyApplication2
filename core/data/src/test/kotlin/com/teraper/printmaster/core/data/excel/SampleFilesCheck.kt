package com.teraper.printmaster.core.data.excel

import com.teraper.printmaster.core.model.ImportFile
import com.teraper.printmaster.core.model.ImportFiles
import com.teraper.printmaster.core.model.MatchMemory
import com.teraper.printmaster.core.model.MatchReason
import com.teraper.printmaster.core.model.PaymentMatch
import com.teraper.printmaster.core.model.PaymentMatcher
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the import on real files when PM_SAMPLES points at a folder with invoice.xlsx and
 * bank.xlsx (client data: never committed). Skipped everywhere else.
 */
class SampleFilesCheck {

    private val dir = System.getenv("PM_SAMPLES")?.let(::File)

    @Test
    fun realFiles() {
        assumeTrue(dir != null && File(dir, "bank.xlsx").exists())
        val invoices = ImportFiles.parse(XlsxReader.read(File(dir, "invoice.xlsx").inputStream())) as ImportFile.Invoices
        val bank = ImportFiles.parse(XlsxReader.read(File(dir, "bank.xlsx").inputStream())) as ImportFile.BankStatement
        println("invoices ${invoices.invoices.size} issuer ${invoices.issuerTaxId}; bank ${bank.payments.size} payments, ${bank.skipped} skipped, account ${bank.account} owner ${bank.ownerTaxId}")
        assertEquals(invoices.invoices.size, invoices.invoices.map { it.fingerprint }.toSet().size)
        assertEquals(bank.payments.size, bank.payments.map { it.fingerprint }.toSet().size)

        val clientIds = invoices.invoices.map { it.clientTaxId!! }.distinct().withIndex().associate { (i, tax) -> tax to i + 1L }
        val clients = invoices.invoices.associate { clientIds.getValue(it.clientTaxId!!) to it.clientName }
        val bySerial = invoices.invoices.associate { it.serial to clientIds.getValue(it.clientTaxId!!) }
        val nameOnly = PaymentMatcher(clients, MatchMemory())
        val full = PaymentMatcher(clients, MatchMemory(invoiceClients = bySerial, invoiceAmounts = invoices.invoices.groupBy({ clientIds.getValue(it.clientTaxId!!) }, { it.amount }).mapValues { it.value.toSet() }))

        val serial = Regex("""[A-Z]\s?\d{10}""")
        var right = 0; var asked = 0; var wrong = 0; var none = 0; var known = 0
        val counts = HashMap<String, Int>()
        for (p in bank.payments) {
            val truth = serial.findAll(p.purpose).mapNotNull { bySerial[it.value.replace(" ", "")] }.firstOrNull()
            if (truth != null) {
                known++
                when (val m = nameOnly.match(p)) {
                    is PaymentMatch.Auto -> if (m.clientId == truth) right++ else { wrong++; println("WRONG ${p.payerName} -> ${clients[m.clientId]}") }
                    is PaymentMatch.Suggested -> asked++
                    PaymentMatch.None -> none++
                }
            }
            val key = when (val m = full.match(p)) {
                is PaymentMatch.Auto -> "auto " + m.reason
                is PaymentMatch.Suggested -> "suggested " + m.reason
                PaymentMatch.None -> "none"
            }
            counts[key] = (counts[key] ?: 0) + 1
        }
        println("name only, on $known payments with a known invoice: right $right, asked $asked, wrong $wrong, none $none")
        println("full matching of all ${bank.payments.size}: $counts")
        assertEquals(0, wrong)
    }
}
