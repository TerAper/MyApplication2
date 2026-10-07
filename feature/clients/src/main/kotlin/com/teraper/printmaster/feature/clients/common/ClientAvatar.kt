package com.teraper.printmaster.feature.clients.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientType

/** Square with the client's initials; firms blue, private people grey. */
@Composable
internal fun ClientAvatar(name: String, type: ClientType, modifier: Modifier = Modifier) {
    val c = PmTheme.colors
    val (bg, fg) = if (type == ClientType.FIRM) c.primaryContainer to c.primary else c.surfaceMuted to c.inkSecondary
    Box(
        modifier = modifier.size(42.dp).background(bg, PmTheme.shapes.button),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), color = fg, style = MaterialTheme.typography.titleSmall)
    }
}

/** "«ԱԲԳ Սերվիս» ՍՊԸ" → "ԱՍ"; legal-form words (ՍՊԸ, ՓԲԸ, ԱՁ…) are skipped. */
internal fun initials(name: String): String {
    val words = ClientSearch.normalizeText(name).uppercase().split(' ')
        .filter { it.isNotEmpty() && it !in LEGAL_FORMS }
    return words.take(2).joinToString("") { it.take(1) }.ifEmpty { "?" }
}

private val LEGAL_FORMS = setOf("ՍՊԸ", "ՓԲԸ", "ԲԲԸ", "ԱՁ", "ՀԿ", "ՊՈԱԿ", "ՓԲԸ", "LLC", "CJSC")
