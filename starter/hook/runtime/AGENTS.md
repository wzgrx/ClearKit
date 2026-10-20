# hook/runtime：框架，不改

功能框架、熔断和崩溃保护、设置读取、符号表加载、和 App 通信。所有功能都靠它，改错了全部功能出问题。
需要改时先在 docs/STATUS.md 的“需要维护者”里写清楚原因，由维护者或强模型改（护栏会拦未经允许的改动）。
`access/HostAccess.kt` 是生成的，用 `./gradlew :tools:symbolfinder:run --args="$PWD"` 重新生成，不要手改。
