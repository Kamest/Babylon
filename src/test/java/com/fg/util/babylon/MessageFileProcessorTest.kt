package com.fg.util.babylon

import junit.framework.Assert.assertEquals
import org.junit.Test

class MessageFileProcessorTest {

    @Test
    fun `when no snapshot of previous translation and no translations (first run case) then all messages are exported to translation sheet`() {
        val emptySnapshot = FakeTranslationSnapshot(emptyMap())
        val mfProcessor = MessageFileProcessor(emptySnapshot)

        val primaryMessages = mapOf(
                "pagination.prev" to "Previous",
                "pagination.next" to "Next",
                "price.zeroPriceString" to "Free"
        )
        val translateTo = listOf("cz", "sk")
        val noTranslations = emptyMap<Language, Messages>()

        val expected = listOf(
                listOf("pagination.prev", "Previous", null, null),
                listOf("pagination.next", "Next", null, null),
                listOf("price.zeroPriceString", "Free", null, null)
        )
        val sheet = mfProcessor.prepareTranslationSheet("i18n/common.properties", primaryMessages, noTranslations, translateTo)
        assertEquals(expected, sheet)
    }

    @Test
    fun `when there is a new primary message file then its messages are exported to translation sheet, but not the messages already in snapshot`() {
        val snapshot = FakeTranslationSnapshot(mapOf(
                "i18n/common.properties" to mapOf(
                        "pagination.prev" to "Previous",
                        "pagination.next" to "Next",
                        "price.zeroPriceString" to "Free")))
        val mfProcessor = MessageFileProcessor(snapshot)

        val primaryMessages = mapOf(
                "pagination.prev" to "Previous",
                "pagination.next" to "Next",
                "price.zeroPriceString" to "Free",
                "availability.text.ALWAYS_AVAILABLE" to "Always in stock",
                "availability.text.NOT_AVAILABLE" to "Momentarily unavailable"
        )

        val translateTo = listOf("cz", "sk")
        val translations = mapOf(
                "cz" to mapOf(
                        "pagination.prev" to "Předchozí",
                        "pagination.next" to "Následující",
                        "price.zeroPriceString" to "Zdarma"),
                "sk" to mapOf(
                        "pagination.prev" to "Predchádzajúce",
                        "pagination.next" to "Nasledujúce",
                        "price.zeroPriceString" to "Zadarmo"
                )
        )

        val expected = listOf(
                listOf("availability.text.ALWAYS_AVAILABLE", "Always in stock", null, null),
                listOf("availability.text.NOT_AVAILABLE", "Momentarily unavailable", null, null)
        )
        val sheet = mfProcessor.prepareTranslationSheet("i18n/common.properties", primaryMessages, translations, translateTo)
        assertEquals(expected, sheet)
    }

}