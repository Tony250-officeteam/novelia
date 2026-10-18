package cc.novelia.app.data.model

import kotlinx.serialization.Serializable

/** 文件按内容摘要保存在本机，设置和备份中不保存外部 URI 或绝对路径。 */
@Serializable data class ReaderCustomFont(val id: String, val name: String) {
    val valid get() = id.matches(Regex("[0-9a-f]{64}")) && name.isNotBlank() &&
        name.length <= 160 && name.none(Char::isISOControl)
}

val READER_FONT_FAMILIES = listOf("system", "sans-serif", "serif", "monospace", "custom")
val READER_FONT_LABELS = listOf("系统默认", "无衬线", "衬线", "等宽", "自定义")

val ReaderSettings.validFont: Boolean
    get() = fontFamily in READER_FONT_FAMILIES && (customFont == null || customFont.valid) &&
        (fontFamily != "custom" || customFont != null)

val ReaderSettings.resolvedFontFamily: String
    get() = fontFamily.takeIf { it in READER_FONT_FAMILIES && (it != "custom" || customFont?.valid == true) } ?: "system"

val ReaderSettings.fontLabel: String
    get() = if(resolvedFontFamily == "custom") customFont!!.name else READER_FONT_LABELS[READER_FONT_FAMILIES.indexOf(resolvedFontFamily)]
