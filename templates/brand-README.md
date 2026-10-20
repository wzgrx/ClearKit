# <模块中文名> · 品牌资源

<日期> 维护者确认名字“<模块中文名>”和图标（ClearKit 水晶玻璃家族）。

- 家族共同点：暖色玻璃主体 + 晶透蓝的第二面 + 一个乳白记号 + 霓虹外发光。
- 和宿主的联系只借意象：<主体是什么，来自什么昵称 / 物件>；<第二面是什么，代表什么内容>；不用宿主的 logo 和文字。
- 颜色：主体 <c1> → <c2>；第二面 <c1> → <c2>；记号乳白。

![母版](<名字>-master.png)

## 文件

| 文件 | 说明 |
|---|---|
| `<名字>-master.png` | 母版，1024×1024 透明 PNG（含外发光） |
| `<名字>.json` | ClearKit 图标配置，改图从这里改 |
| `<名字>-mono.svg` | 主题图标线稿 |
| `app/src/main/res/drawable-nodpi/ic_<名字>_art.png` | 自适应图标前景图 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 前景：四边内缩 22% |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | 主题图标（由线稿转换） |

背景色：浅色 `#F5F2EF`、深色 `#303030`。

## 改图

```bash
# 在 ClearKit 仓库里
blender -b --python blender/build_icon.py -- <本仓库>/docs/brand/<名字>.json --out out/<名字>
cp -r out/<名字>/android/res/* <本仓库>/app/src/main/res/
cp out/<名字>/master.png <本仓库>/docs/brand/<名字>-master.png
python3 tools/svg2vector.py <本仓库>/docs/brand/<名字>-mono.svg -o <本仓库>/app/src/main/res/drawable/ic_launcher_monochrome.xml
```

改完在浅色、深色、圆形、圆角方四种底上看一遍（`out/<名字>/preview.png`），`report.txt` 安全区要“通过”。
