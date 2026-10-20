package io.github.clearkit.starter.hook.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import io.github.clearkit.starter.model.settings.Setting
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * 【宿主】里的界面截图（设置窗、工具盘），改界面时在电脑上看。只在 `-Pbc.shots=<输出目录>` 时跑：
 * `./gradlew :hook:ui:testDebugUnitTest --tests '*HostShotsTest*' -Pbc.shots=/tmp/shots`。
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class HostShotsTest {
    private class FakeStore : SettingsStore {
        private val values = HashMap<String, Any>()
        override fun value(setting: Setting<*>): Any = values[setting.key] ?: setting.default
        override fun write(setting: Setting<*>, value: Any?, done: (String?) -> Unit) {
            if (value == null) values.remove(setting.key) else values[setting.key] = value
            done(null)
        }
        override val pendingRestart: Set<String> = setOf("misc.exampleCount")
        override fun restart(activity: Activity) = Unit
        override fun openApp(activity: Activity, route: String?) = Unit
    }

    private fun out(): File? = System.getProperty("bc.shots").orEmpty().takeIf { it.isNotEmpty() }?.let { File(it).apply { mkdirs() } }

    private fun capture(view: View, file: File) {
        val w = view.width.takeIf { it > 0 } ?: 780
        val h = view.height.takeIf { it > 0 } ?: 1688
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(name: String, dark: Boolean, open: (Activity) -> Unit) {
        val dir = out() ?: return
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        open(activity)
        ShadowLooper.idleMainLooper()
        val dialog = ShadowDialog.getLatestDialog() ?: return
        val decor = dialog.window!!.decorView
        val w = 780; val h = 1688
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, w, h)
        ShadowLooper.idleMainLooper()
        capture(decor, File(dir, "$name.png"))
    }

    @Test
    fun settingsSheet() {
        assumeTrue(out() != null)
        shoot("host_sheet_cleanup", false) { SettingsSheet(it, FakeStore(), "cleanup").show() }
    }

    @Test
    fun settingsSheetDark() {
        assumeTrue(out() != null)
        shoot("host_sheet_misc_dark", true) { SettingsSheet(it, FakeStore(), "misc").show() }
    }
}
