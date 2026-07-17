package me.vangoo.domain.organizations;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Схованка ордену: itemKey → кількість. Невидима членам (вміст знає лише куратор).
 * Входи: здобич рейдів, DELIVER-завдання, одноразовий сід. Виходи: ЛИШЕ фавори.
 */
public class OrderStash {

    private final Map<String, Integer> items = new HashMap<>();

    public void add(String itemKey, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        items.merge(itemKey, amount, Integer::sum);
    }

    /** Атомарне списання; false — бракує (нічого не змінюється). */
    public boolean take(String itemKey, int amount) {
        int have = amountOf(itemKey);
        if (have < amount) {
            return false;
        }
        if (have == amount) {
            items.remove(itemKey);
        } else {
            items.put(itemKey, have - amount);
        }
        return true;
    }

    public int amountOf(String itemKey) {
        return items.getOrDefault(itemKey, 0);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(items);
    }

    public void restore(Map<String, Integer> saved) {
        items.clear();
        saved.forEach((k, v) -> {
            if (v != null && v > 0) {
                items.put(k, v);
            }
        });
    }
}
