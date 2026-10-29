package com.example.myapplication.vault

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SafeMediaImportTest {
    @Test fun originalIsDeletedOnlyAfterDurableCommit() {
        val events = mutableListOf<String>()
        importSafely(stage = { events += "copy"; "file" }, commit = { events += "commit" },
            discardUncommitted = { events += "discard" }, deleteOriginal = { events += "delete" })
        assertEquals(listOf("copy", "commit", "delete"), events)
    }
    @Test fun fullDiskWhileCommittingNeverDeletesOriginal() {
        var deleted = false; var discarded = false
        try {
            importSafely(stage = { "copy" }, commit = { throw IOException("full disk") },
                discardUncommitted = { discarded = true }, deleteOriginal = { deleted = true })
            fail("Expected failure")
        } catch (_: IOException) { }
        assertFalse(deleted); assertTrue(discarded)
    }
    @Test fun partialBatchFailureRetainsAllCommittedCopies() {
        val vault = mutableSetOf<String>(); val originals = mutableSetOf("a", "b", "c")
        for (name in originals.toList()) {
            try {
                importSafely(stage = { name }, commit = { vault += it }, discardUncommitted = { vault -= it },
                    deleteOriginal = { if (name == "b") throw SecurityException("revoked"); originals -= name })
            } catch (_: SecurityException) { }
        }
        assertEquals(setOf("a", "b", "c"), vault)
        assertEquals(setOf("b"), originals)
    }
    @Test fun failedCopyLeavesOriginalUntouched() {
        var committed = false; var deleted = false
        try {
            importSafely<String>(stage = { throw IOException("truncated input") }, commit = { committed = true },
                discardUncommitted = {}, deleteOriginal = { deleted = true })
        } catch (_: IOException) { }
        assertFalse(committed); assertFalse(deleted)
    }
}
