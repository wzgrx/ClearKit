package io.github.wzgrx.clearkit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 字号（规范 v1 “字号”）：九档，名字说用途。 */
@Immutable
data class ClearType(
    /** 46 / 54 · 900：状态卡的数字。 */
    val display: TextStyle = TextStyle(fontSize = 46.sp, lineHeight = 54.sp, fontWeight = FontWeight.Black),
    /** 26 / 34 · 900：一级页标题。 */
    val pageTitle: TextStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black),
    /** 20 / 28 · 700：二级页标题、弹层标题。 */
    val title: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    /** 17 / 24 · 700：首页分组、卡片大标题。 */
    val section: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    /** 16 / 22 · 700：分类行、卡片标题。 */
    val itemStrong: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    /** 15 / 21 · 500：设置行标题。 */
    val item: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    /** 14 / 22 · 400：正文、说明。 */
    val body: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    /** 13 / 18 · 500：分组标签。 */
    val label: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    /** 12 / 17 · 400：副标题、路径、时间。 */
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
)
