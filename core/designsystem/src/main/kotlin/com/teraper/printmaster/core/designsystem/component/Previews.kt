package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Money

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DesignSystemPreview() {
    PmTheme {
        Column(
            modifier = Modifier.background(PmTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PmCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Client firm ՍՊԸ", style = MaterialTheme.typography.titleSmall)
                    AmountText(Money.ofDram(-180_000), tone = AmountTone.Debt)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PmTag("INVOICE", TagTone.Debt)
                PmTag("BANK", TagTone.Info)
                PmTag("CASH", TagTone.Paid)
                PmTag("NEW", TagTone.Neutral)
                PmTag("UNMATCHED", TagTone.Warning)
            }
            PmPrimaryButton("Cash payment", onClick = {}, icon = PmIcons.Add, modifier = Modifier.fillMaxWidth())
            PmSecondaryButton("Import Excel", onClick = {}, icon = PmIcons.ImportExcel, modifier = Modifier.fillMaxWidth())
        }
    }
}
