package cc.novelia.app.files.importing

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** 附件优先于 EXTRA_TEXT；部分文件管理器只通过 ClipData 传递文件。 */
internal fun Intent.sharedDocumentUris(): List<Uri> {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()
    val streams = runCatching {
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java))
    }.getOrDefault(emptyList()).filterIsInstance<Uri>()
    return streams.ifEmpty {
        val clip = clipData
        val items = if(clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        items.ifEmpty { listOfNotNull(data?.takeIf { it.scheme == "content" }) }
    }.distinct()
}
