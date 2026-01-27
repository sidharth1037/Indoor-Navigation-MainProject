package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Search button that adapts its shape based on floor slider visibility.
 * 
 * When floor slider is visible: compact tall rectangle matching slider height (~80dp)
 * When floor slider is hidden: elongated search box filling available width
 * 
 * @param isSliderVisible Whether the floor slider is currently visible
 * @param containerWidth The available width for the search box when expanded
 * @param modifier Modifier for the component
 */
@Composable
fun SearchButton(
    isSliderVisible: Boolean,
    containerWidth: Dp,
    modifier: Modifier = Modifier
) {
    // Animate width: 52dp when slider is visible, containerWidth when slider is hidden
    val buttonWidth by animateDpAsState(
        targetValue = if (isSliderVisible) 52.dp else containerWidth,
        animationSpec = tween(durationMillis = 300),
        label = "search_button_width"
    )
    
    // Animate height: match FloorSlider height when visible (~80dp), standard bar height when hidden (48dp)
    val buttonHeight by animateDpAsState(
        targetValue = if (isSliderVisible) 85.dp else 48.dp,
        animationSpec = tween(durationMillis = 300),
        label = "search_button_height"
    )
    
    // Animate corner radius: matches FloorSlider's 28dp radius when visible
    val cornerRadius by animateDpAsState(
        targetValue = if (isSliderVisible) 28.dp else 24.dp,
        animationSpec = tween(durationMillis = 300),
        label = "search_button_corner"
    )
    
    Box(
        modifier = modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                // TODO: Navigation to search screen
            }
    ) {
        // Search icon - fixed on the right side
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(24.dp)
        )

        // Placeholder text - only visible when the search box is expanded
        AnimatedVisibility(
            visible = !isSliderVisible && buttonWidth > 150.dp,
            enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 100)),
            exit = fadeOut(animationSpec = tween(durationMillis = 100)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Text(
                text = "Search for a room or place...",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 20.dp)
            )
        }
    }
}
