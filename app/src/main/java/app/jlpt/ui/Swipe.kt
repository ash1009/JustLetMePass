package app.jlpt.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Swipe left for [onNext], right for [onPrev] (null: nothing that way). Only a clear sideways
 * swipe counts, so scrolling up and down and tapping work as before.
 */
@Composable
fun Modifier.swipeNav(onPrev: (() -> Unit)?, onNext: (() -> Unit)?): Modifier {
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    val need = with(LocalDensity.current) { 70.dp.toPx() }
    return this.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total < -need -> next?.invoke()
                    total > need -> prev?.invoke()
                }
            },
        ) { change, amount ->
            total += amount
            change.consume()
        }
    }
}

/**
 * Swipe between levels on a page that shows N5 to N1: left goes one level harder, right one
 * easier. The page takes the swipe, so it does not move to another tab.
 */
@Composable
fun Modifier.levelSwipe(level: Int, set: (Int) -> Unit): Modifier = swipeNav(
    onPrev = if (level < 5) ({ set(level + 1) }) else null,
    onNext = if (level > 1) ({ set(level - 1) }) else null,
)

/** One page of a list ([ids]) that you can swipe through, sliding in from the side you swiped to. */
@Composable
fun SwipePages(ids: List<String>, id: String, go: (String) -> Unit, page: @Composable (String) -> Unit) {
    val i = ids.indexOf(id)
    Box(
        Modifier.fillMaxSize().swipeNav(
            onPrev = if (i > 0) ({ go(ids[i - 1]) }) else null,
            onNext = if (i >= 0 && i < ids.lastIndex) ({ go(ids[i + 1]) }) else null,
        ),
    ) {
        AnimatedContent(
            targetState = id,
            transitionSpec = {
                val forward = ids.indexOf(targetState) >= ids.indexOf(initialState)
                slideInHorizontally { w -> if (forward) w else -w } togetherWith slideOutHorizontally { w -> if (forward) -w else w }
            },
            label = "page",
        ) { cur -> page(cur) }
    }
}
