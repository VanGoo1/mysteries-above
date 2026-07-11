package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsiderationTest {

    private static final Consideration.ItemDemand DEMAND =
            new Consideration.ItemDemand("characteristic:Door:7", 1);

    @Test
    void moneyOnlyIsNotBarterAndPaysCommission() {
        Consideration c = Consideration.money(PoundMoney.ofCoppets(20));
        assertFalse(c.isBarter());
        assertNull(c.item());
        assertTrue(c.hasMoney());
        assertEquals(2, c.commission(0.10).coppets()); // ceil(20 * 0.10)
    }

    @Test
    void pureItemIsBarterAndChargesNoCommission() {
        Consideration c = Consideration.of(DEMAND, PoundMoney.ofCoppets(0));
        assertTrue(c.isBarter());
        assertEquals(DEMAND, c.item());
        assertFalse(c.hasMoney());
        assertEquals(0, c.commission(0.10).coppets()); // barter never pays commission
    }

    @Test
    void itemWithBootIsBarterAndStillChargesNoCommission() {
        Consideration c = Consideration.of(DEMAND, PoundMoney.ofCoppets(60));
        assertTrue(c.isBarter());
        assertTrue(c.hasMoney());
        assertEquals(60, c.money().coppets());
        assertEquals(0, c.commission(0.10).coppets());
    }

    @Test
    void emptyConsiderationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Consideration.money(PoundMoney.ofCoppets(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Consideration.ItemDemand("x", 0));
    }
}
