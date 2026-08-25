package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.CorpseCollectorLore;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.pathways.common.RecordedEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.Tag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Death Sequence 9: Знання (Нежить).
 *
 * <p>Вікі: трупозбирач має «abundant wealth of information pertaining to the specific
 * characteristics and weaknesses of many Undead creatures… This also includes just regular
 * corpses which makes a Corpse Collector an expert on autopsy». Обидва боки того самого
 * знання — дві гілки одного каста, за тим, що в прицілі:
 *
 * <ul>
 *   <li><b>Мітка слабкого місця</b> — у прицілі жива нежить: ваш урон по НІЙ множиться
 *       на час мітки, плюс звіт про ціль.</li>
 *   <li><b>Розтин</b> — нежиті в прицілі немає: читає місце на предмет смертей
 *       (хто вбив кого й коли) з історії CoreProtect.</li>
 * </ul>
 *
 * <p>Єдина активна здібність тіру, тобто єдине джерело mastery на Посл. 9: {@code Beyonder}
 * нараховує його лише для {@code AbilityType.ACTIVE}.
 */
public class UndeadKnowledge extends ActiveAbility {

    /** Мітка живе рівно стільки, скільки її підписка на урон — окремий ключ, не {@code casterId}. */
    private record Mark(UUID targetId, UUID subKey) {}

    private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Знання Нежиті";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Ви знаєте нежить зсередини\n" +
                        "§7У прицілі нежить: §fмітка слабкого місця\n" +
                        "§c⚔ Ваш урон по ній ×%.2f протягом %d с §7(до %d бл)\n\n" +
                        "§7Нежиті в прицілі немає: §fсканування місця\n" +
                        "§b☠ Смерті в радіусі %d бл за останні %d хв (до %d записів)",
                CorpseCollectorLore.markDamageMultiplier(userSequence),
                CorpseCollectorLore.markDurationSeconds(userSequence),
                (int) CorpseCollectorLore.markRange(userSequence),
                (int) CorpseCollectorLore.autopsyRadius(userSequence),
                CorpseCollectorLore.autopsyWindowSeconds(userSequence) / 60,
                CorpseCollectorLore.autopsyMaxRecords(userSequence));
    }

    @Override
    public int getSpiritualityCost() {
        return CorpseCollectorLore.KNOWLEDGE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return CorpseCollectorLore.knowledgeCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null) return AbilityResult.failure("Немає тіла для огляду");

        Sequence sequence = context.getCasterBeyonder().getSequence();
        Optional<LivingEntity> aimed = context.targeting()
                .getTargetedEntity(CorpseCollectorLore.markRange(sequence));

        // getCategory() на 1.21+ кидає UnsupportedOperationException — нежить лише тегом.
        if (aimed.isPresent() && Tag.ENTITY_TYPES_UNDEAD.isTagged(aimed.get().getType())) {
            return markWeakness(context, caster, sequence, aimed.get());
        }
        return performAutopsy(context, caster, sequence);
    }

    /* ===================== МІТКА СЛАБКОГО МІСЦЯ ===================== */

    private AbilityResult markWeakness(IAbilityContext context, Player caster,
                                       Sequence sequence, LivingEntity target) {
        UUID casterId = caster.getUniqueId();
        UUID targetId = target.getUniqueId();
        int durationTicks = CorpseCollectorLore.markDurationSeconds(sequence) * 20;
        double multiplier = CorpseCollectorLore.markDamageMultiplier(sequence);
        Color deathColor = PathwayBranding.liquidOf("Death");

        // MAX_CONCURRENT_MARKS = 1: нова мітка ЗАМІНЮЄ попередню разом із її підпискою.
        Mark previous = marks.remove(casterId);
        if (previous != null) context.events().unsubscribeAll(previous.subKey());

        UUID subKey = UUID.randomUUID();
        marks.put(casterId, new Mark(targetId, subKey));

        // TTL підписки і є тривалістю мітки — окремого таймера чи прибирання не треба.
        context.events().subscribeToTemporaryEvent(
                subKey,
                EntityDamageByEntityEvent.class,
                event -> targetId.equals(event.getEntity().getUniqueId())
                        && isDealtBy(event, casterId),
                event -> {
                    event.setDamage(event.getDamage() * multiplier);
                    context.effects().spawnParticle(Particle.SOUL,
                            event.getEntity().getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2);
                },
                durationTicks
        );

        context.glowing().setGlowing(targetId, casterId,
                PathwayBranding.textOf("Death"), durationTicks);

        // Промінь від очей до цілі + кільце на влучанні (партикл кільця — лише DUST).
        context.effects().playTravelingBeam(
                caster.getEyeLocation(), target.getEyeLocation(), deathColor,
                () -> {
                    context.effects().playExplosionRingEffect(target.getLocation(), 1.2,
                            Particle.DUST, new Particle.DustOptions(deathColor, 1.0f));
                    context.effects().playSound(target.getLocation(),
                            Sound.ENTITY_SKELETON_CONVERTED_TO_STRAY, 0.7f, 0.6f);
                });
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.9f, 0.8f);

        reportTarget(context, casterId, target, multiplier);
        return AbilityResult.success();
    }

    /** Мітка має спрацьовувати і в ближньому бою, і зі стріли — інакше вона тихо марна на дистанції. */
    /**
     * Чи цей удар — від кастера: вруко́пашну або його снарядом. Пакетно-видима (не private),
     * бо тим самим правилом живе вузол {@link EyeOfDeath} — дубль цієї умови означав би, що
     * одна з двох здібностей тихо перестане зараховувати стріли.
     */
    static boolean isDealtBy(EntityDamageByEntityEvent event, UUID casterId) {
        if (event.getDamager() instanceof Player p) {
            return p.getUniqueId().equals(casterId);
        }
        return event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter
                && shooter.getUniqueId().equals(casterId);
    }

    private void reportTarget(IAbilityContext context, UUID casterId,
                              LivingEntity target, double multiplier) {
        String ailments = target.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .map(type -> type.getKey().getKey().replace('_', ' '))
                .collect(Collectors.joining(", "));
        if (ailments.isEmpty()) ailments = "нічого";

        context.messaging().sendMessage(casterId, String.format(
                "%s☠ %s%s%s: %.0f ❤, слабке місце знайдено (урон ×%.2f)",
                ChatColor.DARK_GREEN, ChatColor.WHITE, target.getName(),
                ChatColor.GRAY, target.getHealth(), multiplier));
        context.messaging().sendMessage(casterId,
                ChatColor.GRAY + "  Стан: " + ChatColor.LIGHT_PURPLE + ailments);
    }

    /* ===================== РОЗТИН ===================== */

    private AbilityResult performAutopsy(IAbilityContext context, Player caster, Sequence sequence) {
        Location scene = aimedScene(caster, CorpseCollectorLore.markRange(sequence));
        int radius = (int) CorpseCollectorLore.autopsyRadius(sequence);
        int window = CorpseCollectorLore.autopsyWindowSeconds(sequence);

        List<RecordedEvent> deaths = context.events().getPastEvents(scene, radius, window).stream()
                .filter(event -> event.getType() == RecordedEvent.EventType.DEATH)
                .limit(CorpseCollectorLore.autopsyMaxRecords(sequence))
                .toList();

        if (deaths.isEmpty()) {
            return AbilityResult.failure("Тут ніхто не вмирав — розтинати нічого");
        }

        UUID casterId = caster.getUniqueId();
        Color deathColor = PathwayBranding.liquidOf("Death");

        context.effects().playRisingSpiral(caster.getLocation(), 2.2, 0.6, deathColor, 30);
        context.effects().playSound(caster.getLocation(), Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.6f);

        context.messaging().sendMessageToActionBar(casterId, Component.text(
                ChatColor.DARK_GREEN + "☠ Розтин: " + ChatColor.WHITE + deaths.size()
                        + ChatColor.GRAY + " смертей поблизу"));

        long now = System.currentTimeMillis();
        for (RecordedEvent death : deaths) {
            long minutesAgo = Math.max(0, (now - death.getTimestamp()) / 60_000L);

            context.messaging().sendMessage(casterId, String.format(
                    "%s  • %s %s(%d хв тому)",
                    ChatColor.GRAY, death.getDescription(), ChatColor.DARK_GRAY, minutesAgo));

            // Позначка трупа двома шарами: кільце душ по землі + висхідний пил кольору шляху.
            Location grave = RecordedEvents.toLocation(death);
            if (grave == null) continue;
            grave.add(0, 0.2, 0);
            context.effects().playCircleEffect(grave, 0.8, Particle.SOUL, 60);
            context.effects().playGlowingDust(grave, deathColor);
        }
        context.effects().playSoundForPlayer(casterId, Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.5f);

        return AbilityResult.success();
    }

    /** Місце, яке оглядають: блок під прицілом, а якщо дивитись у небо — під собою. */
    private Location aimedScene(Player caster, double range) {
        Block aimed = caster.getTargetBlockExact((int) range);
        return aimed != null ? aimed.getLocation().add(0.5, 1, 0.5) : caster.getLocation();
    }
}
