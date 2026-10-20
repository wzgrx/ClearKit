# 08 · 给 AI 的执行手册

这一篇写给**执行任务的 AI**（包括能力一般的模型）。照着做，不需要自己发明流程。
每一步写了：做什么、用什么命令、怎么判断做对了、做错了怎么办。

- 在已有模块里干活：先读那个项目的 `AGENTS.md`、`docs/STATUS.md`、`docs/TASKS.md`，**一次只做一张任务卡**。
- 具体怎么写代码：[11-cookbook.md](11-cookbook.md)（加设置、写 JSON 规则、写查询、挂方法……每个都有可照抄的代码）。
- 环境没装好：[10-environment.md](10-environment.md)。
- 维护者怎么给你拆任务、哪些事你不该做：[09-low-capability-models.md](09-low-capability-models.md)。

## 0. 总规则（每一步都适用）

1. **先读后写**：改任何文件前先读它；改模块代码前读那个仓库的 `AGENTS.md`。
2. **一次只改一件事**，改完马上验证（出图看、跑测试），再改下一件。
3. **不确定就停下来问维护者**，不要猜。必须问的事：起名、选图标意象、发 Release、改家族风格参数、删东西。
4. **不说没验证过的话**：报告里“已完成”只写真正跑过、看过结果的；没测到的写“没测到”和原因。
5. 所有文案、文档、提交信息用中文；说明写人话，不写代码名（必要时括号里附上）。
6. 不碰：签名私钥、账号令牌、`.env`；不做绕过付费的功能；会替账号操作的功能测试时不打开。

## 0.5 任务：从零开始一个新模块

**输入**：宿主名、宿主包名、模块名（已确认）、GitHub 仓库名。

1. 按 [10](10-environment.md) 第 1–4 节装好环境（JDK、SDK、DexAdapt 并排放）。
2. 生成项目（参数说明：`python3 tools/new_module.py --help`）：

   ```bash
   python3 tools/new_module.py --out ~/projects/<英文名小写> --name <模块名> --host <宿主名> \
     --host-package <宿主包名> --package io.github.<所有者>.<英文名小写> --code <英文名大驼峰> --repo <所有者>/<仓库名>
   ```

   **判断**：输出“已生成 …”，没有“错误：还有没替换的占位”。
3. `cd` 进去，`git init && git config core.hooksPath .githooks && git add -A && git commit -m "从 ClearKit 模板生成"`，然后 `scripts/verify.sh`。
   **判断**：最后一行“本地验证通过”。不通过：多半是环境（看 [10](10-environment.md)），不要改代码去凑。
4. 按新项目 `docs/TASKS.md` 的 T01、T02 做（宿主安装包、第一个接口）。
5. 图标按第 1 节做；做好以后替换模板的占位图标（`app/src/main/res/drawable-nodpi/ic_module_art.png` 等）。

**完成的标准**：新项目 `scripts/verify.sh` 通过；GitHub 上建好私有仓库并推上去（建仓库前问维护者）。

## 1. 任务：给新模块做图标

**输入**：宿主名（例如“某某视频”）、模块名（已确认）。

**步骤**

1. 读 [03-icon-design.md](03-icon-design.md) 全文。
2. 写 3 个意象组合给维护者选，格式：

   ```
   方案 A：主体 = 一个 ___（来自宿主的 ___ 昵称 / 物件），第二面 = ___（宿主的内容形态），记号 = 四角星
   方案 B：……
   方案 C：……
   ```

   **等维护者选**，不要自己定。
3. 复制模板：`cp blender/specs/template.json blender/specs/<英文名>.json`，改 `name`、`title`、`android.name`。
4. 摆构图：主体用 `egg`（参数查 [04](04-icon-pipeline.md) 第 4 节的“用 egg 做常见主体”表），第二面用 `slab`，记号用 `star`。
   复杂轮廓：写一个 SVG（只用填充路径，viewBox 宽 100 左右），用 `svg` 形状。
5. 出快速图：

   ```bash
   blender -b --python blender/build_icon.py -- blender/specs/<英文名>.json --out out/<英文名> --quick
   ```

   **判断**：输出最后一行是 `CLEARKIT_DONE`。是 `CLEARKIT_FAILED` 就看上一行的原因，查 [04](04-icon-pipeline.md) 第 7 节的表。
6. **看图**：打开 `out/<英文名>/preview.png`（如果你能看图片），检查：
   - 主体在左下、第二面在右上、记号压在主体中间；
   - 四个格子里内容都没有被圆形切掉；
   - 深色底上能看清。

   看不了图片时，至少确认 `report.txt` 里“安全区 … 通过”。
7. 调整：一次改一两个数，重复第 5、6 步。常见调整：
   - 整体偏了 → 改 `root.location`（往右偏就把 x 减小，往上偏就把 z 减小）；
   - 太小 → 零件的 `params` 尺寸整体乘 1.1，或 `android.inset` 从 0.22 降到 0.2（不要低于 0.18）；
   - 安全区超出 → 反过来缩小，或 `inset` 加到 0.24–0.26。
8. 给维护者看 `preview.png`，**确认后**出正式图（去掉 `--quick`）。
9. 接进项目（[04](04-icon-pipeline.md) 第 6 节），主题图标线稿用 `tools/svg2vector.py` 转。
10. 在模块仓库 `docs/brand/README.md` 写明：意象和含义、文件清单、怎么改图（模板见 `templates/brand-README.md`）。

**完成的标准**：`report.txt` 通过；`app/src/main/res` 里有生成的文件；`docs/brand/` 有母版、JSON、README；
构建通过（模块的 `scripts/verify.sh`）；在手机桌面上看过（如果有测试机）。

## 2. 任务：给已有模块加一个功能

1. 读模块的 `AGENTS.md`、[06-module-architecture.md](06-module-architecture.md)、[11-cookbook.md](11-cookbook.md) 对应的菜谱。
2. 在宿主的反编译源码里找 Hook 点：先搜界面上的文字（`grep -rn "界面上的字"`）、接口路径、类名关键词。
3. 判断改哪层：**能在接口数据里改的就改数据**（JSON / gRPC 规则）；改不了再 Hook 方法。
4. 写查询 → 重新生成符号表 → 确认每个内置版本 `0 missing`。
5. 写 Feature，登记到 `AllFeatures`；新设置加进 `Settings`、示意图（PagePreviews）、App 页面；重新生成路由表。
6. 能写成纯函数的写单测。
7. 跑 `scripts/verify.sh`（或模块里写的提交门禁脚本）。
8. 真机验证（[07](07-workflow.md) 阶段 7 的规矩），测完还原设置。
9. 提交：一个功能一个提交，信息写做了什么、为什么、在哪些版本上验证过。

**常见坑**（都在哔哩净界里踩过）：

- 宿主有多个 JSON 解析入口，只挂一个会漏（6.9.2：直播接口走 `parseObject(String, Type, Feature...)`，规则一直没生效）。
  加规则后**一定在真机上看规则有没有命中**（详细日志里 `rule … applied`）。
- 判断“长按”之类用调用栈时，跳过自己的栈帧（函数名里就带着关键词）。
- 系统服务可能是子类重写了方法（HyperOS 的剪贴板），只挂基类拦不到。
- 宿主切到后台时不要拉起任何页面（会把宿主带回前台）。
- 列表控件里改外观，布局会被宿主自己改回来：在布局回调里再看一眼，不要只在创建时改一次。

## 3. 任务：对照上游模块补功能

1. 克隆上游，读它的**全部**设置项和对应实现（不要只看 README 和设置名）。
2. 每一项在我们代码里搜：有 / 没有 / 我们更强 / 名字不同但是同一个东西。
3. 核对上游有没有**空开关**（设置项有、代码里没人读）：用设置键名在它的源码里搜，只出现在设置页的就是空开关。
4. 写对照表（参考 BiliClear 的 `docs/upstream/bbzq-ads-2026-10-20.md`），标出“不做”的和原因。
5. 维护者确认后逐项做（第 2 节）。

## 4. 报告怎么写

给维护者的总结按这个顺序：

1. 一句话结论（做完了没有、发了什么版本）。
2. 做了什么（用用户能感知的话，不写代码细节）。
3. 怎么验证的、结果。
4. 没做的、没测到的、有风险的（明确写出来）。
5. 需要维护者决定的事（如果有）。

不要写：没跑过的测试“通过了”；没看过的图“效果很好”；猜的原因写成确定的。

## 5. 提示词模板

把下面的内容连同本仓库一起交给执行的 AI：

```
你在做净界模块家族的工作。规范在 ClearKit 仓库（README.md、docs/、AGENTS.md）。
任务：<一句话任务>。
要求：
1. 先读 ClearKit 的 AGENTS.md 和 docs/08-ai-playbook.md 里对应的小节，按步骤做。
2. 每改一步就验证（出图 / 跑测试），不确定就停下来问我。
3. 起名、选图标意象、发 Release 这些要我确认。
4. 最后按 08 第 4 节的格式报告，没验证过的不要说完成。
```

出图标的专用版本：

```
用 ClearKit 给「<模块中文名>」（宿主：<宿主名>）做图标。
先读 docs/03-icon-design.md、docs/04-icon-pipeline.md、docs/08-ai-playbook.md 第 1 节。
第一步只给我 3 个意象方案，等我选。
```
