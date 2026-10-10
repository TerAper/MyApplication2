package com.teraper.printmaster.feature.team.space

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.core.model.TeamMember
import com.teraper.printmaster.core.model.TeamSpace
import com.teraper.printmaster.core.model.TeamState
import com.teraper.printmaster.feature.team.R
import com.teraper.printmaster.feature.team.rememberGoogleSignIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SYNC_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM, HH:mm")

@Composable
internal fun TeamRoute(onBack: () -> Unit, viewModel: TeamViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val signIn = rememberGoogleSignIn(viewModel.googleClientId, viewModel::onSignInResult)
    // "Master finished an order" comes as a notification: ask once the space exists.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val hasSpace = state.team.space != null
    LaunchedEffect(hasSpace) {
        if (hasSpace && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val shareText = state.team.space?.let { stringResource(R.string.feature_team_share_text, it.name, it.joinCode.orEmpty()) }
    val shareTitle = stringResource(R.string.feature_team_share_code)
    TeamScreen(
        state = state,
        actions = TeamActions(
            onBack = onBack,
            onSignIn = signIn,
            onCreateSpace = viewModel::onCreateSpace,
            onNewCode = viewModel::onNewCode,
            onShareCode = {
                if (shareText != null) {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), shareTitle))
                }
            },
            onLinkClick = viewModel::onLinkClick,
            onLinkTo = viewModel::onLinkTo,
            onDismissLink = viewModel::onDismissLink,
            onRemoveClick = viewModel::onRemoveClick,
            onConfirmRemove = viewModel::onConfirmRemove,
            onDismissRemove = viewModel::onDismissRemove,
            onSyncNow = viewModel::onSyncNow,
            onSignOut = viewModel::onSignOut,
            onDismissError = viewModel::onDismissError,
        ),
    )
}

internal data class TeamActions(
    val onBack: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onCreateSpace: () -> Unit = {},
    val onNewCode: () -> Unit = {},
    val onShareCode: () -> Unit = {},
    val onLinkClick: (TeamMember) -> Unit = {},
    val onLinkTo: (Long?) -> Unit = {},
    val onDismissLink: () -> Unit = {},
    val onRemoveClick: (TeamMember) -> Unit = {},
    val onConfirmRemove: () -> Unit = {},
    val onDismissRemove: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
internal fun TeamScreen(state: TeamUiState, actions: TeamActions) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_team_title),
            navigationLabel = stringResource(R.string.feature_team_back),
            onNavigate = actions.onBack,
        )
        if (state.isLoading) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val team = state.team
            val space = team.space
            when {
                !team.available -> PmEmptyState(PmIcons.Warning, stringResource(R.string.feature_team_unavailable), stringResource(R.string.feature_team_unavailable_message))
                team.email == null -> SignInCard(state.busy, actions.onSignIn)
                space == null -> {
                    Text(stringResource(R.string.feature_team_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
                    PmPrimaryButton(stringResource(R.string.feature_team_create), actions.onCreateSpace, Modifier.fillMaxWidth(), enabled = !state.busy)
                }
                space.isOwner -> {
                    CodeCard(space, state.busy, actions)
                    MembersCard(team, state.masters, actions)
                    SyncCard(state, actions)
                }
                else -> Text(stringResource(R.string.feature_team_joined_as_master, space.name), style = MaterialTheme.typography.bodyMedium)
            }
            val email = team.email
            if (email != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.feature_team_signed_in, email), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    TextButton(onClick = actions.onSignOut) { Text(stringResource(R.string.feature_team_sign_out)) }
                }
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = PmTheme.colors.primary, strokeWidth = 3.dp)
        }
    }

    state.error?.let { error ->
        PmMessageDialog(
            title = stringResource(R.string.feature_team_error_title),
            message = stringResource(
                when (error) {
                    TeamError.SIGN_IN -> R.string.feature_team_error_sign_in
                    TeamError.CREATE -> R.string.feature_team_error_create
                    TeamError.NEW_CODE -> R.string.feature_team_error_code
                },
            ),
            okText = stringResource(R.string.feature_team_ok),
            onDismiss = actions.onDismissError,
        )
    }
    state.linking?.let { member -> LinkDialog(member, state.masters, actions) }
    state.confirmRemove?.let { member ->
        PmConfirmDialog(
            title = stringResource(R.string.feature_team_remove_title, member.name),
            message = stringResource(R.string.feature_team_remove_message),
            confirmText = stringResource(R.string.feature_team_remove),
            dismissText = stringResource(R.string.feature_team_cancel),
            onConfirm = actions.onConfirmRemove,
            onDismiss = actions.onDismissRemove,
            destructive = true,
        )
    }
}

@Composable
private fun SignInCard(busy: Boolean, onSignIn: () -> Unit) {
    Text(stringResource(R.string.feature_team_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
    PmPrimaryButton(stringResource(R.string.feature_team_sign_in), onSignIn, Modifier.fillMaxWidth(), enabled = !busy)
    Text(stringResource(R.string.feature_team_sign_in_note), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
}

@Composable
private fun CodeCard(space: TeamSpace, busy: Boolean, actions: TeamActions) {
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.feature_team_code_title), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
            Text(
                space.joinCode.orEmpty().chunked(4).joinToString(" "),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.feature_team_code_hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkSecondary, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PmPrimaryButton(stringResource(R.string.feature_team_share_code), actions.onShareCode, Modifier.weight(1f), icon = PmIcons.Share)
                PmSecondaryButton(stringResource(R.string.feature_team_new_code), actions.onNewCode, Modifier.weight(1f), enabled = !busy)
            }
        }
    }
}

@Composable
private fun MembersCard(team: TeamState, masters: List<Master>, actions: TeamActions) {
    Text(stringResource(R.string.feature_team_members), Modifier.padding(start = 2.dp, top = 4.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
    PmCard(Modifier.fillMaxWidth()) {
        if (team.members.isEmpty()) {
            Text(stringResource(R.string.feature_team_no_members), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkMuted)
        }
        team.members.forEachIndexed { index, member ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            val linked = masters.firstOrNull { it.id == member.masterId }
            Row(Modifier.fillMaxWidth().clickable { actions.onLinkClick(member) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(member.name.ifBlank { member.email }, style = MaterialTheme.typography.titleSmall)
                    Text(member.email, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    if (linked != null) {
                        Text(stringResource(R.string.feature_team_linked_to, linked.name), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.paid)
                    }
                }
                if (linked == null) PmTag(stringResource(R.string.feature_team_needs_link), TagTone.Warning)
                TextButton(onClick = { actions.onRemoveClick(member) }) { Text(stringResource(R.string.feature_team_remove), color = PmTheme.colors.error) }
            }
        }
    }
    Text(stringResource(R.string.feature_team_members_hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
}

@Composable
private fun SyncCard(state: TeamUiState, actions: TeamActions) {
    PmCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_team_sync), style = MaterialTheme.typography.titleSmall)
                val sync = state.sync
                val lastSyncAt = sync.lastSyncAt
                Text(
                    when {
                        sync.running -> stringResource(R.string.feature_team_syncing)
                        sync.failed -> stringResource(R.string.feature_team_sync_failed)
                        lastSyncAt != null -> stringResource(R.string.feature_team_synced_at, lastSyncAt.atZone(ZoneId.systemDefault()).format(SYNC_TIME))
                        else -> stringResource(R.string.feature_team_not_synced)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sync.failed) PmTheme.colors.error else PmTheme.colors.inkMuted,
                )
            }
            TextButton(onClick = actions.onSyncNow, enabled = !state.sync.running) { Text(stringResource(R.string.feature_team_sync_now)) }
        }
    }
}

@Composable
private fun LinkDialog(member: TeamMember, masters: List<Master>, actions: TeamActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissLink,
        title = { Text(stringResource(R.string.feature_team_link_title, member.name.ifBlank { member.email })) },
        text = {
            Column {
                Text(stringResource(R.string.feature_team_link_message), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
                masters.forEach { master ->
                    Text(
                        master.name,
                        Modifier.fillMaxWidth().clickable { actions.onLinkTo(master.id) }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    stringResource(R.string.feature_team_link_new),
                    Modifier.fillMaxWidth().clickable { actions.onLinkTo(null) }.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PmTheme.colors.primary,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = actions.onDismissLink) { Text(stringResource(R.string.feature_team_cancel)) } },
        containerColor = PmTheme.colors.surface,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun TeamScreenPreview() {
    PmTheme {
        TeamScreen(
            TeamUiState(
                isLoading = false,
                team = TeamState(
                    available = true, email = "owner@gmail.com",
                    space = TeamSpace("w1", "Alfa", isOwner = true, joinCode = "K7PQ2MXA"),
                    members = listOf(TeamMember("u1", "Armen", "armen@gmail.com", 1), TeamMember("u2", "Davit", "davit@gmail.com", null)),
                ),
                masters = listOf(Master(1, "Armen")),
            ),
            TeamActions(),
        )
    }
}
