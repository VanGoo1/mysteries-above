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
    private static volatile me.vangoo.application.services.AbilityLockManager abilityLockManager;
    private static volatile me.vangoo.infrastructure.theft.TheftLedger theftLedger;

    private MythicBridge() {}

    public static void init(BeyonderService service,
                            me.vangoo.application.services.AbilityLockManager locks,
                            me.vangoo.infrastructure.theft.TheftLedger ledger) {
        beyonderService = service;
        abilityLockManager = locks;
        theftLedger = ledger;
    }

    /** Реєструє кастомні механіки/умови MythicMobs (пакет components). Викликати в onEnable ПІСЛЯ init(...). */
    public static void registerComponents(JavaPlugin plugin) {
        new CustomComponentRegistry(plugin, "me.vangoo.infrastructure.mythic.components");
    }

    public static BeyonderService beyonders() {
        return beyonderService;
    }

    /** Печатка всіх здібностей жертви (MA_Error_*_MindLock). */
    public static me.vangoo.application.services.AbilityLockManager abilityLocks() {
        return abilityLockManager;
    }

    /** Реєстр крадіжок: моб краде силу так само, як гравець-Прометей (MA_Error_S6_PowerTheft). */
    public static me.vangoo.infrastructure.theft.TheftLedger thefts() {
        return theftLedger;
    }
}
