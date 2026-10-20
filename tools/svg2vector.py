#!/usr/bin/env python3
"""
SVG → Android VectorDrawable（主题图标 ic_launcher_monochrome.xml、快速设置磁贴、通知小图标用）。只用 Python 标准库。

    python3 tools/svg2vector.py 线稿.svg -o ic_launcher_monochrome.xml               # 自适应图标：108dp 画布，内容缩进中间 60%
    python3 tools/svg2vector.py 线稿.svg -o ic_tile.xml --size 24 --fit 0.9          # 24dp 小图标

支持：<path>（d、fill、stroke、stroke-width、stroke-linecap、stroke-linejoin、fill-rule）、<rect>、<circle>、<ellipse>、<line>、
<polygon>、<polyline>，以及 <g> 上的 translate / scale 变换。颜色一律输出成黑色（主题图标和磁贴由系统上色）；
fill="none" 的只保留描边。不支持：渐变、滤镜、蒙版、文字、旋转变换（先在绘图软件里“转为路径”）。
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = '{http://www.w3.org/2000/svg}'


def num(v, default=0.0):
    if v is None:
        return default
    m = re.match(r'\s*(-?[\d.]+(?:e-?\d+)?)', str(v))
    return float(m.group(1)) if m else default


def fmt(x):
    s = f'{x:.3f}'.rstrip('0').rstrip('.')
    return s if s not in ('-0', '') else '0'


def parse_transform(t):
    """只认 translate 和 scale，返回 (sx, sy, tx, ty)。"""
    sx = sy = 1.0
    tx = ty = 0.0
    for kind, args in re.findall(r'(translate|scale|rotate|matrix|skew\w*)\s*\(([^)]*)\)', t or ''):
        vals = [float(x) for x in re.split(r'[\s,]+', args.strip()) if x]
        if kind == 'translate':
            tx += vals[0] * sx
            ty += (vals[1] if len(vals) > 1 else 0) * sy
        elif kind == 'scale':
            sx *= vals[0]
            sy *= vals[1] if len(vals) > 1 else vals[0]
        else:
            sys.exit(f'不支持 {kind} 变换：先在绘图软件里把形状转成路径、去掉变换')
    return sx, sy, tx, ty


TOKEN = re.compile(r'[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?')
ARGC = {'M': 2, 'L': 2, 'H': 1, 'V': 1, 'C': 6, 'S': 4, 'Q': 4, 'T': 2, 'A': 7, 'Z': 0}


def transform_path(d, sx, sy, tx, ty):
    """把路径里的坐标按 (sx, sy, tx, ty) 变换（相对命令只缩放不平移）。"""
    out = []
    toks = TOKEN.findall(d)
    i = 0
    cmd = None
    while i < len(toks):
        t = toks[i]
        if re.match(r'[A-Za-z]', t):
            cmd = t
            out.append(t)
            i += 1
            if cmd in 'Zz':
                continue
        if cmd is None:
            sys.exit('路径数据格式不对：' + d[:40])
        n = ARGC[cmd.upper()]
        args = [float(x) for x in toks[i:i + n]]
        if len(args) < n:
            break
        i += n
        rel = cmd.islower()
        up = cmd.upper()
        if up in 'MLT':
            x, y = args
            out += [fmt(x * sx + (0 if rel else tx)), fmt(y * sy + (0 if rel else ty))]
        elif up == 'H':
            out.append(fmt(args[0] * sx + (0 if rel else tx)))
        elif up == 'V':
            out.append(fmt(args[0] * sy + (0 if rel else ty)))
        elif up in 'CSQ':
            for k in range(0, n, 2):
                out += [fmt(args[k] * sx + (0 if rel else tx)), fmt(args[k + 1] * sy + (0 if rel else ty))]
        elif up == 'A':
            rx, ry, rot, large, sweep, x, y = args
            out += [fmt(rx * abs(sx)), fmt(ry * abs(sy)), fmt(rot), str(int(large)), str(int(sweep)),
                    fmt(x * sx + (0 if rel else tx)), fmt(y * sy + (0 if rel else ty))]
        # 同一命令后面跟着的重复参数：M 之后隐含 L
        if up == 'M':
            cmd = 'l' if rel else 'L'
    return ' '.join(out)


def shape_to_d(el):
    tag = el.tag.replace(SVG_NS, '')
    g = el.get
    if tag == 'path':
        return g('d')
    if tag == 'rect':
        x, y, w, h = num(g('x')), num(g('y')), num(g('width')), num(g('height'))
        rx = num(g('rx'), num(g('ry')))
        ry = num(g('ry'), rx)
        if rx <= 0:
            return f'M{x},{y} H{x + w} V{y + h} H{x} Z'
        return (f'M{x + rx},{y} H{x + w - rx} A{rx},{ry} 0 0 1 {x + w},{y + ry} V{y + h - ry} A{rx},{ry} 0 0 1 {x + w - rx},{y + h} '
                f'H{x + rx} A{rx},{ry} 0 0 1 {x},{y + h - ry} V{y + ry} A{rx},{ry} 0 0 1 {x + rx},{y} Z')
    if tag in ('circle', 'ellipse'):
        cx, cy = num(g('cx')), num(g('cy'))
        rx = num(g('r')) if tag == 'circle' else num(g('rx'))
        ry = num(g('r')) if tag == 'circle' else num(g('ry'))
        return f'M{cx - rx},{cy} A{rx},{ry} 0 1 0 {cx + rx},{cy} A{rx},{ry} 0 1 0 {cx - rx},{cy} Z'
    if tag == 'line':
        return f'M{num(g("x1"))},{num(g("y1"))} L{num(g("x2"))},{num(g("y2"))}'
    if tag in ('polygon', 'polyline'):
        pts = re.split(r'[\s,]+', (g('points') or '').strip())
        pairs = [f'{pts[k]},{pts[k + 1]}' for k in range(0, len(pts) - 1, 2)]
        return 'M' + ' L'.join(pairs) + (' Z' if tag == 'polygon' else '')
    return None


def style_of(el, inherited):
    st = dict(inherited)
    for k, v in re.findall(r'([\w-]+)\s*:\s*([^;]+)', el.get('style') or ''):
        st[k] = v.strip()
    for k in ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-rule'):
        if el.get(k) is not None:
            st[k] = el.get(k)
    return st


def walk(el, inherited, xf, out):
    st = style_of(el, inherited)
    sx, sy, tx, ty = xf
    a, b, c, d = parse_transform(el.get('transform'))
    xf2 = (sx * a, sy * b, tx + c * sx, ty + d * sy)
    dd = shape_to_d(el)
    if dd:
        if el.tag.replace(SVG_NS, '') in ('line', 'polyline'):
            st = dict(st, fill='none')  # 线段没有面积，不填充
        out.append((transform_path(dd, *xf2), st, xf2))
    for child in el:
        walk(child, st, xf2, out)


def convert(svg_path, size, fit, viewport=None):
    root = ET.parse(svg_path).getroot()
    vb = root.get('viewBox')
    if vb:
        vx, vy, vw, vh = [float(x) for x in re.split(r'[\s,]+', vb.strip())]
    else:
        vx, vy, vw, vh = 0, 0, num(root.get('width'), 24), num(root.get('height'), 24)
    box = viewport or size
    s = box * fit / max(vw, vh)
    ox = (box - vw * s) / 2 - vx * s
    oy = (box - vh * s) / 2 - vy * s
    shapes = []
    walk(root, {'fill': '#000'}, (s, s, ox, oy), shapes)
    if not shapes:
        sys.exit('SVG 里没有能用的形状')
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- 由 clear-kit/tools/svg2vector.py 生成 -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{fmt(size)}dp" android:height="{fmt(size)}dp"',
        f'    android:viewportWidth="{fmt(box)}" android:viewportHeight="{fmt(box)}">',
    ]
    for d, st, xf in shapes:
        attrs = []
        fill = st.get('fill', '#000')
        stroke = st.get('stroke')
        if fill and fill != 'none':
            attrs.append('android:fillColor="#FF000000"')
            if st.get('fill-rule') == 'evenodd':
                attrs.append('android:fillType="evenOdd"')
        if stroke and stroke != 'none':
            attrs.append('android:strokeColor="#FF000000"')
            attrs.append(f'android:strokeWidth="{fmt(num(st.get("stroke-width"), 1) * abs(xf[0]))}"')
            cap = st.get('stroke-linecap')
            if cap in ('round', 'square', 'butt'):
                attrs.append(f'android:strokeLineCap="{cap}"')
            join = st.get('stroke-linejoin')
            if join in ('round', 'bevel', 'miter'):
                attrs.append(f'android:strokeLineJoin="{join}"')
        if not attrs:
            continue
        lines.append('    <path ' + ' '.join(attrs))
        lines.append(f'        android:pathData="{d}" />')
    lines.append('</vector>')
    return '\n'.join(lines) + '\n'


def main():
    ap = argparse.ArgumentParser(description='SVG → Android VectorDrawable')
    ap.add_argument('svg')
    ap.add_argument('-o', '--out', required=True)
    ap.add_argument('--size', type=float, default=108, help='输出尺寸（dp），自适应图标用 108，小图标用 24')
    ap.add_argument('--fit', type=float, default=None, help='内容占画布的比例；108 默认 0.6（安全区），其它默认 0.9')
    a = ap.parse_args()
    fit = a.fit if a.fit is not None else (0.6 if a.size == 108 else 0.9)
    xml = convert(a.svg, a.size, fit)
    with open(a.out, 'w', encoding='utf-8') as f:
        f.write(xml)
    print('已写入', a.out)


if __name__ == '__main__':
    main()
