package cc.novelia.app.data.model

import cc.novelia.app.data.storage.appJson
import cc.novelia.app.data.webdav.*
import cc.novelia.app.reader.readerSettingsDifferences
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ReaderFontTest {
    private val font = ReaderCustomFont("a".repeat(64), "中文字体.ttf")

    @Test fun legacySettingsUseSystemFontAndBothScopesSurviveSerialization() {
        assertEquals("system", appJson.decodeFromString<ReaderSettings>("{}").resolvedFontFamily)
        val reader = ReaderSettings(fontFamily = "custom", customFont = font)
        val book = reader.copy(fontFamily = "serif")
        val state = LibraryState(reader = reader, bookSettings = mapOf("local/book" to book))
        assertEquals(state, appJson.decodeFromString<LibraryState>(appJson.encodeToString(state)))
        val backup = SettingsBackup(reader = reader)
        assertEquals(backup, appJson.decodeFromString<SettingsBackup>(appJson.encodeToString(backup)))
        assertEquals(reader, reader.withEInkMode(true).withEInkMode(false).copy(eInkPreferences = null))
        assertEquals(font, book.customFont)
    }

    @Test fun untrustedPathsAndIncompleteCustomSettingsCannotBeUsedAsFontFiles() {
        assertTrue(font.valid)
        listOf("../outside", "/system/fonts/a.ttf", "a".repeat(63), "A".repeat(64)).forEach { id ->
            val settings = ReaderSettings(fontFamily = "custom", customFont = font.copy(id = id))
            assertFalse(settings.validFont)
            assertEquals("system", settings.resolvedFontFamily)
        }
        assertFalse(font.copy(name = "\nname").valid)
        assertFalse(font.copy(name = " ").valid)
        assertFalse(ReaderSettings(fontFamily = "custom").validFont)
        assertEquals("system", ReaderSettings(fontFamily = "unknown").resolvedFontFamily)
    }

    @Test fun differencesOnlyDescribeTheActiveFont() {
        val defaults = ReaderSettings()
        val selected = defaults.copy(fontFamily = "custom", customFont = font)
        val difference = readerSettingsDifferences(selected, defaults).single()
        assertEquals("阅读字体", difference.label)
        assertEquals(font.name, difference.book)
        assertTrue(readerSettingsDifferences(selected.copy(fontFamily = "system"), defaults).isEmpty())
    }

    @Test fun webDavNeverReplacesDeviceLocalFontsEvenWithDevicePreferencesEnabled() {
        val key = "syosetu/n1"
        val remote = LibraryState(reader = ReaderSettings(fontFamily = "custom", customFont = font, fontSize = 24f),
            bookSettings = mapOf(key to ReaderSettings(fontFamily = "custom", customFont = font)))
        val target = LibraryState(reader = ReaderSettings(fontFamily = "serif"),
            bookSettings = mapOf(key to ReaderSettings(fontFamily = "monospace")))
        listOf(false, true).forEach { includeDevice ->
            val projection = WebDavProjection.library(remote, setOf(SyncDomain.SETTINGS), includeDevicePreferences = includeDevice)
            assertFalse(projection.toString().contains("fontFamily"))
            assertFalse(projection.toString().contains(font.id))
            val docs = SyncReplica(deviceId = "a").track(emptyMap(), projection).bind("dataset").documents
            val restored = WebDavProjection.applyLibrary(target, docs, setOf(SyncDomain.SETTINGS), includeDevicePreferences = includeDevice)
            assertEquals("serif", restored.reader.fontFamily)
            assertEquals("monospace", restored.bookSettings.getValue(key).fontFamily)
            assertEquals(24f, restored.reader.fontSize)
        }
    }
}
