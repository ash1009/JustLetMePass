package app.jlpt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draws a kanji or kana one stroke at a time, in the right order. Tap to play again. */
@Composable
fun StrokeOrder(char: String, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val a = app()
    val paths = remember(char) {
        a.content.strokes(char).mapNotNull { d -> runCatching { PathParser().parsePathString(d).toPath() }.getOrNull() }
    }
    if (paths.isEmpty()) return
    var run by remember(char) { mutableIntStateOf(0) }
    val progress = remember(char) { Animatable(0f) }
    LaunchedEffect(char, run) {
        progress.snapTo(0f)
        progress.animateTo(paths.size.toFloat(), tween(durationMillis = 650 * paths.size, easing = LinearEasing))
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val guide = LocalExtra.current.line
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(LocalExtra.current.card)
            .border(1.dp, guide, shape)
            .clickable { run++ },
    ) {
        Canvas(Modifier.size(size)) {
            val scale = this.size.minDimension / 109f
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            // Cross guide lines, like practice paper.
            drawLine(guide, Offset(this.size.width / 2, 0f), Offset(this.size.width / 2, this.size.height), 2f, pathEffect = dash)
            drawLine(guide, Offset(0f, this.size.height / 2), Offset(this.size.width, this.size.height / 2), 2f, pathEffect = dash)
            val p = progress.value
            val width = 4.2f
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                val style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                paths.forEachIndexed { i, path ->
                    when {
                        p >= i + 1 -> drawPath(path, ink, style = style)
                        p > i -> {
                            drawPath(path, guide, style = style)
                            val m = PathMeasure()
                            m.setPath(path, false)
                            val part = Path()
                            m.getSegment(0f, m.length * (p - i), part, true)
                            drawPath(part, accent, style = style)
                        }
                        else -> drawPath(path, guide, style = style)
                    }
                }
            }
        }
    }
}
