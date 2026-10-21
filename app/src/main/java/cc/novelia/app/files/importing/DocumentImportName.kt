package cc.novelia.app.files.importing

/** 扩展名优先；缺失时只根据明确的书籍 MIME 补齐，不能把未知二进制当作 TXT。 */
internal fun documentImportName(displayName: String?, pathName: String?, mimeType: String?): String {
    val name = displayName?.takeIf(String::isNotBlank)
        ?: pathName?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "导入文档"
    val extension = name.substringAfterLast('.', "").lowercase()
    if(extension in setOf("epub", "txt", "srt")) return name
    require(extension.isEmpty()) { "支持 EPUB、TXT 和 SRT 文件" }
    val format = when(mimeType?.substringBefore(';')?.trim()?.lowercase()) {
        "application/epub+zip" -> "epub"
        "text/plain" -> "txt"
        "application/x-subrip", "application/srt", "text/srt" -> "srt"
        else -> error("无法识别文件类型，请分享 EPUB、TXT 或 SRT 文件")
    }
    return "$name.$format"
}
