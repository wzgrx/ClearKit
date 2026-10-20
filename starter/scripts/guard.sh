#!/usr/bin/env bash
# 改动护栏：检查这次改动（相对上一次提交）有没有越界。scripts/verify.sh 和提交前钩子（.githooks/pre-commit）都会跑。
# 用法：scripts/guard.sh [--staged]（--staged 只看已暂存的改动，提交前钩子用）
#
# 会失败的情况（失败信息说明怎么办）：
#   1. 改了框架目录：hook/runtime、tools、scripts、gradle、构建脚本、.githooks。维护者确认要改时：GUARD_ALLOW=1 scripts/verify.sh
#   2. 测试变少了（删了测试、@Test 数量减少）或加了 @Ignore
#   3. 手改了生成的文件（HostAccess.kt、symbols/*.json、docs/adapt/）却没改符号定义
#   4. 要提交密钥库、签名配置
# 不是 git 仓库或还没有提交时跳过。
set -euo pipefail
cd "$(dirname "$0")/.."
git rev-parse --verify -q HEAD >/dev/null 2>&1 || { echo "护栏：还没有提交，跳过"; exit 0; }
staged=false
[[ "${1:-}" == "--staged" ]] && staged=true

if $staged; then
  changed=$(git diff --cached --name-only --diff-filter=ACMRD)
  diff_text=$(git diff --cached -U0 -- '*/src/test/*')
else
  changed=$( { git diff --name-only --diff-filter=ACMRD HEAD; git ls-files --others --exclude-standard; } | sort -u)
  diff_text=$(git diff -U0 HEAD -- '*/src/test/*')
fi
[[ -z "$changed" ]] && { echo "护栏：没有改动"; exit 0; }
fail=0
err() { echo "护栏不通过：$*" >&2; fail=1; }

# 1. 框架目录
if [[ "${GUARD_ALLOW:-}" != "1" ]]; then
  protected=$(grep -E '^(hook/runtime/|tools/|scripts/|gradle/|\.githooks/|settings\.gradle\.kts$|build\.gradle\.kts$|[^/]+(/[^/]+)?/build\.gradle\.kts$)' <<<"$changed" | grep -v '/access/HostAccess\.kt$' || true)
  [[ -n "$protected" ]] && err "改了框架文件（要改先问维护者，确认后用 GUARD_ALLOW=1）：
$(sed 's/^/    /' <<<"$protected")"
fi

# 2. 测试变少、@Ignore
count_head=$(git grep -h -c '@Test' HEAD -- '*/src/test/*' 2>/dev/null | awk '{s+=$1} END {print s+0}')
if $staged; then
  count_now=$(git grep --cached -h -c '@Test' -- '*/src/test/*' 2>/dev/null | awk '{s+=$1} END {print s+0}')
else
  count_now=$(git ls-files -co --exclude-standard -- '*/src/test/*' | while read -r f; do [[ -f "$f" ]] && { grep -c '@Test' "$f" || true; }; done | awk '{s+=$1} END {print s+0}')
fi
(( count_now < count_head )) && err "测试从 $count_head 个变成了 $count_now 个。不许删测试让检查通过；测试失败说明代码有问题，改代码"
grep -qE '^\+.*(@([a-z]+\.)*(Ignore|Disabled)\b|assumeTrue\(false\)|assumeTrue\(\s*false)' <<<"$diff_text" && err "加了 @Ignore / @Disabled / assumeTrue(false)：不许跳过测试"

# 3. 生成文件
generated=$(grep -E '(/access/HostAccess\.kt|core/symbols/src/main/resources/symbols/.*\.json|^docs/adapt/)' <<<"$changed" || true)
sources=$(grep -E '(^core/symbols/src/main/kotlin/|/model/HostBuild\.kt$)' <<<"$changed" || true)
[[ -n "$generated" && -z "$sources" ]] && err "改了生成的文件但没改符号定义（这些文件由 ./gradlew :tools:symbolfinder:run 生成，不要手改）：
$(sed 's/^/    /' <<<"$generated")"

# 4. 密钥
secrets=$(grep -iE '(\.jks|\.keystore|\.p12|signing\.env|\.env)$' <<<"$changed" || true)
[[ -n "$secrets" ]] && err "这些是签名或口令文件，永远不进仓库：
$(sed 's/^/    /' <<<"$secrets")"

(( fail == 0 )) && echo "护栏通过（$(wc -l <<<"$changed") 个文件有改动）"
exit $fail
