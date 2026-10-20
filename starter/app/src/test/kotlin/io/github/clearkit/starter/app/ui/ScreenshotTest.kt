package io.github.clearkit.starter.app.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.ui.theme.DarkColors
import io.github.clearkit.starter.app.ui.theme.LightColors
import io.github.clearkit.starter.app.ui.theme.LocalBcColors
import io.github.clearkit.starter.app.ui.theme.materialScheme
import io.github.clearkit.starter.model.bridge.Bridge
import io.github.clearkit.starter.model.bridge.FeatureStatus
import io.github.clearkit.starter.model.bridge.HostStats
import io.github.clearkit.starter.model.bridge.RuntimeStatus
import io.github.clearkit.starter.model.settings.Settings
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 界面截图：把各页渲染成 PNG，改界面时在电脑上逐页看（不占真机）。只在 `-Pbc.shots=<输出目录>` 时跑：
 * `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*' -Pbc.shots=/tmp/shots`。
 * 要截的页可以用 `-Pbc.shots.routes=home,scene/video` 限定。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun shots() {
        val out = System.getProperty("bc.shots").orEmpty()
        assumeTrue("没给 -Pbc.shots，跳过截图", out.isNotEmpty())
        val dir = File(out).apply { mkdirs() }
        val app = ModuleApp.instance
        app.settings.set(Settings.Module.onboarded, true)
        // 演示数据：一份运行报告、这周的计数、几条最近改动（只在截图里）
        val week = java.time.LocalDate.now().let { d -> val wf = java.time.temporal.WeekFields.ISO; d.get(wf.weekBasedYear()) * 100 + d.get(wf.weekOfWeekBasedYear()) }
        val features = listOf(
            FeatureStatus("cleanup.exampleAds", "示例：去掉接口里的广告", FeatureStatus.State.ACTIVE),
            FeatureStatus("misc.exampleToast", "示例：打开首页时提示", FeatureStatus.State.OFF),
            FeatureStatus("net.rewrite", "接口数据改写", FeatureStatus.State.ACTIVE),
        )
        app.status.update(Bridge.json.encodeToString(RuntimeStatus.serializer(), RuntimeStatus("1.0.0", io.github.clearkit.starter.model.HOST_PACKAGE, 1, "0.0.0", true, false, true, features, System.currentTimeMillis())))
        app.stats.update(Bridge.json.encodeToString(HostStats.serializer(), HostStats(io.github.clearkit.starter.model.HOST_PACKAGE, mapOf("w$week.card" to 128L, "w$week.ev.cleanup.exampleAds" to 128L))))
        // 模块已启用、两个客户端都装着且已适配（反射改内部状态，只在截图里）
        @Suppress("UNCHECKED_CAST")
        fun <T> flow(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as kotlinx.coroutines.flow.MutableStateFlow<T>
        flow<io.github.clearkit.starter.app.data.ModuleService.State>(io.github.clearkit.starter.app.data.ModuleService, "_state").value =
            io.github.clearkit.starter.app.data.ModuleService.State.Active("LSPosed", "1.10", 102, setOf(io.github.clearkit.starter.model.HOST_PACKAGE))
        val statuses = flow<Map<String, io.github.clearkit.starter.app.adapt.HostAdapter.Status>>(app.hostAdapter, "_statuses")
        statuses.value = mapOf(
            io.github.clearkit.starter.model.HOST_PACKAGE to io.github.clearkit.starter.app.adapt.HostAdapter.Status(io.github.clearkit.starter.model.HOST_PACKAGE, installed = true, versionCode = 1, versionName = "0.0.0", bundled = true, bundledMatches = true),
        )
        app.settings.set(Settings.Misc.exampleToast, true)
        val routes = (System.getProperty("bc.shots.routes") ?: "").split(',').filter { it.isNotBlank() }.ifEmpty { DEFAULT }
        var route by mutableStateOf(routes.first())
        var dark by mutableStateOf(false)
        rule.setContent {
            val colors = if (dark) DarkColors else LightColors
            CompositionLocalProvider(LocalBcColors provides colors) {
                MaterialTheme(colorScheme = materialScheme(colors)) {
                    key(route, dark) { AppShell(entry = route.removeSuffix("@dark").removeSuffix("@hant")) }
                }
            }
        }
        for (r in routes) {
            io.github.clearkit.starter.hook.ui.ZhText.mode = if (r.endsWith("@hant")) "hant" else "hans"
            route = r
            dark = r.endsWith("@dark")
            rule.waitForIdle()
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, r.replace('/', '_').replace('?', '_').replace('=', '_') + ".png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    companion object {
        val DEFAULT = listOf(
            "home", "features", "mine", "home@dark", "cleanup", "misc", "search", "onboarding", "quick", "changes",
            "config", "adapt", "status", "diagnostics", "module", "appearance", "about", "config/scope",
        )
    }
}
