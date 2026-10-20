# core/symbols：宿主里的类和方法（DexAdapt 查询）

- 一个功能一个文件 `src/main/kotlin/.../symbols/<分组>/XxxSymbols.kt`，写完在 `SymbolCatalog.groups` 登记。
- 查询只用跨版本不变的特征：没混淆的类名、字符串常量、方法形状、调用关系。**不要写混淆后的名字（a、b、c）。**
- 改完运行 `./gradlew :tools:symbolfinder:run --args="$PWD"`，每个内置版本都要 `0 missing`。
- 某版本找不到：先核对查询；确认那个版本真没有这个功能才加 `.absentIn(HostBuild.X)` 并写注释。不确定就停下问。
- `src/main/resources/symbols/` 是生成的，不要手改。
- 做法：ClearKit docs/11「写查询」「符号缺失怎么办」。级别 L2。
