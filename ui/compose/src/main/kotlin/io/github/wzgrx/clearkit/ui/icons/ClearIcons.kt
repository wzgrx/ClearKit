package io.github.wzgrx.clearkit.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 家族统一的线条图标（24 格、2 线宽、圆头），和设计稿用同一套路径，不引入整套图标库。
 * 路径写成 SVG 的 d 字符串；圆和圆角矩形用 [circle]、[rect] 换成路径。
 */
object ClearIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    private fun icon(name: String, vararg d: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            d.forEach {
                addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()

    val Home = icon("home", "M4 11l8-7 8 7v9H4z")
    val Grid = icon("grid", rect(4f, 4f, 7f, 7f, 2f), rect(13f, 4f, 7f, 7f, 2f), rect(4f, 13f, 7f, 7f, 2f), rect(13f, 13f, 7f, 7f, 2f))
    val Person = icon("person", circle(12f, 8f, 4f), "M4 20c1.5-4 4.5-6 8-6s6.5 2 8 6")
    val Wrench = icon("wrench", "M14.5 4.2c2.7-.8 5.7 1.4 5.3 4.4l-2.4-.4-1.6-1.6-.4-2.2M19.8 8.6c-.6 2.6-3.4 4-5.7 3l-7.5 7.5c-.8.8-2.2.8-2.2-.6 0-.9.5-1.1.5-1.1l7.5-7.5c-1-2.3 0-4.9 2.1-5.7")
    val Chevron = icon("chevron", "M9 6l6 6-6 6")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Search = icon("search", circle(11f, 11f, 7f), "M20 20l-3.5-3.5")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Check = icon("check", "M5 12l4 4 10-10")
    val Video = icon("video", rect(3f, 5f, 18f, 14f, 3f), "M10 9l5 3-5 3z")
    val Dynamic = icon("dynamic", circle(6f, 18f, 2f), "M4 4a16 16 0 0 1 16 16M4 10a10 10 0 0 1 10 10")
    val Comment = icon("comment", "M4 5h16v11H9l-5 4z", "M8 9h8M8 12h5")
    val Live = icon("live", circle(12f, 12f, 2f), "M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4M5 5a10 10 0 0 0 0 14M19 5a10 10 0 0 1 0 14")
    val Globe = icon("globe", circle(12f, 12f, 9f), "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18")
    val Download = icon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Upload = icon("upload", "M12 20V9M7 14l5-5 5 5M5 4h14")
    val Cloud = icon("cloud", "M7 18a5 5 0 0 1-.5-10A6 6 0 0 1 18 9a4.5 4.5 0 0 1-1 9z")
    val Capsule = icon("capsule", rect(3f, 8f, 18f, 8f, 4f), circle(8f, 12f, 1.5f))
    val Palette = icon("palette", circle(12f, 12f, 9f), circle(8.5f, 10f, 1.2f), circle(12f, 7.5f, 1.2f), circle(15.5f, 10f, 1.2f), "M12 21a3 3 0 0 1 0-6h2")
    val Filter = icon("filter", "M4 6h16M7 12h10M10 18h4")
    val AdOff = icon("adoff", rect(5f, 3f, 14f, 18f, 2f), "M9 9l6 6M15 9l-6 6")
    val Skip = icon("skip", "M5 5l9 7-9 7z", "M18 5v14")
    val Timer = icon("timer", circle(12f, 13f, 8f), "M12 9v4l2 2M9 2h6")
    val Gear = icon("gear", circle(12f, 12f, 3f), "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9L7 7M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val Star = icon("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.5 2.9 1-6.1-4.3-4.3 6.1-.9z")
    val Clock = icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Alert = icon("alert", "M12 3l10 18H2z", "M12 10v4M12 17v.5")
    val Shield = icon("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M9 12l2 2 4-4")
    val Block = icon("block", circle(12f, 12f, 9f), "M5.6 5.6l12.8 12.8")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Paste = icon("paste", rect(6f, 4f, 12f, 17f, 2f), "M9 4V3h6v1M9 10h6M9 14h6")
    val Doc = icon("doc", "M6 3h9l4 4v14H6z", "M9 12h7M9 16h7")
    val Adapt = icon("adapt", "M12 3v4M12 17v4M3 12h4M17 12h4", circle(12f, 12f, 3f))
    val Power = icon("power", "M12 3v8", "M6.3 7.5a8 8 0 1 0 11.4 0")
    val Image = icon("image", rect(3f, 5f, 18f, 14f, 2f), circle(9f, 10f, 2f), "M21 16l-5-5-8 8")
    val ListLines = icon("list", "M4 6h16M4 12h10M4 18h7")
    val Dots = icon("dots", circle(5f, 12f, 1.2f), circle(12f, 12f, 1.2f), circle(19f, 12f, 1.2f))
    val Restart = icon("restart", "M20 12a8 8 0 1 1-2.3-5.6", "M20 4v5h-5")
    val Eye = icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val EyeOff = icon("eyeoff", "M3 3l18 18", "M10.6 5.1A10 10 0 0 1 22 12s-1 2-3 4", "M6.6 6.6C3.8 8.5 2 12 2 12s3.5 7 10 7c1.8 0 3.4-.5 4.7-1.3")
    val Lock = icon("lock", rect(5f, 11f, 14f, 10f, 2f), "M8 11V8a4 4 0 0 1 8 0v3")
    val Moon = icon("moon", "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z")
    val Bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4")
    val Focus = icon("focus", circle(12f, 12f, 8f), circle(12f, 12f, 3f))
    val Share = icon("share", circle(6f, 12f, 2.5f), circle(18f, 6f, 2.5f), circle(18f, 18f, 2.5f), "M8.2 10.8l7.6-3.6M8.2 13.2l7.6 3.6")
    val Link = icon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val History = icon("history", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5", "M12 8v4l3 2")
    val Package = icon("package", "M3 7l9-4 9 4v10l-9 4-9-4z", "M3 7l9 4 9-4M12 11v10")
    val Info = icon("info", circle(12f, 12f, 9f), "M12 11v5M12 8v.5")
    val Phone = icon("phone", rect(6f, 2f, 12f, 20f, 3f), "M11 18h2")
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4")
    val Trash = icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    // 统一设计 v1 补的
    val Media = icon("media", rect(3f, 4f, 18f, 16f, 3f), "M3 9h18M8 4l2 5M14 4l2 5", "M10.5 12.5l4 2.5-4 2.5z")
    val Speed = icon("speed", "M4 16a8 8 0 1 1 16 0", "M12 16l4-5", "M4 16h2M18 16h2")
    val Location = icon("location", "M12 21s-7-6-7-11a7 7 0 0 1 14 0c0 5-7 11-7 11z", circle(12f, 10f, 2.5f))
    val Archive = icon("archive", rect(3f, 4f, 18f, 5f, 1.5f), "M5 9v10h14V9", "M10 13h4")
    val Folder = icon("folder", "M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Tag = icon("tag", "M3 12V4h8l10 10-8 8z", circle(7.5f, 8.5f, 1.2f))
    val Bookmark = icon("bookmark", "M6 3h12v18l-6-4-6 4z")
    val Sliders = icon("sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Sparkle = icon("sparkle", "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z")
    val Copy = icon("copy", rect(8f, 8f, 13f, 13f, 2f), "M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3")
    val Play = icon("play", "M7 5l12 7-12 7z")
    val Pause = icon("pause", "M8 5v14M16 5v14")
    val Heart = icon("heart", "M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z")
    val Calendar = icon("calendar", rect(3f, 5f, 18f, 16f, 2f), "M3 10h18M8 3v4M16 3v4")
    val Music = icon("music", "M9 18V6l11-2v12", circle(6f, 18f, 3f), circle(17f, 16f, 3f))
    val Danmaku = icon("danmaku", rect(3f, 5f, 18f, 14f, 3f), "M7 10h6M7 14h10")
    val Layers = icon("layers", "M12 3l9 5-9 5-9-5z", "M3 13l9 5 9-5")
    val Network = icon("network", circle(12f, 5f, 2f), circle(5f, 19f, 2f), circle(19f, 19f, 2f), "M12 7v5M12 12l-6 5M12 12l6 5")
    val Pulse = icon("pulse", "M3 12h4l3-7 4 14 3-7h4")
    val Key = icon("key", circle(8f, 15f, 4f), "M11 12l9-9M17 6l3 3")
    val TextLines = icon("text", "M5 5h14M12 5v14M9 19h6")
    val Sun = icon("sun", circle(12f, 12f, 4f), "M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val Drag = icon("drag", "M5 9h14M5 15h14")
    val Wand = icon("wand", "M4 20L16 8", "M15 4v2M19 8h2M18 5l1.5-1.5M14 4.5", "M20 13v2M10 3H8")
    val External = icon("external", "M14 4h6v6M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
    val User = icon("user2", circle(9f, 8f, 3.5f), "M2 20c1-3.5 3.6-5.5 7-5.5s6 2 7 5.5", "M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5c2 .8 3.4 2.6 4 5.5")
    val Minus = icon("minus", "M5 12h14")
}
