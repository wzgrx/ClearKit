package io.github.clearkit.starter.model.settings

/** 一个分类里声明的设置，按声明顺序收集。 */
abstract class SettingGroup(val prefix: String) {
    private val own = mutableListOf<Setting<*>>()
    val all: List<Setting<*>> get() = own

    private fun <S : Setting<*>> add(s: S, soon: Boolean = false, legacy: String? = null): S =
        s.also { it.comingSoon = soon; it.legacyKey = legacy; own += it }
    private fun k(name: String) = "$prefix.$name"

    /** [soon]：功能还没实现，设置页标“即将支持”（见 [Setting.comingSoon]）。 */
    protected fun bool(name: String, default: Boolean, title: String, summary: String? = null, restart: Boolean = false, soon: Boolean = false, legacy: String? = null) =
        add(BoolSetting(k(name), default, title, summary, restart), soon, legacy)

    protected fun int(
        name: String, default: Int, title: String, summary: String? = null, restart: Boolean = false,
        range: IntRange = 0..Int.MAX_VALUE, unit: String = "", presets: List<Int> = emptyList(), soon: Boolean = false, legacy: String? = null,
    ) = add(IntSetting(k(name), default, title, summary, restart, range, unit, presets), soon, legacy)

    protected fun float(name: String, default: Float, title: String, summary: String? = null, presets: List<Float> = emptyList(), legacy: String? = null) =
        add(FloatSetting(k(name), default, title, summary, false, presets), legacy = legacy)

    protected fun string(
        name: String, default: String, title: String, summary: String? = null, restart: Boolean = false, soon: Boolean = false,
        format: TextFormat = TextFormat.PLAIN, presets: List<Pair<String, String>> = emptyList(), legacy: String? = null,
    ) = add(StringSetting(k(name), default, title, summary, restart, format, presets.map { ChoiceSetting.Option(it.first, it.second) }), soon, legacy)

    protected fun stringSet(
        name: String, default: Set<String>, title: String, summary: String? = null, restart: Boolean = false,
        choices: List<Pair<String, String>> = emptyList(), legacy: String? = null,
    ) = add(StringSetSetting(k(name), default, title, summary, restart, choices.map { ChoiceSetting.Option(it.first, it.second) }), legacy = legacy)

    protected fun choice(
        name: String, default: String, title: String, vararg options: Pair<String, String>,
        summary: String? = null, restart: Boolean = false, soon: Boolean = false, legacy: String? = null,
    ) = add(ChoiceSetting(k(name), default, title, options.map { ChoiceSetting.Option(it.first, it.second) }, summary, restart), soon, legacy)
}
