package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderStashTest {

    @Test
    void takeIsAtomicAndConserving() {
        OrderStash stash = new OrderStash();
        stash.add("custom:herb", 3);
        assertFalse(stash.take("custom:herb", 5)); // бракує → нічого не змінилось
        assertEquals(3, stash.amountOf("custom:herb"));
        assertTrue(stash.take("custom:herb", 3));
        assertEquals(0, stash.amountOf("custom:herb"));
        assertTrue(stash.isEmpty());
    }

    @Test
    void snapshotRestoreRoundTrips() {
        OrderStash stash = new OrderStash();
        stash.add("characteristic:Door:9", 1);
        OrderStash copy = new OrderStash();
        copy.restore(stash.snapshot());
        assertEquals(1, copy.amountOf("characteristic:Door:9"));
    }

    @Test
    void restoreDropsNonPositiveEntries() {
        OrderStash stash = new OrderStash();
        stash.restore(Map.of("custom:a", 0, "custom:b", 2));
        assertEquals(0, stash.amountOf("custom:a"));
        assertEquals(2, stash.amountOf("custom:b"));
    }

    @Test
    void addRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new OrderStash().add("x", 0));
    }
}
