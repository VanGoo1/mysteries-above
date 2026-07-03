package me.vangoo.infrastructure.mythic;

import me.vangoo.application.services.BeyonderService;

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

    public static BeyonderService beyonders() {
        return beyonderService;
    }
}
