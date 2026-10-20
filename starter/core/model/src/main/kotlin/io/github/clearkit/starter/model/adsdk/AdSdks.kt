package io.github.clearkit.starter.model.adsdk

/**
 * 第三方广告联盟 SDK（穿山甲、广点通、快手、百度、Mintegral、Sigmob、TopOn……）：很多 App 的开屏、插屏、激励视频广告
 * 走这些 SDK。SDK 的公开接口类名不混淆（给接入方调用的），所以不用 DexAdapt 查询，任何宿主都能直接用。
 * 思路参考 Shuoh118/Ads-kill（MIT），这里重新实现，判断逻辑都是纯函数、有单测。
 *
 * 要加新的 SDK：在 [PREFIXES] 加包名前缀，在 [ENTRY_CLASSES] 加它的加载 / 展示入口类（从 SDK 的接入文档里查），跑 AdSdksTest。
 */
object AdSdks {
    /** 广告 SDK 的包名前缀（判断调用栈、监听器是不是广告 SDK 的）。 */
    val PREFIXES: List<String> = listOf(
        "com.bytedance.sdk.openadsdk.", "com.byted.pangle.",   // 穿山甲
        "com.qq.e.",                                           // 广点通（优量汇）
        "com.baidu.mobads.",                                   // 百度百青藤
        "com.kwad.",                                           // 快手联盟
        "com.mbridge.msdk.", "com.mintegral.",                  // Mintegral
        "com.sigmob.",                                         // Sigmob
        "com.anythink.",                                       // TopOn
        "com.beizi.",                                          // 倍孜
        "com.jd.ad.sdk.",                                      // 京媒
        "com.huawei.openalliance.ad.",                         // 华为鲸鸿动能
        "com.unity3d.ads.", "com.applovin.", "com.google.android.gms.ads.", // 海外常见
    )

    /** 加载 / 展示广告的入口类：在这些类上挂“加载、展示、是否就绪”这类方法。类不存在就跳过（宿主没接入这家）。 */
    val ENTRY_CLASSES: List<String> = listOf(
        "com.bytedance.sdk.openadsdk.TTAdNative",
        "com.bytedance.sdk.openadsdk.TTAdManager",
        "com.bytedance.sdk.openadsdk.TTSplashAd",
        "com.bytedance.sdk.openadsdk.TTNativeExpressAd",
        "com.bytedance.sdk.openadsdk.TTFullScreenVideoAd",
        "com.bytedance.sdk.openadsdk.TTRewardVideoAd",
        "com.qq.e.ads.splash.SplashAD",
        "com.qq.e.ads.nativ.NativeUnifiedAD",
        "com.qq.e.ads.nativ.NativeExpressAD",
        "com.qq.e.ads.interstitial2.UnifiedInterstitialAD",
        "com.qq.e.ads.rewardvideo.RewardVideoAD",
        "com.baidu.mobads.sdk.api.SplashAd",
        "com.baidu.mobads.sdk.api.BaiduNativeManager",
        "com.baidu.mobads.sdk.api.ExpressInterstitialAd",
        "com.kwad.sdk.api.KsLoadManager",
        "com.kwad.sdk.api.KsSplashScreenAd",
        "com.kwad.sdk.api.KsInterstitialAd",
        "com.mbridge.msdk.out.MBSplashHandler",
        "com.sigmob.windad.Splash.WindSplashAD",
        "com.anythink.splashad.api.ATSplashAd",
        "com.anythink.interstitial.api.ATInterstitial",
    )

    /** 方法名以这些词开头才拦（不碰初始化、回调设置这类，免得 SDK 自己出错）。 */
    private val VERBS = listOf("load", "show", "preload", "fetch", "isready", "isloaded", "isvalid", "isadready")

    enum class Action {
        /** 返回 false：“广告没准备好”，宿主就不展示。 */
        RETURN_FALSE,
        /** 什么都不做直接返回：不去请求、不展示。 */
        SKIP,
    }

    fun isAdClass(className: String): Boolean = PREFIXES.any { className.startsWith(it) }

    /**
     * 一个入口方法该怎么拦；null = 不拦。只拦返回 boolean 或 void 的：返回对象的拦了以后宿主拿到 null 容易崩。
     * [returnType] 是 Java 的类型名（`boolean`、`void`……）。
     */
    fun actionFor(methodName: String, returnType: String): Action? {
        val n = methodName.lowercase()
        if (VERBS.none { n.startsWith(it) }) return null
        return when (returnType) {
            "boolean" -> Action.RETURN_FALSE
            "void" -> Action.SKIP
            else -> null
        }
    }

    /** 调用栈里第一个广告 SDK 的类名（没有返回 null）。只看前 [depth] 层。 */
    fun adCaller(stack: List<String>, depth: Int = 24): String? = stack.take(depth).firstOrNull(::isAdClass)
}
