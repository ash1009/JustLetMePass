package app.jlpt.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.Clip
import app.jlpt.data.Playlist
import app.jlpt.data.PlaylistService

/** What the playlist is now playing, kept while the app is open (the audio itself plays on). */
private object NowPlaying { var clips: List<Clip> = emptyList(); var title: String = "" }

/**
 * Japanese audio to have on in the background: your words, sentences, stories, dialogues or
 * phrases, all in Japanese. Keeps playing with the screen off.
 */
@Composable
fun ListenScreen(onBack: () -> Unit) {
    val a = app()
    val ctx = LocalContext.current
    val now by a.speech.now.collectAsState()
    val kind by a.prefs.listenKind.state.collectAsState()
    val gap by a.prefs.listenGap.state.collectAsState()
    val examLevel by a.prefs.examLevel.state.collectAsState()
    var level by rememberSaveable { mutableStateOf(examLevel) }
    var showEn by rememberSaveable { mutableStateOf(false) }
    val mine = now?.key == PlaylistService.KEY
    val playing = mine && now?.playing == true
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun startPlaylist() {
        val clips = Playlist.build(kind, a.content, a.study, level)
        if (clips.isEmpty()) return
        NowPlaying.clips = clips
        NowPlaying.title = Playlist.kinds.firstOrNull { it.first == kind }?.second ?: ""
        a.speech.play(PlaylistService.KEY, clips.map { it.speaker to it.jp }, 0, gap.toLong())
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        PlaylistService.start(ctx)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Listen", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Japanese audio from what you're learning. It keeps playing with the screen off, so put your phone away and just listen.",
                style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
            )
            Gap(16.dp)
            Label("What to play")
            Gap(8.dp)
            ChoiceRow(Playlist.kinds, kind) { a.prefs.listenKind.value = it }
            if (kind == "stories" || kind == "dialogues") {
                Gap(8.dp)
                ChoiceRow((5 downTo 1).map { it to lvl(it) }, level) { level = it }
            }
            Gap(16.dp)
            Label("Pause between")
            Gap(8.dp)
            ChoiceRow(listOf(800 to "Short", 1500 to "Normal", 3000 to "Long (to repeat aloud)"), gap) { a.prefs.listenGap.value = it }

            Gap(24.dp)
            Panel {
                val n = now
                val clip = if (mine && n != null) NowPlaying.clips.getOrNull(n.index) else null
                if (clip != null) {
                    Label("${NowPlaying.title} · ${n!!.index + 1} of ${n.total}")
                    Gap(10.dp)
                    Ruby(clip.jp, size = 24.sp, modifier = Modifier.fillMaxWidth())
                    Gap(6.dp)
                    if (showEn) Text(clip.en, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                    TextButton(onClick = { showEn = !showEn }) { Text(if (showEn) "Hide English" else "Show English") }
                } else {
                    Text("Nothing playing", style = MaterialTheme.typography.titleMedium)
                    Text("Pick what to play, then press play.", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                }
                Gap(16.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Rounded.SkipPrevious, "Back one", enabled = mine) { a.speech.skip(-1) }
                    RoundButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", big = true) {
                        when {
                            playing -> a.speech.pause()
                            mine && now?.done == false -> { a.speech.resume(PlaylistService.KEY); PlaylistService.start(ctx) }
                            else -> startPlaylist()
                        }
                    }
                    RoundButton(Icons.Rounded.SkipNext, "Next one", enabled = mine) { a.speech.skip(1) }
                    RoundButton(Icons.Rounded.Stop, "Stop", enabled = mine) { a.speech.stop() }
                }
                if (mine) {
                    Gap(8.dp)
                    TextButton(onClick = { startPlaylist() }, Modifier.align(Alignment.CenterHorizontally)) { Text("New mix") }
                }
            }
            Gap(12.dp)
            Text(
                "While it plays, a play/pause control shows in your notifications. It goes away when you stop.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, textAlign = TextAlign.Start,
            )
            Gap(32.dp)
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, big: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val size = if (big) 72.dp else 48.dp
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (big) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (big) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(if (big) 40.dp else 24.dp))
    }
}
