package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderMembershipTest {

    private OrderMembership membership() {
        return new OrderMembership(UUID.randomUUID(), "order-aurora", "Пан у сірому");
    }

    @Test
    void consumeFavorSpendsCheapestSufficientOne() {
        OrderMembership m = membership();
        m.addFavor(new Favor(TaskWeight.MAJOR, 1L));
        m.addFavor(new Favor(TaskWeight.STANDARD, 2L));
        m.addFavor(new Favor(TaskWeight.LIGHT, 3L));

        Optional<Favor> spent = m.consumeFavor(TaskWeight.STANDARD);
        assertTrue(spent.isPresent());
        assertEquals(TaskWeight.STANDARD, spent.get().weight()); // не MAJOR
        assertEquals(2, m.favors().size());
    }

    @Test
    void consumeFavorFailsWhenNothingCoversWeight() {
        OrderMembership m = membership();
        m.addFavor(new Favor(TaskWeight.LIGHT, 1L));
        assertTrue(m.consumeFavor(TaskWeight.MAJOR).isEmpty());
        assertEquals(1, m.favors().size()); // нічого не списано
    }

    @Test
    void taskWindowResetMirrorsChurchQuota() {
        OrderMembership m = membership();
        m.consumeTaskSet();
        m.consumeTaskSet();
        assertEquals(2, m.taskSetsUsed());
        m.startTaskWindow(1000L);
        assertEquals(0, m.taskSetsUsed());
        assertEquals(1000L, m.lastTaskRefreshEpochMillis());
    }
}
