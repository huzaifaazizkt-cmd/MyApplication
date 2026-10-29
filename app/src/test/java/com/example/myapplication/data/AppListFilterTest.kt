package com.example.myapplication.data

import org.junit.Assert.*
import org.junit.Test

class AppListFilterTest {
    private val apps = listOf(
        AppListItem("browser", "Browser", "browser", 1),
        AppListItem("bank", "Bank", "bank", 1),
        AppListItem("istanbul", "İstanbul", "i̇stanbul", 1),
        AppListItem("isik", "Işık", "işık", 1))

    @Test fun tabsKeepTheirExistingLockedAndUnlockedMeaning() {
        val locked = setOf("bank")
        assertEquals(listOf("bank"), filterAppList(apps, locked, true, "").map { it.packageName })
        assertEquals(listOf("browser", "istanbul", "isik"), filterAppList(apps, locked, false, "").map { it.packageName })
    }
    @Test fun searchTrimsWhitespaceAndIgnoresCase() {
        assertEquals(listOf("browser"), filterAppList(apps, emptySet(), false, "  BROW  ").map { it.packageName })
    }
    @Test fun dottedAndDotlessTurkishLettersRemainSearchable() {
        assertEquals(listOf("istanbul"), filterAppList(apps, emptySet(), false, "istanbul").map { it.packageName })
        assertEquals(listOf("isik"), filterAppList(apps, emptySet(), false, "ışık").map { it.packageName })
    }
    @Test fun searchCannotShowAnAppFromTheOtherTabOrMutateTheCatalog() {
        assertTrue(filterAppList(apps, setOf("bank"), false, "bank").isEmpty())
        assertEquals(4, apps.size)
    }
}
