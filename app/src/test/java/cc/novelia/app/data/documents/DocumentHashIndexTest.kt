package cc.novelia.app.data.documents

import org.junit.Assert.*
import org.junit.Test

class DocumentHashIndexTest {
    @Test fun legacyLibraryIsReadOnceAcrossManyImports() {
        var reads = 0
        var saved = emptyMap<String, String>()
        val ids = (1..100).map(Int::toString)
        val index = DocumentHashIndex(readHash = { reads++; "hash-$it" }, persist = { saved = it })
        index.reconcile(ids)
        repeat(10) { assertEquals("93", index.find("hash-93", { it in ids })) }
        index.flush()
        assertEquals(100, reads)
        val restored = DocumentHashIndex(saved, { error("Must use persisted metadata") }, {})
        restored.reconcile(ids)
        assertEquals("93", restored.find("hash-93", { it in ids }))
    }

    @Test fun removedBooksAreNotReturnedAndNewBooksDoNotRequireDecoding() {
        val index = DocumentHashIndex(readHash = { error("Unexpected body read") }, persist = {})
        index.record("new", "hash")
        assertEquals("new", index.find("hash", { it == "new" }))
        assertNull(index.find("hash", { false }))
        index.remove("new")
        assertNull(index.find("hash", { false }))
        assertNull(index.find("", { true }))
    }

    @Test fun twoThousandIncrementalImportsOnlyCheckMatchingCandidatesAndFlushOnce() {
        var reads = 0; var checks = 0; var writes = 0
        val index = DocumentHashIndex(readHash = { reads++; "hash-$it" }, persist = { writes++ })
        index.reconcile(emptyList())
        repeat(2000) { i ->
            assertNull(index.find("hash-$i", { checks++; true }))
            index.record("$i", "hash-$i")
            assertEquals("$i", index.find("hash-$i", { checks++; true }))
        }
        assertEquals(0, reads)
        assertEquals(2000, checks)
        assertEquals(0, writes)
        index.flush(); index.flush()
        assertEquals(1, writes)
    }

    @Test fun staleEntriesRepairedAndShelfPreferenceSurvivesHashChanges() {
        val index = DocumentHashIndex(mapOf("stale" to "same"), { "same" }, {})
        index.reconcile(listOf("retained", "shelf", "another"))
        assertEquals("shelf", index.find("same", { true }) { sequenceOf("missing", "shelf", "another") })
        index.record("shelf", "changed")
        assertEquals("another", index.find("same", { true }) { sequenceOf("shelf", "another") })
        index.remove("another")
        assertEquals("retained", index.find("same", { true }))
    }

    @Test fun failedFlushCanBeRetriedAndCancellationDoesNotGetSwallowed() {
        var fail = true; var writes = 0
        val index = DocumentHashIndex(readHash = { throw kotlinx.coroutines.CancellationException() }, persist = {
            writes++; if(fail) throw java.io.IOException("full")
        })
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { index.reconcile(listOf("old")) }
        index.record("new", "hash")
        assertThrows(java.io.IOException::class.java) { index.flush() }
        fail = false
        index.flush(); index.flush()
        assertEquals(2, writes)
    }
}
