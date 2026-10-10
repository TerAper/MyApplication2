package com.teraper.printmaster.core.designsystem.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * False on a master's phone that joined a company: debts, balances and cash totals are the
 * company's business, so screens hide them. Set once by the app for the whole UI.
 */
val LocalShowMoney = staticCompositionLocalOf { true }
