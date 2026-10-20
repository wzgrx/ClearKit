"""
水晶玻璃图标工具（净界家族）。只用 Blender 自带的 bpy、bmesh、numpy，不需要装别的东西。

一般不直接改这个文件：图标的样子写在 specs/*.json 里，用 build_icon.py 出图。
这里的数值是哔哩净界 / 红薯净界 2026-10 定稿时的参数（Standard 色彩、Principled 玻璃、边缘光、两层外发光），
改了会让整个家族风格不一致。
"""
import json
import math
import os

import bmesh
import bpy
import numpy as np
from mathutils import Vector

# ---------------------------------------------------------------- 颜色

def srgb(hex_color):
    """#RRGGBB → Blender 材质用的线性颜色。"""
    h = hex_color.lstrip('#')
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c)


def hex_rgb(hex_color):
    """#RRGGBB → 0–1 的 sRGB（直接和 PNG 像素比、叠底色用）。"""
    h = hex_color.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float32)


# ---------------------------------------------------------------- 场景

def clear_scene():
    """只删物体和数据，不重置设置（重置会卸掉 Blender MCP 插件）。"""
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.lights, bpy.data.cameras, bpy.data.curves):
        for b in list(coll):
            coll.remove(b)
    for c in list(bpy.data.collections):
        bpy.data.collections.remove(c)


def pick_device(sc, want='auto'):
    """GPU 优先（OPTIX → CUDA → HIP → METAL），都没有用 CPU。返回实际用的设备名。"""
    sc.render.engine = 'CYCLES'
    try:
        prefs = bpy.context.preferences.addons['cycles'].preferences
    except KeyError:
        sc.cycles.device = 'CPU'
        return 'CPU'
    order = ['OPTIX', 'CUDA', 'HIP', 'METAL', 'ONEAPI'] if want == 'auto' else [want]
    for t in order:
        if t == 'CPU':
            break
        try:
            prefs.compute_device_type = t
            prefs.get_devices()
            devs = [d for d in prefs.devices if d.type == t]
            if devs:
                for d in prefs.devices:
                    d.use = d.type == t
                sc.cycles.device = 'GPU'
                return t
        except (TypeError, ValueError):
            continue
    sc.cycles.device = 'CPU'
    return 'CPU'


def setup_scene(render):
    """相机、灯光、世界、渲染设置。render 见 spec 的 render 段。"""
    sc = bpy.context.scene
    device = pick_device(sc, render.get('device', 'auto'))
    sc.cycles.samples = int(render.get('samples', 512))
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 16
    sc.cycles.transmission_bounces = 16
    sc.cycles.transparent_max_bounces = 16
    sc.render.film_transparent = True
    size = int(render.get('size', 1024))
    sc.render.resolution_x = sc.render.resolution_y = size
    sc.render.resolution_percentage = 100
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA'
    sc.render.image_settings.color_depth = '8'
    # Standard 色彩管理：颜色更饱和（AgX 会把霓虹色压灰）
    sc.view_settings.view_transform = 'Standard'
    try:
        sc.view_settings.look = 'None'
    except TypeError:
        pass

    w = sc.world or bpy.data.worlds.new('World')
    sc.world = w
    w.use_nodes = True
    bg = next(n for n in w.node_tree.nodes if n.type == 'BACKGROUND')
    bg.inputs['Color'].default_value = (0.92, 0.93, 0.97, 1)
    bg.inputs['Strength'].default_value = 0.8

    cam_cfg = render.get('camera', {})
    cam_data = bpy.data.cameras.new('Cam')
    cam_data.lens = cam_cfg.get('lens', 85)
    cam = bpy.data.objects.new('Cam', cam_data)
    sc.collection.objects.link(cam)
    cam.location = cam_cfg.get('location', (0, -14, 1.2))
    cam.rotation_euler = [math.radians(a) for a in cam_cfg.get('rotation_deg', (85, 0, 0))]
    sc.camera = cam

    # 五盏椭圆面光：主光、补光、左右轮廓光、顶光（椭圆让玻璃上的高光是圆的）
    for name, loc, rot, sz, energy in (
        ('Key', (-3, -6, 5), (50, 0, -25), 6, 900),
        ('Fill', (4, -5, 1), (75, 0, 35), 5, 350),
        ('RimL', (-5, 3, 2), (70, 0, -130), 3, 500),
        ('RimR', (5, 3, 2), (70, 0, 130), 3, 500),
        ('Top', (0, 0, 7), (0, 0, 0), 5, 300),
    ):
        ld = bpy.data.lights.new(name, 'AREA')
        ld.size = sz
        ld.energy = energy * render.get('light_scale', 1.0)
        ld.shape = 'ELLIPSE'
        lo = bpy.data.objects.new(name, ld)
        sc.collection.objects.link(lo)
        lo.location = loc
        lo.rotation_euler = [math.radians(a) for a in rot]
    setup_glow(render.get('glow', {}))
    return device


def setup_glow(glow):
    """
    霓虹外发光：渲染结果模糊两次（宽 90、窄 24），按比例减淡后垫在原图下面。
    RGB 和 Alpha 一定要乘同一个系数，不然透明 PNG 的光晕边缘会发白。
    """
    sc = bpy.context.scene
    wide = glow.get('wide', [90, 0.55])
    tight = glow.get('tight', [24, 0.75])
    old = bpy.data.node_groups.get('BC_Glow')
    if old:
        bpy.data.node_groups.remove(old)
    ng = bpy.data.node_groups.new('BC_Glow', 'CompositorNodeTree')
    ng.interface.new_socket('Image', in_out='OUTPUT', socket_type='NodeSocketColor')
    N, L = ng.nodes, ng.links
    rl = N.new('CompositorNodeRLayers')
    out = N.new('NodeGroupOutput')

    def halo(size, gain):
        bl = N.new('CompositorNodeBlur')
        try:
            bl.inputs['Size'].default_value = (size, size)
        except TypeError:
            bl.inputs['Size'].default_value = size
        try:
            bl.inputs['Type'].default_value = 'Gaussian'
        except (TypeError, KeyError):
            pass
        L.new(rl.outputs['Image'], bl.inputs['Image'])
        sp = N.new('CompositorNodeSeparateColor')
        L.new(bl.outputs['Image'], sp.inputs['Image'])
        cb = N.new('CompositorNodeCombineColor')
        for ch in ('Red', 'Green', 'Blue', 'Alpha'):
            m = N.new('ShaderNodeMath')
            m.operation = 'MULTIPLY'
            m.inputs[1].default_value = gain
            m.use_clamp = True
            L.new(sp.outputs[ch], m.inputs[0])
            L.new(m.outputs[0], cb.inputs[ch])
        return cb

    w = halo(*wide)
    t = halo(*tight)
    ao1 = N.new('CompositorNodeAlphaOver')
    L.new(w.outputs['Image'], ao1.inputs['Background'])
    L.new(t.outputs['Image'], ao1.inputs['Foreground'])
    ao2 = N.new('CompositorNodeAlphaOver')
    L.new(ao1.outputs['Image'], ao2.inputs['Background'])
    L.new(rl.outputs['Image'], ao2.inputs['Foreground'])
    L.new(ao2.outputs['Image'], out.inputs[0])
    sc.compositing_node_group = ng
    sc.render.use_compositing = True


# ---------------------------------------------------------------- 材质

def _principled(m):
    m.use_nodes = True
    return next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')


def _set(p, key, value):
    if key in p.inputs:
        p.inputs[key].default_value = value


def crystal(name, cfg):
    """
    水晶玻璃：Principled 玻璃 + 清漆 + 薄膜虹彩 + 自发光；可选沿物体 X 轴的两色渐变、边缘提亮。
    cfg: color, gradient [c1, c2], glow, transmission, roughness, rim, rim_glow, rim_k
    """
    m = bpy.data.materials.new(name)
    p = _principled(m)
    rgb = srgb(cfg['color'])
    _set(p, 'Base Color', (*rgb, 1))
    _set(p, 'Roughness', cfg.get('roughness', 0.12))
    _set(p, 'IOR', 1.42)
    _set(p, 'Transmission Weight', cfg.get('transmission', 0.6))
    _set(p, 'Coat Weight', 1.0)
    _set(p, 'Coat Roughness', 0.03)
    _set(p, 'Emission Color', (*rgb, 1))
    _set(p, 'Emission Strength', cfg.get('glow', 0.5))
    _set(p, 'Thin Film Thickness', 280.0)
    _set(p, 'Thin Film IOR', 1.33)
    nt = m.node_tree
    ramp = None
    if cfg.get('gradient'):
        c1, c2 = cfg['gradient']
        tc = nt.nodes.new('ShaderNodeTexCoord')
        mp = nt.nodes.new('ShaderNodeMapping')
        mp.inputs['Location'].default_value[0] = 0.5
        mp.inputs['Scale'].default_value[0] = 0.25
        gr = nt.nodes.new('ShaderNodeTexGradient')
        ramp = nt.nodes.new('ShaderNodeValToRGB')
        ramp.color_ramp.elements[0].color = (*srgb(c1), 1)
        ramp.color_ramp.elements[1].color = (*srgb(c2), 1)
        nt.links.new(tc.outputs['Object'], mp.inputs['Vector'])
        nt.links.new(mp.outputs['Vector'], gr.inputs['Vector'])
        nt.links.new(gr.outputs['Fac'], ramp.inputs['Fac'])
        nt.links.new(ramp.outputs['Color'], p.inputs['Base Color'])
        nt.links.new(ramp.outputs['Color'], p.inputs['Emission Color'])
    if cfg.get('rim'):
        # 边缘提亮：自发光强度 = rim_glow + rim_k × 朝向^2.2，颜色往 rim 色混
        lw = nt.nodes.new('ShaderNodeLayerWeight')
        lw.inputs['Blend'].default_value = 0.35
        pw = nt.nodes.new('ShaderNodeMath'); pw.operation = 'POWER'; pw.inputs[1].default_value = 2.2
        mu = nt.nodes.new('ShaderNodeMath'); mu.operation = 'MULTIPLY'; mu.inputs[1].default_value = cfg.get('rim_k', 3.0)
        ad = nt.nodes.new('ShaderNodeMath'); ad.operation = 'ADD'; ad.inputs[1].default_value = cfg.get('rim_glow', cfg.get('glow', 0.45))
        mx = nt.nodes.new('ShaderNodeMix'); mx.data_type = 'RGBA'
        nt.links.new(lw.outputs['Facing'], pw.inputs[0])
        nt.links.new(pw.outputs[0], mu.inputs[0])
        nt.links.new(mu.outputs[0], ad.inputs[0])
        nt.links.new(ad.outputs[0], p.inputs['Emission Strength'])
        if ramp:
            nt.links.new(ramp.outputs['Color'], mx.inputs[6])
        else:
            mx.inputs[6].default_value = (*rgb, 1)
        mx.inputs[7].default_value = (*srgb(cfg['rim']), 1)
        nt.links.new(pw.outputs[0], mx.inputs[0])
        nt.links.new(mx.outputs[2], p.inputs['Emission Color'])
    return m


def milk(name, cfg):
    """乳白记号（家族的“净”）：不透明、微微透光、轻自发光。"""
    m = bpy.data.materials.new(name)
    p = _principled(m)
    _set(p, 'Base Color', (*srgb(cfg.get('color', '#FFFFFF')), 1))
    _set(p, 'Roughness', 0.18)
    _set(p, 'Subsurface Weight', 0.4)
    _set(p, 'Coat Weight', 1.0)
    _set(p, 'Emission Color', (1, 1, 1, 1))
    _set(p, 'Emission Strength', cfg.get('glow', 0.35))
    return m


def build_materials(spec):
    mats = {}
    for name, cfg in spec.get('materials', {}).items():
        kind = cfg.get('type', 'crystal')
        mats[name] = milk(name, cfg) if kind == 'milk' else crystal(name, cfg)
    return mats


# ---------------------------------------------------------------- 形状
# 坐标约定：相机在 -Y 看向 +Y，X 向右，Z 向上。y 越小越靠前（离镜头越近）。

def _mesh(name, bm, coll, smooth=True):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    coll.objects.link(o)
    for poly in me.polygons:
        poly.use_smooth = smooth
    return o


def _extrude_outline(bm, pts_xz, depth):
    """XZ 平面上的轮廓（逆时针）挤出成厚度 depth 的片（往 +Y 方向）。"""
    vs = [bm.verts.new((x, 0, z)) for x, z in pts_xz]
    f = bm.faces.new(vs)
    ext = bmesh.ops.extrude_face_region(bm, geom=[f])
    for v in [e for e in ext['geom'] if isinstance(e, bmesh.types.BMVert)]:
        v.co.y += depth
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)


def shape_egg(name, p, coll):
    """
    变形球：红薯、果子、豆子、气泡……都从它变。
    p: sx sy sz（三个方向的尺寸）、taper（两头收细，0–0.5）、taper_lin（一头更细）、
       bumps [[幅度, 频率, 相位], ...]（表面起伏）、lift（中间往上拱）、
       dent_top {depth, radius}（顶部凹窝，果子用）、squash（越往上越窄）、point（底部变尖）、segments [u, v]
    """
    u, v = p.get('segments', [48, 24])
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=u, v_segments=v, radius=1.0)
    sx, sy, sz = p.get('sx', 1.0), p.get('sy', 1.0), p.get('sz', 1.0)
    a, b = p.get('taper', 0.0), p.get('taper_lin', 0.0)
    bumps = p.get('bumps', [])
    lift = p.get('lift', 0.0)
    dent = p.get('dent_top')
    squash, point = p.get('squash', 0.0), p.get('point', 0.0)
    for vert in bm.verts:
        x, y, z = vert.co
        taper = 1.0 - a * x * x - b * x
        bump = 1.0 + sum(amp * math.sin(freq * x + ph) for amp, freq, ph in bumps)
        dz = 0.0
        if dent and z > 0:
            rxy = math.sqrt(x * x + y * y)
            dz = -dent['depth'] * math.exp(-(rxy / dent['radius']) ** 2)
        sq = 1.0 - squash * z
        vert.co = Vector((
            x * sx * sq,
            y * sy * taper * bump * sq,
            z * sz * taper * bump + lift * (1 - x * x) + dz - point * max(0.0, -z) ** 3,
        ))
    o = _mesh(name, bm, coll)
    if p.get('subsurf', 0):
        m = o.modifiers.new('Sub', 'SUBSURF')
        m.levels = m.render_levels = p['subsurf']
    return o


def shape_leaf(name, p, coll):
    """叶子 / 花瓣：压扁的球，两头尖、往上翘。尺寸用 scale 调。"""
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=32, v_segments=16, radius=1.0)
    curl = p.get('curl', 0.35)
    for vert in bm.verts:
        x, y, z = vert.co
        w = (1 - x * x) ** 0.6 if abs(x) < 1 else 0
        vert.co = Vector((x, y * p.get('thickness', 0.25), z * (0.35 + 0.65 * w) + curl * x * x))
    return _mesh(name, bm, coll)


def shape_slab(name, p, coll):
    """圆角矩形片：卡片、文字行、按钮、屏幕。p: w h r depth bevel seg"""
    w, h, r = p['w'], p['h'], p.get('r', 0.2)
    seg = p.get('seg', 10)
    pts = []
    for cx, cz, a0 in ((w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180), (w / 2 - r, -h / 2 + r, 270)):
        for i in range(seg + 1):
            a = math.radians(a0 + 90 * i / seg)
            pts.append((cx + r * math.cos(a), cz + r * math.sin(a)))
    bm = bmesh.new()
    _extrude_outline(bm, pts, p.get('depth', 0.18))
    o = _mesh(name, bm, coll)
    if p.get('bevel', 0.06):
        bv = o.modifiers.new('Bevel', 'BEVEL')
        bv.width = p.get('bevel', 0.06)
        bv.segments = 5
    return o


def shape_polygon(name, p, coll):
    """任意多边形挤出：播放键、箭头、对勾……p: points [[x, z], ...]（逆时针）depth bevel"""
    bm = bmesh.new()
    _extrude_outline(bm, p['points'], p.get('depth', 0.2))
    o = _mesh(name, bm, coll, smooth=False)
    if p.get('bevel', 0.08):
        bv = o.modifiers.new('Bevel', 'BEVEL')
        bv.width = p.get('bevel', 0.08)
        bv.segments = 8
        bv.affect = 'EDGES'
    return o


def shape_star(name, p, coll):
    """星形闪光（默认四角星，家族里的“净”）。p: points outer inner depth bevel"""
    n = p.get('points', 4)
    outer, inner = p.get('outer', 0.62), p.get('inner', 0.15)
    pts = []
    for i in range(2 * n):
        a = math.radians(90 + i * 180 / n)
        r = outer if i % 2 == 0 else inner
        pts.append((r * math.cos(a), r * math.sin(a)))
    bm = bmesh.new()
    _extrude_outline(bm, pts, p.get('depth', 0.18))
    o = _mesh(name, bm, coll, smooth=False)
    if p.get('bevel', 0.05):
        bv = o.modifiers.new('Bevel', 'BEVEL')
        bv.width = p.get('bevel', 0.05)
        bv.segments = 6
    return o


def shape_cylinder(name, p, coll):
    """短圆柱：茎、柄、天线。p: radius depth segments（沿 Z 轴）"""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=p.get('segments', 32), radius1=p.get('radius', 0.07),
                          radius2=p.get('radius', 0.07), depth=p.get('depth', 0.5))
    return _mesh(name, bm, coll)


def shape_tube(name, p, coll):
    """弯管：沿 XZ 平面的贝塞尔曲线（茎、天线、电线）。p: points [[x, z], ...] radius"""
    cd = bpy.data.curves.new(name, 'CURVE')
    cd.dimensions = '3D'
    sp = cd.splines.new('BEZIER')
    pts = p['points']
    sp.bezier_points.add(len(pts) - 1)
    for bp, (x, z) in zip(sp.bezier_points, pts):
        bp.co = (x, 0, z)
        bp.handle_left_type = bp.handle_right_type = 'AUTO'
    cd.bevel_depth = p.get('radius', 0.08)
    cd.bevel_resolution = 6
    cd.use_fill_caps = True
    o = bpy.data.objects.new(name, cd)
    coll.objects.link(o)
    return o


def shape_svg(name, p, coll, base_dir):
    """
    从 SVG 文件挤出（任意轮廓：小电视、胶片、书本……）。只用路径的填充形状，描边会被忽略。
    p: file（相对 spec 的路径）width（缩放到多宽）depth bevel
    顺序很重要：先合并、居中、缩放到 width，再设挤出厚度（SVG 原始尺寸很小，先设厚度会变成一大块），最后转网格、立起来。
    """
    import addon_utils
    addon_utils.enable('io_curve_svg', default_set=True)
    before = set(bpy.data.objects)
    bpy.ops.import_curve.svg(filepath=os.path.join(base_dir, p['file']))
    new = [o for o in bpy.data.objects if o not in before and o.type == 'CURVE']
    if not new:
        raise RuntimeError('SVG 里没有可用的路径：' + p['file'])
    bpy.ops.object.select_all(action='DESELECT')
    for o in new:
        o.select_set(True)
    bpy.context.view_layer.objects.active = new[0]
    if len(new) > 1:
        bpy.ops.object.join()
    o = bpy.context.view_layer.objects.active
    for c in list(o.users_collection):
        c.objects.unlink(o)
    coll.objects.link(o)
    o.name = name
    bpy.ops.object.origin_set(type='ORIGIN_GEOMETRY', center='BOUNDS')
    o.location = (0, 0, 0)
    bpy.context.view_layer.update()  # 刚导入的曲线尺寸要刷新后才准
    xs = [pt.co.x for sp in o.data.splines for pt in sp.bezier_points] + [pt.co.x for sp in o.data.splines for pt in sp.points]
    raw_w = (max(xs) - min(xs)) * o.scale.x if xs else o.dimensions.x
    s = p.get("width", 2.0) / max(raw_w, 1e-6)
    o.scale = (s, s, s)
    # properties=False：不然曲线点的半径也跟着放大，倒角会变成一大坨
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True, properties=False)
    o.data.dimensions = '2D'
    o.data.fill_mode = 'BOTH'
    o.data.extrude = p.get('depth', 0.2) / 2
    o.data.bevel_depth = p.get('bevel', 0.03)
    o.data.bevel_resolution = 4
    bpy.ops.object.convert(target='MESH')
    o = bpy.context.view_layer.objects.active
    o.data.materials.clear()  # SVG 自带的填充色材质不要
    # SVG 在 XY 平面：转到正对镜头的 XZ 平面
    o.rotation_euler = (math.radians(90), 0, 0)
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
    for poly in o.data.polygons:
        poly.use_smooth = True
    return o


SHAPES = {
    'egg': shape_egg, 'leaf': shape_leaf, 'slab': shape_slab, 'polygon': shape_polygon,
    'star': shape_star, 'cylinder': shape_cylinder, 'tube': shape_tube,
}


def build_parts(spec, mats, base_dir):
    """按 spec.parts 建物体；没写 parent 的挂到根空物体上（整体平移用 spec.root.location）。"""
    sc = bpy.context.scene
    coll = bpy.data.collections.new(spec.get('name', 'Icon'))
    sc.collection.children.link(coll)
    root = bpy.data.objects.new('Root', None)
    coll.objects.link(root)
    root.location = spec.get('root', {}).get('location', (0, 0, 0))
    objs = {}
    for part in spec['parts']:
        pid, kind = part['id'], part['shape']
        params = part.get('params', {})
        if kind == 'svg':
            o = shape_svg(pid, params, coll, base_dir)
        elif kind in SHAPES:
            o = SHAPES[kind](pid, params, coll)
        else:
            raise ValueError(f'不认识的形状 {kind}（可用：{", ".join(SHAPES)}, svg）')
        mat = mats.get(part.get('material'))
        if mat is None:
            raise ValueError(f'{pid} 的 material “{part.get("material")}” 没在 materials 里定义')
        o.data.materials.append(mat)
        objs[pid] = o
        o.parent = objs[part['parent']] if part.get('parent') else root
        o.location = part.get('location', (0, 0, 0))
        o.rotation_euler = [math.radians(a) for a in part.get('rotation_deg', (0, 0, 0))]
        o.scale = part.get('scale', (1, 1, 1))
    return objs


# ---------------------------------------------------------------- 出图后处理（numpy，PNG 像素是 sRGB）

def load_rgba(path):
    img = bpy.data.images.load(path, check_existing=False)
    w, h = img.size
    a = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(a)
    bpy.data.images.remove(img)
    return a.reshape(h, w, 4)  # 注意：第 0 行是图片最下面一行


def save_rgba(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    h, w = arr.shape[:2]
    img = bpy.data.images.new('tmp_out', w, h, alpha=True)
    img.pixels.foreach_set(np.ascontiguousarray(arr, dtype=np.float32).ravel())
    img.filepath_raw = path
    img.file_format = 'PNG'
    img.save()
    bpy.data.images.remove(img)


def resize(arr, size):
    """缩放到 size×size（Blender 自带的缩放，下采样是盒式平均）。"""
    h, w = arr.shape[:2]
    img = bpy.data.images.new('tmp_rs', w, h, alpha=True)
    img.pixels.foreach_set(np.ascontiguousarray(arr, dtype=np.float32).ravel())
    img.scale(size, size)
    out = np.empty(size * size * 4, dtype=np.float32)
    img.pixels.foreach_get(out)
    bpy.data.images.remove(img)
    return out.reshape(size, size, 4)


def crop_square(arr, threshold=0.04, margin=0.02):
    """按内容（含外发光）外框裁成正方形，四边留 margin 的空。"""
    alpha = arr[..., 3]
    ys, xs = np.where(alpha > threshold)
    if len(xs) == 0:
        raise RuntimeError('渲染结果是空的：检查 parts 是不是都在镜头外')
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    side = int(max(x1 - x0, y1 - y0) * (1 + 2 * margin)) + 2
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    out = np.zeros((side, side, 4), dtype=np.float32)
    sx0, sy0 = cx - side // 2, cy - side // 2
    h, w = arr.shape[:2]
    for dy in range(side):
        yy = sy0 + dy
        if 0 <= yy < h:
            lo, hi = max(0, sx0), min(w, sx0 + side)
            if hi > lo:
                out[dy, lo - sx0:hi - sx0] = arr[yy, lo:hi]
    return out


def over(fg, bg_hex):
    """透明图叠到底色上（sRGB 里直接混，PNG 像素本来就是 sRGB）。"""
    bg = hex_rgb(bg_hex)
    a = fg[..., 3:4]
    rgb = fg[..., :3] * a + bg * (1 - a)
    return np.concatenate([rgb, np.ones_like(a)], axis=2)


def adaptive_tile(art, bg_hex, inset, mask, size=512):
    """
    模拟桌面上的自适应图标：108 单位的画布铺底色，前景四边内缩 inset，再按遮罩（circle / squircle / square）
    裁出中间 72 单位（系统实际显示的范围）。
    """
    canvas = 4 * size
    inner = int(canvas * (1 - 2 * inset))
    fg = resize(art, inner)
    layer = np.zeros((canvas, canvas, 4), dtype=np.float32)
    o = (canvas - inner) // 2
    layer[o:o + inner, o:o + inner] = fg
    full = over(layer, bg_hex)
    vis = int(canvas * 72 / 108)
    c0 = (canvas - vis) // 2
    view = full[c0:c0 + vis, c0:c0 + vis]
    yy, xx = np.mgrid[0:vis, 0:vis]
    u = (xx + 0.5) / vis * 2 - 1
    v = (yy + 0.5) / vis * 2 - 1
    if mask == 'circle':
        inside = u * u + v * v <= 1
    elif mask == 'squircle':
        inside = np.abs(u) ** 4 + np.abs(v) ** 4 <= 1
    else:
        inside = np.ones_like(u, dtype=bool)
    view = view.copy()
    view[..., 3] = inside.astype(np.float32)
    return resize(view, size)


def safe_zone_ok(art, inset, threshold=0.25):
    """不透明的内容（不算外发光）有没有超出圆形遮罩：超出时返回 False。"""
    canvas = 1024
    inner = int(canvas * (1 - 2 * inset))
    fg = resize(art, inner)
    o = (canvas - inner) // 2
    ys, xs = np.where(fg[..., 3] > threshold)
    if len(xs) == 0:
        return True
    cx = cy = canvas / 2
    r = canvas * 72 / 108 / 2
    d = np.sqrt((xs + o + 0.5 - cx) ** 2 + (ys + o + 0.5 - cy) ** 2)
    return bool(d.max() <= r)


def load_spec(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)
