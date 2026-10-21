package io.github.clearkit.starter.hook.runtime

import io.github.clearkit.starter.model.adapt.AdaptReport

/**
 * 适配过程的事件（【宿主】里的适配卡片听这些事件，见 hook/ui 的 AdaptCard）。只在真的要重新解析时才有进度：
 * 【宿主】升级到没有可用适配表的版本、模块更新让本机表失效、安装包换了；已经适配好的版本只在核对出问题时给结果。
 * 运行时不依赖界面层，由入口（ModuleEntry）把 [listener] 接到卡片上；回调都在主线程。
 */
object AdaptationEvents {
    interface Listener {
        /** 开始等 App 本机适配这个版本。 */
        fun onAdapting(versionName: String)
        /** App 那边的解析进度（[total] 为 0 表示还在准备）。 */
        fun onProgress(done: Int, total: Int)
        /** 新表换上了（或核对出问题）：[report] 说明哪些生效、哪些要重开、哪些用不了。[card] 为假时用户关了适配卡片，只在有问题时给短提示。 */
        fun onResult(report: AdaptReport, card: Boolean)
        /** 等太久还没好：[detail] 说明原因和现在的情况。 */
        fun onTimeout(versionName: String, detail: String)
    }

    @Volatile
    var listener: Listener? = null
}
