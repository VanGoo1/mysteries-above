package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.context.IAmplificationContext;
import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Жива сесія «Німб Сонця» — активна аура одного власника з періодичною ціною.
 * Раз на вікно ({@link #REFRESH_PERIOD_TICKS}) списує духовність, лікує власника й союзників
 * поруч, карає темних/нежить поруч і оновлює золоте кільце над головою. У посиленому режимі
 * (Seq 5, {@code enhanced=true}) лікування — Регенерація II й додатково знімає негативні ефекти
 * із союзників ("очищення"). Вичерпалась духовність (або власник офлайн) — сесія гасне сама.
 * Тримає лише глобальний {@link IBeyonderContext} і {@link IVisualEffectsContext} — жодного
 * захопленого {@code IAbilityContext}.
 */
final class SunHaloSession {

    static final long REFRESH_PERIOD_TICKS = 20L; // раз на секунду
    private static final int BUFF_DURATION_TICKS = 40;
    private static final int DEBUFF_DURATION_TICKS = 20;

    private static final PotionEffectType[] NEGATIVE_EFFECTS = {
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA,
    };

    private final UUID ownerId;
    private final int range;
    private final int damage;
    private final int periodicCost;
    private final Color color;
    private final boolean enhanced;
    private final IBeyonderContext beyonderContext;
    private final IVisualEffectsContext visuals;
    private final IAmplificationContext amplificationContext;
    private final Map<UUID, SunHaloSession> sessions;
    private BukkitTask task;
    private BukkitTask haloTask;

    SunHaloSession(UUID ownerId, int range, int damage, int periodicCost, Color color, boolean enhanced,
                   IBeyonderContext beyonderContext, IVisualEffectsContext visuals,
                   IAmplificationContext amplificationContext, Map<UUID, SunHaloSession> sessions) {
        this.ownerId = ownerId;
        this.range = range;
        this.damage = damage;
        this.periodicCost = periodicCost;
        this.color = color;
        this.enhanced = enhanced;
        this.beyonderContext = beyonderContext;
        this.visuals = visuals;
        this.amplificationContext = amplificationContext;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Запускає персистентний німб над головою власника — тримається, доки аура активна. */
    void start() {
        haloTask = visuals.playPersistentHalo(ownerId, color);
    }

    /** Гасить ауру: прибирає з активних, зупиняє таски (тик і німб). Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.0f, enhanced ? 0.5f : 0.6f);
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        if (haloTask != null && !haloTask.isCancelled()) {
            haloTask.cancel();
        }
    }

    /** Один тік: списує періодичну ціну; вичерпано — гасне; інакше лікує/(очищує)/карає. */
    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            cancel();
            return;
        }

        Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
        if (beyonder == null) {
            cancel();
            return;
        }
        if (beyonder.getSpirituality().current() < periodicCost) {
            owner.sendActionBar(Component.text("✗ Духовність вичерпана — німб згасає"));
            cancel();
            return;
        }
        beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));

        buffAlly(owner);
        for (Player ally : owner.getWorld().getNearbyPlayers(owner.getLocation(), range)) {
            buffAlly(ally);
        }

        for (var entity : owner.getWorld().getNearbyEntities(owner.getLocation(), range, range, range)) {
            if (!(entity instanceof LivingEntity living) || living.getUniqueId().equals(ownerId)) continue;
            if (!HolyTargetClassifier.isDarkOrUndead(living, beyonderContext)) continue;

            living.damage((int) Math.ceil(damage * amplificationContext.getDamageMultiplier(ownerId)));
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, DEBUFF_DURATION_TICKS, 0, false, false));
            if (enhanced) {
                living.getWorld().spawnParticle(Particle.END_ROD, living.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2);
            }
        }
    }

    private void buffAlly(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, BUFF_DURATION_TICKS, enhanced ? 1 : 0, false, false));
        if (enhanced) {
            for (PotionEffectType negative : NEGATIVE_EFFECTS) {
                target.removePotionEffect(negative);
            }
        }
    }
}
