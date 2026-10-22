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
| `components` | 卡片拼接（`CardRow`、`SectionLabel`、`CloseCard`…）、各种行（`ListRow`、`NavRow`、`SwitchRow`、`PickerRow`、`ValueRow`）、`ClearSwitch`、`Badge`、`HueTile`、`Notice`、`PillButton`、`BottomActions`、`Segmented`、`FilterPill(s)`、`ClearChip`、`ClearTextField`、`ClearSheet`、加载 / 空 / 出错；入口页用的 `IconTile`（`NavRow` / `SwitchRow` 的 `icon`）、`EntryCard`、`CountChip`；首页用的 `BlockHeading`、`HeroCard` / `HeroNumber` / `HeroChips`、`TileGrid` + `ToggleTile`、`QuickGrid`、`UndoRow`；媒体用的 `ProgressCard`、`CoverCard` + `MasonryColumns`、`ThumbRow` + `ThumbBox` |
| `shell` | `ClearShell`（四个页签 + 页面栈 + 玻璃底栏）、`PageStack`（页面栈本体，自己有外壳的模块直接用）、`PageMotion`、`CardOrigin` / `cardOrigin`、`PageHeader`、`PageFrame`、`Navigator`、`UnsavedGuard`、`LocalPageRoute` / `LocalPageDepth` |

## 页面栈（`PageStack`）

照 HyperOS 系统的 Activity 转场做（小米窗口管理 `AppTransitionInjector` 的参数，K90 上和系统设置的录屏逐帧对过）：

- 二级页不透明、从右边整页滑进来；下面一层左移 1/4 宽、盖 0 → 0.5 的黑色遮罩；页面本身不做透明度变化（没有“消失再浮现”的空档，也不离屏合成）。
- 程序化进出 500ms，物理曲线 `PageMotion.Easing`（response 0.8、damping 0.95）；中途改方向、手势松手用临界阻尼弹簧接着当前速度走。
- 系统返回手势（预测式返回）1:1 跟手；页面有离开确认（`UnsavedGuard`）时不跟手，先问。
- 新页先在屏幕外组合、排好、画好，下一帧才开始滑：第一帧慢只是晚一点出发，不会半路卡。页面里不要再分帧放内容（会在滑进来时看到一块块冒出来）。
- 栈里的页都留在组合里（返回不重建，滚动位置都在），被盖住并且停稳后不摆放（不画、不接触摸）；离栈顶超过 4 层才放掉组合。
- 每页有自己的生命周期（停稳的栈顶 RESUMED，其余 STARTED）和 ViewModel 存储（退栈时清掉），同 Jetpack Navigation 的每个目的地。
- 页面要知道自己是哪页用 `LocalPageRoute` / `LocalPageDepth`，别用 `Navigator.top`。
- 从卡片打开的详情：卡片加 `Modifier.cardOrigin(origin)`，打开时 `nav.open(route, origin.rect)`，新页从卡片展开、返回时缩回去。

模块只管强调色、文字转换（`ClearTheme(textTransform = …)`，哔哩净界的简繁）和自己的页面。
宿主进程里的界面（设置窗、工具盘、提示条）是纯 View，后面在 `ui/view` 里加。

## 宿主端（View）：`ui/view`

跑在宿主进程里的界面（设置抽屉、工具盘、提示）不能用 Compose 和模块资源，用这个小库拿同一套规范 v1 的颜色和尺寸：

```kotlin
// settings.gradle.kts
include(":clearkit-view")
project(":clearkit-view").projectDir = file("../clear-kit/ui/view")
// hook/ui/build.gradle.kts
implementation(project(":clearkit-view"))
```

- `clearViewColors(ViewAccent.Teal / Rose, dark)`：ARGB 整数的中性色和强调色（和 App 端 `clearColors` 同值）。
- `ViewRadius`、`ViewType`：圆角（dp）和字号（sp）。

