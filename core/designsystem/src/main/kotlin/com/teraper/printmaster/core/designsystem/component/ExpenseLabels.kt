package com.teraper.printmaster.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.designsystem.R
import com.teraper.printmaster.core.model.ExpenseCategory

@Composable
fun ExpenseCategory.label(): String = stringResource(
    when (this) {
        ExpenseCategory.PARTS -> R.string.core_designsystem_expense_parts
        ExpenseCategory.TONER -> R.string.core_designsystem_expense_toner
        ExpenseCategory.TRANSPORT -> R.string.core_designsystem_expense_transport
        ExpenseCategory.RENT -> R.string.core_designsystem_expense_rent
        ExpenseCategory.SALARY -> R.string.core_designsystem_expense_salary
        ExpenseCategory.TAXES -> R.string.core_designsystem_expense_taxes
        ExpenseCategory.OTHER -> R.string.core_designsystem_expense_other
    },
)
