#!/usr/bin/env python3
"""
从 ClearKit 的模板（starter/）生成一个新的净界模块项目。

用法（参数都要给，不会交互提问）：

    python3 tools/new_module.py \
        --out ~/projects/foo-clear \
        --name 某某净界 --host 某某视频 \
        --host-package com.foo.video \
        --package io.github.wzgrx.fooclear \
        --code FooClear \
        --repo wzgrx/FooClear \
        [--launch-activity com.foo.video.MainActivity]

做的事：
1. 把 starter/ 复制到 --out（不复制 build、.gradle 这些构建产物）；
2. 替换占位：包名 io.github.clearkit.starter、模块英文名 ClearStarter / clearstarter、仓库 OWNER/ClearStarter、
   宿主包名 com.example.host、启动页、中文名【模块】【宿主】；
3. 把源码目录 io/github/clearkit/starter 挪到新包名对应的目录；
4. 检查替换干净了（还剩占位就报错），打印下一步。

生成后：cd 到 --out，`git init`，按新项目的 AGENTS.md 和 docs/TASKS.md 开始。
"""
import argparse
import os
import re
import shutil
import sys
from pathlib import Path

KIT = Path(__file__).resolve().parent.parent
STARTER = KIT / "starter"
SKIP_DIRS = {"build", ".gradle", ".kotlin", ".idea", "out", ".git"}
SKIP_FILES = {"local.properties"}
TEXT_EXT = {
    ".kt", ".kts", ".xml", ".md", ".sh", ".py", ".yaml", ".yml", ".json", ".list", ".prop", ".properties",
    ".toml", ".html", ".txt", ".pro", ".gitignore", ".commit", ".bat", "",
}
OLD_PKG = "io.github.clearkit.starter"


def fail(msg: str) -> None:
    print("错误：" + msg, file=sys.stderr)
    sys.exit(1)


def check_args(a) -> None:
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}", a.package):
        fail(f"--package 要像 io.github.你的名字.模块名（全小写，至少三段）：{a.package}")
    if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+", a.host_package):
        fail(f"--host-package 不像包名：{a.host_package}")
    if not re.fullmatch(r"[A-Z][A-Za-z0-9]+", a.code):
        fail(f"--code 要用英文大驼峰，例如 FooClear：{a.code}")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", a.repo):
        fail(f"--repo 要写成 所有者/仓库名：{a.repo}")
    if a.launch_activity and "." not in a.launch_activity:
        fail(f"--launch-activity 要写完整类名：{a.launch_activity}")
    if not a.name.strip() or not a.host.strip():
        fail("--name 和 --host 不能是空的")
    if not STARTER.is_dir():
        fail(f"找不到模板目录 {STARTER}")


def copy_tree(dst: Path) -> None:
    def ignore(_dir, names):
        return [n for n in names if n in SKIP_DIRS or n in SKIP_FILES]
    shutil.copytree(STARTER, dst, ignore=ignore)


def replacements(a):
    # 顺序有讲究：长的、包含别的占位的先换
    pairs = [
        ("OWNER/ClearStarter", a.repo),
        (OLD_PKG, a.package),
        ("io/github/clearkit/starter", a.package.replace(".", "/")),
        ("ClearStarter", a.code),
        ("clearstarter", a.code.lower()),
    ]
    if a.launch_activity:
        pairs.append(("com.example.host.MainActivity", a.launch_activity))
    pairs += [
        ("com.example.host", a.host_package),
        ("【模块】", a.name),
        ("【宿主】", a.host),
    ]
    return pairs


def rewrite_text(root: Path, pairs) -> int:
    n = 0
    for p in root.rglob("*"):
        if not p.is_file() or (p.suffix not in TEXT_EXT and p.name not in {"gradlew"}):
            continue
        try:
            s = p.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        t = s
        for old, new in pairs:
            t = t.replace(old, new)
        if t != s:
            p.write_text(t, encoding="utf-8")
            n += 1
    return n


def move_packages(root: Path, package: str) -> int:
    old_rel = Path("io/github/clearkit/starter")
    new_rel = Path(package.replace(".", "/"))
    moved = 0
    for src_root in [d for d in root.rglob("kotlin") if d.is_dir()]:
        old = src_root / old_rel
        if not old.is_dir():
            continue
        new = src_root / new_rel
        new.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(old), str(new))
        # 删掉挪空的 io/github/clearkit 目录
        d = old.parent
        while d != src_root and d.is_dir() and not any(d.iterdir()):
            d.rmdir()
            d = d.parent
        moved += 1
    return moved


def leftovers(root: Path, launch_given: bool):
    bad = [OLD_PKG, "ClearStarter", "clearstarter", "【模块】", "【宿主】", "OWNER/"]
    if launch_given:
        bad.append("com.example.host")
    hits = []
    for p in root.rglob("*"):
        if not p.is_file() or p.suffix not in TEXT_EXT:
            continue
        try:
            s = p.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for b in bad:
            if b in s:
                hits.append(f"{p.relative_to(root)}: {b}")
    for p in root.rglob("*"):
        if "clearkit/starter" in str(p):
            hits.append(f"目录没挪走：{p.relative_to(root)}")
    return hits


def main() -> None:
    ap = argparse.ArgumentParser(description="从 ClearKit 模板生成新的净界模块")
    ap.add_argument("--out", required=True, help="新项目目录（必须不存在）")
    ap.add_argument("--name", required=True, help="模块中文名，例如 某某净界（先和维护者确认）")
    ap.add_argument("--host", required=True, help="宿主中文名，例如 某某视频")
    ap.add_argument("--host-package", required=True, help="宿主包名，例如 com.foo.video")
    ap.add_argument("--package", required=True, help="模块包名（也是代码包名），例如 io.github.wzgrx.fooclear")
    ap.add_argument("--code", required=True, help="模块英文名（大驼峰），例如 FooClear")
    ap.add_argument("--repo", required=True, help="GitHub 仓库，例如 wzgrx/FooClear")
    ap.add_argument("--launch-activity", default="", help="宿主启动页完整类名（不知道就先不给，见 docs/11「查启动页类名」）")
    a = ap.parse_args()
    check_args(a)
    out = Path(os.path.expanduser(a.out)).resolve()
    if out.exists():
        fail(f"{out} 已经存在；换个目录，或者确认没用后自己删掉")
    copy_tree(out)
    changed = rewrite_text(out, replacements(a))
    moved = move_packages(out, a.package)
    left = leftovers(out, bool(a.launch_activity))
    print(f"已生成 {out}：替换了 {changed} 个文件，挪了 {moved} 个源码目录")
    if left:
        print("错误：还有没替换的占位：", file=sys.stderr)
        for h in left[:40]:
            print("  " + h, file=sys.stderr)
        sys.exit(1)
    dexadapt = out.parent / "dexadapt"
    print()
    print("下一步：")
    if not dexadapt.is_dir():
        print(f"  0. 没找到 {dexadapt}：DexAdapt 要和项目并排放（见 ClearKit docs/10-environment.md「DexAdapt」）")
    print(f"  1. cd {out} && git init && git add -A && git commit -m '从 ClearKit 模板生成'")
    print("  2. scripts/verify.sh --fast   # 应该全部通过")
    if not a.launch_activity:
        print("  3. 宿主启动页类名还没填：按 ClearKit docs/11-cookbook.md「查启动页类名」改 ExampleSymbols.kt")
    print("  4. 读新项目的 AGENTS.md，从 docs/TASKS.md 的 T01 开始")


if __name__ == "__main__":
    main()
