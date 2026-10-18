package cc.novelia.app.reader

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.AtomicFile
import cc.novelia.app.data.model.ReaderCustomFont
import cc.novelia.app.data.model.ReaderSettings
import cc.novelia.app.data.model.resolvedFontFamily
import cc.novelia.app.data.storage.appJson
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream
import java.io.DataInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 导入先限流、校验，再原子安装；损坏文件不能替换当前字体。 */
internal object ReaderFontStore {
    const val MAX_FONT_BYTES = 64L * 1024 * 1024
    private val cache = LruCache<String, Typeface>(8)
    private val lock = Any()
    val revision = MutableStateFlow(0)

    suspend fun importFont(context: Context, uri: Uri): ReaderCustomFont = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        }?.filterNot(Char::isISOControl)?.take(160)?.takeIf(String::isNotBlank) ?: "自定义字体"
        resolver.openInputStream(uri)?.use { importStream(context, name, it) } ?: error("无法读取字体文件")
    }

    internal suspend fun importStream(context: Context, name: String, input: InputStream): ReaderCustomFont = withContext(Dispatchers.IO) {
        val directory = directory(context).apply { check(isDirectory || mkdirs()) { "无法创建字体目录" } }
        val temporary = File.createTempFile("import-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if(count < 0) break
                    total += count
                    require(total <= MAX_FONT_BYTES) { "字体文件不能超过 64 MB" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            require(temporary.length() >= 12) { "请选择有效的 TTF、OTF 或 TTC 字体文件" }
            val signature = ByteArray(4)
            DataInputStream(temporary.inputStream()).use { it.readFully(signature) }
            require(signature.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                signature.toString(Charsets.US_ASCII) in listOf("OTTO", "ttcf", "true")) { "请选择有效的 TTF、OTF 或 TTC 字体文件" }
            val typeface = try { Typeface.createFromFile(temporary) }
                catch(e: RuntimeException) { throw IllegalArgumentException("无法加载此字体，请选择有效的 TTF、OTF 或 TTC 文件", e) }
            val id = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val font = ReaderCustomFont(id, name)
            require(font.valid) { "字体名称无效" }
            val target = file(context, font)
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                // 每个字体独立保存名称；导入下一款不会覆盖已有字体的记录。
                writeMetadata(context, font)
                check(temporary.renameTo(target)) { "无法保存字体文件" }
                cache.put(target.absolutePath, typeface)
                revision.update { it + 1 }
            }
            font
        } finally { temporary.delete() }
    }

    /** 扫描旧版留下的所有字体文件；已知设置可补回原文件名。 */
    suspend fun library(context: Context, knownFonts: List<ReaderCustomFont> = emptyList()): List<ReaderCustomFont> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val known = knownFonts.filter { it.valid }.associateBy { it.id }
            directory(context).listFiles().orEmpty().filter { it.isFile && it.extension == "font" }.mapNotNull { source ->
                val fallback = ReaderCustomFont(source.nameWithoutExtension, "历史字体 ${source.nameWithoutExtension.take(8)}")
                if(!fallback.valid) return@mapNotNull null
                val stored = runCatching {
                    metadata(context, fallback).openRead().bufferedReader(Charsets.UTF_8).use {
                        appJson.decodeFromString<ReaderCustomFont>(it.readText())
                    }
                }.getOrNull()?.takeIf { it.valid && it.id == fallback.id }
                stored ?: known[fallback.id]?.also { writeMetadata(context, it) } ?: fallback
            }.sortedWith(compareBy<ReaderCustomFont> { it.name.lowercase() }.thenBy { it.id })
        }
    }

    suspend fun remove(context: Context, font: ReaderCustomFont) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val source = file(context, font)
            check(!source.exists() || source.delete()) { "无法删除字体，请重试" }
            metadata(context, font).delete()
            cache.remove(source.absolutePath)
            revision.update { it + 1 }
        }
    }

    fun cached(context: Context, settings: ReaderSettings): Typeface? = when(settings.resolvedFontFamily) {
        "serif" -> Typeface.SERIF
        "monospace" -> Typeface.MONOSPACE
        "sans-serif" -> Typeface.SANS_SERIF
        "custom" -> cache.get(file(context, settings.customFont!!).absolutePath)
        else -> Typeface.DEFAULT
    }

    fun load(context: Context, settings: ReaderSettings): Typeface? = synchronized(lock) {
        cached(context, settings) ?: settings.customFont?.takeIf { it.valid }?.let { font ->
            val source = file(context, font)
            runCatching { Typeface.createFromFile(source) }.getOrNull()?.also { cache.put(source.absolutePath, it) }
        }
    }

    private fun metadata(context: Context, font: ReaderCustomFont) = AtomicFile(File(directory(context), "${font.id}.json"))
    private fun writeMetadata(context: Context, font: ReaderCustomFont) {
        val atomic = metadata(context, font)
        val stream = atomic.startWrite()
        try {
            stream.write(appJson.encodeToString(font).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch(error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun directory(context: Context) = File(context.filesDir, "reader-fonts")
    private fun file(context: Context, font: ReaderCustomFont): File {
        require(font.valid)
        return File(directory(context), "${font.id}.font")
    }
}
