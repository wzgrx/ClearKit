#!/usr/bin/env python3
"""
检查 docs/TASKS.md 的任务卡格式（格式说明见 ClearKit docs/09-low-capability-models.md 第 3 节）。
scripts/verify.sh 会跑；不合格时列出哪张卡缺什么。用法：python3 scripts/check_tasks.py [TASKS.md 路径]
"""
import re
import sys
from pathlib import Path

STATES = {"待做", "进行中", "完成", "卡住"}
REQUIRED = ["级别", "要读", "改的文件", "完成标准"]

path = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/TASKS.md")
if not path.is_file():
    print(f"任务卡检查：没有 {path}，跳过")
    sys.exit(0)
text = path.read_text(encoding="utf-8")
cards = re.split(r"^## ", text, flags=re.M)[1:]
problems, ids, doing = [], set(), 0
for card in cards:
    head, _, body = card.partition("\n")
    m = re.match(r"(T\d+)\s+(.+?)\s+——\s+(\S+)\s*$", head.strip())
    if not m:
        problems.append(f"标题格式不对：“## {head.strip()}”，应该是“## T07 一句话标题 —— 待做”")
        continue
    tid, _, state = m.groups()
    if tid in ids:
        problems.append(f"{tid}：编号重复")
    ids.add(tid)
    if state not in STATES:
        problems.append(f"{tid}：状态“{state}”不认识，只能是 {'/'.join(sorted(STATES))}")
    if state == "进行中":
        doing += 1
    if state in {"待做", "进行中"}:
        for field in REQUIRED:
            if not re.search(rf"^- {field}[：:]", body, flags=re.M):
                problems.append(f"{tid}：缺“- {field}：”")
        level = re.search(r"^- 级别[：:]\s*(\S+)", body, flags=re.M)
        if level and level.group(1) not in {"L1", "L2", "L3"}:
            problems.append(f"{tid}：级别只能是 L1 / L2 / L3")
    if state == "完成" and not re.search(r"验证", body):
        problems.append(f"{tid}：完成的卡要写验证结果（“- 验证：命令和结果”）")
if doing > 1:
    problems.append(f"同时有 {doing} 张卡“进行中”：一次只做一张")
if problems:
    print("任务卡检查不通过：", file=sys.stderr)
    for p in problems:
        print("  " + p, file=sys.stderr)
    sys.exit(1)
print(f"任务卡检查通过（{len(cards)} 张）")
