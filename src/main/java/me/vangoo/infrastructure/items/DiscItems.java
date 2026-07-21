package me.vangoo.infrastructure.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Спільні правила для предметів плагіну, що стоять на музичній пластинці.
 *
 * <p>Усі предмети плагіну (інгредієнти, предмети здібностей, Характеристики, монети, речі орденів)
 * зроблені з {@code MUSIC_DISC_*} замість {@code PAPER}: пластинку не сплутати з ванільним папером,
 * який шлях Блазня споживає як справжній ресурс ({@code DollBatch}, {@code PaperThrower},
 * {@code PaperSubstitution}), і кожна категорія має власний матеріал, тож лишається розрізнюваною
 * навіть без ресурс-паку.
 *
 * <p>Пластинка приносить дві вади, які треба лікувати на КОЖНОМУ такому предметі, тому вони зібрані
 * тут, а не скопійовані по фабриках: ванільний диск стакається до 1, і його можна вставити в
 * програвач. Пропустиш один із двох викликів — предмет або перестане стакатись, або зникне в jukebox.
 */
public final class DiscItems {

    /** Ліміт стака, який мали предмети на папері — зберігаємо його при переїзді на пластинку. */
    public static final int STACK_SIZE = 64;

    private DiscItems() {
    }

    /** Чи стоїть предмет на музичній пластинці (і, отже, потребує обробки цим класом). */
    public static boolean isDisc(Material material) {
        return material != null && material.name().startsWith("MUSIC_DISC_");
    }

    /**
     * Піднімає ліміт стака до {@link #STACK_SIZE}. Ванільний диск стакається до 1 — без цього
     * виклику інвентар гравця забило б поштучними інгредієнтами.
     */
    public static void applyStackSize(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        meta.setMaxStackSize(STACK_SIZE);
    }

    /**
     * Best-effort: прибирає компонент {@code jukebox_playable}, щоб предмет не можна було вставити в
     * програвач. Реалізовано РЕФЛЕКСІЄЮ через Paper DataComponent API
     * ({@code ItemStack.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE)}) — компілюємось проти
     * spigot-api, який цього API не має. На Paper спрацьовує; на чистому Spigot мовчки нічого не
     * робить (там знадобився б лістенер на взаємодію з jukebox).
     *
     * @return {@code true}, якщо компонент вдалося зняти
     */
    public static boolean stripJukeboxPlayable(ItemStack item) {
        if (item == null) {
            return false;
        }
        try {
            Class<?> typesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Class<?> typeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
            Object jukeboxType = typesClass.getField("JUKEBOX_PLAYABLE").get(null);
            item.getClass().getMethod("unsetData", typeClass).invoke(item, jukeboxType);
            return true;
        } catch (Throwable ignored) {
            // Spigot/стара версія без DataComponent API — лишаємо предмет як є.
            return false;
        }
    }

    /**
     * Застосовує обидва правила до готового стака: підіймає ліміт стака й знімає
     * {@code jukebox_playable}. Зручний вхід для фабрик, що не тримають {@link ItemMeta} під рукою.
     */
    public static ItemStack finish(ItemStack item) {
        if (item == null || !isDisc(item.getType())) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyStackSize(meta);
            item.setItemMeta(meta);
        }
        stripJukeboxPlayable(item);
        return item;
    }
}
