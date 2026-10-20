# app：模块自己的 App（Compose）

- 功能页按 `Settings` 分组和 `FeatureCatalog` **自动生成**：加设置、加功能都不用改这里。
- 只有要做专门的页面（比如带列表编辑的名单页）时才改：在 `ui/pages/` 写页面，在 `ui/pages/Pages.kt` 的 `Page()` 里加路由。
- 文字都是中文，宿主名用【宿主】这类生成时替换好的名字，不写死别的 App 名。
- 截图检查：`./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*' -Pbc.shots=<输出目录>`。
