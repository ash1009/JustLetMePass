package app.jlpt.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.R
import app.jlpt.data.Kind
import app.jlpt.data.Reminder
import kotlinx.coroutines.launch

/** A short first run: the exam, daily time and reminder, and whether hiragana is known. */
@Composable
fun Onboarding() {
    val a = app()
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Reminder.schedule(ctx, a.prefs)
    }

    fun finish(knowsHiragana: Boolean) {
        scope.launch {
            a.prefs.hiraganaKnown.value = knowsHiragana
            if (knowsHiragana) a.study.markKnown(a.study.ids(Kind.HIRA))
            a.prefs.onboarded.value = true
            Reminder.schedule(ctx, a.prefs)
        }
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (page) {
            0 -> {
                Gap(40.dp)
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(180.dp))
                Text("Just Let me Pass This", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Gap(8.dp)
                Text(
                    "A little Japanese every day, picked for you. Words, kanji, grammar, reading and listening, all the way from N5 to N1.",
                    textAlign = TextAlign.Center, color = LocalExtra.current.muted,
                )
                Gap(32.dp)
                Button(onClick = { page = 1 }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Set up (30 seconds)", fontSize = 17.sp) }
            }
            1 -> {
                Gap(16.dp)
                Text("Your plan", style = MaterialTheme.typography.headlineMedium)
                Gap(4.dp)
                Text("You can change all of this later in Settings.", color = LocalExtra.current.muted)
                Gap(24.dp)
                PlanSettings()
                Gap(32.dp)
                Button(onClick = { page = 2 }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Next", fontSize = 17.sp) }
            }
            else -> {
                Gap(40.dp)
                Text("あいうえお", fontSize = 48.sp, fontFamily = JpBig)
                Gap(16.dp)
                Text("Can you read hiragana?", style = MaterialTheme.typography.headlineSmall)
                Gap(8.dp)
                Text(
                    "If yes, we'll start with katakana. Hiragana still comes back now and then so you keep it.",
                    textAlign = TextAlign.Center, color = LocalExtra.current.muted,
                )
                Gap(32.dp)
                Button(onClick = { finish(true) }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Yes, I can", fontSize = 17.sp) }
                Gap(8.dp)
                OutlinedButton(onClick = { finish(false) }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Not yet, teach me") }
            }
        }
        Gap(24.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Text(if (i == page) "●" else "○", color = if (i == page) MaterialTheme.colorScheme.primary else LocalExtra.current.line)
            }
        }
    }
}
