package com.example.myapplication

import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.Design.screens.VaultVideoPlayer
import com.example.myapplication.Design.screens.createVaultVideoPlayer
import com.example.myapplication.security.AuthSession
import com.example.myapplication.vault.VaultMediaItem
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Uses a private synthetic clip; no gallery/storage permission is required. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VaultVideoPlayerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var clip: File
    private lateinit var media: VaultMediaItem
    private val players = mutableListOf<ExoPlayer>()

    @Before fun prepareClip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        clip = File.createTempFile("vault-playback-", ".mp4", instrumentation.targetContext.cacheDir)
        instrumentation.context.assets.open("qa-video.mp4").use { input ->
            clip.outputStream().use { output -> input.copyTo(output) }
        }
        media = VaultMediaItem(Uri.fromFile(clip), clip.name, "Test", true, true, clip.absolutePath)
        compose.runOnUiThread { AuthSession.unlock() }
    }

    @After fun cleanUp() {
        compose.runOnUiThread { AuthSession.lock() }
        compose.waitForIdle()
        compose.runOnUiThread {
            players.forEach { if (!it.isReleased) it.release() }
        }
        clip.delete()
    }

    @Test fun textureRendersFramesAndPausedSeekSurvivesRestorationAndPageChanges() {
        val active = mutableStateOf(true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            VaultVideoPlayer(media, active.value, Modifier.fillMaxSize(), playerFactory = { context ->
                createVaultVideoPlayer(context).also(players::add)
            })
        }
        waitFor { players.lastOrNull()?.let { !it.isReleased && it.isPlaying && it.currentPosition > 100 } == true }
        waitFor {
            val root = compose.activity.window.decorView
            val texture = findView(root, TextureView::class.java)
            val bitmap = texture?.getBitmap(128, 128)
            val hasFrame = bitmap != null && bitmap.width > 0 && bitmap.height > 0
            bitmap?.recycle()
            hasFrame && (players.last().videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 &&
                findView(root, SurfaceView::class.java) == null
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.video_pause)).performClick()
        waitFor { !players.last().isPlaying && !players.last().playWhenReady }
        var target = 0L
        compose.runOnUiThread { target = players.last().duration / 2 }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(target.toFloat()) }
        waitFor { abs(players.last().currentPosition - target) < 500 }

        restoration.emulateSavedInstanceStateRestore()
        waitFor { players.size >= 2 && players.last().playbackState == Player.STATE_READY }
        compose.runOnUiThread {
            assertTrue(players.first().isReleased)
            assertFalse(players.last().playWhenReady)
            assertTrue(abs(players.last().currentPosition - target) < 500)
            active.value = false
        }
        waitFor { players.last().isReleased }
        val count = players.size
        compose.runOnUiThread { active.value = true }
        waitFor { players.size > count && players.last().playbackState == Player.STATE_READY }
        compose.runOnUiThread {
            assertFalse(players.last().playWhenReady)
            assertTrue(abs(players.last().currentPosition - target) < 500)
        }
        var end = 0L
        compose.runOnUiThread { end = (players.last().duration - 100).coerceAtLeast(0) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(end.toFloat()) }
        compose.onNodeWithContentDescription(context.getString(R.string.video_play)).performClick()
        waitFor { players.last().playbackState == Player.STATE_ENDED }
        compose.onNodeWithContentDescription(context.getString(R.string.video_play)).performClick()
        waitFor { players.last().isPlaying && players.last().currentPosition < players.last().duration / 2 }
    }

    @Test fun authenticationAndBackgroundPauseActualPlayerAndOnlyActivePageAllocatesOne() {
        val active = mutableStateOf(false)
        compose.setContent {
            VaultVideoPlayer(media, active.value, Modifier.fillMaxSize(), playerFactory = { context ->
                createVaultVideoPlayer(context).also(players::add)
            })
        }
        compose.runOnIdle { assertTrue(players.isEmpty()); active.value = true }
        waitFor { players.lastOrNull()?.isPlaying == true }
        val beforeBackground = players.last()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.runOnUiThread { assertFalse(beforeBackground.isPlaying) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor { players.last().isPlaying }
        compose.runOnUiThread { AuthSession.lock() }
        waitFor { players.last().isReleased }
        val count = players.size
        compose.runOnUiThread { active.value = false; AuthSession.unlock() }
        compose.runOnIdle { assertEquals(count, players.size) }
        compose.runOnUiThread { active.value = true }
        waitFor { players.size > count && players.last().isPlaying }
    }

    @Test fun revocationBeforePreparationDoesNotStartPlayback() {
        var playedWhileLocked = false
        compose.setContent {
            VaultVideoPlayer(media, true, Modifier.fillMaxSize(), playerFactory = { context ->
                createVaultVideoPlayer(context).also { player ->
                    players.add(player)
                    player.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying && !AuthSession.isAuthenticated.value) playedWhileLocked = true
                        }
                    })
                    AuthSession.lock()
                }
            })
        }
        waitFor { players.isNotEmpty() && players.all { it.isReleased } }
        compose.runOnUiThread { assertFalse(playedWhileLocked) }
    }

    @Test fun unreadableVideoShowsFriendlyErrorAndReleasesDecoder() {
        assertTrue(clip.delete())
        compose.setContent {
            VaultVideoPlayer(media, true, Modifier.fillMaxSize(), playerFactory = { context ->
                createVaultVideoPlayer(context).also(players::add)
            })
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(context.getString(R.string.video_playback_failed)).fetchSemanticsNodes().isNotEmpty()
        }
        waitFor { players.isNotEmpty() && players.all { it.isReleased } }
    }

    private fun waitFor(condition: () -> Boolean) {
        compose.waitUntil(15_000) {
            var matched = false
            compose.runOnUiThread { matched = condition() }
            matched
        }
    }

    private fun <T : View> findView(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            findView(root.getChildAt(index), type)?.let { return it }
        }
        return null
    }
}
