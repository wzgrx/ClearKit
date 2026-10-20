# ClearKit · 给 AI 的约定

这个仓库是“净界”模块家族的规范和工具。你（AI）在这里或拿这里的东西去做模块时，按下面做。

## 必读

- 要做什么先查 [README.md](README.md) 的“先看哪里”，读对应的 `docs/` 篇目。
- 具体步骤、判断标准、提示词在 [docs/08-ai-playbook.md](docs/08-ai-playbook.md)。照着做，不要自己发明流程。
- 写代码的逐步做法在 [docs/11-cookbook.md](docs/11-cookbook.md)；环境在 [docs/10-environment.md](docs/10-environment.md)；
  在某个模块项目里干活时，以那个项目的 `AGENTS.md`、`docs/STATUS.md`、`docs/TASKS.md` 为准。

## 规则

1. 所有文档、文案、提交信息用中文，写人话。
2. 改文件前先读；一次改一件事，改完马上验证（出图 / 跑测试 / 看结果）。
3. 必须先问维护者的事：起名、选图标意象、改家族风格参数（`blender/crystal_kit.py` 里的材质、灯光、外发光、色彩管理）、发 GitHub Release、删东西。
4. 不说没验证过的结论；报告格式见 docs/08 第 4 节。
5. 不做绕过付费的功能；不提交签名私钥、令牌、`.env`。

## 改这个仓库本身

- 改了 `blender/` 的代码：至少用 `--quick` 重跑 `blender/specs/` 下全部 JSON，`report.txt` 都要“通过”，
  `shuclear.json` 出来的图要和 `examples/family/shuclear.png` 一致（形状、颜色、位置）。
- 改了 `tools/svg2vector.py`：用 `blender/specs/svg/` 下的 SVG 转一遍，输出要是合法 XML、坐标在画布内。
- 改了 `starter/` 或 `tools/new_module.py`：用生成脚本生成一个临时项目（`--out /tmp/...` 以外的目录要和 `dexadapt` 并排），
  在里面跑 `scripts/verify.sh`，必须通过；然后删掉临时项目。模板里不能出现任何具体宿主（哔哩哔哩、小红书……）的代码和文字，
  占位只用 `【模块】`、`【宿主】`、`com.example.host`、`io.github.clearkit.starter`、`ClearStarter`。
- `docs/11-cookbook.md` 里的代码改了：贴进生成的临时项目编译一遍。
- 文档里的命令改了要实际跑一遍。
- 新的经验（踩过的坑、调好的参数）写进对应文档，不要只留在对话里。
