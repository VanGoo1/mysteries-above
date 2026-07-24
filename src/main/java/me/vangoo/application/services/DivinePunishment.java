package me.vangoo.application.services;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Божественна кара — перевикористовуваний ранер покарання (не лише для контрактів Sun).
 * Механіки викликають {@link #punish(UUID)}; послідовність кар — упорядкований набір
 * приватних кроків, тож додати новий штраф = додати метод і рядок виклику (без окремого
 * реєстру/фабрики). Ефект-візуал бере з {@link IVisualEffectsContext}; печатку здібностей —
 * зі спільного {@link AbilityLockManager}. Обидва — глобальні сервіси без ідентичності
 * кастера, тож ранер тримає їх напряму.
 */
public class DivinePunishment {

    private static final double TRUE_DAMAGE = 12.0;         // ігнорує броню (пряме здоров'я)
    private static final int SEAL_MIN_SECONDS = 600;        // 10 хв
    private static final int SEAL_MAX_SECONDS = 900;        // 15 хв
    private static final Color HOLY_GOLD = PathwayBranding.liquidOf("Sun");

    private final AbilityLockManager abilityLockManager;
    private final IVisualEffectsContext visualEffects;

    public DivinePunishment(AbilityLockManager abilityLockManager, IVisualEffectsContext visualEffects) {
        this.abilityLockManager = abilityLockManager;
        this.visualEffects = visualEffects;
    }

    /** Карає гравця повним ланцюгом Божественної кари. No-op, якщо гравець офлайн. */
    public void punish(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        int sealSeconds = ThreadLocalRandom.current().nextInt(SEAL_MIN_SECONDS, SEAL_MAX_SECONDS + 1);

        strikeHolyLightning(player);
        dealTrueDamage(player);
        sealAbilities(player, sealSeconds);
        showBrokenSunDisc(player, sealSeconds);

        player.sendMessage(ChatColor.GOLD + "☀ Бог засвідчив вирок. Ваші здібності запечатано на "
                + (sealSeconds / 60) + " хв.");
    }

    private void strikeHolyLightning(Player player) {
        visualEffects.playHolyLightning(player.getLocation());
    }

    /** Пряме зняття здоров'я — оминає броню/резисти (на відміну від {@code damage()}). */
    private void dealTrueDamage(Player player) {
        double newHealth = Math.max(0.0, player.getHealth() - TRUE_DAMAGE);
        player.setHealth(newHealth);
    }

    private void sealAbilities(Player player, int sealSeconds) {
        abilityLockManager.lockPlayer(player.getUniqueId(), sealSeconds);
    }

    private void showBrokenSunDisc(Player player, int sealSeconds) {
        visualEffects.playBrokenSunDisc(player.getUniqueId(), HOLY_GOLD, sealSeconds * 20);
    }
}
