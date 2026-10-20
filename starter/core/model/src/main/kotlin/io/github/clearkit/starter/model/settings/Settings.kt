package io.github.clearkit.starter.model.settings

/**
 * 全部设置，按分组。**加一个设置的步骤**（见 ClearKit docs/11-cookbook.md「加一个设置」）：
 * 1. 在下面对应分组里加一行（bool / int / string / stringSet / choice）；
 * 2. 在 model/preview/PagePreviews.kt 里这一组的示意图上加一块绑定它；
 * 3. 写功能代码读它（宿主里用 `settings[Settings.分组.名字]`）。
 * App 的页面按分组自动生成，不用改界面代码。
 *
 * 键名规则：`分组前缀.英文小驼峰`，一旦发布不要改（用户存的值按键名找）。
 */
object Settings {
    object Module : SettingGroup("module") {
        val enabled = bool("enabled", true, "模块主开关", "关闭后所有功能停用", restart = true)
        val safeMode = bool("safeMode", false, "安全模式", "只保留净化类功能，其它暂停", restart = true)
        val autoDisableCrashing = bool("autoDisableCrashing", true, "功能出错时自动停用", "导致【宿主】崩溃的功能下次启动不加载")
        val showInfo = bool("showInfo", true, "提示模块运行信息", "【宿主】启动时提示模块已加载、适配情况")
        val verboseLog = bool("verboseLog", false, "保存详细日志", "排查问题时打开")
        val hideLauncherIcon = bool("hideLauncherIcon", false, "隐藏桌面图标", "隐藏后从 LSPosed 管理器打开")
        /** 根权限（只在模块 App 里用，宿主进程不读）。 */
        val autoRoot = bool("autoRoot", true, "自动获取根权限", "打开【模块】时在后台申请一次；有了根权限，“立即重开”直接重启【宿主】")
        val autoRestartHost = bool("autoRestartHost", false, "离开【模块】时自动重开【宿主】", "改了要重开才生效的设置，切走时自动重启正在运行的【宿主】。需要根权限")
        /** 首页“常用开关”放哪几项（开关设置的键）。 */
        val quickToggles = stringSet("quickToggles", setOf("cleanup.exampleAds", "misc.exampleToast"), "首页常用开关", "首页放哪几个开关")
        val onboarded = bool("onboarded", false, "看过首次引导")
        val adaptCard = bool("adaptCard", true, "显示适配进度卡片", "装好或更新【模块】、【宿主】升级后第一次打开时，在【宿主】顶部显示适配进度和结果")
        /** App 每点一次“重新启用崩溃停用的功能”加一，宿主进程看到变化就清掉崩溃记录。 */
        val crashResetToken = int("crashResetToken", 0, "重新启用崩溃停用的功能")
        /** 维护模式：这些功能（按功能 id）不装，排查是哪个功能引起问题时用。 */
        val disabledFeatures = stringSet("disabledFeatures", emptySet(), "维护模式里停用的功能", "排查问题时临时停用；下次打开【宿主】生效", restart = true)
    }

    /** 示例分组“去广告与精简”：换成你的功能。示例功能见 hook/features/.../example/。 */
    object Cleanup : SettingGroup("cleanup") {
        /** 通用（任何宿主都能用，不依赖宿主版本）：第三方广告联盟 SDK 的加载和展示（AdSdkFeature）。 */
        val adSdk = bool(
            "adSdk", false, "拦截广告联盟的广告",
            "穿山甲、广点通、快手、百度等广告联盟的开屏、插屏、激励视频不再加载。个别 App 的开屏可能多等几秒", restart = true,
        )
        /** 通用：摇一摇跳转广告（ShakeAdFeature）。 */
        val shakeJump = bool("shakeJump", true, "摇一摇不跳转广告", "广告联盟的“摇一摇打开”不再响应；App 自己的摇一摇功能不受影响", restart = true)
        /** 示例：改接口数据（ExampleJsonRuleFeature）。 */
        val exampleAds = bool("exampleAds", true, "去掉示例广告", "接口返回的数据里去掉标了广告的条目（示例：JSON 规则）")
        /** 示例：多选（选上 = 隐藏）。 */
        val exampleHide = stringSet(
            "exampleHide", emptySet(), "隐藏的入口", "选上的入口不再显示（示例：多选）",
            choices = listOf("shop" to "商城", "game" to "游戏中心", "live" to "直播"),
        )
    }

    /** 示例分组“增强”。 */
    object Misc : SettingGroup("misc") {
        /** 示例：按特征找到一个方法挂钩子（ExampleHookFeature）。 */
        val exampleToast = bool("exampleToast", false, "示例：打开首页时提示", "【宿主】首页打开时弹一条提示（示例：DexAdapt 查询 + 挂钩子）")
        /** 示例：数值。 */
        val exampleCount = int("exampleCount", 3, "示例数值", "示例：每次显示几条", range = 1..10, unit = "条", presets = listOf(1, 3, 5))
    }

    /** 外观（模块 App 自己的样子）。 */
    object Appearance : SettingGroup("appearance") {
        val themeMode = choice(
            "themeMode", "system", "主题方式",
            "system" to "跟随系统", "light" to "浅色", "dark" to "深色",
            summary = "只改【模块】自己的界面",
        )
        val accent = string(
            "accent", "", "强调色", "留空用默认色；写六位色值", format = TextFormat.COLOR,
            presets = listOf("" to "默认", "1E88E5" to "天蓝", "2E7D32" to "松绿", "EF6C00" to "橙", "6A1B9A" to "紫", "455A64" to "石墨"),
        )
        val density = choice(
            "density", "standard", "界面密度",
            "standard" to "标准（一屏约 10 行）", "comfortable" to "宽松（说明全显示）",
            summary = "标准时说明只显示一行，点说明可以展开；触摸范围不缩小",
        )
        val reduceMotion = bool("reduceMotion", false, "减少动效", "页面切换不做动画；加载中的提示照常显示")
        val locale = choice(
            "locale", "system", "界面文字",
            "system" to "跟随系统", "hans" to "简体中文", "hant" to "繁體中文",
            summary = "【模块】和【宿主】里的面板；繁体由系统自动转换（安卓 10 起）",
        )
    }

    /** 配置作用域（多个宿主渠道、多个账号各自覆盖一部分设置，见 [ConfigScope]）。 */
    object Scope : SettingGroup("scope") {
        val enabled = bool("enabled", false, "启用分层配置", "不同渠道和账号可以各自覆盖一部分设置；关闭后都用全局值")
        /** 现在编辑哪一层：global / 渠道 / u<UID>（账号层）。 */
        val edit = string("edit", ConfigScope.GLOBAL, "现在改的是", "选一层后，各设置页改的只是这一层的值")
        val accounts = stringSet("accounts", emptySet(), "见过的账号")
    }

    /** 全部分组：新分组要加进来，App 的“功能”页按这里的顺序列。 */
    val groups: List<SettingGroup> = listOf(Module, Cleanup, Misc, Appearance, Scope)

    val all: List<Setting<*>> by lazy { groups.flatMap { it.all } }

    /** 一次性的请求和令牌：不是用户的配置，备份和恢复都不带。 */
    val transient: Set<Setting<*>> by lazy { setOf(Module.crashResetToken) }

    private val byKey: Map<String, Setting<*>> by lazy { all.associateBy { it.key } }

    fun byKey(key: String): Setting<*>? = byKey[key]
}
