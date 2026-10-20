# 04 · 图标流水线（怎么出图）

一份 JSON 描述图标，一条命令出全部资源。风格参数（材质、灯光、外发光、色彩管理）写死在 `blender/crystal_kit.py` 里，
保证家族一致；你只改 JSON 里的**形状、位置、颜色**。

## 1. 准备环境

只需要 Blender（4.2 以上，推荐 5.2）。Blender 自带 Python 和 numpy，不用装别的。

| 系统 | 命令里的 `blender` 换成 |
|---|---|
| Linux / WSL | 解压版里的 `blender`，例如 `~/tools/blender-5.2.2-linux-x64/blender` |
| Windows | `"D:\Soft\Blender 5.2\blender.exe"`（在 PowerShell / cmd 里跑，路径用 Windows 写法） |
| macOS | `/Applications/Blender.app/Contents/MacOS/Blender` |

- `-b` 是无界面运行，WSL 里没有图形环境也能渲染（会打印几行 `EGL Error`，可以忽略）。
- 有 NVIDIA 显卡会自动用 GPU（OPTIX 或 CUDA），没有就用 CPU。参考：RTX 5090 笔记本 + CUDA，正式图约 10 秒、快速图几秒；CPU 正式图要几分钟到十几分钟。
- `report.txt` 第二行写着实际用的设备和耗时。

## 2. 命令

```bash
# 快速：512 像素、64 采样，调形状颜色时用
blender -b --python blender/build_icon.py -- blender/specs/<名字>.json --out out/<名字> --quick

# 正式：1024 像素、512 采样
blender -b --python blender/build_icon.py -- blender/specs/<名字>.json --out out/<名字>

# 想在 Blender 里手动微调：加 --save-blend，打开 out/<名字>/scene.blend
```

其它参数：`--samples 256`（自定采样数）、`--device CPU`（强制用 CPU）。

成功时最后一行是 `CLEARKIT_DONE`；失败时打印 `CLEARKIT_FAILED` 和出错原因。

## 3. 输出

| 文件 | 用在哪 |
|---|---|
| `master.png` | 母版（透明背景，含外发光）。放进模块仓库的 `docs/brand/<名字>-master.png` |
| `preview.png` | 桌面效果：浅色圆形、浅色圆角方、深色圆形、深色圆角方。评审看这张 |
| `android/res/drawable-nodpi/ic_<名字>_art.png` | 前景图（按内容外框裁成正方形，512×512） |
| `android/res/drawable/ic_launcher_foreground.xml` | 自适应图标前景：四边内缩 22% |
| `android/res/mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | 自适应图标（底色 + 前景 + 主题图标） |
| `android/res/values/ic_launcher_colors.xml`、`values-night/…` | 底色：浅色 `#F5F2EF`、深色 `#303030` |
| `android/res/mipmap-*dpi/ic_launcher.png` | 安卓 8 以前用的旧版图标（minSdk ≥ 26 可以不要） |
| `store/icon-512.png` | 应用商店、GitHub 社交预览、Release 页用（不透明） |
| `host/brand_mark_192.png` | 宿主进程里画模块图标（见第 7 节） |
| `report.txt` | 检查结果、设备、耗时、文件清单 |

## 4. JSON 字段说明

先看能跑的例子：`blender/specs/shuclear.json`（红薯净界定稿）、`template.json`（最简起步）、`svg_demo.json`（SVG 形状）、`guo_trial.json`（果子、弯管、播放键）。

### 顶层

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | 是 | 英文小写，用在文件名里 |
| `title` | 否 | 中文名，写进生成的 XML 注释 |
| `render` | 否 | `size`（默认 1024）、`samples`（默认 512）、`device`（默认 auto）。一般不改 |
| `root.location` | 否 | 整体平移 `[x, y, z]`，用来居中 |
| `materials` | 是 | 材质表，键是材质名 |
| `parts` | 是 | 零件列表，按顺序建 |
| `android` | 否 | `name`（资源名，默认同 `name`）、`inset`（默认 0.22）、`bg_light`、`bg_dark`、`monochrome`（true 时自适应图标里引用 `@drawable/ic_launcher_monochrome`，要自己放线稿） |

`_说明` 这类以下划线开头的字段会被忽略，可以用来写注释（JSON 本身不支持注释）。

### 材质

```json
"materials": {
  "body":   { "type": "crystal", "color": "#FF7A2E", "gradient": ["#FF2D6F", "#FF9A3D"], "glow": 0.6, "transmission": 0.6, "roughness": 0.1, "rim": "#FFE0C2", "rim_glow": 0.45 },
  "second": { "type": "crystal", "color": "#4FA8FF", "gradient": ["#3E8BFF", "#A8E2FF"], "glow": 0.35, "transmission": 0.8, "roughness": 0.12, "rim": "#E2F4FF", "rim_glow": 0.32 },
  "milk":   { "type": "milk" }
}
```

| 字段 | 说明 | 家族里的取值 |
|---|---|---|
| `type` | `crystal`（彩色玻璃）或 `milk`（乳白记号） | |
| `color` | 基础色（有渐变时只影响边缘光的底色） | |
| `gradient` | 两色渐变 `[左, 右]`，沿零件自己的 X 方向 | 主体暖色、第二面蓝色 |
| `glow` | 自发光强度 | 主体 0.4–0.6，第二面 0.3–0.45 |
| `transmission` | 透光（0 不透明，1 全透） | 主体 0.5–0.65，第二面 0.75–0.85 |
| `roughness` | 粗糙度，越小越亮 | 0.1–0.14 |
| `rim` | 边缘提亮的颜色，取渐变浅端再提亮 | 每个玻璃材质都配 |
| `rim_glow` | 边缘光底数（不写时等于 `glow`） | |
| `rim_k` | 边缘光强度系数，默认 3；嫌边太亮改成 2.2 | |

### 零件

```json
{ "id": "card", "shape": "slab", "material": "second", "parent": null,
  "location": [1.15, 1.25, 0.75], "rotation_deg": [0, -10, 0], "scale": [1, 1, 1],
  "params": { "w": 2.6, "h": 3.3, "r": 0.42, "depth": 0.18 } }
```

| 字段 | 说明 |
|---|---|
| `id` | 零件名，其它零件的 `parent` 用它 |
| `shape` | 形状，见下表 |
| `material` | 材质名，必须在 `materials` 里 |
| `parent` | 跟着哪个零件走（例如卡片上的文字行跟着卡片）；不写就挂在根上 |
| `location` | 位置；有 `parent` 时是相对父零件 |
| `rotation_deg` | 旋转（度），一般只转 Y：正值逆时针、负值顺时针 |
| `scale` | 缩放 |

坐标：**X 向右，Z 向上，Y 越小越靠前**。主体 y≈0，第二面 y≈+1.2（往后），记号 y≈-1.1（压在主体前面）。
画面大约能放下 x、z 各 ±3 的范围。

### 形状

| shape | 用来做 | params |
|---|---|---|
| `egg` | 主体：红薯、果子、豆子、水滴、气泡 | `sx` `sy` `sz` 三个方向尺寸；`taper` 两头收细（0–0.5）；`taper_lin` 一头更细；`bumps` 表面起伏 `[[幅度, 频率, 相位], …]`；`lift` 中间拱起；`dent_top {depth, radius}` 顶部凹窝；`squash` 越往上越窄；`point` 底部变尖；`segments [u, v]`；`subsurf` 细分级数（1–2 更圆滑） |
| `slab` | 卡片、屏幕、文字行、按钮 | `w` 宽、`h` 高、`r` 圆角、`depth` 厚度、`bevel` 边缘倒角、`seg` 圆角分段 |
| `star` | 闪光记号 | `points`（角数，默认 4）、`outer` 外半径、`inner` 内半径、`depth`、`bevel` |
| `polygon` | 播放键、箭头、对勾 | `points [[x, z], …]`（XZ 平面上的轮廓）、`depth`、`bevel` |
| `leaf` | 叶子、花瓣 | `curl` 上翘程度、`thickness` 厚度；大小用零件的 `scale` |
| `cylinder` | 茎、柄 | `radius`、`depth`（长度，沿 Z） |
| `tube` | 弯的茎、天线、线 | `points [[x, z], …]`（自动圆滑）、`radius` |
| `svg` | 任何别的轮廓（小电视、书、胶片） | `file`（相对 JSON 的路径）、`width`（缩放到多宽）、`depth`、`bevel` |

SVG 要求：只用填充的路径（`<path>`、`<rect>`、`<circle>` 都行，描边会被忽略），一个文件里放一个物体的轮廓；
多个分开的形状会合成一个零件。见 `blender/specs/svg/tv.svg`。

### 用 egg 做常见主体

| 想要 | 参数 |
|---|---|
| 红薯（长、两头尖、表面不平） | `sx 1.8, sy 0.92, sz 0.92, taper 0.32, taper_lin 0.08, bumps [[0.035,5,1.3],[0.025,9,0]], lift 0.12, subsurf 2`，再整体转 Y -24° |
| 果子（圆、顶上凹） | `sx 1.25, sy 1.15, sz 1.18, dent_top {depth 0.22, radius 0.28}, squash 0.06, point 0.05, segments [48,32], subsurf 1` |
| 胖胖的圆角块 | `sx 1.5, sy 1.0, sz 1.1, subsurf 2` |
| 水滴（下圆上尖） | `sx 1.0, sy 1.0, sz 1.3, squash 0.35, subsurf 2` |

## 5. 调图的顺序

1. 先只放主体、第二面、记号三个零件，`--quick` 出图，看构图（位置、大小、前后）。
2. 打开 `preview.png` 看：内容是不是偏了（调 `root.location`）、是不是太小（整体放大零件或减小 `android.inset`，不要小于 0.18）。
3. 调主体形状（egg 参数），再加小装饰。
4. 调颜色：只动 `gradient` 和 `rim`，`glow` / `transmission` 在家族范围内微调。
5. `report.txt` 安全区“通过”，四种桌面效果都看过，出正式图（去掉 `--quick`）。

每次只改一两个数，出图对比。改坏了用 git 回退 JSON。

## 6. 接进 Android 项目

```bash
cp -r out/<名字>/android/res/* app/src/main/res/
cp out/<名字>/master.png docs/brand/<名字>-master.png
cp blender/specs/<名字>.json docs/brand/          # 图标配置跟着模块仓库走，以后改图从这里改
```

`AndroidManifest.xml` 的 `<application>` 用 `android:icon="@mipmap/ic_launcher"`、`android:roundIcon="@mipmap/ic_launcher_round"`。
项目里原来有 `launcher_background` 颜色的，删掉重复定义（生成的在 `ic_launcher_colors.xml`）。

### 主题图标（Android 13+ 单色）和快速设置磁贴

系统会把主题图标染成单色，所以要一份**线稿**：用 SVG 画（主体轮廓 + 第二面描边 + 记号镂空），再转成矢量图：

```bash
python3 tools/svg2vector.py docs/brand/<名字>-mono.svg -o app/src/main/res/drawable/ic_launcher_monochrome.xml
python3 tools/svg2vector.py docs/brand/<名字>-mono.svg -o app/src/main/res/drawable/ic_tile_<名字>.xml --size 24 --fit 0.9
```

线稿要点：线宽一致（视图 64 宽时 3–4），圆头圆角；记号用 `fill-rule="evenodd"` 在主体上挖空；
例子见 `blender/specs/svg/shuclear_mono.svg`。JSON 里 `android.monochrome` 设成 `true`。

### 宿主进程里显示模块图标

宿主进程读不到模块的 Android 资源。把 `host/brand_mark_192.png` 放进 hook 模块的 Java 资源
（例如 `hook/ui/src/main/resources/brand/<名字>_mark.png`），用类加载器读：

```kotlin
HostBrand::class.java.classLoader?.getResourceAsStream("brand/<名字>_mark.png")?.use(BitmapFactory::decodeStream)
```

放在和桌面图标一样的底色上（浅 `#F5F2EF` / 深 `#303030`）。参考 ShuClear 的 `hook/ui/.../HostBrand.kt`。

## 7. 出错了怎么办

| 现象 | 原因和办法 |
|---|---|
| `CLEARKIT_FAILED … 不认识的形状` | `shape` 写错，只能用第 4 节表里的 |
| `… material “x” 没在 materials 里定义` | 零件的 `material` 名字和 `materials` 的键对不上 |
| `渲染结果是空的` | 零件都在镜头外：检查 `location` 是不是写成了几十、几百 |
| `report.txt` 安全区“超出” | 内容太大或偏了：调 `root.location` 居中，或零件整体缩小，或 `android.inset` 调到 0.24–0.26 |
| 颜色发灰、不鲜艳 | 有人把色彩管理改成了 AgX。`crystal_kit.py` 里必须是 `Standard` |
| 透明 PNG 外发光边缘发白 | 外发光里 RGB 和 Alpha 的系数不一样。`setup_glow` 里四个通道必须乘同一个数 |
| SVG 零件是黑色的一大块 | SVG 自带材质没清、或缩放时曲线半径被放大。用仓库里的 `crystal_kit.py`（已处理），不要自己改那段顺序 |
| 预览图颜色和 Blender 里看到的不一样 | PNG 像素是 sRGB，叠底色时直接混（`over()`），不要再转线性 |
| WSL 里一堆 `EGL Error` | 无界面运行的正常输出，不影响渲染 |
| 用 Blender MCP 时连接断了 | 不要调 `read_factory_settings`（会卸掉 MCP 插件）；清场景用 `crystal_kit.clear_scene()` |
| Windows 上跑很慢 | 看 `report.txt` 的设备：显示 CPU 说明没找到显卡，装 NVIDIA 驱动后用 `--device CUDA` 试 |

## 8. 风格参数为什么是这些（不要随便改）

都在 `crystal_kit.py`，2026-10 定稿时调出来的：

- 色彩管理 `Standard`（AgX 会把霓虹色压灰）。
- 玻璃：IOR 1.42、清漆 1、清漆粗糙度 0.03、薄膜 280nm（表面一点虹彩）。
- 边缘光：自发光强度 = `rim_glow + rim_k × 朝向^2.2`（Layer Weight，混合 0.35），颜色往 `rim` 色混。
- 灯光：五盏椭圆面光（主光 900、补光 350、左右轮廓 500、顶光 300），椭圆让玻璃上的高光是圆的。
- 相机：85mm，位于 (0, -14, 1.2)，俯 5°。
- 外发光：渲染结果模糊 90（×0.55）和 24（×0.75）两层垫在下面。
- 世界光：淡蓝灰 (0.92, 0.93, 0.97)，强度 0.8。

要改家族风格，所有模块的图标都要重出，先和维护者确认。
