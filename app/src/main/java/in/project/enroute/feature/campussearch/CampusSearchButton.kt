package `in`.project.enroute.feature.campussearch

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Duration (ms) for the morph / reverse-morph animation. */
const val MORPH_DURATION_MS = 300

/**
 * A pill-shaped button that morphs (expands + translates up) to match
 * the search bar in [CampusSearchOverlay].
 *
 * Forward (isMorphing = true): expands horizontally, translates to top.
 * Reverse (isMorphing = false): animates back to resting position.
 *
 * Keep this composable in the tree even when the overlay is visible so
 * the reverse animation plays when the user goes back.
 *
 * @param isMorphing  whether the button is in its morphed (search-bar) state
 * @param restingY    resting vertical offset from the top of the parent
 * @param placeholderText  text shown inside the pill
 * @param onAnimationFinished  called when the forward morph completes
 * @param onClick  called when the user taps the button at rest
 */
@Composable
fun CampusSearchButton(
    isMorphing: Boolean,
    restingY: Dp,
    placeholderText: String = "Search for your campus...",
    onAnimationFinished: () -> Unit,
    onClick: () -> Unit
) {
    // Target Y: match Row(padding top = 8.dp) in CampusSearchOverlay
    val targetY = 8.dp

    val yOffset by animateDpAsState(
        targetValue = if (isMorphing) targetY else restingY,
        animationSpec = tween(MORPH_DURATION_MS),
        label = "morph_y"
    )

    // Resting: centred narrow pill (60dp each side)
    // Morphed: aligns with overlay search box
    //   start = 4dp (Row start) + 48dp (back btn) = 52dp
    //   end   = 16dp (Row end padding)
    val startPadding by animateDpAsState(
        targetValue = if (isMorphing) 52.dp else 60.dp,
        animationSpec = tween(MORPH_DURATION_MS),
        label = "morph_start"
    )
    val endPadding by animateDpAsState(
        targetValue = if (isMorphing) 16.dp else 60.dp,
        animationSpec = tween(MORPH_DURATION_MS),
        label = "morph_end"
    )

    LaunchedEffect(isMorphing) {
        if (isMorphing) {
            delay((MORPH_DURATION_MS - 20).toLong())
            onAnimationFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = endPadding)
            .offset(y = yOffset)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = !isMorphing) { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = placeholderText,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp)
            )
        }
    }
}
