package com.teraper.printmaster.feature.clients.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What a scan gave: the code's text, nothing (closed), or an error (no Google Play services). */
sealed interface ScanOutcome {
    data class Read(val text: String) : ScanOutcome
    data object Closed : ScanOutcome
    data object Unavailable : ScanOutcome
}

/**
 * Scans the barcode or QR code on a printer's label with Google's scanner (it shows its own
 * camera screen, so the app needs no camera permission). Returns a function that starts a scan.
 */
@Composable
internal fun rememberSerialScanner(onResult: (ScanOutcome) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_ITF, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC,
            )
            .enableAutoZoom()
            .build()
        scope.launch {
            val outcome = try {
                val text = GmsBarcodeScanning.getClient(context, options).startScan().await()?.rawValue
                if (text.isNullOrBlank()) ScanOutcome.Closed else ScanOutcome.Read(text)
            } catch (e: CancellationException) {
                // The user closed the scanner.
                ScanOutcome.Closed
            } catch (e: Exception) {
                ScanOutcome.Unavailable
            }
            onResult(outcome)
        }
    }
}
