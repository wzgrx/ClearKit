package io.github.clearkit.starter.model.net

/** 写日志前给凭据打码：JSON 里名字带 token、access_key、cookie、sessdata、bili_jct、password 的字段值，和网址里的同名参数。 */
object LogRedact {
    private const val NAMES = "[A-Za-z_]*(?:token|Token|TOKEN|access_key|accessKey|cookie|Cookie|SESSDATA|sessdata|bili_jct|password|secret)[A-Za-z_]*"
    private val jsonField = Regex("(\"$NAMES\"\\s*:\\s*\")([^\"]*)(\")")
    private val queryParam = Regex("([?&]$NAMES=)([^&\\s\"]*)")

    fun redact(text: String): String =
        queryParam.replace(jsonField.replace(text) { "${it.groupValues[1]}***${it.groupValues[3]}" }) { "${it.groupValues[1]}***" }
}
