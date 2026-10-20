package io.github.clearkit.starter.model.adsdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSdksTest {
    @Test
    fun recognisesSdkPackagesOnly() {
        assertTrue(AdSdks.isAdClass("com.bytedance.sdk.openadsdk.TTAdNative"))
        assertTrue(AdSdks.isAdClass("com.kwad.sdk.core.ShakeDetector"))
        // 前缀要带点：同名开头的别的包不算
        assertFalse(AdSdks.isAdClass("com.qq.emoji.Panel"))
        assertFalse(AdSdks.isAdClass("com.example.host.MainActivity"))
    }

    @Test
    fun onlyLoadShowReadyMethodsReturningBooleanOrVoid() {
        assertEquals(AdSdks.Action.SKIP, AdSdks.actionFor("loadSplashAd", "void"))
        assertEquals(AdSdks.Action.SKIP, AdSdks.actionFor("showAd", "void"))
        assertEquals(AdSdks.Action.RETURN_FALSE, AdSdks.actionFor("isReady", "boolean"))
        // 初始化、设置回调不拦；返回对象的不拦（宿主拿到 null 会崩）
        assertNull(AdSdks.actionFor("init", "void"))
        assertNull(AdSdks.actionFor("setDownloadListener", "void"))
        assertNull(AdSdks.actionFor("loadAdData", "java.lang.Object"))
    }

    @Test
    fun findsAdCallerInStack() {
        val stack = listOf("android.hardware.SensorManager", "com.kwad.components.core.s.a", "com.example.host.App")
        assertEquals("com.kwad.components.core.s.a", AdSdks.adCaller(stack))
        assertNull(AdSdks.adCaller(listOf("com.example.host.ShakeGame", "android.app.Activity")))
        // 只看前几层
        assertNull(AdSdks.adCaller(List(30) { "x.Y" } + "com.qq.e.ads.Foo"))
    }
}
