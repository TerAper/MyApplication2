package com.teraper.printmaster.core.designsystem.component

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CallRecording
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private val RECORDING_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

/** 83 000 ms → "1:23". */
fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** One call recording in a list: who, when, how long. Tap to play. */
@Composable
fun PmRecordingRow(
    recording: CallRecording,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(PmTheme.colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(PmIcons.Play, contentDescription = null, tint = PmTheme.colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(recording.caller, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                recording.startedAt.format(RECORDING_TIME) + " · " + formatDuration(recording.durationMillis),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/**
 * Plays a recording in a bottom sheet; stops when closed. [missingText] is shown when the
 * file is gone (deleted, or the data was restored on another phone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmRecordingPlayerSheet(
    recording: CallRecording,
    missingText: String,
    playLabel: String,
    pauseLabel: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var failed by remember(recording.uri) { mutableStateOf(false) }
    var playing by remember(recording.uri) { mutableStateOf(false) }
    var position by remember(recording.uri) { mutableIntStateOf(0) }
    var duration by remember(recording.uri) { mutableIntStateOf(recording.durationMillis.toInt()) }
    var seeking by remember(recording.uri) { mutableFloatStateOf(-1f) }

    val player = remember(recording.uri) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(context, Uri.parse(recording.uri))
                prepare()
            }
        }.onFailure { failed = true }.getOrNull()
    }
    DisposableEffect(player) {
        player?.setOnCompletionListener { playing = false; position = it.duration }
        player?.let { duration = it.duration; it.start(); playing = true }
        onDispose { player?.release() }
    }
    LaunchedEffect(player, playing) {
        while (player != null && playing) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(200)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(recording.caller, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(recording.startedAt.format(RECORDING_TIME), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkMuted)
            if (failed || player == null) {
                Text(missingText, style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.error)
                return@Column
            }
            Slider(
                value = if (seeking >= 0f) seeking else position.toFloat(),
                onValueChange = { seeking = it },
                onValueChangeFinished = {
                    player.seekTo(seeking.toInt())
                    position = seeking.toInt()
                    seeking = -1f
                },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(thumbColor = PmTheme.colors.primary, activeTrackColor = PmTheme.colors.primary),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(position.toLong()), style = MaterialTheme.typography.labelMedium, color = PmTheme.colors.inkMuted)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    FilledIconButton(
                        onClick = {
                            if (playing) {
                                player.pause()
                            } else {
                                if (position >= duration) player.seekTo(0)
                                player.start()
                            }
                            playing = !playing
                        },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = PmTheme.colors.primary),
                    ) {
                        Icon(
                            if (playing) PmIcons.Pause else PmIcons.Play,
                            contentDescription = if (playing) pauseLabel else playLabel,
                            tint = PmTheme.colors.onPrimary,
                        )
                    }
                }
                Text(formatDuration(duration.toLong()), style = MaterialTheme.typography.labelMedium, color = PmTheme.colors.inkMuted)
            }
        }
    }
}
