# 外部参考

ClearKit 的做法参考了这些资料（2026-10 查过）。链接失效时以各项目仓库为准。

## 框架和接口

- [libxposed/api](https://github.com/libxposed/api)：现代 Xposed API（模板用 102.0.0，`compileOnly`）。README 里有 R8 规则；
  模板不混淆，规则已经写在 `app/proguard-rules.pro`。
- [libxposed/example](https://github.com/libxposed/example)：官方示例模块（配置 App + 注入部分，Java / Kotlin 两套）。
- [LSPosed 开发指南：用现代 API 写模块](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)：
  `META-INF/xposed/` 下的 `java_init.list`、`module.prop`（`minApiVersion`、`targetApiVersion`、`staticScope`）、`scope.list`；
  远程偏好和远程文件；`deoptimize` 解决内联；**不能 Hook 资源**；模块 App 自己不会被 Hook。
- API 102 的变化：[libxposed/api#62](https://github.com/libxposed/api/pull/62)。目标 102 的模块不能再调旧的 `de.robv.android.xposed` 接口。
- [LSPosed 模块仓库提交说明](https://github.com/Xposed-Modules-Repo/submission)：上架流程、包名要求、Release 标签格式（见 docs/11 第 13 节）。

## 同类项目（看思路，不照搬代码）

- [Shuoh118/Ads-kill](https://github.com/Shuoh118/Ads-kill)（MIT）：通用去广告，按名字特征认广告 SDK、拦摇一摇传感器。
  模板的 `AdSdkFeature`、`ShakeAdFeature` 参考了它的思路，重新实现并加了单测。
- [XuanRanDev/XposedScaffold](https://github.com/XuanRanDev/XposedScaffold)：新旧两套 API 的模块脚手架。
- [HighCapable/YukiHook](https://github.com/HighCapable/YukiHook)：Kotlin 的 Hook 封装库（我们用自己的 Feature 框架，不依赖它）。

## 让 AI 接手

- [agents.md](https://agents.md/)：`AGENTS.md` 约定。经验：根文件短、命令在前；子目录放就近的 `AGENTS.md`；
  必须遵守的规则交给脚本和测试，文档只做说明（[Addy Osmani 的课](https://addyosmani.com/agents/15-agents-md/)、
  [Harness 的文章](https://www.harness.io/blog/the-agent-native-repo-why-agents-md-is-the-new-standard)）。
- 规格驱动开发（Spec Kit 一类）：任务按依赖排好、每个任务写明文件路径和验收标准，开工前检查规格和任务对得上
  （[DataChef 的介绍](https://blog.datachef.co/spec-driven-development-ship-features-with-ai-guardrails/)）。ClearKit 的任务卡和
  `check_tasks.py` 就是这个思路的简化版。
