# 【模块】R8 规则。只做裁剪，不混淆：
# - CrashGuard 按包名把崩溃栈归到功能（每个功能一个包），日志里的栈也要可读。
-dontobfuscate

# LSPosed 按 META-INF/xposed/java_init.list 里的类名加载入口。
-keep class io.github.clearkit.starter.hook.ModuleEntry { <init>(...); *; }

# libxposed api 由框架在运行时提供（compileOnly），不在 APK 里。
-dontwarn io.github.libxposed.api.**
-dontwarn io.github.libxposed.annotation.**

# 【宿主】进程内的代码：允许删掉没用到的，但不合并、不搬动类（CrashGuard 靠包名定位功能）。
-keep,allowshrinking class io.github.clearkit.starter.hook.** { *; }

# 崩溃栈带行号。
-keepattributes SourceFile,LineNumberTable
