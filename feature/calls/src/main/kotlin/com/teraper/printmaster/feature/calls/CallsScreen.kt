package com.teraper.printmaster.feature.calls

import android.Manifest
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmClientPickerSheet
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmRecordingPlayerSheet
import com.teraper.printmaster.core.designsystem.component.PmRecordingRow
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CallFolderStatus
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.RecordingClient
import com.teraper.printmaster.feature.calls.R
import java.time.LocalDateTime

/** Opens the picker at Samsung's call folder, so usually it's one tap on "Use this folder". */
private val SAMSUNG_CALL_FOLDER: Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Recordings/Call")

@Composable
internal fun CallsRoute(onBack: () -> Unit, onOpenClient: (Long) -> Unit, viewModel: CallsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        viewModel.onFolderPicked(it?.toString())
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onContactsPermissionResult()
    }
    CallsScreen(
        state = state,
        actions = CallsActions(
            onBack = onBack,
            onPickFolder = { folderLauncher.launch(SAMSUNG_CALL_FOLDER) },
            onAllowContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
            onRefresh = viewModel::scan,
            onTabChange = viewModel::onTabChange,
            onPlay = viewModel::onPlay,
            onStopPlaying = viewModel::onStopPlaying,
            onAttach = viewModel::onAttachClick,
            onIgnore = viewModel::onIgnore,
            onOpenClient = onOpenClient,
            onPickerQueryChange = viewModel::onPickerQueryChange,
            onClientPicked = viewModel::onClientPicked,
            onDismissPicker = viewModel::onDismissPicker,
        ),
    )
}

internal data class CallsActions(
    val onBack: () -> Unit = {},
    val onPickFolder: () -> Unit = {},
    val onAllowContacts: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onTabChange: (CallsTab) -> Unit = {},
    val onPlay: (CallRecording) -> Unit = {},
    val onStopPlaying: () -> Unit = {},
    val onAttach: (CallRecording) -> Unit = {},
    val onIgnore: (CallRecording) -> Unit = {},
    val onOpenClient: (Long) -> Unit = {},
    val onPickerQueryChange: (String) -> Unit = {},
    val onClientPicked: (Long) -> Unit = {},
    val onDismissPicker: () -> Unit = {},
)

@Composable
internal fun CallsScreen(state: CallsUiState, actions: CallsActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_calls_title),
            navigationLabel = stringResource(R.string.feature_calls_back),
            onNavigate = actions.onBack,
            actions = {
                if (state.isScanning) {
                    CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), color = PmTheme.colors.primary, strokeWidth = 2.dp)
                } else if (state.status.folderName != null) {
                    TextButton(onClick = actions.onRefresh) { Text(stringResource(R.string.feature_calls_refresh)) }
                }
            },
        )
        if (state.isLoading) return@Column
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SetupCard(state.status, state.folderError, actions) }
            if (state.status.folderName != null) {
                item {
                    PmSegmentedButtons(
                        options = CallsTab.entries,
                        selected = state.tab,
                        onSelect = actions.onTabChange,
                        label = {
                            when (it) {
                                CallsTab.UNMATCHED -> stringResource(R.string.feature_calls_tab_unmatched, state.status.unmatched)
                                CallsTab.ALL -> stringResource(R.string.feature_calls_tab_all, state.status.total)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.recordings.isEmpty()) {
                    item {
                        PmEmptyState(
                            icon = PmIcons.Recording,
                            title = stringResource(
                                if (state.tab == CallsTab.UNMATCHED) R.string.feature_calls_all_matched else R.string.feature_calls_none,
                            ),
                            message = stringResource(
                                if (state.tab == CallsTab.UNMATCHED) R.string.feature_calls_all_matched_message else R.string.feature_calls_none_message,
                            ),
                        )
                    }
                }
                items(state.recordings, key = { it.id }) { recording ->
                    RecordingItem(recording, state.tab, actions)
                    HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                }
            }
        }
    }

    state.playing?.let { recording ->
        PmRecordingPlayerSheet(
            recording = recording,
            missingText = stringResource(R.string.feature_calls_file_missing),
            playLabel = stringResource(R.string.feature_calls_play),
            pauseLabel = stringResource(R.string.feature_calls_pause),
            onDismiss = actions.onStopPlaying,
        )
    }
    state.picker?.let { picker ->
        PmClientPickerSheet(
            title = stringResource(R.string.feature_calls_attach_title, picker.recording.caller),
            searchHint = stringResource(R.string.feature_calls_search_hint),
            clearLabel = stringResource(R.string.feature_calls_clear),
            query = picker.query,
            clients = state.pickerClients,
            onQueryChange = actions.onPickerQueryChange,
            onPick = actions.onClientPicked,
            onDismiss = actions.onDismissPicker,
        )
    }
}

@Composable
private fun SetupCard(status: CallFolderStatus, folderError: Boolean, actions: CallsActions) {
    PmCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val folderName = status.folderName
            if (folderName == null) {
                Text(stringResource(R.string.feature_calls_setup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.feature_calls_setup_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PmTheme.colors.inkSecondary,
                )
                PmPrimaryButton(
                    text = stringResource(R.string.feature_calls_pick_folder),
                    onClick = actions.onPickFolder,
                    modifier = Modifier.fillMaxWidth(),
                    icon = PmIcons.Folder,
                )
            } else {
                SetupRow(PmIcons.Folder, stringResource(R.string.feature_calls_folder, folderName), stringResource(R.string.feature_calls_change), actions.onPickFolder)
                if (!status.canReadContacts) {
                    Text(
                        stringResource(R.string.feature_calls_contacts_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = PmTheme.colors.inkSecondary,
                    )
                    SetupRow(PmIcons.Contacts, stringResource(R.string.feature_calls_contacts_off), stringResource(R.string.feature_calls_allow), actions.onAllowContacts)
                }
            }
            if (folderError) {
                Text(stringResource(R.string.feature_calls_folder_error), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
            }
        }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, text: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PmTheme.colors.primary)
        Text(text, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun RecordingItem(recording: CallRecording, tab: CallsTab, actions: CallsActions) {
    PmRecordingRow(
        recording = recording,
        onPlay = { actions.onPlay(recording) },
        subtitle = when {
            recording.isMatched -> recording.clients.joinToString(", ") { it.name }
            recording.ignored -> stringResource(R.string.feature_calls_ignored)
            else -> null
        },
        trailing = {
            Row {
                if (tab == CallsTab.UNMATCHED || recording.ignored) {
                    TextButton(onClick = { actions.onIgnore(recording) }) {
                        Text(stringResource(if (recording.ignored) R.string.feature_calls_unignore else R.string.feature_calls_ignore))
                    }
                }
                if (recording.isMatched && tab == CallsTab.ALL) {
                    IconButton(onClick = { actions.onOpenClient(recording.clients.first().id) }) {
                        Icon(PmIcons.Chevron, contentDescription = stringResource(R.string.feature_calls_open_client))
                    }
                } else {
                    IconButton(onClick = { actions.onAttach(recording) }) {
                        Icon(PmIcons.Link, contentDescription = stringResource(R.string.feature_calls_attach), tint = PmTheme.colors.primary)
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun CallsScreenPreview() {
    val time = LocalDateTime.of(2026, 10, 9, 19, 20)
    PmTheme {
        CallsScreen(
            CallsUiState(
                isLoading = false,
                status = CallFolderStatus("Recordings/Call", canReadContacts = false, total = 12, unmatched = 2),
                recordings = listOf(
                    CallRecording(1, "", "", "077001020", time, 83_000),
                    CallRecording(2, "", "", "Apo", time.minusHours(1), 7_000, clients = listOf(RecordingClient(1, "Apo LLC"))),
                ),
            ),
            CallsActions(),
        )
    }
}
