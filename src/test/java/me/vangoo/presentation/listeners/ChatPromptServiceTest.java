package me.vangoo.presentation.listeners;

import me.vangoo.domain.market.PoundMoney;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatPromptServiceTest {

    @Test
    void parsesPoundsAndCoppets() {
        assertEquals(PoundMoney.of(2, 15), ChatPromptService.parsePrice("2 15"));
    }

    @Test
    void parsesPoundsOnly() {
        assertEquals(PoundMoney.of(7, 0), ChatPromptService.parsePrice("7"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(PoundMoney.of(2, 15), ChatPromptService.parsePrice("  2   15  "));
    }

    @Test
    void rejectsNonNumericInput() {
        assertNull(ChatPromptService.parsePrice("abc"));
        assertNull(ChatPromptService.parsePrice("2 abc"));
        assertNull(ChatPromptService.parsePrice(""));
    }

    @Test
    void rejectsNegativeAmounts() {
        assertNull(ChatPromptService.parsePrice("-1"));
        assertNull(ChatPromptService.parsePrice("2 -1"));
    }

    @Test
    void rejectsTooManyParts() {
        assertNull(ChatPromptService.parsePrice("2 15 3"));
    }
}
