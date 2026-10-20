# core/model：设置、示意图、纯函数（不依赖 Android）

- `src/main/kotlin/.../model/settings/Settings.kt`：全部设置，按分组。键名 `分组.英文小驼峰`，发布后不改。
  去广告类默认开，其它默认关；要重开宿主才生效的加 `restart = true`。做法：ClearKit docs/11「加一个设置」。
- `.../settings/FeatureCatalog.kt`：App“功能”页的目录，一个分组一页。加分组：docs/11「加一个功能分组」。
- `.../preview/PagePreviews.kt`：每页顶上的示意图。**每个设置都要画进去**（PreviewCoverageTest 检查）。
- `.../example/ExampleRules.kt`：改 JSON 的纯函数示例；新规则照它写，放 `.../model/<分组>/`，测试放 `src/test` 同样的包。
- `.../adsdk/AdSdks.kt`：通用的广告联盟 SDK 名单；加 SDK 只改这里的名单。
- 规则函数：没改就返回 null；字段不存在、类型不对也返回 null，不抛异常。
- 测试：`./gradlew :core:model:test`。至少测“会改”“不该改”两种情况。
