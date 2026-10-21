# 06 · 模块代码结构

所有净界模块用同一套分层。**新模块不要手工复制**：用 ClearKit 的模板生成（`starter/` 是从哔哩净界抽出来的通用骨架，
去掉了哔哩哔哩专用的东西，带两个示例功能，生成出来就能构建、测试全过）：

```bash
python3 tools/new_module.py --out ~/projects/foo-clear --name 某某净界 --host 某某 \
  --host-package com.foo.app --package io.github.wzgrx.fooclear --code FooClear --repo wzgrx/FooClear
```

环境准备见 [10-environment.md](10-environment.md)，常见开发任务的逐步做法见 [11-cookbook.md](11-cookbook.md)。
完整的参考实现：哔哩净界（wzgrx/BiliClear，功能最全）、红薯净界（wzgrx/ShuClear）。

## 目录

```
core/model/          纯 Kotlin，不依赖 Android：设置项、桥接协议、能写成纯函数的业务逻辑、实物预览的数据模型
core/symbols/        宿主的符号定义（每个功能一个 SymbolGroup）和生成好的内置符号表 resources/symbols/<versionCode>.json
tools/symbolfinder/  电脑上离线解析安装包、生成内置表、版本矩阵、类型化访问器
hook/runtime/        功能注册和生命周期、熔断、崩溃保护、设置读取、符号表加载与热适配、桥接客户端
hook/ui/             宿主进程里的界面（纯代码 View，不能用模块资源），配色和 App 同一套
hook/features/       功能实现：每个功能一个包，在 AllFeatures 登记；入口 hook/ModuleEntry.kt
app/                 模块自己的 App（Compose）：页面、设置存储、本机适配、诊断、备份
../dexadapt/         DexAdapt，复合构建引用源码，用的提交记在 gradle/dexadapt.commit
docs/                adapt/（版本矩阵）、brand/（图标）、upstream/（上游对照）、design/、release/
scripts/             verify.sh（门禁）、smoke.sh（真机冒烟）、signing-env.sh（签名口令，不落盘）
```

## 共享的和每个模块自己写的

| 共享（模板里已经有） | 每个模块自己写 |
|---|---|
| `hook/runtime` 全部：Feature / FeatureScope / HookCall、熔断、崩溃保护、安全模式、日志脱敏、桥接客户端、宿主页面跟踪 | `core/symbols`：宿主的类和方法 |
| `core/model` 的设置框架（SettingGroup、各种 Setting、SettingsReader）、实物预览模型（PagePreviews 的类型和函数） | 设置项本身、每页的示意图数据 |
| 网络改写管道（NetPipeline：fastjson / Gson / kotlinx 解析入口、JSON 规则模型；gRPC 在 BiliClear 里，需要时照搬） | 每个功能的规则和 Hook |
| App 外壳：页签、页面骨架、组件库、主题框架、设置窗、备份、诊断页、桥接服务端 | 强调色、首页那句统计、专属预览 |
| 门禁脚本、截图测试、冒烟脚本、发版步骤 | 支持的宿主版本 |

## 写一个功能（每个模块一样）

1. **找 Hook 点**：宿主安装包反编译（jadx）后读源码；同类上游模块作线索，不照搬。
2. **写查询**（DexAdapt）：只用跨版本稳定的特征——字符串常量、未混淆类名、方法形状（参数和返回类型）、调用关系、字段读写——
   让同一条查询在**每个内置版本**都唯一命中。确认某版本没有这个功能时用 `absentIn(...)` 标注并写明原因。
3. **重新生成**：`./gradlew :tools:symbolfinder:run --args="$PWD"`，每个版本都必须 `0 missing`；同时更新版本矩阵。
4. **写 Feature**：
   - `switches`：控制它的开关（开着任意一个就装）；`listSwitches`：多选设置；数值设置控制的重写 `isOn()`。
   - `requires`：必需的符号，缺了显示“不支持”而不是“失败”。可选符号用 `symbols.methodOrNull(...)`。
   - `install()` 里 `hook(method) { call -> ... }`。Hook 体抛异常会记为失败、回落到原方法，连续失败会熔断；**不要自己吞掉所有异常**。
   - Hook 体里实时读 `settings[...]`；热路径不做 IO、不做反射查找（在 install 时取好）。
5. **优先改数据**：广告、推荐这类优先在网络回复里改（JSON / gRPC 规则），宿主拿不到数据就不建控件；
   控件层的拦截作兜底（只在数据里真有广告时动手，记数，提示规则漏了）。
6. **纯函数 + 单测**：过滤判断、文本处理、协议解析放进 `core/model` 写测试。
7. **设置和预览**：新设置加在对应分组；在 PagePreviews 里画进示意图（覆盖测试会检查）。模板的 App 功能页按设置分组自动生成，不用加行（BiliClear 是手写页面，那边还要在页面里加行、重新生成路由表）。

## 宿主和 App 怎么通信

- 宿主 → App：`BridgeClient.call/post(method, json)`，App 的 ContentProvider 处理（`BridgeHandlers`）。
  只在宿主**真正在前台**时才允许拉起握手页（`HostActivities.foreground()`），否则会把宿主从后台拉到前台。
- App → 宿主：设置经 LSPosed 远程偏好推送；一次性的请求（“清空记录”“立即测速”）用计数令牌设置，宿主看到变化就执行一次。
- 宿主没连上 App 时，要交的数据先存在宿主自己的偏好文件里，下次连上再交（参考 BiliClear 的 `RecordsOutbox`）。

## 宿主版本

- **内置**：每个渠道（国内版、Play 版……）最新版是主要目标，上一版也内置。内置表随安装包发布。
- **其它版本**：App 在手机上用 DexKit 现场适配，同一套查询；查询写得稳，新版本就不用改代码。
- 新版本来了：先跑本机适配看缺哪些，再决定是不是加进内置。

### 宿主升级以后（2026-10 哔哩净界 6.13.0 / 红薯净界 4.1.0 起，模板已带）

宿主升级后第一次打开时，这次运行里还没有这一版的适配表。流程：

1. **先请 App 适配**：装功能之前请 App 本机适配；连上了最多等 3 秒，等到了就一次装全。
   宿主升级、被强行停止后，系统会收回“宿主能看见模块 App”的授权，这时连不上，**立刻放弃**，不白等。
2. **热适配**：App 收到宿主的请求后**直接在自己进程里解析**（不排进 WorkManager：刚被唤醒的 App 常被 JobScheduler 按额度延后几十秒）。
   新表到了：没装上的功能装上；**装上了但缺可选入口的撤下重装**（安装时 `MissRecorder` 记下缺了哪些）。
3. **说清楚结果**（`AdaptReport`）：分三档——全部生效 / 重开后全部生效 / 有功能这一版用不了（功能名 + 原因）。
   只在启动时起作用的功能（`startupOnly = true`：开屏、底栏、启动时读的配置）和依赖了晚装功能的（`dependsOn`）列为“重开后生效”。
   某渠道 / 新版本本来就没有的符号不算缺失（`channelAbsent` / `expectedAbsent`）。
4. **一键重开**（`HostRestart`）：有根权限由 App 强行停止再打开；没有时宿主自己定好重新打开再结束进程。“稍后”收成贴边小胶囊。
5. **提示只在顶部一张卡片**（`AdaptCard`）：适配进度、结果、启动提示、功能被停用的提醒排队显示；开屏、全屏横屏、小窗画中画时延后；
   从后台切回来不重复弹；升级后第一次冷启动说一次“已适配 x.y.z · 全部正常”。

写新功能时要做的只有一件：**只在宿主启动时起作用的功能，标 `override val startupOnly = true`**（见 [11](11-cookbook.md) 第 9 节）。

## 不能做的

- 绕过付费：解锁会员内容、无限试用、免费使用付费装扮。
- 会替账号操作的功能（自动点赞、投币……）可以做，但默认关、界面上标“用你的账号”，**测试时绝不打开**。
- 把账号、令牌、Cookie 写进日志或上传。
