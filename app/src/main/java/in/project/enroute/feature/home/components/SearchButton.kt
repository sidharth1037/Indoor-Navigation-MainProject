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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Search button that adapts its shape based on floor slider visibility.
 * 
 * When floor slider is visible: compact tall rectangle matching slider height (~80dp)
 * When floor slider is hidden: elongated search box filling available width
 * When searching: morphs to match the input field in SearchScreen
 * 
 * @param isSliderVisible Whether the floor slider is currently visible
 * @param containerWidth The available width for the search box when expanded
 * @param isSearching Whether the button is in its morphing state
 * @param onAnimationFinished Callback when the morphing animation completes
 * @param modifier Modifier for the component
 * @param onClick Callback when the button is clicked
 */
@Composable
fun SearchButton(
    isSliderVisible: Boolean,
    containerWidth: Dp,
    modifier: Modifier = Modifier,
    isSearching: Boolean = false,
    sliderHeightDp: Dp = 77.dp,
    onAnimationFinished: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    // Determine target values based on the state
    // When searching, we subtract 48dp (40dp back button + 8dp spacer) to match SearchScreen
    val targetWidth = when {
        isSearching -> containerWidth - 48.dp
        isSliderVisible -> 52.dp
        else -> containerWidth
    }
    
    val targetHeight = when {
        isSearching -> 48.dp
        isSliderVisible -> sliderHeightDp
        else -> 48.dp
    }
    
    val targetCornerRadius = when {
        isSearching -> 24.dp
        isSliderVisible -> 28.dp
        else -> 24.dp
    }

    // Animate dimensions and shape
    val buttonWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 300),
        label = "search_button_width"
    )
    
    val buttonHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(durationMillis = 300),
        label = "search_button_height"
    )
    
    val cornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = tween(durationMillis = 300),
        label = "search_button_corner"
    )
    
    // Notify parent when the morphing animation is complete
    LaunchedEffect(isSearching) {
        if (isSearching) {
            delay(250)
            onAnimationFinished()
        }
    }
    
    Box(
        modifier = modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = !isSearching) { onClick() }
    ) {
        // Search icon - padding adjusts to 12dp to match SearchScreen when searching
        val iconPaddingEnd by animateDpAsState(
            targetValue = if (isSearching) 12.dp else 14.dp,
            label = "icon_padding"
        )
        
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = iconPaddingEnd)
                .size(24.dp)
        )

        // Placeholder text - visible when expanded or searching
        // Padding adjusts to 12dp to match SearchScreen when searching
        val textPaddingStart by animateDpAsState(
            targetValue = if (isSearching) 12.dp else 20.dp,
            label = "text_padding"
        )
        
        AnimatedVisibility(
            visible = (isSearching || !isSliderVisible) && buttonWidth > 120.dp,
            enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 100)),
            exit = fadeOut(animationSpec = tween(durationMillis = 100)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Text(
                text = "Search for a room or place...",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                fontSize = 15.sp,
                modifier = Modifier.padding(start = textPaddingStart)
            )
        }
    }
}
