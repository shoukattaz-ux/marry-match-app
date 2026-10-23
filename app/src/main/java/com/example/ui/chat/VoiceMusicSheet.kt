package com.example.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.media.AudioCodec
import com.example.core.media.VoiceMusic
import com.example.core.media.AudioUnreadableException
import com.example.core.media.VoiceMusicMixer
import com.example.core.media.VoiceRecorder
import com.example.data.media.ChatMusic
import com.example.data.media.VoiceMusicStore
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Longest part of a song worth choosing: a voice message is at most 3 minutes. */
private const val MAX_PART_MS = 180_000L
private const val TEST_RECORDING_MS = 5_000L
private const val WAVEFORM_BARS = 120

/** A song being set up in the sheet: its app copy, name, length and waveform. */
private class LoadedSong(val file: File, val name: String, val durationMs: Long, val waveform: FloatArray)

/**
 * "Background music" for voice messages: choose a song, drag the part, set the volume (quick
 * choices and a free slider), choose all chats or only this chat, try it with 5 seconds of your
 * own voice, and tap Done. Everything stays on this phone until a voice message is sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMusicSheet(store: VoiceMusicStore, matchId: String, otherName: String, lang: AppLanguage, onDismiss: () -> Unit) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatSetting = remember { store.chat(matchId) }
    val allSetting = remember { store.allChats() }
    val initial = (chatSetting as? ChatMusic.Own)?.music ?: allSetting
    var forAllChats by remember { mutableStateOf(chatSetting !is ChatMusic.Own) }

    var song by remember { mutableStateOf<LoadedSong?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(0f..0f) }
    var volume by remember { mutableFloatStateOf(initial?.volume ?: VoiceMusic.DEFAULT_VOLUME) }

    fun load(file: File, name: String, startMs: Long?, endMs: Long?) {
        loading = true
        error = null
        scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                runCatching {
                    val durationMs = AudioCodec.durationMs(file) ?: throw AudioUnreadableException()
                    LoadedSong(file, name, durationMs, AudioCodec.waveform(file, WAVEFORM_BARS, durationMs))
                }.getOrNull()
            }
            loading = false
            if (loaded == null || loaded.durationMs < VoiceMusic.MIN_LENGTH_MS) {
                error = t("voice_music_unreadable")
                return@launch
            }
            song = loaded
            val start = (startMs ?: 0L).coerceIn(0L, loaded.durationMs - VoiceMusic.MIN_LENGTH_MS)
            val end = (endMs ?: (start + 60_000L)).coerceIn(start + VoiceMusic.MIN_LENGTH_MS, minOf(loaded.durationMs, start + MAX_PART_MS))
            range = start.toFloat()..end.toFloat()
        }
    }

    // The song already chosen opens with its part.
    LaunchedEffect(Unit) {
        initial?.let { m ->
            val file = File(m.songFile)
            if (file.isFile) load(file, m.songName, m.startMs, m.endMs) else error = t("voice_music_missing")
        }
    }

    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        scope.launch {
            val imported = withContext(Dispatchers.IO) { store.importSong(uri) }
            if (imported == null) {
                loading = false
                error = t("voice_music_unreadable")
            } else {
                load(imported.first, imported.second, null, null)
            }
        }
    }

    // Playing: the chosen part, or the 5-second test mix.
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    fun stopPlaying() {
        player.value?.runCatching { stop(); release() }
        player.value = null
        playing = null
    }
    DisposableEffect(Unit) { onDispose { stopPlaying() } }

    fun playPart() {
        val s = song ?: return
        stopPlaying()
        val mp = runCatching { MediaPlayer().apply { setDataSource(s.file.absolutePath); prepare(); seekTo(range.start.toInt()); start() } }.getOrNull() ?: return
        player.value = mp
        playing = "part"
        scope.launch {
            while (playing == "part" && mp.runCatching { isPlaying && currentPosition < range.endInclusive }.getOrDefault(false)) delay(100)
            if (playing == "part") stopPlaying()
        }
    }

    // Test with my voice: 5 s recording, mixed exactly like a real message, then played.
    var testStep by remember { mutableStateOf<String?>(null) }
    val testRecorder = remember { VoiceRecorder(context) }
    val testDir = remember { File(context.cacheDir, "voice_music_test").apply { mkdirs() } }
    lateinit var askMic: androidx.activity.result.ActivityResultLauncher<String>
    fun runTest() {
        val s = song ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        stopPlaying()
        error = null
        scope.launch {
            val voice = File(testDir, "voice.m4a")
            val mixed = File(testDir, "mixed.m4a")
            testStep = "recording"
            runCatching { testRecorder.start(voice) {} }.onFailure { testStep = null; error = t("media_record_failed"); return@launch }
            delay(TEST_RECORDING_MS)
            val recorded = testRecorder.stop()
            if (recorded == null) { testStep = null; error = t("media_voice_too_short"); return@launch }
            testStep = "mixing"
            val music = VoiceMusic(s.file.absolutePath, s.name, range.start.toLong(), range.endInclusive.toLong(), volume)
            val ok = withContext(Dispatchers.Default) { runCatching { VoiceMusicMixer.mix(voice, music, mixed) }.isSuccess }
            testStep = null
            if (!ok) { error = t("voice_music_failed"); return@launch }
            val mp = runCatching { MediaPlayer().apply { setDataSource(mixed.absolutePath); prepare(); start() } }.getOrNull() ?: return@launch
            player.value = mp
            playing = "test"
            mp.setOnCompletionListener { if (playing == "test") stopPlaying() }
        }
    }
    askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runTest() else error = t("voice_music_test_mic")
    }
    DisposableEffect(Unit) { onDispose { testRecorder.cancel() } }

    fun done() {
        val s = song ?: return
        val music = VoiceMusic(s.file.absolutePath, s.name, range.start.toLong(), range.endInclusive.toLong(), volume)
        if (forAllChats) {
            store.setAllChats(music)
            // Choosing music for all chats here also ends this chat's own setting or "no music".
            if (chatSetting != ChatMusic.FollowAll) store.setChat(matchId, ChatMusic.FollowAll)
        } else {
            store.setChat(matchId, ChatMusic.Own(music))
        }
        store.cleanUp()
        onDismiss()
    }

    fun dismiss() {
        stopPlaying()
        // Songs picked here but not saved are not kept.
        store.cleanUp()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(t("voice_music_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(t("voice_music_intro"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)

            // 1. Song
            OutlinedButton(onClick = { pickSong.launch(arrayOf("audio/*")) }, enabled = !loading && testStep == null, modifier = Modifier.fillMaxWidth().testTag("voice_music_pick")) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(song?.name ?: t("voice_music_choose"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            song?.let { s ->
                // 2. Part of the song
                Text(t("voice_music_part"), style = MaterialTheme.typography.labelLarge)
                Waveform(s.waveform, s.durationMs, range)
                RangeSlider(
                    value = range,
                    onValueChange = { r ->
                        var start = r.start
                        var end = r.endInclusive
                        if (end - start < VoiceMusic.MIN_LENGTH_MS) {
                            if (start != range.start) start = end - VoiceMusic.MIN_LENGTH_MS else end = start + VoiceMusic.MIN_LENGTH_MS
                        }
                        if (end - start > MAX_PART_MS) {
                            if (start != range.start) end = start + MAX_PART_MS else start = end - MAX_PART_MS
                        }
                        range = start.coerceIn(0f, s.durationMs.toFloat())..end.coerceIn(0f, s.durationMs.toFloat())
                    },
                    valueRange = 0f..s.durationMs.toFloat(),
                    modifier = Modifier.testTag("voice_music_range")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${clock(range.start.toLong())} – ${clock(range.endInclusive.toLong())} (${((range.endInclusive - range.start) / 1000).roundToInt()} s)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { if (playing == "part") stopPlaying() else playPart() }, enabled = testStep == null) {
                        Icon(if (playing == "part") Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t(if (playing == "part") "voice_music_stop" else "voice_music_play_part"))
                    }
                }

                // 3. Volume: quick choices and a free slider
                Text(String.format(t("voice_music_volume"), (volume * 100).roundToInt()), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoiceMusic.PRESETS.forEach { p ->
                        FilterChip(
                            selected = (volume * 100).roundToInt() == (p * 100).roundToInt(),
                            onClick = { volume = p },
                            label = { Text("${(p * 100).roundToInt()}%") },
                            modifier = Modifier.testTag("voice_music_volume_${(p * 100).roundToInt()}")
                        )
                    }
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = (it * 100).roundToInt() / 100f },
                    valueRange = VoiceMusic.MIN_VOLUME..VoiceMusic.MAX_VOLUME,
                    modifier = Modifier.testTag("voice_music_volume_slider")
                )
                Text(t("voice_music_voice_full"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)

                // 4. Where it applies
                Text(t("voice_music_where"), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = forAllChats, onClick = { forAllChats = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(t("voice_music_all_chats"))
                    }
                    SegmentedButton(selected = !forAllChats, onClick = { forAllChats = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(String.format(t("voice_music_only_chat"), otherName), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // 5. Try it
                OutlinedButton(
                    onClick = { if (playing == "test") stopPlaying() else runTest() },
                    enabled = testStep == null,
                    modifier = Modifier.fillMaxWidth().testTag("voice_music_test")
                ) {
                    Icon(if (playing == "test") Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            testStep == "recording" -> t("voice_music_test_recording")
                            testStep == "mixing" -> t("voice_music_mixing")
                            playing == "test" -> t("voice_music_stop")
                            else -> t("voice_music_test")
                        }
                    )
                }
                if (testStep != null) LinearProgressIndicator(Modifier.fillMaxWidth())

                Text(t("voice_music_rights"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                Button(onClick = ::done, enabled = testStep == null && !loading, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("voice_music_done")) {
                    Text(t("voice_music_done"), fontWeight = FontWeight.Bold)
                }
            }

            // Turning music off
            if (chatSetting is ChatMusic.Own) {
                TextButton(onClick = { store.setChat(matchId, ChatMusic.FollowAll); store.cleanUp(); dismiss() }) {
                    Text(t("voice_music_remove_chat"), color = MaterialTheme.colorScheme.error)
                }
            }
            if (allSetting != null) {
                if (chatSetting !is ChatMusic.Off) {
                    TextButton(onClick = { store.setChat(matchId, ChatMusic.Off); dismiss() }) {
                        Text(String.format(t("voice_music_off_chat"), otherName), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { store.setAllChats(null); store.cleanUp(); dismiss() }) {
                    Text(t("voice_music_off_all"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** The song's loudness as bars; the chosen part is coloured. */
@Composable
private fun Waveform(levels: FloatArray, durationMs: Long, range: ClosedFloatingPointRange<Float>) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).testTag("voice_music_waveform")) {
        if (levels.isEmpty() || durationMs <= 0) return@Canvas
        val barWidth = size.width / levels.size
        levels.forEachIndexed { i, level ->
            val atMs = (i + 0.5f) / levels.size * durationMs
            val h = (size.height * 0.85f * level).coerceAtLeast(2f)
            drawRoundRect(
                color = if (atMs in range) active else idle,
                topLeft = Offset(i * barWidth + barWidth * 0.2f, (size.height - h) / 2),
                size = Size(barWidth * 0.6f, h),
                cornerRadius = CornerRadius(barWidth * 0.3f)
            )
        }
    }
}

private fun clock(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)

/**
 * The chip above the input when this chat's voice messages get music: song, volume and where the
 * setting comes from. Tapping it opens the sheet.
 */
@Composable
fun VoiceMusicChip(music: VoiceMusic, ownForChat: Boolean, lang: AppLanguage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t: (String) -> String = { Localization.t(it, lang) }
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                "🎵 ${music.songName} · ${(music.volume * 100).roundToInt()}% · " + t(if (ownForChat) "voice_music_this_chat" else "voice_music_all_chats"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier.testTag("voice_music_chip")
    )
}
