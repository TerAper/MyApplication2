package com.teraper.printmaster.feature.settings.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.AppLanguage
import com.teraper.printmaster.feature.settings.R

@Composable
internal fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember(context) { context.versionName() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.Recreate -> context.findActivity()?.recreate()
            }
        }
    }
    SettingsScreen(state = state, version = version, onBack = onBack, onLanguageClick = viewModel::onLanguageClick)
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    version: String,
    onBack: () -> Unit,
    onLanguageClick: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_settings_title),
            navigationLabel = stringResource(R.string.feature_settings_back),
            onNavigate = onBack,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.feature_settings_section_language))
            PmCard(Modifier.fillMaxWidth()) {
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEachIndexed { index, language ->
                        if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        LanguageRow(language, selected = language == state.language, onClick = { onLanguageClick(language) })
                    }
                }
            }

            SectionTitle(stringResource(R.string.feature_settings_section_import))
            PmCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            stringResource(R.string.feature_settings_excel_columns),
                            style = MaterialTheme.typography.titleSmall,
                            color = PmTheme.colors.inkMuted,
                        )
                        Text(
                            stringResource(R.string.feature_settings_excel_columns_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = PmTheme.colors.inkMuted,
                        )
                    }
                    PmTag(stringResource(R.string.feature_settings_soon), TagTone.Neutral)
                }
            }

            SectionTitle(stringResource(R.string.feature_settings_section_about))
            PmCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.feature_settings_version, version), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.feature_settings_about_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = PmTheme.colors.inkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = PmTheme.colors.inkMuted,
    )
}

@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, Modifier.padding(8.dp), colors = RadioButtonDefaults.colors(selectedColor = PmTheme.colors.primary))
        Text(
            when (language) {
                AppLanguage.SYSTEM -> stringResource(R.string.feature_settings_language_system)
                // Each language is written in itself, so it can be found whatever the app shows now.
                AppLanguage.ARMENIAN -> "Հայերեն"
                AppLanguage.ENGLISH -> "English"
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun SettingsScreenPreview() {
    PmTheme {
        SettingsScreen(SettingsUiState(AppLanguage.ARMENIAN), version = "0.1.0", onBack = {}, onLanguageClick = {})
    }
}
