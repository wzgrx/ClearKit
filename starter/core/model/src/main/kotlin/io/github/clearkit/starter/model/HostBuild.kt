package io.github.clearkit.starter.model

/** 宿主的包名（生成脚本替换）。有多个渠道时每个渠道一个常量。 */
const val HOST_PACKAGE = "com.example.host"

/**
 * 内置适配表的宿主版本：每个渠道的最新版是主要目标，上一版也内置；其它版本由 App 在手机上用 DexKit 现场适配。
 *
 * **拿到宿主安装包后**（ClearKit docs/10-environment.md「收集安装包」）把下面的占位版本换成真的：
 * 名字写成 `渠道_versionCode`，versionCode 和 versionName 从安装包读（`aapt2 dump badging base.apk`）。
 * 安装包放在 `$HOST_APK_DIR/<渠道小写>-<versionCode>/base.apk`。
 */
enum class HostBuild(val channel: Channel, val versionCode: Long, val versionName: String) {
    MAIN_1(Channel.MAIN, 1, "0.0.0"),
    ;

    enum class Channel(val packageName: String, val label: String) {
        MAIN(HOST_PACKAGE, "【宿主】"),
    }

    val label: String get() = "$versionName ${channel.label}"

    companion object {
        fun of(packageName: String, versionCode: Long): HostBuild? =
            entries.firstOrNull { it.channel.packageName == packageName && it.versionCode == versionCode }

        fun channelOf(packageName: String): Channel? = Channel.entries.firstOrNull { it.packageName == packageName }

        fun latest(channel: Channel): HostBuild = entries.filter { it.channel == channel }.maxBy { it.versionCode }
    }
}
