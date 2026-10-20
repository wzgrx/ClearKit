#!/usr/bin/env bash
# 本地门禁：全部单测（有安装包时含符号表校验、DexKit 一致性）+ lint（错误和警告都算失败）+ release APK + 内容检查。
# 用法：scripts/verify.sh [--fast | --release]
#   不带参数：单测 + lint + release APK（有安装包就校验符号表，没有就跳过）。**每次提交前跑这个。**
#   --fast：只跑单测（改代码过程中用）。
#   --release：发布前用。要求每个内置版本都有安装包、符号表全部解析成功、用正式签名。
# 环境（JDK、ANDROID_HOME、HOST_APK_DIR）见 ClearKit docs/10-environment.md；可以写在 ~/.config/clearstarter/env.sh 里。
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f "${MODULE_ENV:-$HOME/.config/clearstarter/env.sh}" ]] && source "${MODULE_ENV:-$HOME/.config/clearstarter/env.sh}"
: "${ANDROID_HOME:?没设 ANDROID_HOME（Android SDK 目录），见 ClearKit docs/10-environment.md}"
# 先查改动有没有越界、任务卡格式对不对（几秒钟，失败信息会说怎么改）
scripts/guard.sh
python3 scripts/check_tasks.py
fast=false; release=false
[[ "${1:-}" == "--fast" ]] && fast=true
[[ "${1:-}" == "--release" ]] && release=true
# 固定 DexAdapt 版本（复合构建引用 ../dexadapt 的源码）：发版时它必须正好在记下的提交、没有未提交改动，旧版才能原样重建。
if $release && [[ -f gradle/dexadapt.commit && -d ../dexadapt/.git ]]; then
  want=$(cat gradle/dexadapt.commit)
  have=$(git -C ../dexadapt rev-parse HEAD)
  if [[ "$have" != "$want" ]]; then
    echo "错误：../dexadapt 在 $have，应在 $want（gradle/dexadapt.commit）；核对无误后把文件更新成新值" >&2; exit 1
  fi
  if [[ -n "$(git -C ../dexadapt status --porcelain)" ]]; then
    echo "错误：../dexadapt 有未提交的改动，发版前先提交，再更新 gradle/dexadapt.commit" >&2; exit 1
  fi
fi
tasks=(test)
if ! $fast; then
  if $release; then
    if [[ -z "${HOST_APK_DIR:-}" || ! -d "$HOST_APK_DIR" ]]; then
      echo "错误：--release 需要【宿主】安装包，HOST_APK_DIR 没设或目录不存在（${HOST_APK_DIR:-未设置}）" >&2
      exit 1
    fi
    export HOST_REQUIRE_APKS=1
  fi
  source scripts/signing-env.sh
  trap 'unset MODULE_RELEASE_STORE_PASSWORD MODULE_RELEASE_KEY_PASSWORD' EXIT
  if $release && [[ -z "${MODULE_RELEASE_STORE_FILE:-}" ]]; then echo "错误：--release 需要正式签名（scripts/signing-env.sh）" >&2; exit 1; fi
  tasks+=(:app:lintRelease :hook:ui:lintRelease :hook:features:lintRelease :hook:runtime:lintRelease :app:assembleRelease)
fi
# 签名口令是构建输入，不能进配置缓存
./gradlew --console=plain --no-configuration-cache "${tasks[@]}"
if ! $fast; then
  report=tools/symbolfinder/build/test-results/test/TEST-io.github.clearkit.starter.tools.symbolfinder.SymbolTablesTest.xml
  if $release && { [[ ! -f "$report" ]] || ! grep -q 'tests="[1-9][0-9]*" skipped="0" failures="0" errors="0"' "$report"; }; then
    echo "错误：符号表校验（SymbolTablesTest）没有真正跑过或没通过：$report" >&2
    exit 1
  fi
  warn=0
  for r in app hook/ui hook/features hook/runtime; do
    xml="$r/build/reports/lint-results-release.xml"
    if [[ ! -f "$xml" ]]; then echo "错误：找不到 lint 报告 $xml" >&2; exit 1; fi
    n=$(grep -cE '<issue([[:space:]]|$)' "$xml" || true)
    if [[ "$n" -gt 0 ]]; then
      warn=$((warn + n))
      grep -o 'id="[^"]*"\|file="[^"]*"\|line="[^"]*"' "$xml" | paste -sd' ' | sed 's/ id=/\nid=/g' | sed "s|^|  $r: |" >&2
    fi
  done
  if [[ "$warn" -gt 0 ]]; then echo "错误：lint 有 $warn 个问题" >&2; exit 1; fi
  apk=app/build/outputs/apk/release/app-release.apk
  # 正式签名的证书指纹：第一次发布后把 apksigner 打印的 SHA-256 写进 gradle/release-cert.sha256，以后每次核对（换了签名用户就没法覆盖安装）
  if $release && [[ -f gradle/release-cert.sha256 ]]; then
    signer=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)
    cert=$("$signer" verify --print-certs "$apk" 2>/dev/null | sed -n 's/.*SHA-256 digest: //p' | head -1)
    if [[ "$cert" != "$(tr -d ' \n' < gradle/release-cert.sha256)" ]]; then
      echo "错误：签名证书不对（$cert），和 gradle/release-cert.sha256 不一致" >&2; exit 1
    fi
  fi
  entries=$(unzip -Z1 "$apk")
  need=(META-INF/xposed/java_init.list META-INF/xposed/module.prop META-INF/xposed/scope.list)
  for t in core/symbols/src/main/resources/symbols/*.json; do need+=("symbols/$(basename "$t")"); done
  for e in "${need[@]}"; do
    if ! grep -qxF "$e" <<<"$entries"; then echo "错误：APK 里缺少 $e" >&2; exit 1; fi
  done
  echo "APK: $apk ($(du -h "$apk" | cut -f1))，入口和符号表齐全"
fi
echo "本地验证通过"
