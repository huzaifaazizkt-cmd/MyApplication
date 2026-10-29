package com.example.myapplication

import android.content.ContentValues
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.example.myapplication.Design.screens.VaultScreen
import com.example.myapplication.security.AuthSession
import com.example.myapplication.vault.VaultMediaItem
import com.example.myapplication.vault.VaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30) // Synthetic MediaStore rows without changing legacy storage permissions.
class VaultPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun videoPlaysInternallyPausesForAuthenticationAndUnhidesFromPreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val name = "AppLock_QA_${UUID.randomUUID()}.mp4"
        val source = resolver.insert(collection, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        })!!
        instrumentation.context.assets.open("qa-video.mp4").use { input ->
            resolver.openOutputStream(source)!!.use { output -> input.copyTo(output) }
        }
        val repository = VaultRepository.get(context)
        try {
            runBlocking {
                assertEquals(0, repository.hide(listOf(VaultMediaItem(source, name, "AppLockQA", true))).failures)
            }
            compose.runOnUiThread { AuthSession.unlock() }
            compose.setContent { VaultScreen() }
            compose.onNodeWithText(context.getString(R.string.videos)).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(name).performClick()
            compose.onNodeWithText(context.getString(R.string.unhide)).assertIsDisplayed()
            waitForPlaybackControls(true)
            compose.waitUntil(15_000) {
                var available = false
                compose.runOnUiThread {
                    available = findVideo(compose.activity.window.decorView)?.isAvailable == true
                }
                available
            }
            val pause = compose.onNodeWithContentDescription(context.getString(R.string.video_pause))
            val unhide = compose.onNodeWithText(context.getString(R.string.unhide))
            assertTrue(pause.fetchSemanticsNode().boundsInRoot.bottom <= unhide.fetchSemanticsNode().boundsInRoot.top)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitForPlaybackControls(true)
            compose.runOnUiThread { AuthSession.lock() }
            waitForPlaybackControls(false)
            compose.runOnUiThread { AuthSession.unlock() }
            waitForPlaybackControls(true)
            compose.onNodeWithText(context.getString(R.string.unhide)).performClick()
            compose.waitUntil(10_000) { repository.items.value.none { it.name == name } }
            assertTrue(repository.items.value.none { it.name == name })
        } finally {
            runBlocking {
                repository.snapshot().filter { it.name == name }.takeIf { it.isNotEmpty() }?.let { repository.restore(it) }
            }
            resolver.delete(collection, "${MediaStore.Video.Media.DISPLAY_NAME}=?", arrayOf(name))
            AuthSession.lock()
        }
    }

    private fun waitForPlaybackControls(expectedPlaying: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val label = context.getString(if (expectedPlaying) R.string.video_pause else R.string.video_play)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun findVideo(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findVideo(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
