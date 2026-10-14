package app.jlpt.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jlpt.JlptApp

@Composable
fun app(): JlptApp = androidx.compose.ui.platform.LocalContext.current.applicationContext as JlptApp

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = LocalExtra.current.card,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .border(1.dp, LocalExtra.current.line, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LocalExtra.current.muted) {
    Text(text.uppercase(), style = SmallCaps, color = color, modifier = modifier)
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun TitleBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SpeakButton(text: String, modifier: Modifier = Modifier, slow: Boolean = false, size: Dp = 44.dp) {
    val a = app()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { a.speech.say(text, slow) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Play sound", tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** A thin bar showing how far along something is. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 8.dp) {
    val track = LocalExtra.current.line
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

/** Two bars in one: started (light) and known (strong). */
@Composable
fun DoubleMeter(started: Float, known: Float, modifier: Modifier = Modifier) {
    val track = LocalExtra.current.line
    val c = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(started.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth(known.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(8.dp)).background(c))
    }
}

@Composable
fun Ring(fraction: Float, modifier: Modifier = Modifier, stroke: Dp = 10.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val track = LocalExtra.current.line
    Canvas(modifier) {
        val w = stroke.toPx()
        val d = size.minDimension - w
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.onBackground else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) cs.onBackground else LocalExtra.current.line), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) cs.background else cs.onBackground, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp = 12.dp) = Spacer(Modifier.width(w))

@Composable
fun StatRow(vararg items: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for ((value, label) in items) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(label, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
        }
    }
}

/** N5 to N1 label. */
fun lvl(level: Int) = "N$level"

val Pad = Modifier.padding(horizontal = 16.dp)
