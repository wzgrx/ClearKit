# 10 · 开发环境（从一台空电脑开始）

照着从上往下做，每一步都有“检查”命令，检查通过再做下一步。系统用 Linux 或 Windows 的 WSL2（Ubuntu）。
版本号以模板里的 `gradle/libs.versions.toml` 和 `gradle/wrapper/gradle-wrapper.properties` 为准；下面写的是 2026-10 验证过的组合。

| 东西 | 版本（验证过） | 干什么用 |
|---|---|---|
| JDK | 21 或更新（用过 Temurin 27） | 跑 Gradle |
| Android SDK | platform `android-37.2`、build-tools `37.0.0`、platform-tools | 编译、adb |
| Gradle | 9.8.1（仓库自带 `./gradlew`，不用装） | 构建 |
| DexAdapt | `gradle/dexadapt.commit` 里记的提交 | 符号查询库，和项目并排放 |
| jadx | 1.5 以上 | 反编译宿主找 Hook 点 |
| Python | 3.10 以上 | 生成脚本、SVG 转换 |
| Blender | 5.2（4.2 以上可用） | 出图标（只有做图标时要） |

## 1. JDK

```bash
sudo apt install -y openjdk-21-jdk   # 或者下载 Temurin，解压后设 JAVA_HOME
java -version                         # 检查：21 或更新
```

## 2. Android SDK（只装命令行工具，不用装 Android Studio）

```bash
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
# 从 https://developer.android.com/studio#command-tools 下载 Linux 版 commandlinetools，解压后把目录改名为 latest
export ANDROID_HOME=~/Android/Sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;37.0.0"
ls $ANDROID_HOME/build-tools        # 检查：有 37.0.0
```

第一次构建时 Gradle 如果报“缺少某个 SDK 包”，按报错里的包名用 sdkmanager 装上再试。

## 3. 环境变量文件

模板的 `scripts/verify.sh` 会自动读 `~/.config/<模块英文名小写>/env.sh`（没有也行，但要自己 export）。内容：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
export HOST_APK_DIR=$HOME/projects/<模块英文名小写>-apks   # 宿主安装包，见第 6 节
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

检查：`source ~/.config/<名>/env.sh && java -version && adb version`

## 4. DexAdapt（必须和项目并排放）

项目用复合构建引用 `../dexadapt` 的源码，所以目录必须是：

```
~/projects/
  dexadapt/        ← 这里
  某某净界项目/     ← 新模块
```

```bash
cd ~/projects
git clone https://github.com/wzgrx/dexadapt.git   # 私有仓库：要先让维护者给你的 GitHub 账号读权限
git -C dexadapt checkout $(cat 某某净界项目/gradle/dexadapt.commit)
```

检查：`ls ~/projects/dexadapt/core/src/main/kotlin` 有内容。没有权限时停下来找维护者，不要自己找替代品。

## 5. 生成新项目并第一次构建

```bash
cd ~/projects
git clone https://github.com/wzgrx/ClearKit.git clear-kit   # 也是私有仓库，同样要读权限
python3 clear-kit/tools/new_module.py --out ~/projects/foo-clear \
  --name 某某净界 --host 某某 --host-package com.foo.app \
  --package io.github.wzgrx.fooclear --code FooClear --repo wzgrx/FooClear
cd foo-clear && git init && git config core.hooksPath .githooks && git add -A && git commit -m "从 ClearKit 模板生成"
scripts/verify.sh --fast     # 检查：最后一行“本地验证通过”
scripts/verify.sh            # 检查：同上，还会打出 release 包
```

第一次构建要下载依赖，可能十几分钟。网络不通时先解决网络（代理），不要改构建脚本。

## 6. 收集宿主安装包

内置适配要用真实安装包。从装了宿主的手机上拉（不用 root）：

```bash
adb devices                                        # 检查：手机在列表里
adb shell pm path com.foo.app                      # 看安装包路径，可能有好几个（base.apk + split_*.apk）
adb shell dumpsys package com.foo.app | grep -m2 -E "versionCode|versionName"
mkdir -p $HOST_APK_DIR/main-<versionCode>
adb pull <上面看到的 base.apk 路径> $HOST_APK_DIR/main-<versionCode>/base.apk
```

- 目录名：`<渠道名小写>-<versionCode>`，渠道名就是 `HostBuild.Channel` 里的名字（模板只有 `MAIN`，所以是 `main-…`）。
- 不要从来路不明的网站下载安装包；只用手机上装的官方版本。
- 安装包不进仓库（`.gitignore` 已经排除 `*.apk`）。

拿到以后按 [11-cookbook.md](11-cookbook.md)「换成真实的宿主版本」改 `HostBuild.kt`。

## 7. 反编译（找 Hook 点）

```bash
jadx -d ~/projects/<名>-src/main-<versionCode> --show-bad-code $HOST_APK_DIR/main-<versionCode>/base.apk
grep -rn "界面上看到的字" ~/projects/<名>-src/main-<versionCode>/resources/res/values/strings.xml
```

反编译源码只放本地，不进仓库。

## 8. 签名

- 平时：没配置签名时自动用调试签名，能装能测。
- 发布前生成正式密钥库（只做一次，**密钥库和口令备份好，丢了以后就没法覆盖安装**）：

```bash
mkdir -p ~/.config/<名>
keytool -genkeypair -v -keystore ~/.config/<名>/release.jks -alias release -keyalg RSA -keysize 4096 -validity 36500
cat > ~/.config/<名>/signing.env <<'EOF'
MODULE_RELEASE_STORE_FILE=/home/你/.config/<名>/release.jks
MODULE_RELEASE_STORE_PASSWORD=你的口令
MODULE_RELEASE_KEY_ALIAS=release
MODULE_RELEASE_KEY_PASSWORD=你的口令
EOF
chmod 600 ~/.config/<名>/signing.env
```

- 第一次正式发布后，把证书指纹写进 `gradle/release-cert.sha256`（`apksigner verify --print-certs` 打印的 SHA-256），
  以后 `scripts/verify.sh --release` 会核对。
- **密钥库、口令、signing.env 永远不进仓库，不贴进对话和日志。**

## 9. 可选：电脑上跑 DexKit 一致性检查

`tools/symbolfinder` 的 `DexKitParityTest` 用电脑版 DexKit 模拟手机上的本机适配。需要 git、cmake、ninja、clang：

```bash
sudo apt install -y cmake ninja-build clang
scripts/build-dexkit-desktop.sh     # 检查：最后一行“完成：…/libdexkit.so”
```

没有它时这个测试自动跳过，不影响平时开发。

## 10. 真机（测试 Hook）

- 手机要 root（Magisk / KernelSU），装 LSPosed（支持 API 102 的版本）。
- 装模块 → LSPosed 里启用模块、勾选宿主 → 重开宿主。
- 冒烟：`scripts/smoke.sh <设备序列号>`；看日志：LSPosed 管理器 › 日志，或 `adb shell su -c 'cat /data/adb/lspd/log/modules_*.log'`。
- 共用的测试机：先看前台（`adb shell dumpsys window | grep mCurrentFocus`），别人在用就别动。
