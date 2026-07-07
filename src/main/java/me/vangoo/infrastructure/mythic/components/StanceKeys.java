package me.vangoo.infrastructure.mythic.components;

import org.bukkit.NamespacedKey;

/** Спільні PDC-ключі станової поведінки: provoke пише, rangedstance читає. */
final class StanceKeys {

    static final NamespacedKey PROVOKED_UNTIL = new NamespacedKey("mysteriesabove", "provoked_until");

    private StanceKeys() {}
}
