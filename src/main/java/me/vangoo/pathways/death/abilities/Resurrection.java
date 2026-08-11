package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.RetinueServant;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;

import java.util.UUID;

/**
 * Death Sequence 6: Воскресіння.
 *
 * <p>Вікі: «They can turn corpses into living skeletons and Zombies; however, this kind of
 * Resurrection does not allow the reanimated corpse to have any will or vitality.» Тіло беремо
 * не з повітря: смерть лишає на місці слід (див. {@link LingeringSouls}), і саме він
 * витрачається — одне тіло, один слуга.
 *
 * <p>«Без волі» живе в {@link UndeadRetinueSession}: слуга не вибирає ворога сам і б'є лише за
 * наказом. Кістяк підіймається скелетом, решта — зомбі.
 *
 * <p>Каст у присіданні розпускає весь почет — той самий жест, що в Спілкуванні з духами
 * (Посл. 8), щоб гравцеві не вчити другий.
 */
public class Resurrection extends ActiveAbility {

    /** Обряд роблять НАД тілом: шукати труп по всій околиці — не воскресіння, а некрополь. */
    private static final double RAISE_RADIUS = 6.0;

    private final LingeringSouls traces;
    private final UndeadRetinue retinue;

    public Resurrection(LingeringSouls traces, UndeadRetinue retinue) {
        this.traces = traces;
        this.retinue = retinue;
    }

    @Override
    public String getName() {
        return "Воскресіння";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Ви піднімаєте тіло, що лежить поруч. Слуга не має ні волі, ні життя: " +
                        "він ходить за вами й б'є лише за наказом.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§a☠ Тіло поблизу (%d бл) встає зомбі або скелетом\n" +
                        "§b✦ Кількість: до %d слуг (спільний на всі способи)\n" +
                        "§7☠ Тіла лежать %d хв після смерті\n" +
                        "§7⇩ Каст у присіданні розпускає всіх слуг",
                (int) RAISE_RADIUS,
                GatekeeperLore.retinueCap(userSequence),
                SpiritGuideLore.CORPSE_TTL_SECONDS / 60);
    }

    @Override
    public int getSpiritualityCost() {
        return SpiritGuideLore.RESURRECTION_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return SpiritGuideLore.resurrectionCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCasterPlayer();
        if (caster == null) {
            return AbilityResult.failure("Ви не в світі");
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();

        // Розпуск нічого не коштує й кулдауну не ставить.
        if (caster.isSneaking()) {
            boolean released = retinue.release(casterId);
            if (!released) {
                context.messaging().sendMessage(casterId, ChatColor.GRAY + "☠ Слуг немає");
            }
            return AbilityResult.deferred();
        }

        if (retinue.isFull(casterId, sequence)) {
            int cap = GatekeeperLore.retinueCap(sequence);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "☠ Більше нежиті вам не підкоряється ("
                    + cap + "/" + cap + ")");
            return AbilityResult.deferred();
        }

        LingeringSouls.Corpse corpse = nearestCorpse(caster.getLocation(), casterId);
        if (corpse == null) {
            return AbilityResult.failure("Поблизу немає тіл");
        }

        Mob servant = raise(corpse);
        traces.consumeCorpse(corpse.victimId());
        // Тіло підняте — душі на цьому місці теж більше нема: труп устав і пішов геть.
        traces.consume(corpse.victimId());

        int size = retinue.enroll(context, casterId, servant, RetinueServant.Kind.RESURRECTED, sequence);
        announce(context, casterId, corpse, size, sequence);
        return AbilityResult.success();
    }

    private LingeringSouls.Corpse nearestCorpse(Location origin, UUID casterId) {
        LingeringSouls.Corpse nearest = null;
        double best = Double.MAX_VALUE;
        for (LingeringSouls.Corpse corpse : traces.corpsesNear(origin, RAISE_RADIUS, casterId)) {
            double distance = corpse.location().distanceSquared(origin);
            if (distance < best) {
                best = distance;
                nearest = corpse;
            }
        }
        return nearest;
    }

    /** Піднімає тіло: кістяк устає скелетом, решта — зомбі; денне сонце слуг не пече. */
    private Mob raise(LingeringSouls.Corpse corpse) {
        Location where = corpse.location().clone().add(0, 0.2, 0);
        String name = ChatColor.DARK_GREEN + "Слуга: " + ChatColor.GRAY + corpse.victimName();

        // Сонце гасить сама сесія почту (`setFireTicks(0)` щотакту): ванільного
        // `setShouldBurnInDay` у Spigot-API немає — див. `.claude/rules/minecraft-version.md`.
        if (corpse.skeletal()) {
            return where.getWorld().spawn(where, Skeleton.class, skeleton -> dress(skeleton, name));
        }
        return where.getWorld().spawn(where, Zombie.class, zombie -> dress(zombie, name));
    }

    private void dress(Mob servant, String name) {
        servant.setCustomName(name);
        servant.setCustomNameVisible(true);
        servant.setCanPickupItems(false);
    }

    private void announce(IAbilityContext context, UUID casterId,
                          LingeringSouls.Corpse corpse, int size, Sequence sequence) {
        Location where = corpse.location();
        Color deathColor = PathwayBranding.liquidOf("Death");

        context.effects().playPillarEffect(where, 2.6, 0.7, deathColor, 30);
        context.effects().playDustMark(where, deathColor, 1.2, 1.4f, 24, 40);
        context.effects().playSound(where, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.9f, 0.5f);
        context.effects().spawnParticle(Particle.SCULK_SOUL, where.clone().add(0, 1, 0),
                14, 0.3, 0.5, 0.3);

        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "☠ "
                + ChatColor.WHITE + corpse.victimName() + ChatColor.DARK_GREEN + " встав із мертвих"
                + ChatColor.GRAY + " (" + size + "/" + GatekeeperLore.retinueCap(sequence) + ")");
    }
}

// Свідомо БЕЗ cleanUp(): він кличеться на вихід БУДЬ-ЯКОГО гравця на спільному екземплярі
// здібності, тож розпустив би почет усім іншим. Сесія сама бачить, що власник офлайн.
