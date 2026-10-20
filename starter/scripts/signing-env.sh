# 发布签名：用 source 载入（scripts/verify.sh 自动载入）。签名材料放在仓库外，默认文件：
#   ~/.config/clearstarter/signing.env（可用 MODULE_SIGNING_ENV 指定别的路径），内容四行：
#     MODULE_RELEASE_STORE_FILE=/绝对路径/release.jks
#     MODULE_RELEASE_STORE_PASSWORD=...
#     MODULE_RELEASE_KEY_ALIAS=...
#     MODULE_RELEASE_KEY_PASSWORD=...
# 怎么生成密钥库见 ClearKit docs/10-environment.md「签名」。没有这个文件时用调试签名（能装能测，不能发布）。
# **密钥库和这个文件永远不要提交进仓库。**
_mod_env="${MODULE_SIGNING_ENV:-$HOME/.config/clearstarter/signing.env}"
if [[ ! -f "$_mod_env" ]]; then
  echo "提示：没有签名配置 $_mod_env，用调试签名构建" >&2
  unset _mod_env
  return 0 2>/dev/null || exit 0
fi
set -a
# shellcheck disable=SC1090
source "$_mod_env"
set +a
unset _mod_env
if [[ ! -f "${MODULE_RELEASE_STORE_FILE:-}" || ${#MODULE_RELEASE_STORE_PASSWORD} -lt 6 ]]; then
  echo "错误：签名配置不完整（密钥库不存在或没有口令）" >&2
  return 1 2>/dev/null || exit 1
fi
