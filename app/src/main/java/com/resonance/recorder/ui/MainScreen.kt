package com.resonance.recorder.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.resonance.recorder.data.db.RecordingEntity
import com.resonance.recorder.ui.components.FloatingRecorderPill
import com.resonance.recorder.ui.components.StaticWaveformVisualizer
import com.resonance.recorder.ui.components.formatCents
import com.resonance.recorder.ui.components.formatDuration
import com.resonance.recorder.ui.theme.GlassContrast
import com.resonance.recorder.ui.theme.LiquidGlassPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val recordings by viewModel.recordings.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val selectedId by viewModel.selectedRecordingId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.playbackPosition.collectAsState()
    val duration by viewModel.playbackDuration.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val selected = recordings.firstOrNull { it.id == selectedId }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording() else permissionDenied = true
    }
    val beginRecording = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D0D0E),
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                showSearch = showSearch,
                query = query,
                onSearchToggle = { showSearch = !showSearch },
                onQueryChange = { query = it }
            )
            val visible = remember(recordings, query) {
                if (query.isBlank()) recordings
                else recordings.filter { it.title.contains(query, ignoreCase = true) }
            }
            if (visible.isEmpty()) {
                EmptyState(hasQuery = query.isNotBlank(), modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 10.dp,
                        bottom = 190.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }) { recording ->
                        RecordingRow(
                            recording = recording,
                            selected = recording.id == selectedId,
                            onSelect = { viewModel.selectRecording(recording) },
                            onPlay = { viewModel.togglePlayback(recording) },
                            onDelete = { viewModel.deleteRecording(recording) }
                        )
                    }
                }
            }
        }

        FloatingRecorderPill(
            isRecording = isRecording,
            elapsedMs = elapsedMs,
            liveRmsFlow = viewModel.liveRmsFlow,
            selectedRecording = selected,
            isPlaying = isPlaying,
            playbackPositionMs = position,
            playbackDurationMs = duration,
            onRecord = beginRecording,
            onStop = viewModel::finishRecording,
            onCancel = viewModel::cancelRecording,
            onPlayPause = { selected?.let(viewModel::togglePlayback) },
            onSeek = viewModel::seekPlayback,
            onDismissPlayback = viewModel::dismissPlayback,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        }
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("Microphone access needed") },
            text = { Text("Resonance needs microphone access to make recordings. Audio never leaves this device.") },
            confirmButton = {
                TextButton(onClick = { permissionDenied = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun Header(
    showSearch: Boolean,
    query: String,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RESONANCE", style = MaterialTheme.typography.labelMedium, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
                Text("Field notes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            PrivacyBadge()
            IconButton(onClick = onSearchToggle, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Search, contentDescription = if (showSearch) "Close search" else "Search recordings")
            }
        }
        AnimatedVisibility(showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
                placeholder = { Text("Search field notes") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun PrivacyBadge() {
    LiquidGlassPill(
        contrast = GlassContrast.CLEAR,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(5.dp))
            Text("100% Air-Gapped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(if (hasQuery) "No matching field notes" else "A quiet beginning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (hasQuery) "Try another search." else "Capture a melody, a rhythm, or the sound of a place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
        )
    }
}

@Composable
private fun RecordingRow(
    recording: RecordingEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play ${recording.title}", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(recording.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatDate(recording.createdAt)} · ${formatDuration(recording.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${recording.title}")
            }
        }
        StaticWaveformVisualizer(
            points = recording.waveformPoints,
            progress = 0f,
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(vertical = 9.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recording.detectedKey?.let { MetricTag("$it ${formatCents(recording.centsDeviation)}") }
            recording.detectedBPM?.let { MetricTag("$it BPM") }
            if (recording.detectedKey == null && recording.detectedBPM == null) MetricTag("Ambient")
        }
    }
}

@Composable
private fun MetricTag(text: String) {
    Text(
        text,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium
    )
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
