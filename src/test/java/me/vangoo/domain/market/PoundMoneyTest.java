package me.vangoo.domain.market;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PoundMoneyTest {

    // --- PoundMoney ---

    @Test
    void convertsPoundsAndCoppets() {
        PoundMoney m = PoundMoney.of(2, 15);
        assertEquals(55, m.coppets());
        assertEquals(2, m.wholePounds());
        assertEquals(15, m.remainderCoppets());
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> PoundMoney.ofCoppets(-1));
        assertThrows(IllegalArgumentException.class, () -> PoundMoney.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> PoundMoney.of(0, -5));
        assertThrows(IllegalArgumentException.class,
                () -> PoundMoney.of(1, 0).minus(PoundMoney.of(2, 0)));
    }

    @Test
    void formatsUkrainian() {
        assertEquals("2 ф 15 к", PoundMoney.of(2, 15).format());
        assertEquals("2 ф", PoundMoney.of(2, 0).format());
        assertEquals("15 к", PoundMoney.of(0, 15).format());
        assertEquals("0 к", PoundMoney.ofCoppets(0).format());
    }

    @Test
    void commissionIsCeiled() {
        // 10% від 55 к = 5.5 → 6 к
        assertEquals(6, PoundMoney.ofCoppets(55).commission(0.10).coppets());
        assertEquals(0, PoundMoney.ofCoppets(0).commission(0.10).coppets());
    }

    @Test
    void timesScalesCoppets() {
        assertEquals(60, PoundMoney.ofCoppets(20).times(3).coppets());
        assertEquals(3, PoundMoney.ofCoppets(20).times(3).wholePounds());
    }

    @Test
    void timesZeroIsZeroMoney() {
        assertTrue(PoundMoney.ofCoppets(20).times(0).isZero());
    }

    @Test
    void timesRejectsNegativeFactor() {
        assertThrows(IllegalArgumentException.class, () -> PoundMoney.ofCoppets(20).times(-1));
    }

    // --- CoinChange (жадібний розмін: спершу коппети, потім фунти) ---

    @Test
    void paysExactlyWithCoppetsFirst() {
        // ціна 30 к; у гаманці 2 ф + 12 к → зняти 12 к + 1 ф, здача 2 к
        CoinChange change = CoinChange.make(2, 12, PoundMoney.ofCoppets(30)).orElseThrow();
        assertEquals(1, change.takePounds());
        assertEquals(12, change.takeCoppets());
        assertEquals(2, change.changeCoppets());
        assertEquals(30, change.paidCoppets());
    }

    @Test
    void paysWithoutChangeWhenCoppetsSuffice() {
        CoinChange change = CoinChange.make(5, 30, PoundMoney.ofCoppets(30)).orElseThrow();
        assertEquals(0, change.takePounds());
        assertEquals(30, change.takeCoppets());
        assertEquals(0, change.changeCoppets());
    }

    @Test
    void poundsOnlyWalletGetsChange() {
        // ціна 30 к; лише 2 фунти → зняти 2 ф (40 к), здача 10 к
        CoinChange change = CoinChange.make(2, 0, PoundMoney.ofCoppets(30)).orElseThrow();
        assertEquals(2, change.takePounds());
        assertEquals(0, change.takeCoppets());
        assertEquals(10, change.changeCoppets());
        assertEquals(30, change.paidCoppets());
    }

    @Test
    void insufficientFundsIsEmpty() {
        assertEquals(Optional.empty(), CoinChange.make(1, 9, PoundMoney.ofCoppets(30)));
        assertEquals(Optional.empty(), CoinChange.make(0, 0, PoundMoney.ofCoppets(1)));
    }

    @Test
    void zeroPriceTakesNothing() {
        CoinChange change = CoinChange.make(3, 3, PoundMoney.ofCoppets(0)).orElseThrow();
        assertEquals(0, change.takePounds());
        assertEquals(0, change.takeCoppets());
        assertEquals(0, change.changeCoppets());
    }
}
