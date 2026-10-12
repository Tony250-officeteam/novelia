package cc.novelia.app.ui.toolbar

import cc.novelia.app.data.model.EdgeToolbarSettings
import cc.novelia.app.data.model.LibraryState
import cc.novelia.app.data.model.SettingsBackup
import cc.novelia.app.data.storage.appJson
import cc.novelia.app.data.webdav.SyncDomain
import cc.novelia.app.data.webdav.WebDavProjection
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class EdgeToolbarSettingsTest {
    @Test fun oldDataEnablesRightHandHistoryAndDownloads() {
        val expected = EdgeToolbarSettings()
        assertEquals(expected, appJson.decodeFromString<LibraryState>("{}").edgeToolbar)
        assertEquals(expected, appJson.decodeFromString<SettingsBackup>("{}").edgeToolbar)
        assertTrue(expected.enabled)
        assertEquals("right", expected.side)
        assertEquals(listOf("history", "downloads"), expected.entries)
    }

    @Test fun draggedPositionAndEntryOrderSurviveBothBackupFormats() {
        val preferences = EdgeToolbarSettings(side = "left", position = .1234f, idleSeconds = 8,
            idleOpacity = .7f, readerVisibility = "always", downloadBadge = false, entries = listOf("notes", "downloads", "history"))
        val library = LibraryState(edgeToolbar = preferences)
        assertEquals(preferences, appJson.decodeFromString<LibraryState>(appJson.encodeToString(library)).edgeToolbar)
        val backup = SettingsBackup(edgeToolbar = preferences)
        assertEquals(preferences, appJson.decodeFromString<SettingsBackup>(appJson.encodeToString(backup)).edgeToolbar)
    }

    @Test fun invalidImportsCannotInjectRoutesOrUnboundedCoordinates() {
        val invalid = listOf(
            EdgeToolbarSettings(position = Float.NaN), EdgeToolbarSettings(position = Float.POSITIVE_INFINITY),
            EdgeToolbarSettings(position = -1f), EdgeToolbarSettings(idleOpacity = 0f),
            EdgeToolbarSettings(idleSeconds = -1), EdgeToolbarSettings(side = "top"),
            EdgeToolbarSettings(readerVisibility = "unknown"), EdgeToolbarSettings(entries = emptyList()),
            EdgeToolbarSettings(entries = listOf("history", "history")),
            EdgeToolbarSettings(entries = listOf("web?url=https://example.test")),
            EdgeToolbarSettings(entries = EdgeToolbarSettings.ENTRIES),
        )
        invalid.forEach { assertFalse(it.valid); assertTrue(it.normalized().valid) }
    }

    @Test fun toolbarChangesRemainLocalEvenWhenReaderSettingsSync() {
        val before = LibraryState()
        val after = before.copy(edgeToolbar = EdgeToolbarSettings(side = "left", position = .8f, enabled = false))
        assertTrue(WebDavProjection.affectedLibraryDomains(before, after).isEmpty())
        assertEquals(WebDavProjection.library(before, setOf(SyncDomain.SETTINGS), includeDevicePreferences = true),
            WebDavProjection.library(after, setOf(SyncDomain.SETTINGS), includeDevicePreferences = true))
    }
}
