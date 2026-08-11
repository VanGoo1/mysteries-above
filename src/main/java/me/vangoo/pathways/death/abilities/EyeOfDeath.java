package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.GravediggerLore;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.pathways.common.Spirits;
import me.vangoo.pathways.common.UndeadEntities;
import me.vangoo.infrastructure.compat.ActionBars;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 8: Око Смерті.
 *
 * <p>Вікі: «By pressing down on their eyes, it will turn gray then colorless, allowing them to
 * quickly determine the weaknesses of even unfamiliar Undead and Spirit World creatures through
 * observation… as though they enter the world of spirits… looking down at the enemy from a
 * higher vantage point as they search for a “node”. Rampagers… count as Dead souls.»
 *
 * <p>Свідомо ІНША роль, ніж мітка {@link UndeadKnowledge} (Посл. 9): та тримається час і бере
 * лише нежить, а тут — вузол, який висить, доки не витрачений, і читає навіть незнайоме:
 * нежить, духів і реймпейджерів (мертві душі) — на повну, живих — на половину надбавки.
 *
 * <p>«Вигляд згори» лишається візуальним (промінь із точки високо над ціллю донизу): камери
 * Bukkit не дає, тож перенести буквальний «higher vantage point» у гру нічим.
 */
public class EyeOfDeath extends ActiveAbility {

    /** Звідки «дивиться» око: точка над ціллю, з якої падає промінь. */
    private static final double GAZE_HEIGHT = 6.0;

    /**
     * Вузол живе, доки його не витратили. Підписка все ж має TTL
     * ({@link GravediggerLore#NODE_TTL_SECONDS}): без нього релогін лишав би слухача назавжди.
     */
    private record Node(UUID targetId, UUID subKey, boolean dead) {}

    private final Map<UUID, Node> nodes = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Око Смерті";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "При використанні наступний ваш удар збільшує шкоду.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§a☠ Мертве (нежить, духи, та ті, хто втатили контроль): ×%.2f, крізь броню\n" +
                        "§b❤ Живе: ×%.2f\n" +
                        "§d✦ Дальність погляду: %d блоків\n" ,
                GravediggerLore.eyeDeadDamageMultiplier(userSequence),
                GravediggerLore.eyeLivingDamageMultiplier(userSequence),
                (int) GravediggerLore.eyeRange(userSequence));
    }

    @Override
    public int getSpiritualityCost() {
        return GravediggerLore.EYE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return GravediggerLore.eyeCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCasterPlayer();
        if (caster == null) {
            return AbilityResult.failure("Ви не в світі");
        }
        Sequence sequence = context.getCasterBeyonder().getSequence();

        Optional<LivingEntity> targeted =
                context.targeting().getTargetedEntity(GravediggerLore.eyeRange(sequence));
        if (targeted.isEmpty()) {
            return AbilityResult.failure("Погляд ні на кому не спинився");
        }
        LivingEntity target = targeted.get();

        boolean dead = isDeadSoul(context, target);
        double multiplier = dead
                ? GravediggerLore.eyeDeadDamageMultiplier(sequence)
                : GravediggerLore.eyeLivingDamageMultiplier(sequence);

        // Вузол один на кастера: новий каст переносить його, знімаючи старий слухач і контур.
        clearNode(context, casterId);

        UUID subKey = UUID.randomUUID();
        UUID targetId = target.getUniqueId();
        int ttlTicks = GravediggerLore.NODE_TTL_SECONDS * 20;

        context.events().subscribeToTemporaryEvent(
                subKey,
                EntityDamageByEntityEvent.class,
                event -> targetId.equals(event.getEntity().getUniqueId())
                        && UndeadKnowledge.isDealtBy(event, casterId),
                event -> strikeNode(context, casterId, event, multiplier, dead),
                ttlTicks
        );
        nodes.put(casterId, new Node(targetId, subKey, dead));

        context.glowing().setGlowing(targetId, casterId, PathwayBranding.textOf("Death"), ttlTicks);
        playGazeFromAbove(context, target, dead);
        reportWeakness(context, casterId, target, dead, multiplier);
        return AbilityResult.success();
    }

    /**
     * Мертва душа: нежить за ванільним тегом, дух за тегом пака або гравець у рейміджі —
     * вікі прямо каже, що ті рахуються мертвими душами.
     */
    private boolean isDeadSoul(IAbilityContext context, LivingEntity target) {
        if (UndeadEntities.isUndead(target.getType())) return true;
        if (Spirits.isSpirit(target)) return true;
        return target instanceof Player player && context.rampage().isInRampage(player.getUniqueId());
    }

    /** Удар у вузол: множник, для мертвого — ще й крізь броню; далі вузол згасає. */
    private void strikeNode(IAbilityContext context, UUID casterId,
                            EntityDamageByEntityEvent event, double multiplier, boolean dead) {
        event.setDamage(event.getDamage() * multiplier);
        if (dead && event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            // Мертве тримається не бронею: знімаємо саме модифікатор броні, решту резистів — ні.
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0);
        }

        Location impact = event.getEntity().getLocation();
        Color deathColor = PathwayBranding.liquidOf("Death");
        context.effects().playExplosionRingEffect(impact, 1.4, Particle.DUST,
                new Particle.DustOptions(deathColor, 1.2f));
        context.effects().spawnParticle(Particle.SCULK_SOUL, impact.clone().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
        context.effects().playSound(impact, Sound.ENTITY_ENDER_EYE_DEATH, 0.9f, 0.5f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN
                + String.format("✦ Вузол пробитий (×%.2f)", multiplier));

        clearNode(context, casterId);
    }

    /** Знімає вузол кастера: підписку, контур і запис. Ідемпотентно. */
    private void clearNode(IAbilityContext context, UUID casterId) {
        Node previous = nodes.remove(casterId);
        if (previous == null) return;

        context.events().unsubscribeAll(previous.subKey());
        context.glowing().removeGlowing(casterId, previous.targetId());
    }

    /** «Погляд згори»: промінь із високої точки в ціль + німб на місці вузла. */
    private void playGazeFromAbove(IAbilityContext context, LivingEntity target, boolean dead) {
        Color deathColor = PathwayBranding.liquidOf("Death");
        Location above = target.getEyeLocation().clone().add(0, GAZE_HEIGHT, 0);

        context.effects().playBeamEffect(above, target.getEyeLocation(), Particle.DUST, 0.25, 30);
        context.effects().playAlertHalo(target.getEyeLocation().clone().add(0, 0.8, 0), deathColor);
        context.effects().playSound(target.getLocation(),
                dead ? Sound.BLOCK_SCULK_SHRIEKER_SHRIEK : Sound.BLOCK_SCULK_SENSOR_CLICKING,
                0.8f, 0.6f);
    }

    /** Читанка слабкого місця: чим ціль є для смерті й скільком ударам вона ще належить. */
    private void reportWeakness(IAbilityContext context, UUID casterId, LivingEntity target,
                                boolean dead, double multiplier) {
        AttributeInstance maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        int healthPercent = maxHealth == null || maxHealth.getValue() <= 0
                ? 100
                : (int) Math.round(100.0 * target.getHealth() / maxHealth.getValue());

        String essence;
        if (UndeadEntities.isUndead(target.getType())) {
            essence = "нежить";
        } else if (Spirits.isSpirit(target)) {
            essence = "дух";
        } else if (dead) {
            essence = "мертва душа (втратив контроль)";
        } else {
            essence = "живе тіло";
        }

        Player caster = context.getCasterPlayer();
        if (caster != null) {
            ActionBars.send(caster, Component.text(
                    ChatColor.DARK_GREEN + "✦ Вузол: " + ChatColor.WHITE + target.getName()
                            + ChatColor.GRAY + " · " + ChatColor.AQUA + essence
                            + ChatColor.GRAY + " · " + ChatColor.RED + healthPercent + "%"
                            + ChatColor.GRAY + " · " + ChatColor.GOLD
                            + String.format("×%.2f", multiplier)));
        }
        context.messaging().sendMessage(casterId, ChatColor.GRAY
                + (dead ? "✦ Смерть уже тримає цю ціль — вузол відкритий навстіж"
                        : "✦ Живе тіло приховує вузол — удар буде слабшим"));
    }
}

// Свідомо БЕЗ cleanUp(): він кличеться на КОЖНИЙ вихід будь-якого гравця на спільному
// екземплярі здібності, тож стер би вузли всім іншим. Підписка вузла має власний TTL
// (NODE_TTL_SECONDS), тож нічого не протікає й без нього.
