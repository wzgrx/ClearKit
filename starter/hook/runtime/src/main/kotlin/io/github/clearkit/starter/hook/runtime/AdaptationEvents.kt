package io.github.clearkit.starter.hook.runtime

/**
 * 适配过程的事件（【宿主】里的适配卡片听这些事件，见 hook/ui 的 AdaptCard）。只在真的要重新解析时才有：
 * 【宿主】升级到没有可用适配表的版本、模块更新让本机表失效、安装包换了；已经适配好的版本一个事件都没有。运行时不依赖界面层，
 * 由入口（ModuleEntry）把 [listener] 接到卡片上；回调都在主线程。
 */
object AdaptationEvents {
    interface Listener {
        /** 开始等 App 本机适配这个版本。 */
        fun onAdapting(versionName: String)
        /** 有可用的表（内置表等），装好或更新模块后第一次启动时逐项核对：之后同样走 [onProgress]、[onAdapted]。 */
        fun onVerifying(versionName: String) = onAdapting(versionName)
        /** App 那边的解析进度（[total] 为 0 表示还在准备）。 */
        fun onProgress(done: Int, total: Int)
        /** 新表到了、已经换上：[found] / [total] 个符号，现在 [active] 个功能生效。 */
        fun onAdapted(versionName: String, found: Int, total: Int, active: Int)
        /** 等太久还没好（App 起不来、扫描失败）。 */
        fun onTimeout(versionName: String)
    }

    @Volatile
    var listener: Listener? = null
}
