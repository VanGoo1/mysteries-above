package me.vangoo.domain.market;

import java.util.Optional;

/**
 * Інструкція оплати фізичними монетами з розміном: скільки фунтів/коппетів зняти
 * і скільки здачі повернути коппетами. Жадібно: спершу коппети, потім фунти.
 */
public record CoinChange(int takePounds, int takeCoppets, int changeCoppets) {

    /** @return empty, якщо в гаманці не вистачає на ціну. */
    public static Optional<CoinChange> make(int poundsHeld, int coppetsHeld, PoundMoney price) {
        if (poundsHeld < 0 || coppetsHeld < 0) {
            throw new IllegalArgumentException("Гаманець не може бути від'ємним");
        }
        int remaining = price.coppets();
        int takeCoppets = Math.min(coppetsHeld, remaining);
        remaining -= takeCoppets;
        int takePounds = (remaining + PoundMoney.COPPETS_PER_POUND - 1) / PoundMoney.COPPETS_PER_POUND;
        if (takePounds > poundsHeld) {
            return Optional.empty();
        }
        int change = takePounds * PoundMoney.COPPETS_PER_POUND - remaining;
        return Optional.of(new CoinChange(takePounds, takeCoppets, change));
    }

    /** Фактично сплачена вартість у коппетах (зняте мінус здача). */
    public int paidCoppets() {
        return takePounds * PoundMoney.COPPETS_PER_POUND + takeCoppets - changeCoppets;
    }
}
