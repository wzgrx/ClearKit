package io.github.clearkit.starter.model.settings

/**
 * 设置值从哪里选（App 的设置页和宿主里的设置窗共用）：
 * - [CATALOG_SETS]：多选，选项来自宿主报来的真实目录（例如底栏项目），不是写死的；
 * - [ORDERS]：排序，按点选的先后排；
 * - [PALETTE]：颜色设置的色板。
 */
object SettingChoices {
    /** 选项来自宿主目录的多选（键）。模板里没有，做“隐藏底栏某项”这类功能时加。 */
    val CATALOG_SETS: Set<String> = emptySet()

    /**
     * 选上 = 隐藏的多选：界面上选上的项变灰、划掉、打叉，而不是打勾，免得看成“要”。
     * 目录里选的都算；固定选项的按标题里的“隐藏”或键名以 Hide 结尾认。
     */
    fun isHideSet(s: Setting<*>): Boolean = s is StringSetSetting &&
        (s.key in CATALOG_SETS || (s.choices.isNotEmpty() && ("隐藏" in s.title || s.key.endsWith("Hide"))))

    val CATALOG_SINGLE: Set<String> = emptySet()

    /** 排序设置 → 可排的项（存逗号分隔的标识）。 */
    val ORDERS: Map<String, List<Pair<String, String>>> = emptyMap()

    /** 色板：名字和颜色（不透明，存成 FF + RRGGBB）。 */
    val PALETTE: List<Pair<String, String>> = listOf(
        "白色" to "FFFFFF", "黑色" to "000000", "浅灰" to "CCCCCC", "深灰" to "444444", "粉色" to "FB7299", "红色" to "F44336",
        "橙色" to "FF9800", "黄色" to "FFEB3B", "绿色" to "4CAF50", "青色" to "00BCD4", "蓝色" to "2196F3", "紫色" to "9C27B0",
    )
}
