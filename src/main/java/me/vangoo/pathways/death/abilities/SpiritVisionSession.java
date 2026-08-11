package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.context.IGlowingContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.pathways.common.Spirits;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Жива сесія «Духовний зір» (той самий патерн, що {@code WindSprintSession}): тікає власним
 * Bukkit-таском раз на секунду, списує духовність, підсвічує нежить крізь стіни і звітує про
 * ціль під поглядом. Скінчилась духовність (або власник офлайн) — зір гасне сам.
 */
final class SpiritVisionSession {

    static final long TICK_PERIOD_TICKS = 5L;
    private static final int COST_EVERY = 4; // ціна раз на 20т (1 с)

    /** З якої Послідовності зір бачить іще й духів (Посл. 8 — «Spirit Vision is enhanced»). */
    private static final int SPIRIT_SIGHT_MAX_SEQUENCE = 8;

    private int runs;

    /**
     * Кого вже підсвічено. Світимо БЕЗ таймера й знімаємо самі: варіант із {@code durationTicks}
     * ставить окремий таск на зняття для кожного виклику, тож повторне підсвічування щосекунди
     * гасилось старим таском — контур блимав.
     */
    private final Set<UUID> glowing = new HashSet<>();

    private final UUID ownerId;
    private final double range;
    private final int periodicCost;
    private final ChatColor glowColor;
    private final IBeyonderContext beyonderContext;
    private final IGlowingContext glowingContext;
    private final Map<UUID, SpiritVisionSession> sessions;
    private BukkitTask task;

    SpiritVisionSession(UUID ownerId, double range, int periodicCost, ChatColor glowColor,
                        IBeyonderContext beyonderContext, IGlowingContext glowingContext,
                        Map<UUID, SpiritVisionSession> sessions) {
        this.ownerId = ownerId;
        this.range = range;
        this.periodicCost = periodicCost;
        this.glowColor = glowColor;
        this.beyonderContext = beyonderContext;
        this.glowingContext = glowingContext;
        this.sessions = sessions;
    }

    void bindTask(BukkitTask task) {
        this.task = task;
    }

    void tick() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) {
            cancel();
            return;
        }

        // Скан частіший за ціну: нежить, що зайшла в радіус, підхоплюється майже одразу.
        revealSpiritWorld(owner);
        reportGazedTarget(owner);

        if (++runs % COST_EVERY != 0) return;

        Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
        if (beyonder == null || beyonder.getSpirituality().current() < periodicCost) {
            owner.sendActionBar(Component.text("✗ Духовність вичерпана — зір мутніє"));
            cancel();
            return;
        }
        beyonder.setSpirituality(beyonder.getSpirituality().decrement(periodicCost));

        // «Бачить нефізичне»: димок душі лише власнику, стан видимості цілей не чіпаємо.
        owner.spawnParticle(Particle.SCULK_SOUL, owner.getEyeLocation(), 2, 0.2, 0.2, 0.2);
    }

    /** Підсвічує нежить одразу при касті, ще до першого платного такту. */
    void applyNow() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        revealSpiritWorld(owner);
        reportGazedTarget(owner);
    }

    /** Гасить контури, прибирає з активних і зупиняє таск. Ідемпотентно. */
    void cancel() {
        sessions.remove(ownerId);
        glowing.forEach(id -> glowingContext.removeGlowing(ownerId, id));
        glowing.clear();

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.playSound(owner.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, 1.4f);
            owner.sendMessage(ChatColor.GRAY + "✦ Духовний зір згас");
        }
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Нежить (а з Посл. 8 — і духи) світиться крізь стіни лише для власника зору, без
     * блимання між тактами. Духи додались саме тут: посилення зору на Посл. 8 у вікі описане
     * без деталей, а бачити духів — рівно те, що стає доступним разом зі Спілкуванням з ними.
     */
    private void revealSpiritWorld(Player owner) {
        boolean spiritsToo = seesSpirits();
        Set<UUID> inRange = new HashSet<>();
        for (Entity entity : owner.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity)) continue;
            // getCategory() на 1.21+ кидає UnsupportedOperationException — нежить лише тегом.
            boolean visible = Tag.ENTITY_TYPES_UNDEAD.isTagged(entity.getType())
                    || (spiritsToo && Spirits.isSpirit(entity));
            if (!visible) continue;
            inRange.add(entity.getUniqueId());
            if (glowing.add(entity.getUniqueId())) {
                glowingContext.setGlowing(entity.getUniqueId(), ownerId, glowColor);
            }
        }

        glowing.removeIf(id -> {
            if (inRange.contains(id)) return false;
            glowingContext.removeGlowing(ownerId, id);
            return true;
        });
    }

    /**
     * Чи зір власника вже дістає духів. Читається живою перевіркою, а не прапорцем із касту:
     * гравець може підвищити Послідовність, не гасячи зір.
     */
    private boolean seesSpirits() {
        Beyonder beyonder = beyonderContext.getBeyonder(ownerId);
        return beyonder != null && beyonder.getSequence().level() <= SPIRIT_SIGHT_MAX_SEQUENCE;
    }

    /** «Deduce a person's health and emotions»: стан і активні ефекти цілі під поглядом. */
    private void reportGazedTarget(Player owner) {
        Entity gazed = owner.getTargetEntity((int) range);
        if (!(gazed instanceof LivingEntity target)) return;

        // Атрибут теоретично може бути відсутній — NPE тут убив би весь тік зору.
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        int healthPercent = maxHealth == null || maxHealth.getValue() <= 0
                ? 100
                : (int) Math.round(100.0 * target.getHealth() / maxHealth.getValue());

        String mood = target.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .map(type -> type.getKey().getKey().replace('_', ' '))
                .collect(Collectors.joining(", "));
        if (mood.isEmpty()) mood = "спокій";

        String essence = "плоть";
        if (Spirits.isSpirit(target)) {
            essence = "дух";
        } else if (Tag.ENTITY_TYPES_UNDEAD.isTagged(target.getType())) {
            essence = "нежить";
        } else if (target instanceof Player p) {
            Beyonder targetBeyonder = beyonderContext.getBeyonder(p.getUniqueId());
            if (targetBeyonder != null) essence = "потойбічний (" + targetBeyonder.getPathway().getName() + ")";
        }

        owner.sendActionBar(Component.text(
                ChatColor.DARK_GREEN + "✦ " + ChatColor.WHITE + target.getName()
                        + ChatColor.GRAY + " · " + ChatColor.RED + healthPercent + "%"
                        + ChatColor.GRAY + " · " + ChatColor.AQUA + essence
                        + ChatColor.GRAY + " · " + ChatColor.LIGHT_PURPLE + mood));
    }
}
