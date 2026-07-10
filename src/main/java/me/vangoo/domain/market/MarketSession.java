package me.vangoo.domain.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    private final double commissionRate;
    private final Random random;
    private final Map<UUID, Integer> aliases = new LinkedHashMap<>();
    private final Map<UUID, Lot> lots = new LinkedHashMap<>();
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
        int alias;
        do {
            alias = 1 + random.nextInt(99);
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
