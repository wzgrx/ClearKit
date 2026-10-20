"""
一条命令出一套图标。用法（在仓库根目录）：

    blender -b --python blender/build_icon.py -- blender/specs/shuclear.json --out out/shuclear
    blender -b --python blender/build_icon.py -- blender/specs/shuclear.json --out out/shuclear --quick

--quick：512×512、64 采样，几秒出一张，调形状和颜色时用；定稿去掉 --quick（1024×1024、512 采样）。
其它可选：--samples N  --device auto|OPTIX|CUDA|HIP|METAL|CPU  --save-blend（顺便存一份 .blend，在 Blender 里手动微调用）

输出（out 目录）：
  master.png                         母版（透明背景，含外发光）
  preview.png                        桌面效果：浅色圆形、浅色圆角方、深色圆形、深色圆角方
  android/res/...                    直接复制进 app/src/main/res（前景图、自适应图标、底色、旧版各尺寸）
  store/icon-512.png                 应用商店 / GitHub 用的 512 图（不透明）
  host/brand_mark_192.png            宿主进程里画模块图标用（Java 资源，见 docs/04-icon-pipeline.md）
  report.txt                         检查结果（安全区、文件清单、渲染设备和耗时）
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import crystal_kit as K  # noqa: E402
import bpy  # noqa: E402
import numpy as np  # noqa: E402


def parse_args():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    if not argv:
        raise SystemExit('用法：blender -b --python blender/build_icon.py -- <spec.json> --out <目录> [--quick]')
    args = {'spec': argv[0], 'out': None, 'quick': False, 'samples': None, 'device': None, 'save_blend': False}
    i = 1
    while i < len(argv):
        a = argv[i]
        if a == '--out':
            args['out'] = argv[i + 1]; i += 2
        elif a == '--quick':
            args['quick'] = True; i += 1
        elif a == '--samples':
            args['samples'] = int(argv[i + 1]); i += 2
        elif a == '--device':
            args['device'] = argv[i + 1]; i += 2
        elif a == '--save-blend':
            args['save_blend'] = True; i += 1
        else:
            raise SystemExit(f'不认识的参数 {a}')
    if not args['out']:
        args['out'] = os.path.join('out', os.path.splitext(os.path.basename(args['spec']))[0])
    return args


ADAPTIVE_FG = '''<?xml version="1.0" encoding="utf-8"?>
<!-- {title}：自适应图标前景。按比例内缩 {pct}%，任何遮罩形状都留在安全区里。由 clear-kit/blender/build_icon.py 生成。 -->
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:insetLeft="{pct}%" android:insetRight="{pct}%"
    android:insetTop="{pct}%" android:insetBottom="{pct}%">
    <bitmap android:src="@drawable/ic_{name}_art"
        android:gravity="fill" android:filter="true" android:antialias="true" />
</inset>
'''

ADAPTIVE_ICON = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
{mono}</adaptive-icon>
'''

COLORS = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="launcher_background">{c}</color>
</resources>
'''


def main():
    args = parse_args()
    spec_path = os.path.abspath(args['spec'])
    spec = K.load_spec(spec_path)
    out = os.path.abspath(args['out'])
    os.makedirs(out, exist_ok=True)
    render = dict(spec.get('render', {}))
    if args['quick']:
        render['size'], render['samples'] = 512, 64
    if args['samples']:
        render['samples'] = args['samples']
    if args['device']:
        render['device'] = args['device']

    K.clear_scene()
    device = K.setup_scene(render)
    mats = K.build_materials(spec)
    K.build_parts(spec, mats, os.path.dirname(spec_path))

    sc = bpy.context.scene
    master = os.path.join(out, 'master.png')
    sc.render.filepath = master
    t0 = time.time()
    bpy.ops.render.render(write_still=True)
    took = time.time() - t0
    if args['save_blend']:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, 'scene.blend'))

    android = spec.get('android', {})
    name = android.get('name', spec.get('name', 'module')).lower()
    inset = float(android.get('inset', 0.22))
    bg_light = android.get('bg_light', '#F5F2EF')
    bg_dark = android.get('bg_dark', '#303030')
    title = spec.get('title', name)

    raw = K.load_rgba(master)
    art = K.crop_square(raw)
    art512 = K.resize(art, 512)
    res = os.path.join(out, 'android', 'res')
    K.save_rgba(art512, os.path.join(res, 'drawable-nodpi', f'ic_{name}_art.png'))
    os.makedirs(os.path.join(res, 'drawable'), exist_ok=True)
    with open(os.path.join(res, 'drawable', 'ic_launcher_foreground.xml'), 'w', encoding='utf-8') as f:
        f.write(ADAPTIVE_FG.format(title=title, pct=round(inset * 100), name=name))
    mono = '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n' if android.get('monochrome') else ''
    os.makedirs(os.path.join(res, 'mipmap-anydpi-v26'), exist_ok=True)
    for fn in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        with open(os.path.join(res, 'mipmap-anydpi-v26', fn), 'w', encoding='utf-8') as f:
            f.write(ADAPTIVE_ICON.format(mono=mono))
    for d, c in (('values', bg_light), ('values-night', bg_dark)):
        os.makedirs(os.path.join(res, d), exist_ok=True)
        with open(os.path.join(res, d, 'ic_launcher_colors.xml'), 'w', encoding='utf-8') as f:
            f.write(COLORS.format(c=c))
    # 安卓 8 以前的旧版图标（圆角方）
    for d, px in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        tile = K.adaptive_tile(art, bg_light, inset, 'squircle', size=px * 2)
        K.save_rgba(K.resize(tile, px), os.path.join(res, f'mipmap-{d}', 'ic_launcher.png'))

    # 桌面效果预览：四种组合拼成一张（第 0 行是最下面，所以先放下排）
    tiles = [K.adaptive_tile(art, bg, inset, mask, 512) for bg, mask in ((bg_light, 'circle'), (bg_light, 'squircle'), (bg_dark, 'circle'), (bg_dark, 'squircle'))]
    gap = np.zeros((512, 32, 4), dtype=np.float32)
    row = np.concatenate([tiles[0], gap, tiles[1], gap, tiles[2], gap, tiles[3]], axis=1)
    pad = np.zeros((48, row.shape[1], 4), dtype=np.float32)
    canvas = K.over(np.concatenate([pad, row, pad], axis=0), '#BDBDBD')
    K.save_rgba(canvas, os.path.join(out, 'preview.png'))

    # 商店图标：不透明方图，内容比桌面图标大一点
    store = K.resize(K.adaptive_tile(art, bg_light, float(android.get('store_inset', 0.16)), 'square', 1024), 512)
    K.save_rgba(store, os.path.join(out, 'store', 'icon-512.png'))
    # 宿主进程里用的小图（透明）
    K.save_rgba(K.resize(art, 192), os.path.join(out, 'host', 'brand_mark_192.png'))

    ok = K.safe_zone_ok(art, inset)
    lines = [
        f'spec: {spec_path}',
        f'设备: {device}  采样: {sc.cycles.samples}  尺寸: {sc.render.resolution_x}  渲染耗时: {took:.1f}s',
        f'安全区（不透明内容在圆形遮罩内）: {"通过" if ok else "超出！把 android.inset 调大，或把 parts 往中间收"}',
        '文件:',
    ]
    for root, _, files in os.walk(out):
        for fn in sorted(files):
            if fn != 'report.txt':
                lines.append('  ' + os.path.relpath(os.path.join(root, fn), out))
    with open(os.path.join(out, 'report.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    print('\n'.join(lines))
    print('CLEARKIT_DONE')


try:
    main()
except SystemExit:
    raise
except Exception as e:  # 让无界面运行时能看出错在哪
    import traceback
    traceback.print_exc()
    print('CLEARKIT_FAILED', e)
    sys.exit(1)
