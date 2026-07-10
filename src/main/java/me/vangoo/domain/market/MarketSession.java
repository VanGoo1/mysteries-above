package me.vangoo.domain.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Агрегат одного збору: учасники з псевдонімами, лоти, замовлення, торги, комісія.
 * Чисте правило: жодного Bukkit; предмети представлені itemKey (custom:<id> /
 * characteristic:<pathway>:<seq> / recipe:<pathway>:<seq>), реальні стаки тримає
 * ескроу-сховище в application за escrowRef (lotId / negotiationId).
 */
public final class MarketSession {

    /** Порушення правила ринку; message — українською, показується гравцеві як є. */
    public static class MarketException extends RuntimeException {
        public MarketException(String message) {
            super(message);
        }
    }

    public record Lot(UUID lotId, UUID sellerId, String itemKey, int amount,
                      PoundMoney price, boolean sold) {}

    /** Команда застосунку: хто платить, хто отримує, скільки згорає, який ескроу передати. */
    public record Settlement(UUID payerId, UUID payeeId, PoundMoney price, PoundMoney commissionPaid,
                             PoundMoney sellerProceeds, CoinChange payerCharge,
                             UUID escrowRef, String itemKey, int amount) {}

    /** Команда застосунку: повернути ескроу-стак власникові. */
    public record Refund(UUID ownerId, UUID escrowRef) {}

    public record BuyOrder(UUID orderId, UUID buyerId, String itemKey, int amount, boolean fulfilled) {}

    public enum NegotiationState { OPEN, ACCEPTED, WITHDRAWN }

    /** Проєкція торгу для GUI (без мутабельного стану назовні). */
    public record NegotiationView(UUID negotiationId, UUID orderId, UUID sellerId, UUID buyerId,
                                  PoundMoney currentPrice, UUID turnOf, NegotiationState state,
                                  String itemKey, int amount) {}

    public record AcceptResult(Settlement settlement, List<Refund> releasedEscrows) {}

    private static final class Negotiation {
        final UUID negotiationId;
        final UUID orderId;
        final UUID sellerId;
        final UUID buyerId;
        PoundMoney currentPrice;
        UUID turnOf; // хто ОТРИМАВ останню ціну — тільки він може прийняти або дати зустрічну
        NegotiationState state = NegotiationState.OPEN;

        Negotiation(UUID negotiationId, UUID orderId, UUID sellerId, UUID buyerId) {
            this.negotiationId = negotiationId;
            this.orderId = orderId;
            this.sellerId = sellerId;
            this.buyerId = buyerId;
        }
    }

    private final double commissionRate;
    private final Random random;
    private final Map<UUID, Integer> aliases = new LinkedHashMap<>();
    private final Map<UUID, Lot> lots = new LinkedHashMap<>();
    private final Map<UUID, BuyOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, Negotiation> negotiations = new LinkedHashMap<>();
    private boolean closed;

    public MarketSession(double commissionRate, Random random) {
        if (commissionRate < 0.0 || commissionRate >= 1.0) {
            throw new IllegalArgumentException("Commission rate must be in [0,1): " + commissionRate);
        }
        this.commissionRate = commissionRate;
        this.random = random;
    }

    /** Реєструє учасника; повертає його номер-псевдонім (ідемпотентно). */
    public int registerParticipant(UUID playerId) {
        Integer existing = aliases.get(playerId);
        if (existing != null) {
            return existing;
        }
        // Діапазон розширюється разом із кількістю учасників, щоб щонайменше половина
        // кандидатів завжди була вільна — інакше зі 100-м учасником цикл зависав би назавжди.
        int bound = Math.max(99, (aliases.size() + 1) * 2);
        int alias;
        do {
            alias = 1 + random.nextInt(bound);
        } while (aliases.containsValue(alias));
        aliases.put(playerId, alias);
        return alias;
    }

    public boolean isParticipant(UUID playerId) {
        return aliases.containsKey(playerId);
    }

    public String aliasOf(UUID playerId) {
        Integer alias = aliases.get(playerId);
        if (alias == null) {
            throw new MarketException("Ви не учасник збору");
        }
        return "Незнайомець №" + alias;
    }

    public UUID listLot(UUID sellerId, String itemKey, int amount, PoundMoney price) {
        ensureOpen();
        ensureParticipant(sellerId);
        if (amount <= 0) {
            throw new MarketException("Кількість має бути додатною");
        }
        if (price.isZero()) {
            throw new MarketException("Ціна має бути більшою за нуль");
        }
        UUID lotId = UUID.randomUUID();
        lots.put(lotId, new Lot(lotId, sellerId, itemKey, amount, price, false));
        return lotId;
    }

    public List<Lot> activeLots() {
        List<Lot> active = new ArrayList<>();
        for (Lot lot : lots.values()) {
            if (!lot.sold()) {
                active.add(lot);
            }
        }
        return active;
    }

    public Settlement buyLot(UUID buyerId, UUID lotId, int buyerPounds, int buyerCoppets) {
        ensureOpen();
        ensureParticipant(buyerId);
        Lot lot = lots.get(lotId);
        if (lot == null || lot.sold()) {
            throw new MarketException("Лот уже недоступний");
        }
        if (lot.sellerId().equals(buyerId)) {
            throw new MarketException("Це ваш власний лот");
        }
        CoinChange charge = CoinChange.make(buyerPounds, buyerCoppets, lot.price())
                .orElseThrow(() -> new MarketException("Недостатньо монет: потрібно " + lot.price().format()));
        lots.put(lotId, new Lot(lot.lotId(), lot.sellerId(), lot.itemKey(), lot.amount(), lot.price(), true));
        PoundMoney commission = lot.price().commission(commissionRate);
        return new Settlement(buyerId, lot.sellerId(), lot.price(), commission,
                lot.price().minus(commission), charge, lotId, lot.itemKey(), lot.amount());
    }

    public UUID placeOrder(UUID buyerId, String itemKey, int amount, Set<String> allowedKeys) {
        ensureOpen();
        ensureParticipant(buyerId);
        if (amount <= 0) {
            throw new MarketException("Кількість має бути додатною");
        }
        if (!allowedKeys.contains(itemKey)) {
            throw new MarketException("Можна замовляти лише інгредієнти відомих вам рецептів");
        }
        UUID orderId = UUID.randomUUID();
        orders.put(orderId, new BuyOrder(orderId, buyerId, itemKey, amount, false));
        return orderId;
    }

    public List<BuyOrder> openOrders() {
        List<BuyOrder> open = new ArrayList<>();
        for (BuyOrder order : orders.values()) {
            if (!order.fulfilled()) {
                open.add(order);
            }
        }
        return open;
    }

    /** Оферта продавця. Застосунок кладе предмет у ескроу за повернутим negotiationId. */
    public UUID offerOnOrder(UUID sellerId, UUID orderId, PoundMoney price) {
        ensureOpen();
        ensureParticipant(sellerId);
        BuyOrder order = orders.get(orderId);
        if (order == null || order.fulfilled()) {
            throw new MarketException("Замовлення вже недоступне");
        }
        if (order.buyerId().equals(sellerId)) {
            throw new MarketException("Це ваше власне замовлення");
        }
        if (price.isZero()) {
            throw new MarketException("Ціна має бути більшою за нуль");
        }
        UUID negotiationId = UUID.randomUUID();
        Negotiation negotiation = new Negotiation(negotiationId, orderId, sellerId, order.buyerId());
        negotiation.currentPrice = price;
        negotiation.turnOf = order.buyerId(); // покупець отримав першу ціну
        negotiations.put(negotiationId, negotiation);
        return negotiationId;
    }

    public void counter(UUID actorId, UUID negotiationId, PoundMoney newPrice) {
        ensureOpen();
        Negotiation negotiation = openNegotiation(negotiationId);
        if (!negotiation.turnOf.equals(actorId)) {
            throw new MarketException("Зараз не ваш хід у цьому торзі");
        }
        if (newPrice.isZero()) {
            throw new MarketException("Ціна має бути більшою за нуль");
        }
        negotiation.currentPrice = newPrice;
        negotiation.turnOf = actorId.equals(negotiation.buyerId)
                ? negotiation.sellerId : negotiation.buyerId;
    }

    /**
     * Акцепт того, чий зараз хід. Платить ЗАВЖДИ покупець, тому гаманець
     * (buyerPounds/buyerCoppets) — покупця, незалежно від того, хто приймає.
     */
    public AcceptResult accept(UUID actorId, UUID negotiationId, int buyerPounds, int buyerCoppets) {
        ensureOpen();
        Negotiation negotiation = openNegotiation(negotiationId);
        if (!negotiation.turnOf.equals(actorId)) {
            throw new MarketException("Прийняти може лише той, хто отримав останню ціну");
        }
        BuyOrder order = orders.get(negotiation.orderId);
        CoinChange charge = CoinChange.make(buyerPounds, buyerCoppets, negotiation.currentPrice)
                .orElseThrow(() -> new MarketException(
                        "У покупця недостатньо монет: потрібно " + negotiation.currentPrice.format()));
        negotiation.state = NegotiationState.ACCEPTED;
        orders.put(order.orderId(),
                new BuyOrder(order.orderId(), order.buyerId(), order.itemKey(), order.amount(), true));
        List<Refund> released = new ArrayList<>();
        for (Negotiation sibling : negotiations.values()) {
            if (sibling.orderId.equals(negotiation.orderId) && sibling.state == NegotiationState.OPEN) {
                sibling.state = NegotiationState.WITHDRAWN;
                released.add(new Refund(sibling.sellerId, sibling.negotiationId));
            }
        }
        PoundMoney commission = negotiation.currentPrice.commission(commissionRate);
        Settlement settlement = new Settlement(negotiation.buyerId, negotiation.sellerId,
                negotiation.currentPrice, commission, negotiation.currentPrice.minus(commission),
                charge, negotiationId, order.itemKey(), order.amount());
        return new AcceptResult(settlement, released);
    }

    /** Відмова будь-якої сторони; ескроу повертається продавцеві. */
    public Refund withdraw(UUID actorId, UUID negotiationId) {
        ensureOpen();
        Negotiation negotiation = openNegotiation(negotiationId);
        if (!negotiation.sellerId.equals(actorId) && !negotiation.buyerId.equals(actorId)) {
            throw new MarketException("Ви не сторона цього торгу");
        }
        negotiation.state = NegotiationState.WITHDRAWN;
        return new Refund(negotiation.sellerId, negotiationId);
    }

    public List<NegotiationView> negotiationsOf(UUID playerId) {
        List<NegotiationView> views = new ArrayList<>();
        for (Negotiation n : negotiations.values()) {
            if (n.state == NegotiationState.OPEN
                    && (n.sellerId.equals(playerId) || n.buyerId.equals(playerId))) {
                BuyOrder order = orders.get(n.orderId);
                views.add(new NegotiationView(n.negotiationId, n.orderId, n.sellerId, n.buyerId,
                        n.currentPrice, n.turnOf, n.state, order.itemKey(), order.amount()));
            }
        }
        return views;
    }

    /** Закриває сесію: усі непродані лоти й відкриті торги → повернення власникам. */
    public List<Refund> close() {
        if (closed) {
            return List.of();
        }
        closed = true;
        List<Refund> refunds = new ArrayList<>();
        for (Lot lot : lots.values()) {
            if (!lot.sold()) {
                refunds.add(new Refund(lot.sellerId(), lot.lotId()));
            }
        }
        for (Negotiation n : negotiations.values()) {
            if (n.state == NegotiationState.OPEN) {
                n.state = NegotiationState.WITHDRAWN;
                refunds.add(new Refund(n.sellerId, n.negotiationId));
            }
        }
        return refunds;
    }

    private Negotiation openNegotiation(UUID negotiationId) {
        Negotiation negotiation = negotiations.get(negotiationId);
        if (negotiation == null || negotiation.state != NegotiationState.OPEN) {
            throw new MarketException("Цей торг уже завершено");
        }
        return negotiation;
    }

    private void ensureOpen() {
        if (closed) {
            throw new MarketException("Ринок уже закрито");
        }
    }

    private void ensureParticipant(UUID playerId) {
        if (!aliases.containsKey(playerId)) {
            throw new MarketException("Ви не учасник збору");
        }
    }
}
