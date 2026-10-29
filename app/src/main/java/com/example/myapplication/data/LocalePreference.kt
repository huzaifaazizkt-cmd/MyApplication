package com.example.myapplication.data

/** Upgrade the original country-picker values to the locales used by translated resources. */
fun canonicalLanguageTag(tag: String): String = when (tag) {
    "pt" -> "pt-PT"
    "fr" -> "fr-FR"
    "es" -> "es-ES"
    "tr" -> "tr-TR"
    "se", "se-NO" -> "nb-NO"
    "in" -> "id"
    else -> tag
}
