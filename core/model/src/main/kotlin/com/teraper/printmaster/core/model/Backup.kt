package com.teraper.printmaster.core.model

import java.time.LocalDate

/** What a backup file (or this phone) holds, shown side by side before restoring. */
data class BackupSummary(
    val companies: List<String> = emptyList(),
    val clients: Int = 0,
    val orders: Int = 0,
    /** Charges and payments together. */
    val moneyEntries: Int = 0,
    /** Day of the newest client, order, charge or payment; null = no data. */
    val lastChange: LocalDate? = null,
)

/** The result of looking at a file the user picked to restore from. */
sealed interface BackupCheck {
    data class Ready(val backup: BackupSummary, val current: BackupSummary) : BackupCheck

    /** Not a PrintMaster backup, or damaged. */
    data object NotABackup : BackupCheck

    /** Saved by a newer version of the app; update the app first. */
    data object FromNewerApp : BackupCheck
}
