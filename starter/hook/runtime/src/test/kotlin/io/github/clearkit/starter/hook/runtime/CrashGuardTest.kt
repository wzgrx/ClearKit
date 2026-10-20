package io.github.clearkit.starter.hook.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CrashGuardTest {
    private val guard = CrashGuard(File.createTempFile("crash_guard", ".json").apply { deleteOnExit() }) {
        mapOf(
            "io.github.clearkit.starter.hook.features.playback.mute.MuteFeature" to "playback.mute",
            "io.github.clearkit.starter.hook.features.note.NoteTracker" to "note.tracker",
            "io.github.clearkit.starter.hook.features.download.note.NoteDownloadFeature" to "download.note",
            "io.github.clearkit.starter.hook.features.feed.HomeFeed" to "feed.home",
            "io.github.clearkit.starter.hook.features.feed.SearchFeed" to "feed.search",
        )
    }

    private fun frame(cls: String, method: String = "m") = StackTraceElement(cls, method, "F.kt", 1)

    private fun error(vararg frames: StackTraceElement, cause: Throwable? = null) =
        RuntimeException("x", cause).apply { stackTrace = arrayOf(*frames) }

    private val boundary = frame(ChainCall::class.java.name, "proceed")
    private val muteBody = frame("io.github.clearkit.starter.hook.features.playback.mute.MuteFeature\$install\$1", "invoke")

    @Test
    fun featureFrameOnTopIsTheCulprit() {
        val e = error(muteBody, frame("android.app.Activity"))
        assertEquals("playback.mute", guard.culprit(e))
    }

    @Test
    fun hostExceptionThrownInsideProceedIsNotBlamedOnTheHookBody() {
        val e = error(
            frame("com.xingin.Host", "onCreate"),
            frame("org.lsposed.Bridge", "invoke"),
            boundary,
            muteBody,
            frame("io.github.clearkit.starter.hook.runtime.SafeHookKt", "runHook"),
            frame("android.app.Activity", "performCreate"),
        )
        assertNull(guard.culprit(e))
    }

    @Test
    fun hostWrapperAroundAProceedExceptionIsNotBlamedEither() {
        val inner = error(frame("com.xingin.Host"), boundary, muteBody)
        val outer = error(frame("com.xingin.Caller"), cause = inner)
        assertNull(guard.culprit(outer))
    }

    @Test
    fun featureCodeCalledBackFromInsideTheOriginalIsStillBlamed() {
        val e = error(
            frame("io.github.clearkit.starter.hook.features.note.NoteTracker", "remember"),
            frame("com.xingin.Host"),
            boundary,
            muteBody,
        )
        assertEquals("note.tracker", guard.culprit(e))
    }

    @Test
    fun causesAreSearchedToo() {
        val inner = error(frame("io.github.clearkit.starter.hook.features.note.sub.X"))
        assertEquals("note.tracker", guard.culprit(error(frame("com.xingin.Host"), cause = inner)))
    }

    @Test
    fun sharedReaderCalledFromAnotherFeaturesListenerBlamesThatFeature() {
        // 共用的读取工具在另一个功能的监听器里出错：算那个功能的，不停掉工具所在包的功能。
        val e = error(
            frame("io.github.clearkit.starter.hook.features.note.NoteReader", "info"),
            frame("io.github.clearkit.starter.hook.features.download.note.NoteSaveSheet", "show"),
            frame("io.github.clearkit.starter.hook.features.download.note.NoteDownloadFeature\$install\$1", "invoke"),
            frame("android.view.View", "performClick"),
        )
        assertEquals("download.note", guard.culprit(e))
    }

    @Test
    fun withoutAFeatureFrameTheOutermostFeaturePackageWins() {
        val e = error(
            frame("io.github.clearkit.starter.hook.features.note.ProfileInFeed", "find"),
            frame("io.github.clearkit.starter.hook.features.download.note.NoteSaveSheet\$show\$1", "onClick"),
            frame("android.view.View", "performClick"),
        )
        assertEquals("download.note", guard.culprit(e))
    }

    @Test
    fun ownHookBodyCallingTheSharedReaderIsStillItsOwnFault() {
        val e = error(
            frame("io.github.clearkit.starter.hook.features.note.NoteReader", "info"),
            frame("io.github.clearkit.starter.hook.features.note.NoteTracker\$install\$2", "invoke"),
        )
        assertEquals("note.tracker", guard.culprit(e))
    }

    @Test
    fun packagesWithSeveralFeaturesAreNotGuessed() {
        assertNull(guard.culprit(error(frame("io.github.clearkit.starter.hook.features.feed.FeedPipeline", "judge"))))
        assertEquals("feed.home", guard.culprit(error(frame("io.github.clearkit.starter.hook.features.feed.HomeFeed\$install\$1", "invoke"))))
    }
}
