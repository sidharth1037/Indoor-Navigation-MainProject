package `in`.project.enroute.feature.home.locationselection

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.net.toUri

/**
 * Tutorial dialog shown after the user confirms their starting location.
 *
 * Plays Walking.mp4 from assets to demonstrate how to hold the phone
 * for accurate position tracking.
 *
 * @param onDismiss Called when the user taps "Got it".
 */
@OptIn(UnstableApi::class)
@Composable
fun WalkingTutorialDialog(onDismiss: () -> Unit) {

    // ── Easy resize handle ─────────────────────────────────────────────────
    val videoHeightDp = 320.dp
    val videoCornerDp = 16f
    // ───────────────────────────────────────────────────────────────────────

    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///Walking.mp4".toUri()))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            // Do NOT set playWhenReady here — we start only after the
            // TextureView surface is available to avoid stretched frames.
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val containerColor = MaterialTheme.colorScheme.primaryContainer

    Dialog(
        onDismissRequest = {
            exoPlayer.stop()
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                // ── Header ────────────────────────────────────────────────
                Text(
                    text = "How to Hold Your Phone",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Hold your phone flat in front of you as shown\n" +
                            "for accurate position tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Video ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeightDp)
                        .clip(RoundedCornerShape(videoCornerDp.dp))
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val cornerPx = videoCornerDp * ctx.resources.displayMetrics.density

                            val textureView = TextureView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surface: SurfaceTexture, width: Int, height: Int
                                    ) {
                                        // Attach the surface and start playback only now —
                                        // ExoPlayer knows the exact surface dimensions, so
                                        // the very first rendered frame has the correct ratio.
                                        exoPlayer.setVideoSurface(android.view.Surface(surface))
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surface: SurfaceTexture, width: Int, height: Int
                                    ) = Unit

                                    override fun onSurfaceTextureDestroyed(
                                        surface: SurfaceTexture
                                    ): Boolean {
                                        exoPlayer.setVideoSurface(null)
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(
                                        surface: SurfaceTexture
                                    ) = Unit
                                }
                            }

                            // Wrap in a FrameLayout with rounded-rect outline clipping.
                            // TextureView supports clipToOutline (SurfaceView does not).
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

                Spacer(modifier = Modifier.height(20.dp))

                // ── Close button ───────────────────────────────────────────
                Button(
                    onClick = {
                        exoPlayer.stop()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(
                        text = "Got it",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
