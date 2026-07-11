package me.vangoo.domain.market;

import me.vangoo.domain.market.MarketSession.Lot;
import me.vangoo.domain.market.MarketSession.MarketException;
import me.vangoo.domain.market.MarketSession.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketSessionTest {

    private static final double RATE = 0.10;

    private MarketSession session;
    private final UUID seller = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        session = new MarketSession(RATE, new Random(42));
        session.registerParticipant(seller);
        session.registerParticipant(buyer);
    }

    @Test
    void aliasesAreUniqueAndStable() {
        int a = session.registerParticipant(seller);
        int b = session.registerParticipant(buyer);
        assertNotEquals(a, b);
        assertEquals(a, session.registerParticipant(seller)); // ідемпотентно
        assertEquals("Незнайомець №" + a, session.aliasOf(seller));
    }

    @Test
    void registeringMoreThanNinetyNineParticipantsTerminatesWithDistinctAliases() {
        // Понад 99 учасників вичерпує діапазон 1..99 — цикл рандомізації має
        // розширювати межі, інакше зависає назавжди на головному потоці.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Set<Integer> assigned = new HashSet<>();
            assigned.add(session.registerParticipant(seller));
            assigned.add(session.registerParticipant(buyer));
            for (int i = 0; i < 120; i++) {
                int alias = session.registerParticipant(UUID.randomUUID());
                assertTrue(assigned.add(alias), "Псевдонім №" + alias + " видано повторно");
            }
            assertEquals(122, assigned.size());
        });
    }

    @Test
    void listedLotIsVisibleAndAnonymousViewCarriesData() {
        UUID lotId = session.listLot(seller, "custom:stellar_aqua_crystal", 2,
                Consideration.money(PoundMoney.of(1, 5)));
        Lot lot = session.activeLots().stream()
                .filter(l -> l.lotId().equals(lotId)).findFirst().orElseThrow();
        assertEquals("custom:stellar_aqua_crystal", lot.itemKey());
        assertEquals(2, lot.amount());
        assertEquals(PoundMoney.of(1, 5), lot.price().money());
        assertFalse(lot.sold());
    }

    @Test
    void rejectsInvalidListings() {
        assertThrows(MarketException.class,
                () -> session.listLot(seller, "custom:x", 0, Consideration.money(PoundMoney.ofCoppets(5))));
        assertThrows(IllegalArgumentException.class,
                () -> session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(0))));
        assertThrows(MarketException.class,
                () -> session.listLot(stranger, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(5))));
    }

    @Test
    void buyLotSettlesWithCommission() {
        UUID lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(30)));
        // покупець: 2 фунти, 0 коппетів
        Settlement s = session.buyLot(buyer, lotId, 2, 0);
        assertEquals(buyer, s.payerId());
        assertEquals(seller, s.payeeId());
        assertEquals(30, s.price().money().coppets());
        assertEquals(3, s.commissionPaid().coppets());     // ceil(30×0.10)
        assertEquals(27, s.sellerProceeds().coppets());    // 30 − 3
        assertEquals(30, s.payerCharge().paidCoppets());
        assertEquals(lotId, s.escrowRef());
        assertTrue(session.activeLots().isEmpty());        // лот зник зі списку
    }

    @Test
    void doubleBuyIsImpossible() {
        UUID lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(10)));
        session.buyLot(buyer, lotId, 1, 0);
        assertThrows(MarketException.class, () -> session.buyLot(buyer, lotId, 1, 0));
    }

    @Test
    void cannotBuyOwnLotOrWithoutFunds() {
        UUID lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(30)));
        assertThrows(MarketException.class, () -> session.buyLot(seller, lotId, 5, 5));
        assertThrows(MarketException.class, () -> session.buyLot(buyer, lotId, 1, 9)); // 29 к < 30 к
        assertEquals(1, session.activeLots().size()); // невдалі спроби не знімають лот
    }

    @Test
    void nonParticipantCannotBuy() {
        UUID lotId = session.listLot(seller, "custom:x", 1, Consideration.money(PoundMoney.ofCoppets(10)));
        assertThrows(MarketException.class, () -> session.buyLot(stranger, lotId, 5, 5));
    }
}
