package io.github.clearkit.starter.hook.features.cleanup.adsdk

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.clearkit.starter.hook.features.module.Evidence
import io.github.clearkit.starter.hook.runtime.Feature
import io.github.clearkit.starter.hook.runtime.FeatureScope
import io.github.clearkit.starter.hook.runtime.ModuleLog
import io.github.clearkit.starter.model.adsdk.AdSdks
import io.github.clearkit.starter.model.settings.Settings

/**
 * 通用去广告：“摇一摇打开广告”。广告 SDK 注册传感器监听时（监听器是 SDK 的类，或调用栈里有 SDK），换成一个永远不触发的空监听器：
 * 注册照常成功，SDK 不报错，只是摇了也没反应。宿主自己的摇一摇（调用栈里没有广告 SDK）不受影响。
 */
object ShakeAdFeature : Feature("cleanup.shakeJump", "摇一摇不跳转广告") {
    override val switches = listOf(Settings.Cleanup.shakeJump)

    private val EMPTY = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) = Unit
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun FeatureScope.install() {
        val targets = SensorManager::class.java.declaredMethods.filter { m ->
            m.name == "registerListener" && m.parameterTypes.size >= 2 && m.parameterTypes[0] == SensorEventListener::class.java
        }
        targets.forEach { m ->
            hook(m) { call ->
                val listener = call.arg(0) ?: return@hook call.proceed()
                if (!settings[Settings.Cleanup.shakeJump]) return@hook call.proceed()
                // 先看监听器本身（便宜），再看调用栈（注册传感器很少发生，开销可以接受）
                val fromAd = AdSdks.isAdClass(listener.javaClass.name) ||
                    AdSdks.adCaller(Throwable().stackTrace.map { it.className }) != null
                if (!fromAd) return@hook call.proceed()
                ModuleLog.i("shake listener from ad sdk replaced: ${listener.javaClass.name}")
                Evidence.acted(this@ShakeAdFeature, settings)
                call.proceed(EMPTY, *call.args.drop(1).toTypedArray())
            }
        }
    }
}
