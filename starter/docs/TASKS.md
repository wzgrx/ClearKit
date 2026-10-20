# 任务卡

> 一张卡 = 一个能在一次工作里做完、能验证的小任务。按顺序做，**一次只做一张**。
> 写卡和拆卡的方法见 ClearKit docs/09-low-capability-models.md。
> 状态：待做 / 进行中 / 完成 / 卡住（卡住时在 STATUS.md 写原因）。

## T01 准备宿主安装包和内置版本 —— 待做

- 级别：L1
- 要读：ClearKit docs/10-environment.md「收集安装包」、docs/11-cookbook.md「换成真实的宿主版本」
- 改的文件：`core/model/.../HostBuild.kt`、`core/symbols/.../example/ExampleSymbols.kt`
- 步骤：
  1. 从手机拉出【宿主】安装包，放到 `$HOST_APK_DIR/main-<versionCode>/base.apk`；
  2. 把 `HostBuild.MAIN_1` 换成真实的版本号和版本名；
  3. 查启动页类名，填进 `ExampleSymbols.launchActivity`；
  4. 运行 `./gradlew :tools:symbolfinder:run --args="$PWD"`，看到 `0 missing`（库没用到的 NetSymbols 按 cookbook 标 absentIn）。
- 完成标准：`scripts/verify.sh` 通过；`core/symbols/src/main/resources/symbols/<versionCode>.json` 生成了。
- 不许：改 `hook/runtime`、改测试。

## T02 抓第一个要改的接口 —— 待做

- 级别：L2
- 要读：ClearKit docs/11-cookbook.md「看宿主的接口数据」「写一条 JSON 规则」
- 改的文件：`core/model/.../`（新规则文件和测试）、`hook/features/.../`（新功能）、`Settings.kt`、`PagePreviews.kt`、`AllFeatures.kt`
- 完成标准：单测里用抓到的真实 JSON（去掉个人信息）；`scripts/verify.sh` 通过；真机日志里看到 `rule … applied`。

（后面的卡由维护者或强模型按 docs/09 的格式写。）
