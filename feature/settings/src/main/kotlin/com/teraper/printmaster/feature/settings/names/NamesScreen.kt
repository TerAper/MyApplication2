package com.teraper.printmaster.feature.settings.names

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.data.AppNames
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.NamedRole
import com.teraper.printmaster.core.model.RoleName
import com.teraper.printmaster.core.model.TextLanguage
import com.teraper.printmaster.feature.settings.R

@Composable
internal fun NamesRoute(onBack: () -> Unit, viewModel: NamesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = AppNames.languageOf(context.resources)
    LaunchedEffect(language) { viewModel.onLanguage(language) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Every screen reads its texts again, now with the new words.
                NamesEvent.Saved -> context.findActivity()?.recreate()
            }
        }
    }
    NamesScreen(
        state = state,
        onBack = onBack,
        onOneChange = viewModel::onOneChange,
        onManyChange = viewModel::onManyChange,
        onReset = viewModel::onReset,
        onSave = viewModel::onSave,
    )
}

@Composable
internal fun NamesScreen(
    state: NamesUiState,
    onBack: () -> Unit,
    onOneChange: (NamedRole, String) -> Unit,
    onManyChange: (NamedRole, String) -> Unit,
    onReset: (NamedRole) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = state.language ?: return
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(R.string.feature_settings_names_title),
            navigationLabel = stringResource(R.string.feature_settings_back),
            onNavigate = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.feature_settings_names_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
            NamedRole.entries.forEach { role ->
                RoleCard(
                    own = language.defaults(role),
                    name = state.names[role] ?: RoleName(),
                    onOneChange = { onOneChange(role, it) },
                    onManyChange = { onManyChange(role, it) },
                    onReset = { onReset(role) },
                )
            }
        }
        PmPrimaryButton(
            text = stringResource(R.string.feature_settings_names_save),
            onClick = onSave,
            enabled = state.hasChanges,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** One role: the app's word (written as is, never renamed itself), then the customer's. */
@Composable
private fun RoleCard(own: RoleName, name: RoleName, onOneChange: (String) -> Unit, onManyChange: (String) -> Unit, onReset: () -> Unit) {
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${own.one.capitalized()} · ${own.many.capitalized()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (name.one.isNotEmpty() || name.many.isNotEmpty()) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.feature_settings_names_reset)) }
                }
            }
            PmTextField(
                value = name.one,
                onValueChange = onOneChange,
                label = stringResource(R.string.feature_settings_names_one),
                placeholder = own.one.capitalized(),
            )
            PmTextField(
                value = name.many,
                onValueChange = onManyChange,
                label = stringResource(R.string.feature_settings_names_many),
                placeholder = own.many.capitalized(),
            )
        }
    }
}

private fun String.capitalized() = replaceFirstChar { it.uppercaseChar() }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun NamesScreenPreview() {
    PmTheme {
        NamesScreen(
            NamesUiState(TextLanguage.ENGLISH, mapOf(NamedRole.MASTER to RoleName("Courier", "Couriers"))),
            onBack = {}, onOneChange = { _, _ -> }, onManyChange = { _, _ -> }, onReset = {}, onSave = {},
        )
    }
}
