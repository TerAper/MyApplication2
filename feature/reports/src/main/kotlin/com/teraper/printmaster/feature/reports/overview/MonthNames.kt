package com.teraper.printmaster.feature.reports.overview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "October 2026" / "Հոկտեմբեր 2026" */
@Composable
internal fun YearMonth.longName(): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, locale()).replaceFirstChar { it.titlecase(locale()) } + " " + year

/** "Oct" / "Հոկ" */
@Composable
internal fun YearMonth.shortName(): String =
    month.getDisplayName(TextStyle.SHORT_STANDALONE, locale()).replaceFirstChar { it.titlecase(locale()) }.trimEnd('.')
