package io.github.clearkit.starter.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import io.github.clearkit.starter.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.clearkit.starter.app.ModuleApp
import io.github.clearkit.starter.app.data.ModuleService
import io.github.clearkit.starter.app.ui.LocalNavigator
import io.github.clearkit.starter.app.ui.components.BcIcons
import io.github.clearkit.starter.app.ui.components.HueTile
import io.github.clearkit.starter.app.ui.components.PageGutter
import io.github.clearkit.starter.app.ui.components.PillButton
import io.github.clearkit.starter.app.ui.components.PillStyle
import io.github.clearkit.starter.app.ui.theme.Bc
import io.github.clearkit.starter.app.ui.theme.Hue
import io.github.clearkit.starter.model.settings.Setting
import io.github.clearkit.starter.model.settings.Settings

/** 第一次打开要不要引导：没看过、也几乎没改过设置（从旧版升级上来的不打扰）。 */
fun needsOnboarding(): Boolean {
    val app = ModuleApp.instance
    if (app.settings[Settings.Module.onboarded]) return false
    return app.settings.stored().keys.count { !it.startsWith("module.") } < 3
}

/**
 * 起点方案：推荐（默认值）、全部关闭。加了新功能后，想让“推荐”多开几项，在第一个 Map 里写 `设置 to 值`。
 */
private val STARTS: List<Triple<String, String, Map<Setting<*>, Any>>> by lazy {
    val off = Settings.groups.filter { FeatureCatalogGroups.contains(it) }.flatMap { it.all }.filterIsInstance<io.github.clearkit.starter.model.settings.BoolSetting>().associate<io.github.clearkit.starter.model.settings.BoolSetting, Setting<*>, Any> { it to false }
    listOf(
        Triple("推荐", "用默认设置：去广告类开着，其它关着", emptyMap()),
        Triple("全部关闭", "自己一项一项开", off),
    )
}

private val FeatureCatalogGroups by lazy { io.github.clearkit.starter.model.settings.FeatureCatalog.pages.map { it.group }.toSet() }

/** 首次打开（设计“首次打开”）：三步检查 + 选一个起点方案。 */
@Composable
fun OnboardingPage() {
    val app = ModuleApp.instance
    val nav = LocalNavigator.current
    val c = Bc.colors
    val module by collect(ModuleService.state)
    val hosts by collect(app.hostAdapter.statuses)
    val reports by collect(app.status.all)
    var pick by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val active = module as? ModuleService.State.Active
    val installed = hosts.values.filter { it.installed }
    fun finish(apply: Boolean) {
        if (apply) {
            val values = STARTS[pick].third
            if (values.isNotEmpty()) {
                runCatching { app.configHistory.save(app.settings.stored(), "首次引导前") }
                app.journal.paused { app.settings.setAll(*values.map { it.key to it.value }.toTypedArray()) }
            }
        }
        app.settings.set(Settings.Module.onboarded, true)
        nav.back()
    }
    Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PageGutter + 4.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(BcIcons.Shield, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(30.dp))
            }
            Text("欢迎使用【模块】", style = Bc.type.pageTitle.copy(fontSize = Bc.type.pageTitle.fontSize * 1.15f), color = c.text)
            Text("三步准备好。设置都存在本机，不登录、不上传。", style = Bc.type.body, color = c.textSecondary)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
                val patched = active == null && patchedRecently(reports.values)
                Step(
                    1, "在 LSPosed 里启用模块并勾选【宿主】",
                    when {
                        patched -> "免根权限模式：【宿主】已用 LSPatch / NPatch 加载模块"
                        active == null -> "还没检测到模块服务；免根权限修补的【宿主】打开一次也行"
                        active.scope.isEmpty() -> "已启用，但还没勾选【宿主】"
                        else -> "已检测到 · 作用域 ${active.scope.size} 个"
                    },
                    if (patched || active != null && active.scope.isNotEmpty()) 2 else if (module is ModuleService.State.Connecting) 1 else 0,
                )
                val ready = installed.filter { it.ready }
                Step(
                    2, "打开一次【宿主】",
                    when {
                        installed.isEmpty() -> "没找到【宿主】，先装好【宿主】"
                        reports.isNotEmpty() -> "已连上 · " + ready.joinToString("、") { "${it.channel?.label} ${it.versionName}" }
                        ready.isNotEmpty() -> "已适配 " + ready.joinToString("、") { "${it.channel?.label} ${it.versionName}" } + "，打开一次让模块连上"
                        else -> "打开后会自动适配，大约几秒"
                    },
                    if (reports.isNotEmpty()) 2 else if (installed.isNotEmpty()) 1 else 0,
                )
                Step(3, "选一个起点", "以后随时能在“我的 → 一键配置方案”里改", 0)
            }
            STARTS.forEachIndexed { i, (title, summary, _) ->
                val on = i == pick
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(if (on) c.accentSoft else c.surface).border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line, shape)
                        .clickable(role = Role.RadioButton) { pick = i }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HueTile(listOf(BcIcons.Star, BcIcons.AdOff, BcIcons.Power)[i], if (i == 0) Hue.HOME else Hue.GRAY)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = Bc.type.itemTitle, fontWeight = FontWeight.Bold, color = c.text)
                        Text(summary, style = Bc.type.caption, color = c.textSecondary)
                    }
                    if (on) Icon(BcIcons.Check, contentDescription = "已选", tint = c.accent, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PageGutter + 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PillButton("开始使用", { finish(true) }, Modifier.fillMaxWidth(), height = 52.dp)
            PillButton("从备份恢复", { app.settings.set(Settings.Module.onboarded, true); nav.replace("config") }, Modifier.fillMaxWidth(), PillStyle.Text)
        }
    }
}

/** 引导的一步：完成（对勾）、进行中（转圈）、没开始（序号）。[state] 0/1/2。 */
@Composable
private fun Step(n: Int, title: String, summary: String, state: Int) {
    val c = Bc.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (state) {
                2 -> Box(Modifier.size(28.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(BcIcons.Check, contentDescription = "完成", tint = c.onAccent, modifier = Modifier.size(16.dp))
                }
                1 -> CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, trackColor = c.seg, strokeWidth = 3.dp)
                else -> Box(Modifier.size(28.dp).clip(CircleShape).border(2.dp, c.outline, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$n", style = Bc.type.caption, color = c.textSecondary)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Bc.type.itemTitle, color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = Bc.type.caption, color = c.textSecondary)
        }
    }
}
