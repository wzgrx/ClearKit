---
name: clear-icon
description: 用 ClearKit 给净界家族模块做水晶玻璃风格图标——选意象、写图标 JSON、Blender 一条命令出母版、桌面预览和全套 Android 资源、主题图标线稿转矢量图。做新模块图标、改图标、检查图标是否符合家族风格时使用。
---

# 净界图标（ClearKit）

完整说明在 ClearKit 仓库：`docs/03-icon-design.md`（画什么）、`docs/04-icon-pipeline.md`（怎么出图）、`docs/08-ai-playbook.md` 第 1 节（逐步做法）。

## 步骤

1. 给维护者 3 个意象方案（主体 = 宿主昵称实物 / 标志物件，第二面 = 内容形态，记号 = 四角星等乳白形状），**等选定**。
2. `cp blender/specs/template.json blender/specs/<名字>.json`，改 name / title / android.name。
3. 主体 `egg`、第二面 `slab`、记号 `star`；复杂轮廓画 SVG 用 `svg`。坐标 X 右、Z 上、Y 越小越靠前。
4. `blender -b --python blender/build_icon.py -- blender/specs/<名字>.json --out out/<名字> --quick`，最后一行要是 `CLEARKIT_DONE`。
5. 看 `out/<名字>/preview.png` 四格、`report.txt` 安全区“通过”；一次改一两个数再出图。
6. 维护者确认后去掉 `--quick` 出正式图；`android/res/*` 复制进项目；母版、JSON、README 放模块的 `docs/brand/`。
7. 主题图标线稿：`python3 tools/svg2vector.py <线稿>.svg -o app/src/main/res/drawable/ic_launcher_monochrome.xml`，JSON 里 `android.monochrome: true`。

## 不要

- 改 `crystal_kit.py` 里的风格参数（Standard 色彩、玻璃参数、灯光、外发光）——会破坏家族一致性，要改先问维护者。
- 用宿主的 logo、字标；在图标里写字；放两个以上记号。
- 没看过 preview 就说图标做好了。
