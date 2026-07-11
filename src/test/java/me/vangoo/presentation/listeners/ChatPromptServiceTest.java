package me.vangoo.presentation.listeners;

import me.vangoo.domain.market.PoundMoney;
import me.vangoo.presentation.listeners.ChatPromptService.Pending;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPromptServiceTest {

    // --- prompt expiry (isLive) ---

    @Test
    void livePromptBeforeDeadline() {
        Pending entry = new Pending(msg -> {}, 1_000L);
        assertTrue(ChatPromptService.isLive(entry, 999L));
        assertTrue(ChatPromptService.isLive(entry, 1_000L)); // рівно на дедлайні — ще живий
    }

    @Test
    void expiredPromptIsNotLive() {
        Pending entry = new Pending(msg -> {}, 1_000L);
        assertFalse(ChatPromptService.isLive(entry, 1_001L));
    }

    @Test
    void missingPromptIsNotLive() {
        assertFalse(ChatPromptService.isLive(null, 0L));
    }

    // --- parsePrice ---

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
