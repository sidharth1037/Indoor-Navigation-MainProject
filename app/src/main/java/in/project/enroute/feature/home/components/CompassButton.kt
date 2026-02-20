package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import `in`.project.enroute.R

/**
 * Circular compass button that shows north relative to the campus.
 *
 * The compass needle is locked to the campus orientation:
 * - [campusNorthDegrees] sets the initial offset (where true north is when the canvas
 *   is at 0° rotation). This value comes from campus_metadata.json.
 * - [canvasRotationDegrees] tracks the user's canvas rotation gesture.
 *
 * Effective needle rotation = campusNorthDegrees + canvasRotationDegrees
 *
 * @param campusNorthDegrees Clockwise degrees from screen-top to true north at canvas rotation 0°
 * @param canvasRotationDegrees Current rotation of the floor plan canvas in degrees
 * @param isSliderVisible Whether the floor slider is visible (affects vertical position)
 * @param isSearching Whether search is morphing into full-screen search (hides compass with scale animation)
 * @param onClick Callback when the button is clicked (can be used to reset canvas rotation)
 * @param modifier Modifier for customization
 */
@Composable
fun CompassButton(
    campusNorthDegrees: Float,
    canvasRotationDegrees: Float,
    isSliderVisible: Boolean,
    isSearching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val buttonSize = 52.dp // 28dp radius = 56dp diameter
    val iconSize = 32.dp

    // Animate top padding based on slider visibility (not affected by search)
    val topPadding by animateDpAsState(
        targetValue = when {
            isSliderVisible -> 92.dp  // Below slider
            else -> 56.dp  // Below search button when no slider
        },
        animationSpec = tween(durationMillis = 300),
        label = "CompassButtonTopPadding"
    )

    // Compass rotation = campus north offset + current canvas rotation.
    // Both values are in degrees (clockwise positive).
    val rotationDegrees = campusNorthDegrees + canvasRotationDegrees

    // Show/hide with scale animation based on search state
    AnimatedVisibility(
        visible = !isSearching,
        enter = scaleIn(animationSpec = tween(300)),
        exit = scaleOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(top = topPadding)
                .size(buttonSize)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .background(color = backgroundColor, shape = CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.compass),
                contentDescription = "Compass - shows north",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(iconSize)
                    .rotate(rotationDegrees)
            )
        }
    }
}
