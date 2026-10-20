# 【模块】— 给 AI 和人的约定

【宿主】（`com.example.host`）的 LSPosed 模块：去广告和体验增强。由 ClearKit 模板生成。**文案、文档、提交信息全用中文。**

## 命令

```bash
scripts/verify.sh --fast                          # 只跑单测（改代码过程中）
scripts/verify.sh                                 # 护栏 + 任务卡 + 单测 + lint + release 包。提交前必须通过
./gradlew :tools:symbolfinder:run --args="$PWD"   # 改了 core/symbols 或 HostBuild.kt 后重新生成符号表和访问器
scripts/smoke.sh <设备序列号>                      # 真机冒烟（手机要 root + LSPosed）
git config core.hooksPath .githooks               # 第一次：启用提交前护栏
```

## 每次工作

1. 读 `docs/STATUS.md`（做到哪了）和 `docs/TASKS.md`，领**第一张“待做”的卡**，改成“进行中”。一次只做一张。
2. 读卡里“要读”的文档（多半是 ClearKit `docs/11-cookbook.md` 的某一节）和要改的目录里的 `AGENTS.md`。
3. 只改卡里列出的文件。改完 `scripts/verify.sh`。
4. 卡改成“完成”并写“- 验证：命令和结果”；更新 STATUS.md；一张卡一个提交。

## 硬规则（护栏和测试会检查，不通过就不能提交）

- 不改框架：`hook/runtime`、`tools`、`scripts`、`gradle`、构建脚本。要改先问维护者。
- 不删测试、不加 `@Ignore`。测试失败说明代码有问题，改代码。
- 不手改生成的文件（`HostAccess.kt`、`symbols/*.json`、`docs/adapt/`），用上面的生成命令。
- 新功能必须在 `AllFeatures` 登记；新设置必须有代码读、必须画进示意图。
- 签名密钥、口令、账号令牌永远不进仓库、不进日志。不做绕过付费的功能。
- 发 GitHub Release、起名、改默认值、删功能：先问维护者。

## 卡住了

同一个错误试两次还不行、要改卡里没列的文件、不确定宿主某版本有没有某功能：**停下**，在 `docs/STATUS.md` 的
“需要维护者”写清楚想做什么、试了什么、错误原文、你的猜测，然后结束。不要猜着改。

## 在哪改什么

| 要做的事 | 去哪 |
|---|---|
| 加设置、画示意图、写纯函数规则 | `core/model/`（看那里的 AGENTS.md） |
| 找宿主里的类和方法 | `core/symbols/` |
| 写功能、登记功能 | `hook/features/` |
| App 界面（一般不用改，功能页自动生成） | `app/` |
| 宿主进程里的界面 | `hook/ui/` |
