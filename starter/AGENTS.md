# 【模块】— 开发约定（给 AI 和人看）

【模块】是【宿主】（`com.example.host`）的 LSPosed 模块：去广告和体验增强。项目由 ClearKit 的模板生成，
家族规范（命名、图标、实物预览、代码分层、流程）在 [ClearKit](https://github.com/wzgrx/ClearKit)。
**所有文案、文档、提交信息用中文。**

## 0. 每次开始工作先做这三件事

1. 读 [docs/STATUS.md](docs/STATUS.md)：现在做到哪了、上次验证结果、下一步。
2. 读 [docs/TASKS.md](docs/TASKS.md)：挑**第一张状态是“待做”**的任务卡，只做这一张。
3. 读这张卡要求读的 ClearKit 文档（多数是 `docs/11-cookbook.md` 的某一节），照着做。

做完：跑 `scripts/verify.sh` → 更新 TASKS.md（这张卡改成“完成”，写验证结果）和 STATUS.md → 提交。
做不下去：按第 6 节“卡住了怎么办”停下来，不要硬改。

## 1. 目录和每个文件管什么

| 位置 | 管什么 | 平时会改吗 |
|---|---|---|
| `core/model/.../settings/Settings.kt` | 全部设置（开关、多选、数值……），按分组 | **常改**：加设置 |
| `core/model/.../settings/FeatureCatalog.kt` | App“功能”页的目录：一个分组一页 | 加新分组时改 |
| `core/model/.../preview/PagePreviews.kt` | 每页顶上的示意图（实物预览） | **常改**：设置要画进去 |
| `core/model/.../example/ExampleRules.kt` | 示例：改 JSON 的纯函数（有单测） | 照着写新规则 |
| `core/model/.../HostBuild.kt` | 宿主包名、内置适配的版本 | 换宿主版本时改 |
| `core/symbols/.../XxxSymbols.kt` | 用 DexAdapt 描述“宿主里的哪个类、哪个方法” | 写需要挂钩子的功能时加 |
| `core/symbols/.../SymbolCatalog.kt` | 登记全部符号组 | 加了符号组就登记 |
| `hook/features/.../AllFeatures.kt` | 登记全部功能 | **每写一个功能都要登记** |
| `hook/features/.../example/` | 两个示例功能：改接口数据、挂一个方法 | 照着写新功能 |
| `hook/features/.../net/NetPipeline.kt` | 接口数据改写管线 | 不改 |
| `hook/runtime/` | 功能框架、和 App 通信、崩溃保护 | **不改**（要改先问维护者） |
| `hook/ui/` | 宿主进程里的界面（提示、设置窗） | 很少改 |
| `app/` | 模块 App（Compose）。功能页按设置自动生成 | 很少改 |
| `tools/symbolfinder/` | 电脑上生成符号表、访问器 | 不改 |

**生成的文件不要手改**（改了也会被覆盖）：`hook/runtime/.../access/HostAccess.kt`、
`core/symbols/src/main/resources/symbols/*.json`、`docs/adapt/*`。它们由
`./gradlew :tools:symbolfinder:run --args="$PWD"` 生成。

## 2. 写一个功能（详细步骤见 ClearKit docs/11-cookbook.md）

1. **能改接口数据就改数据**（最稳、最不容易出问题）：抓到接口返回的 JSON，写一个纯函数规则 + 单测，
   再写一个 `NetRuleFeature` 登记到 `AllFeatures.netRules`。照抄 `ExampleJsonRuleFeature` 和 `ExampleRules`。
2. 改不了数据再挂方法：在 `core/symbols` 写查询（只用跨版本稳定的特征：字符串常量、没混淆的类名、方法形状、调用关系），
   登记到 `SymbolCatalog.groups`，重新生成，再写 `Feature` 登记到 `AllFeatures.list`。照抄 `ExampleHookFeature`。
3. 新设置加在 `Settings` 对应分组里；画进 `PagePreviews` 对应页面的示意图。**App 页面不用改**，按分组自动生成。
4. 能写成纯函数的逻辑放 `core/model` 并写单测。

规矩：
- Hook 体里抛异常会记为失败并回落到原方法，连续失败会熔断；**不要自己 catch 掉所有异常**。
- 热路径（每次调用都走的钩子）里不做文件读写、不做反射查找（在 install 里取好）。
- 设置在钩子里实时读 `settings[...]`，用户改了不用重开。
- 【宿主】在后台时不要打开任何页面（会把它拉回前台）。
- 不做绕过付费的功能；会替账号操作的功能（自动点赞、投币……）测试时不打开。

## 3. 验证

```bash
scripts/verify.sh --fast   # 只跑单测，改代码过程中用
scripts/verify.sh          # 单测 + lint + release 包。每次提交前必须通过
scripts/verify.sh --release  # 发布前：要求有全部内置版本的安装包、正式签名
```

自动检查会抓住这些常见错误（失败信息会说怎么改）：
- 写了功能没在 `AllFeatures` 登记（`AllFeaturesTest`）；
- 加了设置没有代码读它，成了“空开关”（`AllFeaturesTest`）；
- 加了设置没画进示意图（`PreviewCoverageTest`）；功能页没有示意图（`PagePreviewsTest`）；
- 符号定义改了没重新生成访问器（`SymbolTablesTest`）。

**不许为了让测试通过去改测试、删测试、加 `@Ignore`。** 测试失败说明代码有问题，改代码。

## 4. 真机

用之前看前台：`adb -s <设备> shell dumpsys window | grep mCurrentFocus`。只有桌面、【宿主】、【模块】在前台才动手；
别的应用在前台说明有人在用，停下来问。测试改过的设置测完改回去。冒烟：`scripts/smoke.sh <设备>`。

## 5. 提交和发布

- 在 `main` 上开发，一个功能一个提交，信息写：做了什么、为什么、怎么验证的。
- 版本号在 `gradle.properties`（`mod.versionCode` 只增不减）；每次发布在 `CHANGELOG.md` 记一节。
- **发 GitHub Release 前先问维护者。** 签名密钥、口令、账号令牌永远不进仓库、不进日志。

## 6. 卡住了怎么办

同一个问题试了两次还不行，或者需要改“不改”的目录（`hook/runtime`、`tools`、`../dexadapt`），或者任务卡没写清楚：
**停下来**，在 `docs/STATUS.md` 的“需要维护者”里写清楚：想做什么、试了什么、错误信息原文、你的猜测，然后结束这次工作。
不要猜着改、不要扩大改动范围。
