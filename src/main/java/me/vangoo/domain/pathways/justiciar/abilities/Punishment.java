package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Punishment extends ActiveAbility {

    public enum PunishmentMode {
        DISABLE_ABILITIES("Заблокувати здібності (10 хв)"),
        TELEPORT_STRIKE("Телепорт-удар (9 ❤ чистого урону)"),
        IMMOBILIZE("Обездвижити (30 с)");

        private final String desc;
        PunishmentMode(String desc) { this.desc = desc; }
        public String getDesc() { return desc; }
    }

    private static final Map<UUID, PunishmentMode> chosenMode = new ConcurrentHashMap<>();
    private static final Map<String, Long> punishmentCooldowns = new ConcurrentHashMap<>();
    private static final long PUNISHMENT_COOLDOWN_MS = 60_000;

    @Override
    public String getName() {
        return "Покарання";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "ПКМ — перемкнути режим покарання (вибране покарання застосовується автоматично при порушенні).";
    }

    @Override
    public int getSpiritualityCost() {
        return 0;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 0;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        PunishmentMode cur = chosenMode.getOrDefault(casterId, PunishmentMode.TELEPORT_STRIKE);
        PunishmentMode next = nextMode(cur);
        chosenMode.put(casterId, next);

        Component message = Component.text("⚖ Режим покарання: ", NamedTextColor.GOLD)
                .append(Component.text(next.getDesc(), NamedTextColor.YELLOW));
        context.messaging().sendMessageToActionBar(casterId, message);

        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        if (casterLoc != null) {
            context.effects().spawnParticle(Particle.SMOKE, casterLoc.clone().add(0, 1, 0), 12, 0.3, 0.3, 0.3);
        }
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);

        return AbilityResult.success();
    }

    private static PunishmentMode nextMode(PunishmentMode cur) {
        PunishmentMode[] vals = PunishmentMode.values();
        return vals[(cur.ordinal() + 1) % vals.length];
    }

    public static void registerViolation(IAbilityContext context, UUID casterId, UUID violatorId, String action) {
        String cooldownKey = casterId.toString() + ":" + violatorId.toString();
        long currentTime = System.currentTimeMillis();
        Long lastPunishmentTime = punishmentCooldowns.get(cooldownKey);

        if (lastPunishmentTime != null && (currentTime - lastPunishmentTime) < PUNISHMENT_COOLDOWN_MS) {
            if (context.playerData().isOnline(violatorId)) {
                Component msg = Component.text("⚖ Порушення зафіксовано: ", NamedTextColor.YELLOW)
                        .append(Component.text(action, NamedTextColor.YELLOW));
                context.messaging().sendMessageToActionBar(violatorId, msg);
            }

            if (context.playerData().isOnline(casterId)) {
                long timeLeft = (PUNISHMENT_COOLDOWN_MS - (currentTime - lastPunishmentTime)) / 1000;
                Component msg = Component.text("⚖ Покарання на кулдауні ще " + timeLeft + "с", NamedTextColor.GRAY);
                context.messaging().sendMessageToActionBar(casterId, msg);
            }
            return;
        }

        PunishmentMode mode = chosenMode.getOrDefault(casterId, PunishmentMode.TELEPORT_STRIKE);

        if (!context.playerData().isOnline(violatorId)) {
            if (context.playerData().isOnline(casterId)) {
                Component msg = Component.text("Порушення зафіксовано, але порушник офлайн.", NamedTextColor.YELLOW);
                context.messaging().sendMessageToActionBar(casterId, msg);
            }
            return;
        }

        String violatorName = context.playerData().getName(violatorId);

        Component violMsg = Component.text("⚖ Ви порушили: ", NamedTextColor.RED)
                .append(Component.text(action, NamedTextColor.YELLOW))
                .append(Component.text(" | Покарання: ", NamedTextColor.GRAY))
                .append(Component.text(mode.getDesc(), NamedTextColor.YELLOW));
        context.messaging().sendMessageToActionBar(violatorId, violMsg);

        if (context.playerData().isOnline(casterId)) {
            Component casterMsg = Component.text("⚖ ", NamedTextColor.GOLD)
                    .append(Component.text(violatorName, NamedTextColor.YELLOW))
                    .append(Component.text(" порушив: ", NamedTextColor.GRAY))
                    .append(Component.text(action, NamedTextColor.YELLOW))
                    .append(Component.text(" | Покарання: ", NamedTextColor.GRAY))
                    .append(Component.text(mode.getDesc(), NamedTextColor.YELLOW));
            context.messaging().sendMessageToActionBar(casterId, casterMsg);
        }

        applyPunishment(context, casterId, violatorId, mode);

        punishmentCooldowns.put(cooldownKey, currentTime);
        context.scheduling().scheduleDelayed(() -> punishmentCooldowns.remove(cooldownKey), (PUNISHMENT_COOLDOWN_MS / 50) + 20);
    }

    private static void applyPunishment(IAbilityContext context, UUID casterId, UUID violatorId, PunishmentMode mode) {
        if (!context.playerData().isOnline(violatorId)) return;

        Beyonder casterB = context.beyonder().getBeyonder(casterId);
        Beyonder violB = context.beyonder().getBeyonder(violatorId);
        int casterSeq = casterB == null ? 9 : casterB.getSequenceLevel();
        int violSeq = violB == null ? 9 : violB.getSequenceLevel();

        double powerModifier = Math.max(0.5, 1.0 + (5 - Math.max(casterSeq, violSeq)) * 0.1);

        Location violatorLoc = context.playerData().getCurrentLocation(violatorId);
        String violatorName = context.playerData().getName(violatorId);

        switch (mode) {
            case DISABLE_ABILITIES -> {
                int durationSeconds = 10 * 60;
                context.cooldown().lockAbilities(violatorId, durationSeconds);

                if (violatorLoc != null) {
                    context.effects().spawnParticle(Particle.WITCH, violatorLoc.clone().add(0, 1, 0), 30, 0.3, 0.3, 0.3);
                    context.effects().playSound(violatorLoc, Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
                }

                Component violMsg = Component.text("⚖ Ваші здібності заблоковані на 10 хв!", NamedTextColor.RED);
                context.messaging().sendMessageToActionBar(violatorId, violMsg);

                if (context.playerData().isOnline(casterId)) {
                    Component casterMsg = Component.text("⚖ Здібності ", NamedTextColor.GOLD)
                            .append(Component.text(violatorName, NamedTextColor.YELLOW))
                            .append(Component.text(" заблоковано (10 хв)", NamedTextColor.GRAY));
                    context.messaging().sendMessageToActionBar(casterId, casterMsg);
                }
            }

            case TELEPORT_STRIKE -> {
                if (violatorLoc != null) {
                    if (context.playerData().isOnline(casterId)) {
                        context.entity().teleport(casterId, violatorLoc.clone().add(0, 1, 0));
                    }

                    context.effects().spawnParticle(Particle.EXPLOSION, violatorLoc.clone().add(0, 1, 0), 1, 0, 0, 0);
                    context.effects().playSound(violatorLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 0.8f);
                }

                double baseDamage = 18.0 * powerModifier;
                context.entity().damage(violatorId, baseDamage);

                Component violMsg = Component.text("Вас вдарило покарання!", NamedTextColor.DARK_RED);
                context.messaging().sendMessageToActionBar(violatorId, violMsg);

                if (context.playerData().isOnline(casterId)) {
                    Component casterMsg = Component.text("Ви виконали Телепорт-удар (" + (int)baseDamage + " dmg)", NamedTextColor.GOLD);
                    context.messaging().sendMessageToActionBar(casterId, casterMsg);
                }
            }

            case IMMOBILIZE -> {
                int duration = 30;
                int amp = Math.max(1, (int)Math.round(powerModifier));
                context.entity().applyPotionEffect(violatorId, PotionEffectType.SLOWNESS, duration * 20, amp);
                context.entity().applyPotionEffect(violatorId, PotionEffectType.JUMP_BOOST, duration * 20, 128);

                if (violatorLoc != null) {
                    context.effects().spawnParticle(Particle.SOUL, violatorLoc.clone().add(0, 1, 0), 40, 0.4, 0.4, 0.4);
                    context.effects().playSound(violatorLoc, Sound.ENTITY_SHULKER_CLOSE, 1f, 1f);
                }

                Component violMsg = Component.text("Ви тимчасово обездвижені!", NamedTextColor.RED);
                context.messaging().sendMessageToActionBar(violatorId, violMsg);

                if (context.playerData().isOnline(casterId)) {
                    Component casterMsg = Component.text("Покарання: Обездвиження (" + duration + "с)", NamedTextColor.GOLD);
                    context.messaging().sendMessageToActionBar(casterId, casterMsg);
                }
            }
        }
    }
}
