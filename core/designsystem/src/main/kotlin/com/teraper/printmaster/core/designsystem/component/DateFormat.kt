package com.teraper.printmaster.core.designsystem.component

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** 07.10.2026, the format used in Armenia. */
fun LocalDate.formatShort(): String = format(SHORT_DATE)
