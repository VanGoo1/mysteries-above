package me.vangoo.domain.market;

import me.vangoo.domain.market.MarketSession.AcceptResult;
import me.vangoo.domain.market.MarketSession.MarketException;
import me.vangoo.domain.market.MarketSession.Refund;
import me.vangoo.domain.market.MarketSession.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketNegotiationTest {

    private static final Set<String> KNOWN = Set.of("custom:dimensional_wanderer_eye", "custom:crimson_star");

    private MarketSession session;
    private final UUID buyer = UUID.randomUUID();
    private final UUID sellerA = UUID.randomUUID();
    private final UUID sellerB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        session = new MarketSession(0.10, new Random(7));
        session.registerParticipant(buyer);
        session.registerParticipant(sellerA);
        session.registerParticipant(sellerB);
    }

    @Test
    void orderAllowsOnlyKnownIngredients() {
        assertThrows(MarketException.class,
                () -> session.placeOrder(buyer, "custom:unknown_root", 1, KNOWN));
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 2, KNOWN);
        assertEquals(1, session.openOrders().size());
        assertEquals(orderId, session.openOrders().get(0).orderId());
    }

    @Test
    void turnAlternatesAndOnlyReceiverAccepts() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(20));
        // Продавець назвав ціну → хід покупця; продавець прийняти не може
        assertThrows(MarketException.class, () -> session.accept(sellerA, negId, 99, 99));
        // Покупець дає зустрічну → хід продавця; покупець прийняти не може
        session.counter(buyer, negId, PoundMoney.ofCoppets(14));
        assertThrows(MarketException.class, () -> session.accept(buyer, negId, 99, 99));
        assertThrows(MarketException.class, () -> session.counter(buyer, negId, PoundMoney.ofCoppets(1)));
        // Продавець приймає зустрічну; платить ЗАВЖДИ покупець
        AcceptResult result = session.accept(sellerA, negId, 1, 0); // гаманець ПОКУПЦЯ: 1 фунт
        Settlement s = result.settlement();
        assertEquals(buyer, s.payerId());
        assertEquals(sellerA, s.payeeId());
        assertEquals(14, s.price().coppets());
        assertEquals(2, s.commissionPaid().coppets());   // ceil(14×0.10)
        assertEquals(12, s.sellerProceeds().coppets());
        assertEquals(negId, s.escrowRef());
        assertTrue(session.openOrders().isEmpty());
    }

    @Test
    void firstAcceptClosesOrderAndReleasesSiblings() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negA = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(20));
        UUID negB = session.offerOnOrder(sellerB, orderId, PoundMoney.ofCoppets(18));
        AcceptResult result = session.accept(buyer, negB, 1, 0);
        // Паралельний торг A автоматично скасовано з поверненням його ескроу
        assertEquals(List.of(new Refund(sellerA, negA)), result.releasedEscrows());
        // Другий accept по мертвому торгу неможливий
        assertThrows(MarketException.class, () -> session.accept(buyer, negA, 9, 9));
        assertThrows(MarketException.class, () -> session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(5)));
    }

    @Test
    void acceptWithoutBuyerFundsKeepsNegotiationOpen() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(30));
        assertThrows(MarketException.class, () -> session.accept(buyer, negId, 1, 5)); // 25 < 30
        // торг живий — покупець може торгуватись далі
        session.counter(buyer, negId, PoundMoney.ofCoppets(25));
    }

    @Test
    void withdrawByEitherPartyRefundsSeller() {
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(30));
        Refund refund = session.withdraw(buyer, negId);
        assertEquals(new Refund(sellerA, negId), refund);
        assertThrows(MarketException.class, () -> session.counter(buyer, negId, PoundMoney.ofCoppets(1)));
    }

    @Test
    void closeRefundsUnsoldLotsAndOpenNegotiations() {
        UUID lotId = session.listLot(sellerA, "custom:crimson_star", 1, PoundMoney.ofCoppets(10));
        UUID orderId = session.placeOrder(buyer, "custom:crimson_star", 1, KNOWN);
        UUID negId = session.offerOnOrder(sellerB, orderId, PoundMoney.ofCoppets(30));
        List<Refund> refunds = session.close();
        assertTrue(refunds.contains(new Refund(sellerA, lotId)));
        assertTrue(refunds.contains(new Refund(sellerB, negId)));
        assertEquals(2, refunds.size());
        // після закриття всі операції відхиляються
        assertThrows(MarketException.class,
                () -> session.listLot(sellerA, "custom:crimson_star", 1, PoundMoney.ofCoppets(1)));
    }

    /**
     * Інваріант збереження: після довільного сценарію «зайшло = видано + повернено»
     * і для ескроу-предметів, і для грошей (сплачено = виручка + комісія).
     */
    @Test
    void conservationInvariantHolds() {
        UUID lotId = session.listLot(sellerA, "custom:crimson_star", 2, PoundMoney.ofCoppets(30));
        UUID orderId = session.placeOrder(buyer, "custom:dimensional_wanderer_eye", 1, KNOWN);
        UUID negA = session.offerOnOrder(sellerA, orderId, PoundMoney.ofCoppets(20));
        UUID negB = session.offerOnOrder(sellerB, orderId, PoundMoney.ofCoppets(25));
        // ескроу зайшло: lotId, negA, negB
        Settlement lotSale = session.buyLot(buyer, lotId, 2, 0);
        AcceptResult acceptA = session.accept(buyer, negA, 1, 0);
        List<Refund> closeRefunds = session.close();

        // Предмети: кожен із 3 ескроу має рівно один вихід
        java.util.Set<UUID> settled = Set.of(lotSale.escrowRef(), acceptA.settlement().escrowRef());
        java.util.Set<UUID> refunded = new java.util.HashSet<>();
        acceptA.releasedEscrows().forEach(r -> refunded.add(r.escrowRef()));
        closeRefunds.forEach(r -> refunded.add(r.escrowRef()));
        assertEquals(Set.of(lotId, negA), settled);
        assertEquals(Set.of(negB), refunded);

        // Гроші: сплачене покупцем = виручка продавців + комісія
        for (Settlement s : List.of(lotSale, acceptA.settlement())) {
            assertEquals(s.price().coppets(),
                    s.sellerProceeds().coppets() + s.commissionPaid().coppets());
            assertEquals(s.price().coppets(), s.payerCharge().paidCoppets());
        }
    }
}
