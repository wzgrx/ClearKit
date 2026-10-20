# 09 · 让能力一般的模型也能接手

这一篇写给**维护者和强模型**：怎么把工作拆好、怎么设护栏，让 DeepSeek 这类能力一般、上下文短、容易“自信地改错”的模型
也能安全地接手开发。执行任务的模型读 [08-ai-playbook.md](08-ai-playbook.md) 和项目里的 `AGENTS.md` 就够了。

## 1. 弱模型常犯的错，和我们怎么挡

| 常见错误 | 后果 | 怎么挡（已经做在模板里） |
|---|---|---|
| 写了功能忘了登记 | 功能不运行，还以为做完了 | `AllFeaturesTest`：源码里有 `object X : Feature(...)` 却不在 `AllFeatures` 就失败，信息里写怎么改 |
| 加了设置没人读（空开关） | 用户打开了什么都不发生 | `AllFeaturesTest`：功能页里的设置在 `hook/features` 源码里搜不到就失败 |
| 加了设置忘了画示意图 | 实物预览缺块 | `PreviewCoverageTest`、`PagePreviewsTest.everyFeaturePageHasADiagram` |
| 改了符号没重新生成 | 编译用旧访问器、手机上找不到 | `SymbolTablesTest`（访问器过期就失败）、`SymbolCatalogTest` |
| 手改生成的文件 | 下次生成被覆盖 | `scripts/guard.sh`：生成文件改了、符号定义没改就失败 |
| 改了框架代码（runtime） | 全部功能出问题 | `scripts/guard.sh`：`hook/runtime`、`tools`、`scripts`、构建脚本有改动就失败（维护者用 `GUARD_ALLOW=1` 放行） |
| 为了让测试过去改测试 | 问题被藏起来 | `scripts/guard.sh`：`@Test` 数量变少、新加 `@Ignore` / `@Disabled` / `assumeTrue(false)` 就失败 |
| 提交签名密钥、口令文件 | 密钥泄露 | `scripts/guard.sh`：`.jks`、`.keystore`、`signing.env` 这类文件进暂存区就失败 |
| 任务卡写得缺项 | 下一个模型不知道改哪、怎么算完成 | `scripts/check_tasks.py`：缺级别、要读、改的文件、完成标准，或同时两张“进行中”就失败 |
| 一次改太多 | 出错找不到原因 | 一张任务卡只做一件事；做完必须跑 `scripts/verify.sh` 再提交 |
| 没验证就说“完成了” | 维护者被误导 | 报告格式（08 第 4 节）要求贴命令和结果；STATUS.md 记“上次验证” |
| 卡住后乱试 | 越改越坏 | 试两次不行就停，写进 STATUS.md“需要维护者” |
| 不知道项目做到哪了 | 重复做、做错方向 | 每次先读 `docs/STATUS.md` 和 `docs/TASKS.md` |

原则：**能用测试和脚本挡的错误就不靠文档提醒**（文档对模型只是建议，脚本才是硬约束）；失败信息直接写“该怎么改”，弱模型照着改就行。
`scripts/guard.sh` 和 `check_tasks.py` 在 `scripts/verify.sh` 一开头就跑，也装成提交前钩子（`git config core.hooksPath .githooks`），
不跑验证直接提交也拦得住。

文档也按“离代码近、短、命令在前”写：根目录 `AGENTS.md` 只放命令、每次工作的流程和硬规则；`core/model`、`core/symbols`、
`hook/features`、`hook/runtime`、`app` 各有一份几行长的 `AGENTS.md`，说这个目录里怎么做、什么不能碰（模型改哪个目录就读那里的）。
`CLAUDE.md` 只有一行 `@AGENTS.md`，Claude Code 也读同一份。

## 2. 能力分级：什么活给谁

| 级别 | 谁 | 能做 | 不让做 |
|---|---|---|---|
| L1 | 能力一般的模型（DeepSeek 等）、新手 | 按菜谱（[11-cookbook.md](11-cookbook.md)）加设置、画示意图、写 JSON 规则（给了样例 JSON）、改文案、跑脚本、更新文档 | 写 DexAdapt 查询、挂方法、改框架、发版 |
| L2 | 中等模型、熟悉的人 | L1 + 按菜谱写查询、挂方法、隐藏界面元素、处理符号缺失、读反编译源码找 Hook 点 | 改 `hook/runtime`、`tools/`、DexAdapt；发版 |
| L3 | 强模型、维护者 | 全部：架构、框架、DexAdapt、多版本适配、真机排查、发版（发版仍要维护者同意） | — |

每张任务卡标级别。L1 模型只领 L1 卡；遇到需要更高级别的步骤，按“卡住了”停下。

## 3. 任务卡怎么写（维护者 / 强模型写，弱模型执行）

写在项目的 `docs/TASKS.md`。一张卡要能在**一次工作**里做完，**改的文件不超过 5 个**。格式：

```markdown
## T07 隐藏首页右上角的“商城”入口 —— 待做

- 级别：L1
- 要读：ClearKit docs/11-cookbook.md「加一个设置」「写一条 JSON 规则」
- 样例数据：docs/samples/home-top.json（已去掉个人信息）
- 改的文件：Settings.kt（Cleanup 组）、PagePreviews.kt（cleanup 页）、core/model/.../cleanup/TopEntryRules.kt（新）、
  对应测试（新）、hook/features/.../cleanup/TopEntryFeature.kt（新）、AllFeatures.kt
- 步骤：
  1. 在 Settings.Cleanup 加多选 `topEntriesHide`，选项 shop=商城、game=游戏中心；
  2. 写纯函数 `TopEntryRules.remove(data, hidden)`，用样例数据写测试；
  3. 写 NetRuleFeature，markers 用 `"top_entries"`，登记到 AllFeatures.netRules；
  4. 在 cleanup 页示意图的“顶部入口”组里绑定这个多选。
- 完成标准：`scripts/verify.sh` 通过；新测试至少 2 条（有命中、没命中）。
- 不许：改 hook/runtime、改已有测试。
```

要点：
- **给样例数据**：弱模型不会自己抓包，强模型或维护者先把接口 JSON 存进 `docs/samples/`（去掉账号、令牌、手机号）。
- **点名文件**：写全路径或唯一的文件名，不让它自己找。
- **步骤可以机械执行**：每步一个动作，用项目里已有的名字。
- **完成标准是命令**：能跑、能看结果，不写“效果良好”这种判断不了的话。
- 需要找 Hook 点、写查询的活拆成两张：L2/L3 先找好并写进卡里（类名、方法签名、反编译源码位置），L1 再做剩下的。

## 4. 交接：STATUS.md 和 TASKS.md

两个文件就是项目的“记忆”，换模型、换人、隔几天再做都靠它们：

- `docs/STATUS.md`：**覆盖写**，只写现在：版本、能用的、上次验证（命令和结果）、下一步、需要维护者的事。
- `docs/TASKS.md`：任务卡列表，状态 待做 / 进行中 / 完成 / 卡住。完成的卡写验证结果，不删（留着当记录）。

每次工作的固定流程（写在模板的 `AGENTS.md` 第 0 节）：

1. 读 STATUS.md → 读 TASKS.md → 领第一张“待做”卡，改成“进行中”；
2. 读卡要求的文档，照着做；
3. `scripts/verify.sh`；
4. 卡改成“完成”并写验证结果；更新 STATUS.md；
5. 一张卡一个提交。

## 5. 卡住了：升级规则

出现下面任一情况，**停止修改**，在 STATUS.md 的“需要维护者”写清楚后结束：

- 同一个错误改了两次还在；
- 需要改“不改”的目录（`hook/runtime`、`tools/`、`../dexadapt`）或卡里没列的文件；
- 符号在某个版本找不到，且不确定这个版本是不是真没有这个功能；
- 需要用户决定的事：起名、图标意象、发版、删功能、改默认值；
- 任务卡写得不清楚、和代码对不上。

写法（让下一个人不用重来一遍）：

```markdown
## 需要维护者
- T07 第 3 步：规则写好了，但单测里 JSON 解析报错。
  - 试过：1) markers 改成 "top_entries"；2) 把 data 当数组处理
  - 错误原文：<贴最后 20 行>
  - 猜测：样例 JSON 外面不是 {code, data} 包装
```

## 6. 给弱模型的提示词

```
你在开发「<模块名>」，一个 LSPosed 模块。规则在仓库的 AGENTS.md，必须先读它。
这次只做 docs/TASKS.md 里第一张“待做”的卡，不做别的。
1. 先读 docs/STATUS.md、docs/TASKS.md、卡里写的“要读”的文档。
2. 只改卡里列出的文件。不许改测试让它通过。
3. 每改完一步跑 scripts/verify.sh --fast；全部做完跑 scripts/verify.sh。
4. 同一个错误试两次还不行就停下，按 AGENTS.md 第 6 节写进 STATUS.md。
5. 结束前更新 TASKS.md 和 STATUS.md，按 ClearKit docs/08 第 4 节的格式报告。没跑过的不要说通过。
```

## 7. 维护者审查弱模型的提交

- `git diff --stat`：改的文件是不是卡里列的那些；有没有动 `src/test`、`hook/runtime`、生成文件。
- 新设置：键名是不是 `分组.英文小驼峰`、默认值是不是保守（去广告默认开，其它默认关）。
- 新规则：markers 是不是够独特（只写一个很常见的字段名会误伤别的接口）。
- 看 STATUS.md 里写的验证结果是不是真的（自己再跑一次 `scripts/verify.sh`）。
