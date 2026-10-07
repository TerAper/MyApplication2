package com.teraper.printmaster.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.designsystem.R
import com.teraper.printmaster.core.model.OrderStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

// Order words are shown by the Orders and Today tabs and the client card.

@Composable
fun OrderStatus.label(): String = stringResource(
    when (this) {
        OrderStatus.NEW -> R.string.core_designsystem_order_new
        OrderStatus.IN_PROGRESS -> R.string.core_designsystem_order_in_progress
        OrderStatus.DONE -> R.string.core_designsystem_order_done
        OrderStatus.CANCELLED -> R.string.core_designsystem_order_cancelled
    },
)

fun OrderStatus.tagTone(): TagTone = when (this) {
    OrderStatus.NEW -> TagTone.Info
    OrderStatus.IN_PROGRESS -> TagTone.Warning
    OrderStatus.DONE -> TagTone.Paid
    OrderStatus.CANCELLED -> TagTone.Neutral
}

/** "09:30" */
fun LocalTime.formatTime(): String = "%02d:%02d".format(hour, minute)

/** "Today" / "Tomorrow" / "Yesterday" / "Thu 15.10". */
@Composable
fun LocalDate.relativeLabel(today: LocalDate): String = when (this) {
    today -> stringResource(R.string.core_designsystem_today)
    today.plusDays(1) -> stringResource(R.string.core_designsystem_tomorrow)
    today.minusDays(1) -> stringResource(R.string.core_designsystem_yesterday)
    else -> "${weekdayShort()} ${"%02d.%02d".format(dayOfMonth, monthValue)}"
}

/** Short weekday in the app's language, e.g. "Thu" / "Հնգ". */
@Composable
fun LocalDate.weekdayShort(): String {
    val locale = LocalConfiguration.current.locales[0]
    return dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
}
