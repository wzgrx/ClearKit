# 11 · 菜谱：常见开发任务的逐步做法

每个菜谱：**改哪些文件 → 每一步写什么 → 怎么检查**。代码照抄后改名字即可。
路径里的 `<包>` 是你项目的包路径（例如 `io/github/wzgrx/fooclear`），`...` 表示 `src/main/kotlin/<包>`。
菜谱的级别（L1 / L2）见 [09](09-low-capability-models.md) 第 2 节。

目录：
1. 加一个设置（L1）
2. 加一个功能分组 / 一页（L1）
3. 看宿主的接口数据（L1）
4. 写一条 JSON 规则（L1，有样例 JSON 时）
5. 换成真实的宿主版本（L1）
6. 查启动页类名（L1）
7. 写查询：找到宿主里的类和方法（L2）
8. 符号缺失怎么办（L2）
9. 挂一个方法（L2）
10. 隐藏一个界面元素（L2）
11. 写单元测试（L1）
12. 加一个渠道（国际版、谷歌商店版……）（L2）
13. 准备发布（L1，发布本身要维护者同意）
14. 通用去广告：广告联盟 SDK、摇一摇跳转（L1）

---

## 1. 加一个设置（L1）

**改的文件**：`core/model/.../model/settings/Settings.kt`、`core/model/.../model/preview/PagePreviews.kt`、读它的功能代码。

1. 在 `Settings.kt` 对应分组（`object Cleanup`、`object Misc`……）里加一行。五种写法：

   ```kotlin
   // 开关：键名（英文小驼峰）、默认值、标题、一句说明
   val hideBanner = bool("hideBanner", true, "去掉首页横幅广告", "首页顶部轮播里的广告不再出现")
   // 多选（选上 = 隐藏）：选项是 值 to 显示名
   val tabsHide = stringSet("tabsHide", emptySet(), "隐藏的底栏按钮", "选上的按钮不再显示",
       choices = listOf("shop" to "商城", "live" to "直播"))
   // 数值：范围、单位、常用值（示意图里点一下换下一个）
   val pageSize = int("pageSize", 20, "每页条数", "一次加载多少条", range = 10..50, unit = "条", presets = listOf(10, 20, 50))
   // 单选
   val mode = choice("mode", "auto", "模式", "auto" to "自动", "on" to "总是", "off" to "从不")
   // 文字
   val keyword = string("keyword", "", "关键词", "留空不生效")
   ```

   规则：键名一旦发布不要改；去广告类默认 `true`，会改变宿主行为的默认 `false`；要重开宿主才生效的加 `restart = true`。
2. 画进示意图：在 `PagePreviews.kt` 的 `PAGES` 里找到这一组的页（`route` 和分组前缀相同，例如 `"cleanup"`），加一块：

   ```kotlin
   hide("横幅广告", c.hideBanner)                 // 开了会藏掉的东西：划掉
   group("底栏", options(c.tabsHide, c.tabsHide.choices.map { it.id to it.label }))   // 多选：每个选项一块
   value("每页 {v}", m.pageSize)                  // 数值：块里写当前值
   mark("自动播放", m.autoPlay, "关掉")            // 开了会改样子：加标签
   add("保存原图", m.saveImage)                    // 开了会新加的东西：“+”块
   ```

   画法怎么选见 [05-live-preview-ui.md](05-live-preview-ui.md)。块的顺序照宿主界面的真实位置排。
3. 写代码读它：在功能里 `settings[Settings.Cleanup.hideBanner]`（钩子里实时读）；开关还要写进功能的 `switches`，
   多选写进 `listSwitches`。
4. **检查**：`scripts/verify.sh --fast`。没画进示意图 → `PreviewCoverageTest` 失败；没有代码读 → `AllFeaturesTest` 失败，按提示改。
   App 和宿主里的设置窗会自动出现这一项，不用改界面代码。

## 2. 加一个功能分组 / 一页（L1）

一个分组 = App“功能”页里的一行 = 一个页面。

1. `Settings.kt`：仿照 `object Misc` 新建 `object Player : SettingGroup("player") { ... }`，把它加进最下面的 `groups` 列表。
2. `FeatureCatalog.kt`：在 `pages` 里加 `Page("player", "播放", "一句话说明", Settings.Player)`。
3. `PagePreviews.kt`：在 `PAGES` 里加 `Page("player", "播放", listOf(...))`，画出这一页的示意图（第 1 节第 2 步）。
4. 可选：`app/.../ui/pages/FeaturePages.kt` 的 `iconOf` 里给这一页配图标（`BcIcons` 里挑）。
5. **检查**：`scripts/verify.sh --fast`；`PagePreviewsTest.everyFeaturePageHasADiagram` 保证第 3 步没漏。

## 3. 看宿主的接口数据（L1）

1. 装好模块，在模块 App 的“我的 › 模块设置”里打开“保存详细日志”，重开宿主，把要处理的页面刷几遍。
2. 读日志：`adb shell su -c 'cat /data/adb/lspd/log/modules_*.log' | grep "net "`。每种接口第一次出现时记一行：
   `net fastjson 类型名 len=长度 {"code":0,"data":{...` （开头 400 个字，令牌、密钥已经打码）。
3. 找到要改的那份：按界面上看到的字（标题、按钮名）在日志里搜。需要完整 JSON 时请维护者或强模型抓包。
4. 存样例：把 JSON 存成 `docs/samples/<名字>.json`，**删掉账号、昵称、手机号、令牌、头像地址**。
5. 看完把“保存详细日志”关掉。

## 4. 写一条 JSON 规则（L1，有样例 JSON 时）

照抄模板里的 `ExampleRules.kt`（纯函数）、`ExampleRulesTest.kt`（测试）、`ExampleJsonRuleFeature.kt`（功能）。

1. **纯函数**：新建 `core/model/.../model/<分组>/XxxRules.kt`：

   ```kotlin
   import kotlinx.serialization.json.JsonArray
   import kotlinx.serialization.json.JsonObject
   import kotlinx.serialization.json.booleanOrNull
   import kotlinx.serialization.json.jsonPrimitive

   object BannerRules {
       /** 这份 JSON 里独有的字段名（带引号）：不含它们的 JSON 直接跳过。要够独特，别只写 "data"、"list" 这种到处都有的。 */
       val MARKERS = listOf("\"banner_items\"")

       /** 去掉 banner_items 里的广告；没改返回 null。data 是 {code, data} 包装里的 data。 */
       fun removeAds(data: JsonObject): JsonObject? {
           val items = data["banner_items"] as? JsonArray ?: return null
           val kept = items.filterNot { (it as? JsonObject)?.get("is_ad")?.jsonPrimitive?.booleanOrNull == true }
           if (kept.size == items.size) return null
           return JsonObject(data + ("banner_items" to JsonArray(kept)))
       }
   }
   ```

   要点：**没改就返回 null**；字段名照抄样例 JSON；结构对不上（字段不存在、类型不对）也返回 null，不要抛异常。
2. **测试**：照 `ExampleRulesTest` 写至少两条：用样例 JSON 会改、干净的 JSON 不改（见第 11 节）。
3. **功能**：新建 `hook/features/.../hook/features/<分组>/BannerAdsFeature.kt`：

   ```kotlin
   object BannerAdsFeature : NetRuleFeature("cleanup.bannerAds", "去掉首页横幅广告") {
       override val switches = listOf(Settings.Cleanup.hideBanner)
       override val rules = listOf(
           JsonRule("cleanup.bannerAds", markers = BannerRules.MARKERS) { data -> BannerRules.removeAds(data) },
       )
   }
   ```

   一个功能有好几个开关时，在规则里先判断自己的开关（见 `ExampleJsonRuleFeature`）。
4. **登记**：`AllFeatures.kt` 的 `netRules` 里加 `BannerAdsFeature,`。
5. **检查**：`scripts/verify.sh`。真机上（打开“保存详细日志”）日志里出现 `rule cleanup.bannerAds applied` 说明命中了；
   没出现：markers 写错了，或者宿主用的 JSON 库没被挂上（日志里有 `net pipeline: ... missing`），交给 L2/L3。

## 5. 换成真实的宿主版本（L1）

1. 按 [10](10-environment.md) 第 6 节拉出安装包、记下 versionCode 和 versionName。
2. `core/model/.../model/HostBuild.kt`：把 `MAIN_1(Channel.MAIN, 1, "0.0.0")` 改成例如 `MAIN_9_50(Channel.MAIN, 9500803, "9.50.0")`。
   名字随便取但要唯一；以后新版本加一行，旧的最多留一个。
3. 生成符号表：`./gradlew :tools:symbolfinder:run --args="$PWD"`。
4. **检查**：输出里每个版本 `N resolved, 0 missing`；`core/symbols/src/main/resources/symbols/<versionCode>.json` 生成了。
   有 missing：看第 8 节。
5. `scripts/verify.sh`，提交（符号表、`docs/adapt/` 一起提交）。

## 6. 查启动页类名（L1）

```bash
$ANDROID_HOME/build-tools/37.0.0/aapt2 dump badging $HOST_APK_DIR/main-<versionCode>/base.apk | grep launchable-activity
```

输出里 `name='...'` 就是。填进 `core/symbols/.../symbols/example/ExampleSymbols.kt` 的 `ClassQuery(name = "...")`，
再按第 5 节第 3、4 步生成检查。启动页类名写在清单里，不会被混淆，跨版本基本不变。

## 7. 写查询：找到宿主里的类和方法（L2）

宿主的代码大多被混淆（类名、方法名是 a、b、c，每版都变）。查询用**跨版本不变的特征**描述“我要哪一个”，DexAdapt 在每个版本里找唯一符合的那个。

1. 在反编译源码里找到目标（[10](10-environment.md) 第 7 节），记下它的特征，按稳定程度排：
   1. 没混淆的类名（`com.foo.app.MainActivity`、库里的类）——最稳；
   2. 方法里用到的字符串常量（日志文字、接口路径、JSON 字段名）；
   3. 方法形状：参数类型、返回类型、是不是 static；
   4. 调用关系：它调用了哪个已知方法、被哪个已知方法调用；读写了哪个已知字段。
2. 新建 `core/symbols/.../symbols/<分组>/XxxSymbols.kt`：

   ```kotlin
   object FeedSymbols : SymbolGroup("feed") {
       // 类：含这两个字符串的类（字符串要在这个类的方法里出现）
       val feedParser = cls("feedParser", ClassQuery(strings = listOf("feed_ad_info", "parse feed failed")))

       // 方法：在上面的类里，参数 (String)、返回 List、方法里用到字符串 "items"
       val parseItems = method(
           "parseItems",
           MethodQuery(
               owner = TypeRef.Of(feedParser),
               shape = MethodShape(params = listOf(type("java.lang.String")), returns = type("java.util.List"), strings = listOf("items")),
           ),
       )
   }
   ```

   常用字段：`ClassQuery(name, packagePrefix, superclass, interfaces, strings, fieldTypes, methods)`；
   `MethodShape(name, params, returns, strings, isStatic, numbers)`；`MethodQuery(owner, packagePrefix, shape, calls, calledBy, usesFields)`。
3. 登记：`SymbolCatalog.kt` 的 `groups` 里加 `FeedSymbols,`。
4. 生成：`./gradlew :tools:symbolfinder:run --args="$PWD"`。**检查**：每个版本 `0 missing`。
   输出里的“单点依赖”提示：只靠一个条件找到的查询下个版本容易失效，能加一个条件就加。
5. 结果不唯一（找到多个）：加条件；找不到：条件写得太死（字符串在另一个方法里、参数类型写错了），对着反编译源码核对。

不要用混淆后的名字（`a.b.c`、方法名 `a`）写查询——下一版就变了。

## 8. 符号缺失怎么办（L2）

生成时某个版本报 `missing <id>: <原因>`，按顺序判断：

1. **查询写错了**：对着这个版本的反编译源码核对，改查询（第 7 节）。最常见。
2. **这个版本真的没有这个功能**（例如某个渠道没有这个入口），而且你**确认过**：在符号后面加
   `.absentIn(HostBuild.MAIN_9_50)`，并在上面写一行注释说明为什么没有。
3. **宿主没用这个库**（模板 `NetSymbols` 里的 fastjson / Gson / kotlinx 三个库，宿主只用了其中一两个）：
   对没用的那个库的全部符号加 `.absentIn(...)`，注释写“这个宿主不用 fastjson”。至少要留一个库能挂上。
4. **可有可无的符号**：功能里用 `symbols.methodOrNull(...)`，缺了少一点效果，功能照常装；同时用 `absentIn` 标注。

不确定是哪种：停下，按 [09](09-low-capability-models.md) 第 5 节写进 STATUS.md。**不要为了 0 missing 乱标 absentIn。**

## 9. 挂一个方法（L2）

照抄 `hook/features/.../example/ExampleHookFeature.kt`。

```kotlin
object FeedAdsFeature : Feature("cleanup.feedAds", "去掉信息流广告") {
    override val switches = listOf(Settings.Cleanup.feedAds)
    // 必需的符号：缺了显示“这个版本不支持”，不会装
    override val requires = listOf(FeedSymbols.feedParser, FeedSymbols.parseItems)

    override fun FeatureScope.install() {
        // 在 install 里取好要用的东西（类、方法、字段），钩子里不再查找
        hook(symbols.method(FeedSymbols.parseItems)) { call ->
            val result = call.proceed()                       // 先调原方法
            if (!settings[Settings.Cleanup.feedAds]) return@hook result   // 开关实时读
            (result as? List<*>)?.filterNot { isAd(it) } ?: result        // 改返回值
        }
    }
}
```

- `call.arg(0)` 读参数；`call.proceed(新参数1, 新参数2)` 换参数调原方法；不调 `proceed()` 就是拦掉（返回值要和原方法类型对得上，void 返回 `null`）。
- `call.thisObject` 是方法所在的对象（静态方法为 null）。
- 不要 `try { } catch (e: Throwable) { }` 包住全部：框架会记录失败、回落到原方法、连续失败自动停用。
- 登记到 `AllFeatures.list`；**检查**：`scripts/verify.sh`，真机上 App 的“功能运行情况”里这个功能是“生效”。

**钩子挂上了却从来不触发**（功能显示“生效”，效果没有），按顺序排查：
1. 宿主有好几个入口，你挂的那个没走（JSON 解析就踩过：同一个库有好几个重载）。在反编译源码里看调用方，换一个或都挂上。
2. 方法太短，被 ART 内联进调用它的地方了：在 install 里先 `deoptimize(方法)` 再挂（对调用它的方法也可以），有一点性能开销，确认是内联再用。
3. 宿主的子类重写了这个方法（系统服务常见）：挂子类的方法。
4. libxposed API 101 起不能 Hook 资源（布局、字符串），只能 Hook 代码；改界面用第 10 节的办法。

## 10. 隐藏一个界面元素（L2）

能在数据里删就删（第 4 节），删不了再改界面。宿主的控件 id 名通常不混淆，按名字找：

```kotlin
object HideShopButtonFeature : Feature("cleanup.shopButton", "隐藏商城按钮") {
    override val switches = listOf(Settings.Cleanup.hideShop)

    /** 已经处理过的控件（弱引用，页面关了自动清掉）：每个控件只加一次监听。 */
    private val done = java.util.Collections.newSetFromMap(java.util.WeakHashMap<android.view.View, Boolean>())

    override fun FeatureScope.install() {
        // 用功能自己的页面回调（不是 HostActivities.onResumed）：这里出错会记到这个功能上，安装失败时自动撤销
        onActivityResumed { activity ->
            if (!settings[Settings.Cleanup.hideShop]) return@onActivityResumed
            val id = activity.resources.getIdentifier("shop_entry", "id", activity.packageName)   // 名字从反编译的 res/layout 里查
            if (id == 0) return@onActivityResumed
            val view = activity.findViewById<android.view.View>(id) ?: return@onActivityResumed
            view.visibility = android.view.View.GONE
            // 宿主自己会把它改回来：布局变化后再藏一次（每个控件只加一次监听）
            if (done.add(view)) view.viewTreeObserver.addOnGlobalLayoutListener {
                if (settings[Settings.Cleanup.hideShop] && view.visibility != android.view.View.GONE) view.visibility = android.view.View.GONE
            }
        }
    }
}
```

- 列表里的条目（RecyclerView）会复用，改一次不够：在适配器绑定数据的方法上挂钩子（第 9 节），或者改数据。
- `getIdentifier` 有一点开销，同一个名字取一次存起来。

## 11. 写单元测试（L1）

纯函数放在 `core/model`，测试放在 `core/model/src/test/kotlin/<包>/model/<分组>/XxxTest.kt`：

```kotlin
class BannerRulesTest {
    @Test
    fun removesAds() {
        val text = """{"code":0,"data":{"banner_items":[{"id":1},{"id":2,"is_ad":true}]}}"""
        val rule = JsonRule("t", markers = BannerRules.MARKERS) { BannerRules.removeAds(it) }
        assertEquals("""{"code":0,"data":{"banner_items":[{"id":1}]}}""", JsonRewrite.rewrite(text, listOf(rule)))
    }

    @Test
    fun leavesCleanDataAlone() {
        val text = """{"code":0,"data":{"banner_items":[{"id":1}]}}"""
        assertNull(JsonRewrite.rewrite(text, listOf(JsonRule("t", markers = BannerRules.MARKERS) { BannerRules.removeAds(it) })))
    }
}
```

至少测：会改的情况、不该改的情况、结构不对（字段缺失）不崩。跑：`./gradlew :core:model:test` 或 `scripts/verify.sh --fast`。

## 12. 加一个渠道（L2）

宿主有国内版、国际版这类不同包名的版本时：

1. `HostBuild.kt`：`Channel` 里加一行，例如 `INTL("com.foo.app.intl", "国际版")`，并加它的内置版本。
2. `app/src/main/resources/META-INF/xposed/scope.list`：加一行包名。
3. `app/src/main/AndroidManifest.xml` 的 `<queries>`：加一行 `<package android:name="com.foo.app.intl" />`。
4. 安装包放 `$HOST_APK_DIR/intl-<versionCode>/base.apk`，重新生成符号表。某个渠道没有的功能用 `absentIn` 标。

## 13. 准备发布（L1，发布要维护者同意）

1. `gradle.properties`：`mod.versionCode` 加一，`mod.versionName` 按改动大小改（修问题改第三位，加功能改第二位）。
2. `CHANGELOG.md`：最上面加一节，写用户能感知的变化。
3. `scripts/verify.sh --release`（要正式签名和全部内置版本的安装包，见 [10](10-environment.md)）。
4. 真机装上看一遍（冒烟 `scripts/smoke.sh <设备>`）。
5. **问维护者**，同意后：`gh release create v<版本> --repo <仓库> -F <说明文件> app/build/outputs/apk/release/app-release.apk`。
6. 更新 `docs/STATUS.md`。

**上架 LSPosed 官方模块仓库**（modules.lsposed.org，用户能在 LSPosed 管理器里直接下载更新；公开发布，**必须维护者决定**）：
- 包名要用自己的域名，或者 `io.github.<GitHub 用户名>` 开头（模板生成时的 `--package` 照这个写就行）。
- 在 `Xposed-Modules-Repo/submission` 提 issue，标题 `[submission] <包名>`；机器人会建一个同名仓库并邀请你维护。
- 那个仓库里只放 `README.md`（完整介绍）和 `SUMMARY`（一句话），**不放源码**；仓库描述写模块名，主页写反馈地址。
- 每次发布在那个仓库建 Release：**标签 `<versionCode>-<versionName>`**（例如 `3-0.2.0`），标题是版本名，内容是更新说明，
  附上安装包。只改安装包、不重新建 Release，机器人发现不了。

## 14. 通用去广告：广告联盟 SDK、摇一摇跳转（L1）

模板自带两个**不依赖宿主版本**的功能（`hook/features/.../cleanup/adsdk/`），任何宿主都能直接用，不用写查询：

- **拦截广告联盟的广告**（`AdSdkFeature`，默认关）：穿山甲、广点通、快手、百度、Mintegral、Sigmob、TopOn 等 SDK 的
  “加载 / 展示 / 是否就绪”直接拦掉。宿主没接入任何已知 SDK 时，功能状态显示“不支持”。
  有的 App 开屏要等 SDK 回调超时才进首页（多等几秒），所以默认关；在真机上试过没问题再改成默认开。
- **摇一摇不跳转广告**（`ShakeAdFeature`，默认开）：广告 SDK 注册传感器监听时换成空监听，摇了没反应；宿主自己的摇一摇不受影响。

先看宿主接了哪些 SDK：

```bash
jadx -d /tmp/x --no-res $HOST_APK_DIR/main-<versionCode>/base.apk >/dev/null 2>&1
ls /tmp/x/sources/com/bytedance/sdk/openadsdk /tmp/x/sources/com/qq/e /tmp/x/sources/com/kwad /tmp/x/sources/com/baidu/mobads 2>/dev/null | head
```

名单里没有的 SDK：在 `core/model/.../adsdk/AdSdks.kt` 的 `PREFIXES` 加包名前缀，`ENTRY_CLASSES` 加加载 / 展示入口类
（从反编译源码里找宿主调用的那个类，类名必须没混淆），跑 `scripts/verify.sh --fast`（`AdSdksTest`）。
思路参考 [Shuoh118/Ads-kill](https://github.com/Shuoh118/Ads-kill)（MIT）。
