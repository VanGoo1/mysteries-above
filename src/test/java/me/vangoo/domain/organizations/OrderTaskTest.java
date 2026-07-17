package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTaskTest {

    @Test
    void deliverAndHuntWeightDropsWithStrongerSequence() {
        assertEquals(TaskWeight.LIGHT, OrderTask.deliver("custom:x", "X", 9).weight());
        assertEquals(TaskWeight.LIGHT, OrderTask.hunt("mob", "Моб", 6).weight());
        assertEquals(TaskWeight.STANDARD, OrderTask.deliver("custom:x", "X", 5).weight());
        assertEquals(TaskWeight.STANDARD, OrderTask.hunt("mob", "Моб", 0).weight());
    }

    @Test
    void templeAndSpyOpsCarryFixedWeightAndSingleRequirement() {
        OrderTask raid = OrderTask.raid("church-evernight", "Богині Вічної Ночі");
        assertEquals(TaskWeight.MAJOR, raid.weight());
        assertEquals(1, raid.required());
        assertTrue(raid.isTempleOp());
        assertFalse(raid.isSpyOp());

        OrderTask recon = OrderTask.recon("church-evernight", "Богині Вічної Ночі");
        assertEquals(TaskWeight.STANDARD, recon.weight());
        assertTrue(recon.isSpyOp());

        assertEquals(TaskWeight.MAJOR, OrderTask.sabotage("c", "C").weight());
        assertEquals(TaskWeight.MAJOR, OrderTask.assassinate("c", "C").weight());
    }

    @Test
    void progressCapsAtRequired() {
        OrderTask hunt = OrderTask.hunt("mob", "Моб", 9); // required = 1 + 9/3 = 4
        assertEquals(4, hunt.required());
        OrderTask done = hunt.withProgress(99);
        assertEquals(4, done.progress());
        assertTrue(done.isComplete());
    }
}
