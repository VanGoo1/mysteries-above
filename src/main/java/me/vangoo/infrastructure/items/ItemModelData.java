package me.vangoo.infrastructure.items;

import org.bukkit.inventory.meta.ItemMeta;

/**
 * Переклад рядкового ключа моделі в числовий {@code custom_model_data}.
 *
 * <p>Плагін і ресурс-пак усюди оперують ЧИТАБЕЛЬНИМИ ключами ({@code lavos_squid_blood},
 * {@code active}, {@code characteristic}, {@code gold_pound}) — так задано в
 * {@code custom-items.yml} і так простіше жити. Але на 1.21.1 компонент
 * {@code custom_model_data} ще суто числовий: рядкові ключі й предикат
 * {@code minecraft:select} з'явились аж у 1.21.4. Тому ключ детерміновано згортається
 * в число ось тут — єдиному місці, що знає про це перетворення.
 *
 * <p>Число мусить збігатися з {@code predicate.custom_model_data} у файлі моделі пластинки
 * ({@code assets/minecraft/models/item/music_disc_*.json}). Звіряє
 * {@code ResourcePackItemModelTest}; генерує пак {@code scripts/rp-item-models.js}, який
 * рахує рівно цю саму функцію.
 *
 * <p><b>Стабільність — головна вимога.</b> Значення для одного ключа мусить лишатись тим
 * самим НАЗАВЖДИ: у гравців в інвентарях лежать уже проштамповані предмети. Тому це чиста
 * функція від рядка (специфікований {@link String#hashCode()}), а не порядковий номер у
 * списку — інакше додавання нового інгредієнта перемальовувало б усі старі.
 */
public final class ItemModelData {

    /** Нижня межа діапазону: тримаємось подалі від 0 і від дрібних значень інших плагінів. */
    private static final int BASE = 1_000_000;

    /** Розмір діапазону: підсумкові значення лежать у [1_000_000; 9_999_999]. */
    private static final int RANGE = 9_000_000;

    private ItemModelData() {
    }

    /** Числовий {@code custom_model_data} для рядкового ключа моделі. */
    public static int of(String modelKey) {
        return Math.floorMod(modelKey.hashCode(), RANGE) + BASE;
    }

    /**
     * Проставляє {@code custom_model_data} за рядковим ключем. Порожній/{@code null} ключ —
     * no-op: предмет лишається валідним і просто виглядає ванільно.
     */
    public static void apply(ItemMeta meta, String modelKey) {
        if (meta == null || modelKey == null || modelKey.isBlank()) {
            return;
        }
        meta.setCustomModelData(of(modelKey));
    }
}
