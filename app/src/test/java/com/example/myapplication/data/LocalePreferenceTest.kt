package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalePreferenceTest {
    @Test fun migratesNorwegianToTheCorrectLanguage() {
        assertEquals("nb-NO", canonicalLanguageTag("se"))
        assertEquals("nb-NO", canonicalLanguageTag("se-NO"))
    }
    @Test fun alignsOriginalCountryPickerWithResourceRegions() {
        assertEquals("pt-PT", canonicalLanguageTag("pt"))
        assertEquals("fr-FR", canonicalLanguageTag("fr"))
        assertEquals("es-ES", canonicalLanguageTag("es"))
        assertEquals("tr-TR", canonicalLanguageTag("tr"))
    }
    @Test fun preservesExplicitRegionalPreferences() {
        assertEquals("fr-CA", canonicalLanguageTag("fr-CA"))
        assertEquals("en", canonicalLanguageTag("en"))
        assertEquals("id", canonicalLanguageTag("in"))
    }
}
