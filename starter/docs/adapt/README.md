# 多版本适配

- 内置版本：`core/model/.../HostBuild.kt`。电脑上用 `./gradlew :tools:symbolfinder:run --args="$PWD"` 对着安装包生成符号表
  （`core/symbols/src/main/resources/symbols/<versionCode>.json`）和这里的 `matrix.md`、`matrix.html`、`catalog.yaml`。
- 其它版本：模块 App 在手机上用 DexKit 按同样的查询现场生成（“本机适配”），不用发新版。
- 查询写法和“缺符号怎么办”：ClearKit docs/11-cookbook.md。
