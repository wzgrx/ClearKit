#!/usr/bin/env bash
# 设备冒烟（真机或带 LSPosed 的模拟器）：装模块 → 重启【宿主】 → 读 LSPosed 模块日志，汇总
#   用的哪张表（内置 / 本机）、生效的功能数、契约判定、出错和崩溃。需要手机已 root、装了 LSPosed、在 LSPosed 里勾了【宿主】。
# 用法：scripts/smoke.sh <设备序列号> [--host <宿主包名>] [--apk <模块 APK>] [--min-active <最少生效功能数>] [--no-install]
#
# 共享设备规矩：前台不是桌面、【宿主】或【模块】时（别人在用）直接退出，不做任何操作；做完回到桌面。
set -euo pipefail
cd "$(dirname "$0")/.."
serial="${1:-}"; shift || true
[ -n "$serial" ] || { echo "用法：scripts/smoke.sh <设备序列号> [--host 包名] [--apk 文件] [--min-active N] [--no-install]" >&2; exit 2; }
apk=app/build/outputs/apk/release/app-release.apk
min_active=1
install=true
# 宿主包名、模块包名、日志标签都从代码里读，改名以后不用改这里
host=$(sed -n 's/^const val HOST_PACKAGE = "\(.*\)"/\1/p' core/model/src/main/kotlin/*/*/*/*/model/HostBuild.kt 2>/dev/null | head -1)
module=$(sed -n 's/^ *applicationId = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)
tag=$(sed -n 's/^ *private const val TAG = "\(.*\)"/\1/p' hook/runtime/src/main/kotlin/*/*/*/*/hook/runtime/ModuleLog.kt | head -1)
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) apk="$2"; shift 2 ;;
    --host) host="$2"; shift 2 ;;
    --min-active) min_active="$2"; shift 2 ;;
    --no-install) install=false; shift ;;
    *) echo "不认识的参数 $1" >&2; exit 2 ;;
  esac
done
adb_() { adb -s "$serial" "$@"; }
focus() { adb_ shell "dumpsys window | grep -m1 mCurrentFocus" 2>/dev/null | tr -d '\r'; }
gate() {
  local f; f=$(focus)
  if ! grep -qE "launcher|miui.home|$host|$module" <<<"$f"; then
    echo "停止：设备前台是别的应用（$f），可能有人在用" >&2; exit 4
  fi
}

adb_ get-state >/dev/null 2>&1 || { echo "设备 $serial 不在线" >&2; exit 3; }
gate
if $install; then
  [ -f "$apk" ] || { echo "没有 $apk（先 scripts/verify.sh）" >&2; exit 2; }
  echo "安装 $apk"
  adb_ install -r "$apk" | tail -1
  gate
fi
version=$(adb_ shell dumpsys package "$host" | grep -m1 versionCode | tr -d '\r' | sed 's/^ *//')
echo "【宿主】（$host）：$version"
start=$(adb_ shell date +%Y-%m-%dT%H:%M:%S | tr -d '\r')
adb_ logcat -c
adb_ shell am force-stop "$host"
adb_ shell monkey -p "$host" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 20
log=$(adb_ shell "su -c 'cat \$(ls -t /data/adb/lspd/log/modules_*.log | head -1)'" 2>/dev/null | tr -d '\r' | grep -a "$tag" | awk -v s="$start" '{ t=$2; if (t >= s) print }' || true)
crash=$(adb_ logcat -d -b crash 2>/dev/null | grep -aE "$host|$module" | head -5 || true)
adb_ shell input keyevent HOME

table=$(grep -aoE "using [a-z ()_]+ symbol table for [0-9]+: [0-9]+ found, [0-9]+ missing" <<<"$log" | tail -1 || true)
runtime=$(grep -aoE "runtime started: \{[^}]*\}" <<<"$log" | tail -1 || true)
active=$(grep -aoE "ACTIVE=[0-9]+" <<<"$runtime" | cut -d= -f2 || true)
failed=$(grep -aoE "FAILED=[0-9]+" <<<"$runtime" | cut -d= -f2 || true)
echo "表：${table:-（没找到）}"
echo "运行时：${runtime:-（没找到）}"
grep -aE "contract|observing|guessed location|adaptation" <<<"$log" | sed "s/^.*$tag[^]]*\] /  /" | tail -20 || true
errors=$(grep -aE " E/LSPosedFramework .*$tag" <<<"$log" | sed "s/^.*$tag[^]]*\] /  /" | head -10 || true)
[ -n "$errors" ] && { echo "模块报错："; echo "$errors"; }
[ -n "$crash" ] && { echo "崩溃："; echo "$crash"; }

ok=true
[ -n "$runtime" ] || { echo "失败：没看到运行时启动"; ok=false; }
[ -n "$active" ] && [ "$active" -ge "$min_active" ] || { echo "失败：生效功能 ${active:-0} 个，少于 $min_active"; ok=false; }
[ -z "${failed:-}" ] || [ "$failed" = 0 ] || { echo "失败：$failed 个功能出错"; ok=false; }
[ -z "$crash" ] || { echo "失败：有崩溃"; ok=false; }
$ok && echo "冒烟通过" || exit 1
