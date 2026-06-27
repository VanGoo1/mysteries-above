package me.vangoo.infrastructure.items;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Конденсує (вилучає) Характеристику у світ на заданій локації. Спільний ефект для обох каналів
 * вилучення: смерть Бешаного Warden (рампейдж) та смерть маріонетки із захопленою особистістю.
 * Правило тривіальне — рівно 1× Характеристика[шлях, seq]; мінт делегується {@link CharacteristicCodec}.
 */
public final class CharacteristicExtractor {

    private final CharacteristicCodec codec;

    public CharacteristicExtractor(CharacteristicCodec codec) {
        this.codec = codec;
    }

    /**
     * Мінтить 1× Характеристику й кидає її у світ на {@code loc}. Крапля незнищенна (не згорає в
     * лаві/вибуху). Повертає створену сутність-предмет ({@code null}, якщо локація/світ невалідні).
     */
    public Item extractTo(Location loc, String pathwayName, int sequence) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        World world = loc.getWorld();
        ItemStack item = codec.create(pathwayName, sequence, 1);
        Item dropped = world.dropItem(loc, item);
        dropped.setInvulnerable(true); // дефіцитне ядро не має тривіально зникати

        // Флейвор конденсації.
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.6f);
        world.spawnParticle(Particle.WITCH, loc.clone().add(0, 0.5, 0), 30, 0.3, 0.3, 0.3, 0.05);
        return dropped;
    }
}
