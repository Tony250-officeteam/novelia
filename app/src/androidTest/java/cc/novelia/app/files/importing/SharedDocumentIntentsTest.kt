package cc.novelia.app.files.importing

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class SharedDocumentIntentsTest {
    private val first = Uri.parse("content://books/first.epub")
    private val second = Uri.parse("content://books/second.txt")

    @Test fun registeredTypesAppearInSingleAndMultipleShareResolvers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val types = listOf("application/epub+zip", "text/plain", "application/x-subrip", "application/srt", "text/srt", "application/octet-stream", "*/*")
        for(action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) for(type in types) {
            val intent = Intent(action).setType(type).setPackage(context.packageName)
            @Suppress("DEPRECATION")
            val matches = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue("Missing share target for $action $type", matches.any { it.activityInfo.name == "cc.novelia.app.MainActivity" })
        }
        @Suppress("DEPRECATION")
        val images = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("image/png")
            .setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(images.isEmpty())
    }

    @Test fun attachmentsAreReadEvenWhenTextAndClipDataAreAlsoPresent() {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, first).putExtra(Intent.EXTRA_TEXT, "文件说明")
            .apply { clipData = ClipData.newRawUri("书籍", first) }
        assertEquals(listOf(first), share.sharedDocumentUris())
        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second, first))
        assertEquals(listOf(first, second), multiple.sharedDocumentUris())
    }

    @Test fun clipOnlySharesWorkButOrdinaryClipboardTextIsNotAFile() {
        val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            clipData = ClipData.newRawUri("书籍", first).apply { addItem(ClipData.Item(second)); addItem(ClipData.Item(first)) }
        }
        assertEquals(listOf(first, second), share.sharedDocumentUris())
        assertTrue(Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newPlainText("链接", "https://n.novelia.cc/novel/test/1") }
            .sharedDocumentUris().isEmpty())
        assertTrue(Intent(Intent.ACTION_VIEW, first).sharedDocumentUris().isEmpty())
        assertEquals(listOf(first), Intent(Intent.ACTION_SEND).setDataAndType(first, "application/epub+zip").sharedDocumentUris())
    }

    @Test fun malformedStreamExtraDoesNotCrashTheReceiver() {
        assertTrue(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "not a Uri").sharedDocumentUris().isEmpty())
        val share = Intent(Intent.ACTION_SEND_MULTIPLE).putExtra(Intent.EXTRA_STREAM, "not a list").apply {
            clipData = ClipData.newRawUri("书籍", first)
        }
        assertEquals(listOf(first), share.sharedDocumentUris())
        assertTrue(Intent(Intent.ACTION_SEND_MULTIPLE).putStringArrayListExtra(Intent.EXTRA_STREAM, arrayListOf("not a Uri"))
            .sharedDocumentUris().isEmpty())
    }
}
