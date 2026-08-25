package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.infrastructure.disguise.EntityDisguiseService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Жива сесія «Трупної личини» (каркас — {@link SpiritVisionSession}): власний Bukkit-таск,
 * духовність списується раз на секунду, скінчилась — личина спадає сама.
 *
 * <p>Личина сильніша за {@link GloomyPresence}: нежить знімається з прицілу БЕЗ перевірки на
 * провокацію — вдарений зомбі теж губить ціль, бо бачить перед собою труп, а не кривдника.
 * Платня за це — сонце: труп горить удень так само, як зомбі.
 */
final class CorpseGuiseSession {

    static final long TICK_PERIOD_TICKS = 5L;
    private static final int COST_EVERY = 4;   // ціна раз на 20т (1 с)
    private static final int REMASK_EVERY = 20; // пакети маски раз на 100т (5 с) — для нових глядачів
    private static final int SUN_FIRE_TICKS = 60;

    /** Аури гниття, що труп просто не помічає. */
    private static final PotionEffectType[] IGNORED_EFFECTS = {
            PotionEffectType.WITHER, PotionEffectType.POISON, PotionEffectType.HUNGER
    };

    private final UUID ownerId;
    private final int periodicCost;
    private final IBeyonderContext beyonderContext;
    private final IVisualEffectsContext effects;
    private final Map<UUID, CorpseGuiseSession> sessions;
    private BukkitTask task;
    private int runs;

    CorpseGuiseSession(UUID ownerId, int periodicCost, IBeyonderContext beyonderContext,
                       IVisualEffectsContext effects, Map<UUID, CorpseGuiseSession> sessions) {
        this.ownerId = ownerId;
        this.periodicCost = periodicCost;
        this.beyonderContext = beyonderContext;
        this.effects = effects;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Надіває маску одразу при касті, ще до першого такту. */
    void applyNow() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) EntityDisguiseService.disguiseAsMob(owner, EntityType.ZOMBIE);
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) {
            cancel();
            return;
        }

        endureDecay(owner);
        releaseUndead(owner);
        burnInSunlight(owner);

        runs++;
        if (runs % REMASK_EVERY == 0) {
            // Сервіс шле пакети одноразово, тож глядач, що зайшов у зону видимості пізніше,
            // бачить справжнього гравця, доки маску не переслати.
            EntityDisguiseService.disguiseAsMob(owner, EntityType.ZOMBIE);
        }
        if (runs % COST_EVERY != 0) return;

        Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
        if (beyonder == null || beyonder.getSpirituality().current() < periodicCost) {
            owner.sendActionBar(Component.text("✗ Духовність вичерпана — личина спадає"));
            cancel();
            return;
        }
        beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));
    }

    /** Знімає маску, прибирає з активних і зупиняє таск. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId);

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            EntityDisguiseService.undisguise(owner);
            owner.playSound(owner.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.6f, 1.4f);
            owner.sendMessage(ChatColor.GRAY + "☠ Трупна личина спала");
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /** «Better endure the erosion of Decay, Cold and Death auras»: гниття й мороз трупа не беруть. */
    private void endureDecay(Player owner) {
        for (PotionEffectType type : IGNORED_EFFECTS) {
            if (owner.hasPotionEffect(type)) owner.removePotionEffect(type);
        }
        if (owner.getFreezeTicks() > 0) owner.setFreezeTicks(0);
    }

    /**
     * Нежить губить ціль навіть після удару — на відміну від {@link GloomyPresence}, де удар
     * знімає захист на {@link CorpseCollectorLore#PROVOKE_MEMORY_SECONDS} секунд.
     */
    private void releaseUndead(Player owner) {
        Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
        if (beyonder == null) return;
        double radius = CorpseCollectorLore.gloomRadius(beyonder.getSequence());

        for (Entity nearby : owner.getNearbyEntities(radius, radius, radius)) {
            // getCategory() на 1.21+ кидає UnsupportedOperationException — нежить лише тегом.
            if (!(nearby instanceof Mob mob) || !Tag.ENTITY_TYPES_UNDEAD.isTagged(nearby.getType())) continue;
            if (mob.getTarget() == null || !ownerId.equals(mob.getTarget().getUniqueId())) continue;
            mob.setTarget(null);
        }
    }

    /** Ціна личини: тіло зомбі горить під відкритим сонцем так само, як справжнє. */
    private void burnInSunlight(Player owner) {
        World world = owner.getWorld();
        boolean exposed = world.getEnvironment() == World.Environment.NORMAL
                && world.isDayTime()
                && !world.hasStorm()
                && !owner.isInWater()
                && owner.getEyeLocation().getBlock().getLightFromSky() == 15;
        if (!exposed) return;

        owner.setFireTicks(Math.max(owner.getFireTicks(), SUN_FIRE_TICKS));
        effects.playDustMark(owner.getEyeLocation(), PathwayBranding.liquidOf("Death"),
                0.35, 1.0f, 6, 0);
    }
}
