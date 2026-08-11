package me.vangoo.pathways.common;

import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Заміна ванільного тега {@code #minecraft:undead}: {@code Tag.ENTITY_TYPES_UNDEAD} з'явився
 * в Bukkit API пізніше за 1.21.1 (як і перейменування {@code Attribute}-констант поруч, див.
 * {@code .claude/rules/minecraft-version.md}), тож склад тега продубльовано тут one time —
 * набір типів той самий, що й у {@code LightningStrike.isUndead} (Tyrant), лише за
 * {@link EntityType}, а не {@code instanceof}, бо саме так його читають виклики в Death.
 *
 * <p>Спільне місце — {@code pathways.common} — бо перевірку читає кілька шляхів (Tyrant,
 * Death), а не один; за зразком {@link SoulWard} і {@link Spirits}.
 */
public final class UndeadEntities {

    private static final Set<EntityType> TYPES = EnumSet.of(
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.BOGGED,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE,
            EntityType.WITHER, EntityType.ZOGLIN, EntityType.PHANTOM, EntityType.GIANT);

    private UndeadEntities() {
    }

    public static boolean isUndead(EntityType type) {
        return TYPES.contains(type);
    }
}
