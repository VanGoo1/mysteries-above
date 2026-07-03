package me.vangoo.infrastructure.mythic;

import io.lumine.mythic.core.skills.CustomComponentRegistry;
import me.vangoo.application.services.BeyonderService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Статичний міст до сервісів плагіна для кастомних компонентів MythicMobs.
 * MythicMobs сам конструює механіки/умови з фіксованою сигнатурою конструктора,
 * тому DI тут неможливий — єдиний дозволений static-виняток (див. .claude/rules/mythic-creatures.md).
 */
public final class MythicBridge {

    private static volatile BeyonderService beyonderService;

    private MythicBridge() {}

    public static void init(BeyonderService service) {
        beyonderService = service;
    }

    /** Реєструє кастомні механіки/умови MythicMobs (пакет components). Викликати в onEnable ПІСЛЯ init(...). */
    public static void registerComponents(JavaPlugin plugin) {
        new CustomComponentRegistry(plugin, "me.vangoo.infrastructure.mythic.components");
    }

    public static BeyonderService beyonders() {
        return beyonderService;
    }
}
