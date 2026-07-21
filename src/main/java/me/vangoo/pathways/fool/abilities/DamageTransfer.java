package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.RecentDamageHeal;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 7: Magician — Damage Transfer (Перенос шкоди), реворк у зцілення.
 *
 * <p>Загоює частину шкоди, отриманої за останні 10с (не до фулл): звичайний
 * каст — на собі з власного пулу; Shift+ПКМ на цілі — лікує ЦІЛЬ з її власного
 * недавнього пулу шкоди. Пул наповнює глобальний {@code FoolCombatListener}
 * через {@link #recordDamage}. Формула — {@link RecentDamageHeal}.
 */
public class DamageTransfer extends ActiveAbility {

    private static final int BASE_COST = 100;
    private static final int BASE_COOLDOWN = 45;
    private static final double MAX_RANGE = 8.0;

    // Недавня шкода будь-якої істоти (акумулюється в межах вікна). Живить власна широка
    // підписка нижче — жодного зовнішнього listener'а. Instance-поле: екземпляр один на pathway.
    private final Map<UUID, DamageRecord> recentDamage = new ConcurrentHashMap<>();
    // Ключ єдиної широкої підписки на шкоду (ставиться ліниво при першому касті, живе до вимкнення).
    private volatile UUID trackingKey;

    @Override
    public String getName() {
        return "Перенос шкоди";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int ceiling = (int) RecentDamageHeal.healCeiling(userSequence);
        return "Загоює половину шкоди, отриманої за останні " + (RecentDamageHeal.DAMAGE_WINDOW_MS / 1000) +
                "с (до " + ceiling + " HP, не до фулл). Shift+ПКМ на цілі — лікує її з " +
                "її ж недавньої шкоди.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    /**
     * Ставить (один раз) власну широку підписку на {@link EntityDamageEvent}, що фіксує
     * недавню шкоду будь-якої істоти. Ключ — окремий UUID, тож інші здібності її не чіпають;
     * підписка живе до вимкнення плагіна (авто-знімається Bukkit'ом). Ліниво при першому касті.
     */
    private void ensureTracking(IAbilityContext context) {
        if (trackingKey != null) return;
        synchronized (this) {
            if (trackingKey != null) return;
            UUID key = UUID.randomUUID();
            context.events().subscribeToTemporaryEvent(key,
                    EntityDamageEvent.class,
                    e -> e.getEntity() instanceof LivingEntity && !e.isCancelled(),
                    e -> recordDamage(e.getEntity().getUniqueId(), e.getFinalDamage()),
                    Integer.MAX_VALUE
            );
            trackingKey = key;
        }
    }

    private void recordDamage(UUID entityId, double amount) {
        if (amount <= 0) return;
        long now = System.currentTimeMillis();
        recentDamage.compute(entityId, (id, existing) -> {
            if (existing != null && (now - existing.timestamp) < RecentDamageHeal.DAMAGE_WINDOW_MS) {
                existing.amount += amount;
                existing.timestamp = now;
                return existing;
            }
            return new DamageRecord(now, amount);
        });
    }

    private double recentDamageOf(UUID id) {
        DamageRecord record = recentDamage.get(id);
        if (record == null) return 0;
        if (System.currentTimeMillis() - record.timestamp > RecentDamageHeal.DAMAGE_WINDOW_MS) {
            recentDamage.remove(id);
            return 0;
        }
        return record.amount;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        ensureTracking(context);
        UUID casterId = context.getCasterId();
        Sequence seq = context.getCasterBeyonder().getSequence();

        if (context.playerData().isSneaking(casterId)) {
            return healTarget(context, casterId, seq);
        }
        return healSelf(context, casterId, seq);
    }

    private AbilityResult healSelf(IAbilityContext context, UUID casterId, Sequence seq) {
        double recent = recentDamageOf(casterId);
        double heal = RecentDamageHeal.healAmount(recent, seq);
        if (heal <= 0) {
            return AbilityResult.failure("Ви не отримували шкоди впродовж останніх " +
                    (RecentDamageHeal.DAMAGE_WINDOW_MS / 1000) + " секунд");
        }
        recentDamage.remove(casterId);
        context.entity().heal(casterId, heal);

        Location loc = context.playerData().getCurrentLocation(casterId);
        if (loc != null) {
            context.effects().spawnParticle(Particle.HEART, loc.clone().add(0, 2, 0), 8, 0.3, 0.2, 0.3);
            context.effects().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 12, 0.3, 0.4, 0.3);
            context.effects().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.4f);
        }
        context.messaging().sendMessage(casterId,
                ChatColor.GREEN + "✦ Ви загоїли " + ChatColor.WHITE +
                        String.format("%.1f", heal) + ChatColor.GREEN + " HP");
        return AbilityResult.success();
    }

    private AbilityResult healTarget(IAbilityContext context, UUID casterId, Sequence seq) {
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі в радіусі " + (int) MAX_RANGE + " блоків");
        }
        LivingEntity target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        double recent = recentDamageOf(targetId);
        double heal = RecentDamageHeal.healAmount(recent, seq);
        if (heal <= 0) {
            return AbilityResult.failure("Ціль не отримувала шкоди останнім часом");
        }
        recentDamage.remove(targetId);

        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        Location targetLoc = target.getLocation();
        playTransferEffect(context, casterLoc, targetLoc);

        context.scheduling().scheduleDelayed(() -> {
            context.entity().heal(targetId, heal);
            context.effects().spawnParticle(Particle.HEART, targetLoc.clone().add(0, 1.5, 0), 8, 0.3, 0.3, 0.3);
            context.effects().playSound(targetLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.2f);

            context.messaging().sendMessage(casterId,
                    ChatColor.GREEN + "✦ Ви зцілили " + ChatColor.WHITE +
                            (target instanceof Player p ? p.getName() : target.getType().name()) +
                            ChatColor.GREEN + " на " + String.format("%.1f", heal) + " HP");
            if (target instanceof Player targetPlayer) {
                context.messaging().sendMessage(targetId,
                        ChatColor.GREEN + "✦ Ваші рани загоєно (+" + String.format("%.1f", heal) + " HP)");
            }
        }, 12L);

        return AbilityResult.success();
    }

    private void playTransferEffect(IAbilityContext context, Location from, Location to) {
        if (from == null || to == null) return;
        for (int i = 0; i < 12; i++) {
            int tick = i;
            context.scheduling().scheduleDelayed(() -> {
                double progress = tick / 12.0;
                double x = from.getX() + (to.getX() - from.getX()) * progress;
                double y = (from.getY() + 1) + (to.getY() + 1 - from.getY() - 1) * progress;
                double z = from.getZ() + (to.getZ() - from.getZ()) * progress;
                Location point = new Location(from.getWorld(), x, y, z);
                Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(120, 230, 120), 1.0f);
                if (from.getWorld() != null) {
                    from.getWorld().spawnParticle(Particle.DUST, point, 3, 0.05, 0.05, 0.05, 0, green);
                }
            }, tick);
        }
        context.effects().playSound(from, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.9f);
    }

    private static class DamageRecord {
        long timestamp;
        double amount;

        DamageRecord(long timestamp, double amount) {
            this.timestamp = timestamp;
            this.amount = amount;
        }
    }

    @Override
    public void cleanUp() {
        // cleanUp() кличеться на КОЖЕН вихід гравця (інстанс спільний), тож НЕ чистимо
        // глобальний recentDamage — інакше вихід одного стер би трекінг усіх. Мапа
        // самоочищається за вікном при читанні; підписка знімається на вимкненні плагіна.
    }
}
