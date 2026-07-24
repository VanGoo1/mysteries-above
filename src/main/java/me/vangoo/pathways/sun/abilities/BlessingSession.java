package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.context.IVisualEffectsContext;
import me.vangoo.domain.entities.Beyonder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Жива сесія «Благословення» — активна аура одного власника з періодичною ціною.
 * <p>
 * Тікає власним Bukkit-таском: раз на вікно ({@link #REFRESH_PERIOD_TICKS}) списує духовність
 * із {@link Beyonder} і оновлює ефекти на власнику й союзниках поруч. Вичерпалась духовність
 * (або власник офлайн) — сесія гасне сама. Тримає лише глобальний, не прив'язаний до кастера
 * {@link IBeyonderContext} і спільні колекції активних кастерів — жодного захопленого
 * {@code IAbilityContext}.
 */
final class BlessingSession {

    static final long REFRESH_PERIOD_TICKS = 100L; // вікно списання/оновлення (5 с)
    private static final int EFFECT_DURATION_TICKS = 120; // тривалість Опору — перекриває вікно
    private static final int AURA_DURATION_TICKS = 16;    // швидке згасання аури активації

    private final UUID ownerId;
    private final int range;
    private final int amplifier;
    private final int periodicCost;
    private final Color color;
    private final IBeyonderContext beyonderContext;
    private final IVisualEffectsContext visuals;
    private final Set<UUID> blessed;
    private final Map<UUID, BlessingSession> sessions;
    private BukkitTask task;

    BlessingSession(UUID ownerId, int range, int amplifier, int periodicCost, Color color,
                    IBeyonderContext beyonderContext, IVisualEffectsContext visuals,
                    Set<UUID> blessed, Map<UUID, BlessingSession> sessions) {
        this.ownerId = ownerId;
        this.range = range;
        this.amplifier = amplifier;
        this.periodicCost = periodicCost;
        this.color = color;
        this.beyonderContext = beyonderContext;
        this.visuals = visuals;
        this.blessed = blessed;
        this.sessions = sessions;
    }

    /** Прив'язує повторюваний таск, щоб сесія могла скасувати саму себе. */
    void bindTask(BukkitTask task) {
        this.task = task;
    }

    /** Гасить ауру: знімає ефекти, прибирає з активних, зупиняє таск. Ідемпотентно. */
    void cancel() {
        blessed.remove(ownerId);
        sessions.remove(ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            for (Player target : owner.getWorld().getNearbyPlayers(owner.getLocation(), range)) {
                target.removePotionEffect(PotionEffectType.RESISTANCE);
            }
            owner.removePotionEffect(PotionEffectType.RESISTANCE);
            owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.0f, 0.5f);
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /** Активація: благословляє всіх у радіусі та спалахує на кожному швидкою аурою. */
    void applyNow() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        boolean ownerSeen = false;
        for (Player target : owner.getWorld().getNearbyPlayers(owner.getLocation(), range)) {
            bless(target);
            visuals.playFadingAura(target.getLocation(), color, AURA_DURATION_TICKS);
            if (target.getUniqueId().equals(ownerId)) ownerSeen = true;
        }
        if (!ownerSeen) { // підстраховка, якщо власник поза вибіркою getNearbyPlayers
            bless(owner);
            visuals.playFadingAura(owner.getLocation(), color, AURA_DURATION_TICKS);
        }
    }

    /** Один тік: списує періодичну ціну; вичерпано — гасне; інакше оновлює ефекти. */
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
            owner.sendActionBar(Component.text("✗ Духовність вичерпана — благословення згасає"));
            cancel();
            return;
        }
        beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));
        applyBlessing(owner);
    }

    private void applyBlessing(Player owner) {
        for (Player target : owner.getWorld().getNearbyPlayers(owner.getLocation(), range)) {
            bless(target);
        }
        bless(owner); // на випадок, якщо власник поза вибіркою getNearbyPlayers
    }

    private void bless(Player target) {
        target.removePotionEffect(PotionEffectType.WITHER);
        target.removePotionEffect(PotionEffectType.DARKNESS);
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, EFFECT_DURATION_TICKS, amplifier, false, false));
    }
}
