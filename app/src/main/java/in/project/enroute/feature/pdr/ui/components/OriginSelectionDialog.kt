package `in`.project.enroute.feature.pdr.ui.components

import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.net.toUri
import `in`.project.enroute.feature.settings.data.SettingsRepository
import android.util.Log
import androidx.compose.material.icons.rounded.Directions
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * Dialog for selecting the starting location for PDR tracking.
 * Shows a general instruction, a collapsible section with two video tutorials
 * (Tap to Set and Search to Set), and two action buttons.
 *
 * The show/hide instructions state is persisted via DataStore so the user's
 * preference is remembered across sessions.
 */
@OptIn(UnstableApi::class)
@Composable
fun OriginSelectionDialog(
    onDismiss: () -> Unit,
    onSelectPoint: () -> Unit,
    onSelectLocation: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }

    // Extract scrollState so we can manipulate it before animations run
    val scrollState = rememberScrollState()

    val showInstructionsPref by settingsRepository.showOriginInstructions.collectAsState(initial = null)

    // Re-created once when DataStore first loads (key changes null → non-null).
    val instructionsVisibility = remember(showInstructionsPref != null) {
        MutableTransitionState(showInstructionsPref ?: false)
    }

    val videoCornerDp = 12f
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    // ── Single shared player ──────────────────────────────────────────────
    val player = remember {
        // 1. Memory Management: Limit buffer size to prevent OOM on low-end devices.
        // 5MB is generally more than enough for short, local UI tutorial videos.
        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(1024 * 1024 * 5)
            .build()

        // 2. Decoder Fallback: If the hardware decoder chokes on a video format,
        // this tells ExoPlayer to try the software decoder instead of immediately crashing.
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
            }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Track which video is closest to the viewport center
    var viewportCenterY by remember { mutableFloatStateOf(0f) }
    var tapCenterY by remember { mutableFloatStateOf(0f) }
    var searchCenterY by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    val activeVideo by remember {
        derivedStateOf {
            val tapDist = abs(tapCenterY - viewportCenterY)
            val searchDist = abs(searchCenterY - viewportCenterY)
            if (tapDist <= searchDist) "TapToSet.mp4" else "SearchToSet.mp4"
        }
    }

    // Stop player when instructions collapse
    LaunchedEffect(instructionsVisibility.targetState) {
        if (!instructionsVisibility.targetState) {
            player.stop()
            player.clearVideoSurface()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .onGloballyPositioned { coords ->
                    viewportCenterY = coords.boundsInWindow().center.y
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState), // Attached here
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Title ─────────────────────────────────────────────
                Text(
                    text = "Set Your Location",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── General instruction ────────────────────────────────
                Text(
                    text = "Choose how you want to set your starting location on the map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Show/Hide instructions button ──────────────────────
                TextButton(
                    onClick = {
                        val newValue = !instructionsVisibility.targetState
                        if (!newValue) {
                            // Fix #1: Snap scroll to top immediately to prevent bounds-clamping layout jumps
                            // as the container violently shrinks.
                            scope.launch { scrollState.scrollTo(0) }
                        }
                        instructionsVisibility.targetState = newValue
                        scope.launch { settingsRepository.saveShowOriginInstructions(newValue) }
                    }
                ) {
                    Text(
                        text = if (instructionsVisibility.targetState) "Hide Instructions" else "Show Instructions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (instructionsVisibility.targetState) Icons.Rounded.KeyboardArrowUp
                        else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // ── Collapsible instructions ───────────────────────────
                AnimatedVisibility(
                    visibleState = instructionsVisibility,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // ── Tap to Set ──────────────────────────────────
                        InstructionSection(
                            title = "Tap to Set",
                            description = "Tap directly on the map where you are standing to set your location.",
                            assetFileName = "TapToSet.mp4",
                            player = player,
                            isActive = activeVideo == "TapToSet.mp4",
                            isVisible = instructionsVisibility.targetState,
                            onPositioned = { tapCenterY = it },
                            videoHeight = 450.dp,
                            cornerRadius = videoCornerDp,
                            containerColor = containerColor
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Search to Set ───────────────────────────────
                        InstructionSection(
                            title = "Search to Set",
                            description = "Search for a room or place and set it as your location.",
                            assetFileName = "SearchToSet.mp4",
                            player = player,
                            isActive = activeVideo == "SearchToSet.mp4",
                            isVisible = instructionsVisibility.targetState,
                            onPositioned = { searchCenterY = it },
                            videoHeight = 700.dp,
                            cornerRadius = videoCornerDp,
                            containerColor = containerColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Action buttons ─────────────────────────────────────
                Button(
                    onClick = onSelectPoint,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("Tap on Map", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSelectLocation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("Search Location", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Cancel ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

/**
 * A single instruction block: title, description text, and a looping video.
 * Uses a shared [player] — only the section where [isActive] is true will
 * claim the player and render video. This avoids multiple decoder instances.
 */
@OptIn(UnstableApi::class)
@Composable
private fun InstructionSection(
    title: String,
    description: String,
    assetFileName: String,
    player: ExoPlayer,
    isActive: Boolean,
    isVisible: Boolean,
    onPositioned: (Float) -> Unit,
    videoHeight: Dp,
    cornerRadius: Float,
    containerColor: androidx.compose.ui.graphics.Color
) {
    // Keep a reference to the TextureView to pass to ExoPlayer
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    // When this section becomes active (and textureView is ready), claim the player
    LaunchedEffect(isActive, isVisible, textureViewRef) {
        val tv = textureViewRef
        if (isActive && isVisible && tv != null) {
            player.stop()
            player.setVideoTextureView(tv) // ExoPlayer handles Surface lifecycle internally
            player.setMediaItem(MediaItem.fromUri("asset:///$assetFileName".toUri()))
            player.prepare()
            player.playWhenReady = true
        } else if (!isVisible && tv != null) {
            player.clearVideoTextureView(tv) // Detach instantly when hiding
        }
    }

    // Clean up when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            textureViewRef?.let { tv ->
                player.clearVideoTextureView(tv)
            }
            textureViewRef = null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Video ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(videoHeight)
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(containerColor)
                .onGloballyPositioned { coords ->
                    onPositioned(coords.boundsInWindow().center.y)
                },
            contentAlignment = Alignment.Center
        ) {
            // Fix #2: Leave the AndroidView inside the composition permanently while this block is visible.
            // AnimatedVisibility naturally removes the block when the collapse finishes.
            // Dynamically removing it DURING the animation forces native UI layout invalidations, dropping frames.
            AndroidView(
                factory = { ctx ->
                    val cornerPx = cornerRadius * ctx.resources.displayMetrics.density

                    val textureView = TextureView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    // Assign to our Compose state variable
                    textureViewRef = textureView

                    FrameLayout(ctx).apply {
                        clipToOutline = true
                        outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(
                                view: android.view.View,
                                outline: android.graphics.Outline
                            ) {
                                outline.setRoundRect(
                                    0, 0, view.width, view.height, cornerPx
                                )
                            }
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        addView(textureView)
                    }
                },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/**
 * Non-intrusive error snackbar shown at bottom when user taps an invalid location.
 * Auto-dismisses after 3 seconds.
 */
@Composable
fun OriginLocationErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d("OriginLocationErrorSnackbar", "Rendering snackbar: $message")

    // Auto-dismiss after 3 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Rounded.Direction
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}