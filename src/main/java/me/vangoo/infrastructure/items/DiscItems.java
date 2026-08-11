package me.vangoo.infrastructure.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DiscItems {

    public static final int STACK_SIZE = 64;

    private DiscItems() {
    }

    public static boolean isDisc(Material material) {
        return material != null && material.name().startsWith("MUSIC_DISC_");
    }

    public static void applyStackSize(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        meta.setMaxStackSize(STACK_SIZE);
    }

    /**
     * Best-effort: прибирає компонент {@code jukebox_playable}, щоб предмет не
     * можна було вставити в
     * програвач. Реалізовано РЕФЛЕКСІЄЮ через Paper DataComponent API
     * ({@code ItemStack.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE)}) —
     * компілюємось проти
     * spigot-api, який цього API не має. На Paper спрацьовує; на чистому Spigot
     * мовчки нічого не
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
