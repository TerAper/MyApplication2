package com.teraper.printmaster.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector

/** All icons in one place, so changing the icon style is a one-file change. */
object PmIcons {
    val Today: ImageVector = Icons.Rounded.Home
    val Clients: ImageVector = Icons.Rounded.Group
    val Orders: ImageVector = Icons.Rounded.CalendarMonth
    val Payments: ImageVector = Icons.Rounded.AccountBalanceWallet
    val More: ImageVector = Icons.Rounded.GridView

    val Add: ImageVector = Icons.Rounded.Add
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Close: ImageVector = Icons.Rounded.Close
    val Search: ImageVector = Icons.Rounded.Search
    val Call: ImageVector = Icons.Rounded.Call
    val Map: ImageVector = Icons.Rounded.Place
    val ImportExcel: ImageVector = Icons.Rounded.UploadFile
    val Edit: ImageVector = Icons.Rounded.Edit
    val Delete: ImageVector = Icons.Rounded.Delete
}
