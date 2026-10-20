# <模块中文名>（<英文名>）— 开发约定

<一句话：给哪个宿主、做什么>。家族规范见 ClearKit（命名、图标、实物预览、代码分层、流程），这里只写本模块特有的。
所有文案、说明、提交信息用中文。

## 目标宿主

内置符号表的版本在 `core/model/.../HostBuild.kt`：

| 渠道 | 包名 | 主要目标 | 也内置 |
|---|---|---|---|
| 国内版 | <包名> | <版本>（<versionCode>） | <上一版> |

每个依赖私有符号的功能都必须在全部内置版本里解析成功，除非确认某版本没有该功能（`absentIn(...)` 标注并说明）。

## 模块与职责

（照 ClearKit docs/06-module-architecture.md 的目录，写本模块的实际包名）

## 写一个功能

1. 找 Hook 点：反编译源码在 `<路径>`；
2. 写查询 → `./gradlew :tools:symbolfinder:run --args="$PWD"`，每个版本 `0 missing`；
3. 写 Feature，登记 `AllFeatures`；设置画进示意图；
4. 纯函数写单测。

## 验证与提交

- 环境：`source <环境脚本>`
- `scripts/verify.sh`：单测 + lint + 签名 release 包；`--fast` 只跑单测
- 版本号在 `gradle.properties`；发 GitHub Release 前先问维护者

## 真机

测试机 `<地址>`：用之前看前台，只有桌面、宿主、本模块在前台才动手；测完还原设置。冒烟 `scripts/smoke.sh <设备>`。
