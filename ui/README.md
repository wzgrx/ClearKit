# clearkit-ui：净界家族共用界面库

统一设计 v1（画布“净界统一设计 v1”）落到代码的部分。哔哩净界、红薯净界引用同一份源码，改一处两边一起变。

## 引用

ClearKit 仓库和模块仓库并排放（`~/projects/clear-kit`、`~/projects/<模块>`），在模块的 `settings.gradle.kts`：

```kotlin
include(":clearkit-ui")
project(":clearkit-ui").projectDir = file("../clear-kit/ui/compose")
```

App 模块 `implementation(project(":clearkit-ui"))`。版本跟模块自己的版本目录走，要的条目写在 `compose/build.gradle.kts` 开头。

## 内容（`io.github.wzgrx.clearkit.ui`）

| 包 | 有什么 |
|---|---|
| `theme` | `ClearColors`（中性色共用，`ClearAccent.Teal` / `Rose` / `from(种子色)` 换强调色）、`ClearType` 九档字号、`ClearHue` 八个场景色相、`Radius`、`Space`、`ClearTheme`、`Clear.colors/type` |
| `motion` | 按压缩小 + 弹回（`PressIndication`）、橡皮筋回弹、液态玻璃 `liquidGlass`、`ClearSprings` |
| `icons` | `ClearIcons`：和设计稿同一套线条图标 |
| `components` | 卡片拼接（`CardRow`、`SectionLabel`、`CloseCard`…）、各种行（`ListRow`、`NavRow`、`SwitchRow`、`PickerRow`、`ValueRow`）、`ClearSwitch`、`Badge`、`HueTile`、`Notice`、`PillButton`、`BottomActions`、`Segmented`、`FilterPill(s)`、`ClearChip`、`ClearTextField`、`ClearSheet`、加载 / 空 / 出错 |
| `shell` | `ClearShell`（四个页签 + 页面栈 + 玻璃底栏）、`PageHeader`、`PageFrame`、`Navigator`、`UnsavedGuard` |

模块只管强调色、文字转换（`ClearTheme(textTransform = …)`，哔哩净界的简繁）和自己的页面。
宿主进程里的界面（设置窗、工具盘、提示条）是纯 View，后面在 `ui/view` 里加。
