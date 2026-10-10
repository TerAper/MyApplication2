package com.teraper.printmaster.feature.team.space

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.AttachResult
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.Master
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
    // New, finished and turned-down orders come as notifications: ask once something is shared.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(state.sharesAnything) {
        if (state.sharesAnything && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val shareTitle = stringResource(R.string.feature_team_share_code)
    val ownerName = state.team.displayName.orEmpty()
    TeamScreen(
        state = state,
        actions = TeamActions(
            onBack = onBack,
            onSignIn = signIn,
            onSignOut = viewModel::onSignOut,
            onInvite = viewModel::onInvite,
            onNewCode = viewModel::onNewCode,
            onShareCode = { company -> context.shareCode(company, ownerName, shareTitle) },
            onRemoveClick = viewModel::onRemoveClick,
            onConfirmRemove = viewModel::onConfirmRemove,
            onDismissRemove = viewModel::onDismissRemove,
            onAttachClick = viewModel::onAttachClick,
            onAttachCodeChange = viewModel::onAttachCodeChange,
            onAttachNameChange = viewModel::onAttachNameChange,
            onConfirmAttach = viewModel::onConfirmAttach,
            onDismissAttach = viewModel::onDismissAttach,
            onDismissAttached = viewModel::onDismissAttached,
            onLeaveClick = viewModel::onLeaveClick,
            onConfirmLeave = viewModel::onConfirmLeave,
            onDismissLeave = viewModel::onDismissLeave,
            onSyncNow = viewModel::onSyncNow,
            onDismissError = viewModel::onDismissError,
        ),
    )
}

private fun Context.shareCode(company: Company, ownerName: String, title: String) {
    val code = company.joinCode ?: return
    val text = getString(R.string.feature_team_share_text, company.name, code)
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title))
}

internal data class TeamActions(
    val onBack: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onInvite: (Long) -> Unit = {},
    val onNewCode: (Long) -> Unit = {},
    val onShareCode: (Company) -> Unit = {},
    val onRemoveClick: (Company, Master) -> Unit = { _, _ -> },
    val onConfirmRemove: () -> Unit = {},
    val onDismissRemove: () -> Unit = {},
    val onAttachClick: () -> Unit = {},
    val onAttachCodeChange: (String) -> Unit = {},
    val onAttachNameChange: (String) -> Unit = {},
    val onConfirmAttach: () -> Unit = {},
    val onDismissAttach: () -> Unit = {},
    val onDismissAttached: () -> Unit = {},
    val onLeaveClick: (Company) -> Unit = {},
    val onConfirmLeave: () -> Unit = {},
    val onDismissLeave: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val team = state.team
            when {
                !team.available -> PmEmptyState(PmIcons.Warning, stringResource(R.string.feature_team_unavailable), stringResource(R.string.feature_team_unavailable_message))
                team.email == null -> {
                    Text(stringResource(R.string.feature_team_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
                    PmPrimaryButton(stringResource(R.string.feature_team_sign_in), actions.onSignIn, Modifier.fillMaxWidth(), icon = PmIcons.Google)
                }
                else -> {
                    Text(stringResource(R.string.feature_team_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
                    SectionTitle(stringResource(R.string.feature_team_my_companies))
                    state.own.forEach { OwnCompanyCard(it, busy = state.busyCompanyId == it.company.id, actions) }
                    SectionTitle(stringResource(R.string.feature_team_attached_companies))
                    AttachedCompaniesCard(state.attached, state.busyCompanyId, actions)
                    PmSecondaryButton(stringResource(R.string.feature_team_enter_code), actions.onAttachClick, Modifier.fillMaxWidth(), icon = PmIcons.Add)
                    if (state.sharesAnything) SyncCard(state, actions)
                    AccountRow(team, actions)
                }
            }
        }
    }
    Dialogs(state, actions)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 2.dp, top = 10.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

/** An own company: its code for masters and who attached. */
@Composable
private fun OwnCompanyCard(sharing: OwnCompanySharing, busy: Boolean, actions: TeamActions) {
    val company = sharing.company
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompanyBadge(company.initials, company.colorIndex)
                Text(company.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = PmTheme.colors.primary, strokeWidth = 2.dp)
            }
            val code = company.joinCode
            if (code == null) {
                Text(stringResource(R.string.feature_team_invite_hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                PmPrimaryButton(stringResource(R.string.feature_team_invite), { actions.onInvite(company.id) }, Modifier.fillMaxWidth(), enabled = !busy, icon = PmIcons.Share)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.feature_team_code_title), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
                        Text(
                            code.chunked(4).joinToString(" "),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(onClick = { actions.onNewCode(company.id) }, enabled = !busy) { Text(stringResource(R.string.feature_team_new_code)) }
                }
                PmPrimaryButton(stringResource(R.string.feature_team_share_code), { actions.onShareCode(company) }, Modifier.fillMaxWidth(), icon = PmIcons.Share)
                if (sharing.masters.isEmpty()) {
                    Text(stringResource(R.string.feature_team_no_members), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                } else {
                    Text(stringResource(R.string.feature_team_members), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
                    sharing.masters.forEach { master ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(master.name, style = MaterialTheme.typography.bodyLarge)
                                master.email?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted) }
                            }
                            TextButton(onClick = { actions.onRemoveClick(company, master) }) { Text(stringResource(R.string.feature_team_remove), color = PmTheme.colors.error) }
                        }
                    }
                }
            }
        }
    }
}

/** Other owners' companies this user works for. */
@Composable
private fun AttachedCompaniesCard(companies: List<Company>, busyCompanyId: Long?, actions: TeamActions) {
    PmCard(Modifier.fillMaxWidth()) {
        if (companies.isEmpty()) {
            Text(stringResource(R.string.feature_team_no_attached), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkMuted)
        }
        companies.forEachIndexed { index, company ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompanyBadge(company.initials, company.colorIndex)
                Column(Modifier.weight(1f)) {
                    Text(company.name, style = MaterialTheme.typography.titleSmall)
                    if (company.ownerName.isNotBlank()) {
                        Text(stringResource(R.string.feature_team_owner, company.ownerName), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                }
                when {
                    busyCompanyId == company.id -> CircularProgressIndicator(Modifier.size(20.dp), color = PmTheme.colors.primary, strokeWidth = 2.dp)
                    company.isShared -> TextButton(onClick = { actions.onLeaveClick(company) }) { Text(stringResource(R.string.feature_team_leave), color = PmTheme.colors.error) }
                    else -> PmTag(stringResource(R.string.feature_team_left), TagTone.Neutral)
                }
            }
        }
    }
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
private fun AccountRow(team: TeamState, actions: TeamActions) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.feature_team_signed_in, team.email.orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        TextButton(onClick = actions.onSignOut) { Text(stringResource(R.string.feature_team_sign_out)) }
    }
}

@Composable
private fun Dialogs(state: TeamUiState, actions: TeamActions) {
    state.error?.let { error ->
        PmMessageDialog(
            title = stringResource(R.string.feature_team_error_title),
            message = stringResource(
                when (error) {
                    TeamError.SIGN_IN -> R.string.feature_team_error_sign_in
                    TeamError.INVITE -> R.string.feature_team_error_create
                    TeamError.NEW_CODE -> R.string.feature_team_error_code
                    TeamError.REMOVE, TeamError.LEAVE -> R.string.feature_team_error_internet
                },
            ),
            okText = stringResource(R.string.feature_team_ok),
            onDismiss = actions.onDismissError,
        )
    }
    state.attach?.let { AttachDialog(it, actions) }
    state.justAttached?.let { attached ->
        PmMessageDialog(
            title = stringResource(R.string.feature_team_attached_title, attached.companyName),
            message = stringResource(R.string.feature_team_attached_message, attached.ownerName.ifBlank { attached.companyName }),
            okText = stringResource(R.string.feature_team_ok),
            onDismiss = actions.onDismissAttached,
        )
    }
    state.confirmRemove?.let { (company, master) ->
        PmConfirmDialog(
            title = stringResource(R.string.feature_team_remove_title, master.name),
            message = stringResource(R.string.feature_team_remove_message, company.name),
            confirmText = stringResource(R.string.feature_team_remove),
            dismissText = stringResource(R.string.feature_team_cancel),
            onConfirm = actions.onConfirmRemove,
            onDismiss = actions.onDismissRemove,
            destructive = true,
        )
    }
    state.confirmLeave?.let { company ->
        PmConfirmDialog(
            title = stringResource(R.string.feature_team_leave_title, company.name),
            message = stringResource(R.string.feature_team_leave_message),
            confirmText = stringResource(R.string.feature_team_leave),
            dismissText = stringResource(R.string.feature_team_cancel),
            onConfirm = actions.onConfirmLeave,
            onDismiss = actions.onDismissLeave,
            destructive = true,
        )
    }
}

@Composable
private fun AttachDialog(form: AttachForm, actions: TeamActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissAttach,
        title = { Text(stringResource(R.string.feature_team_enter_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.feature_team_attach_hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkSecondary)
                PmTextField(
                    value = form.code,
                    onValueChange = actions.onAttachCodeChange,
                    label = stringResource(R.string.feature_team_code_label),
                    error = when {
                        form.codeTooShort -> stringResource(R.string.feature_team_error_code_short)
                        form.failure == AttachResult.WrongCode -> stringResource(R.string.feature_team_error_wrong_code)
                        form.failure == AttachResult.OwnCompany -> stringResource(R.string.feature_team_error_own_code)
                        form.failure == AttachResult.AlreadyAttached -> stringResource(R.string.feature_team_error_already)
                        else -> null
                    },
                    capitalization = KeyboardCapitalization.Characters,
                )
                PmTextField(
                    value = form.name,
                    onValueChange = actions.onAttachNameChange,
                    label = stringResource(R.string.feature_team_your_name),
                    error = if (form.nameMissing) stringResource(R.string.feature_team_error_name) else null,
                    capitalization = KeyboardCapitalization.Words,
                )
                if (form.failure == AttachResult.Failed || form.failure == AttachResult.NotSignedIn) {
                    Text(stringResource(R.string.feature_team_error_join), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onConfirmAttach, enabled = !form.busy) { Text(stringResource(R.string.feature_team_join)) } },
        dismissButton = { TextButton(onClick = actions.onDismissAttach) { Text(stringResource(R.string.feature_team_cancel)) } },
        containerColor = PmTheme.colors.surface,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun TeamScreenPreview() {
    PmTheme {
        TeamScreen(
            TeamUiState(
                isLoading = false,
                team = TeamState(available = true, email = "apo@gmail.com"),
                own = listOf(
                    OwnCompanySharing(Company(1, "Xerox Service", joinCode = "K7PQ2MXA"), listOf(Master(3, "Armen", email = "armen@gmail.com", companyIds = setOf(1)))),
                    OwnCompanySharing(Company(2, "Yellow Print", colorIndex = 2), emptyList()),
                ),
                attached = listOf(Company(5, "Delta", colorIndex = 4, kind = CompanyKind.ATTACHED, ownerName = "Armen", isShared = true)),
            ),
            TeamActions(),
        )
    }
}
