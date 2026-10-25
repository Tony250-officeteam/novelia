package cc.novelia.app.data.model

import cc.novelia.app.data.webdav.WebDavProjection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DiscoverLayoutSettingsTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test fun oldSettingsDefaultToGridAndBothChoicesSurviveBackup() {
        assertTrue(json.decodeFromString<LibraryState>("{}").discoverWenkuGrid)
        assertTrue(json.decodeFromString<SettingsBackup>("{}").discoverWenkuGrid)
        for(grid in listOf(true, false)) {
            val state = LibraryState(discoverWenkuGrid = grid)
            assertEquals(grid, json.decodeFromString<LibraryState>(json.encodeToString(state)).discoverWenkuGrid)
            val backup = SettingsBackup(discoverWenkuGrid = grid)
            assertEquals(grid, json.decodeFromString<SettingsBackup>(json.encodeToString(backup)).discoverWenkuGrid)
        }
    }

    @Test fun changingLayoutDoesNotQueueWebDavChangesOrAlterItsProjection() {
        val before = LibraryState()
        val after = before.copy(discoverWenkuGrid = false)
        assertTrue(WebDavProjection.affectedLibraryDomains(before, after).isEmpty())
        assertEquals(WebDavProjection.library(before), WebDavProjection.library(after))
    }
}
