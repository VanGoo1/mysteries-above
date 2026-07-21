package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.BreathDepthLimit;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 7: Magician — Underwater Breathing (Підводне дихання).
 *
 * <p>Пасивка: під водою потойбічний дихає нескінченно, ПОКИ не занурився глибше
 * порогу від поверхні ({@link BreathDepthLimit}, база 5 блоків, росте зі силою).
 * Глибше — кисень витрачається як звичайно.
 */
public class AquaticBreath extends PermanentPassiveAbility {

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Підводне дихання";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви дихаєте під водою нескінченно, поки не занурились глибше " +
                BreathDepthLimit.maxDepth(userSequence) + " блоків від поверхні.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        tickCounters.put(context.getCasterId(), 0);
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        tickCounters.remove(context.getCasterId());
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        tickCounters.put(casterId, counter);
        if (counter % 5 != 0) return; // раз на 0.25с достатньо, щоб тримати кисень

        Player player = context.getCasterPlayer();
        if (player == null || !player.isInWater()) return;

        int limit = BreathDepthLimit.maxDepth(context.getCasterBeyonder().getSequence());
        if (depthBelowSurface(player) <= limit) {
            player.setRemainingAir(player.getMaximumAir());
        }
    }

    /** Скільки блоків води над головою гравця до першого не-водяного блоку (поверхні). */
    private int depthBelowSurface(Player player) {
        Location eye = player.getEyeLocation();
        int depth = 0;
        Block block = eye.getBlock();
        while (block.getType() == Material.WATER && depth < 64) {
            depth++;
            block = block.getRelative(0, 1, 0);
        }
        return depth;
    }

    @Override
    public void cleanUp() {
        tickCounters.clear();
    }
}
