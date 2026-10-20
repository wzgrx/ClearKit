# hook/features：功能实现（运行在宿主进程里）

- 每个功能一个文件，放 `src/main/kotlin/.../hook/features/<分组>/`，**写完在 `AllFeatures.kt` 登记**
  （改接口数据的进 `netRules`，其它进 `list`）。AllFeaturesTest 会查漏登记和空开关。
- 照抄示例：改接口 JSON → `example/ExampleJsonRuleFeature.kt`；挂方法 → `example/ExampleHookFeature.kt`；
  通用去广告 → `cleanup/adsdk/`。
- `switches` 写控制它的开关，`listSwitches` 写多选；钩子里实时读 `settings[...]`。
- 不要 catch 住所有异常（框架会记失败、回落原方法、连续失败自动停用）；热路径里不读写文件、不做反射查找。
- 宿主在后台时不要打开任何页面。
- `net/NetPipeline.kt`、`ModuleEntry.kt` 不改。
