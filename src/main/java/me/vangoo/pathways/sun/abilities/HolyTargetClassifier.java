package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.HolyAffinity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Ефект-шар: чи вважати ціль "темною/нежиттю" для святих здібностей Sun.
 * Гравці — за pathway ({@link HolyAffinity}), моби — за ванільним типом істоти.
 * Спільна для кількох здібностей Sun (Sunshine, EvilDetection, HolyLightSummoning, SunHalo,
 * CleaveOfPurification).
 */
final class HolyTargetClassifier {

    private static final Set<EntityType> UNDEAD_TYPES = Set.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.SKELETON, EntityType.WITHER_SKELETON,
            EntityType.STRAY, EntityType.PHANTOM, EntityType.WITHER
    );

    private HolyTargetClassifier() {
    }

    static boolean isDarkOrUndead(LivingEntity target, IAbilityContext context) {
        return isDarkOrUndead(target, context.beyonder());
    }

    /**
     * Перевантаження на голому {@link IBeyonderContext}: класифікація цілі не несе ідентичності
     * жодного конкретного кастера, тож її можна тримати як глобальний сервіс у shared-хендлері
     * ({@code CleaveOfPurification}), а не захоплювати повний {@code IAbilityContext} власника касту.
     */
    static boolean isDarkOrUndead(LivingEntity target, IBeyonderContext beyonderContext) {
        if (target instanceof Player) {
            Beyonder beyonder = beyonderContext.getBeyonder(target.getUniqueId());
            return beyonder != null && HolyAffinity.isDark(beyonder.getPathway().getName());
        }
        return UNDEAD_TYPES.contains(target.getType());
    }
}
