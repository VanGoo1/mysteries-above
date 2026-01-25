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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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
            context.effects().spawnParticle(Particle.SOUL, casterLoc.clone().add(0, 1, 0), 12, 0.3, 0.3, 0.3);
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
                int duration = 30; // Секунди
                int durationTicks = duration * 20;

                // 1. Накладаємо "важкі" ефекти (MAX рівень)
                // Slowness 255: блокує ходьбу
                context.entity().applyPotionEffect(violatorId, PotionEffectType.SLOWNESS, durationTicks, 255);
                // Jump Boost 250: блокує стрибки
                context.entity().applyPotionEffect(violatorId, PotionEffectType.JUMP_BOOST, durationTicks, 250);
                // Mining Fatigue 255: блокує ламання блоків та уповільнює удари
                context.entity().applyPotionEffect(violatorId, PotionEffectType.MINING_FATIGUE, durationTicks, 255);

                if (violatorLoc != null) {
                    // 2. Механіка "Якоря"
                    final Location anchorLocation = violatorLoc.clone();
                    // Трохи піднімаємо, щоб гравець не застряг у підлозі
                    if (anchorLocation.getBlock().getType().isSolid()) {
                        anchorLocation.add(0, 0.1, 0);
                    }

                    // Змінні для керування таском
                    final int[] elapsed = {0};
                    final BukkitTask[] taskRef = new BukkitTask[1]; // Обгортка для зберігання посилання на таск

                    // Запускаємо через ваш контекст
                    taskRef[0] = context.scheduling().scheduleRepeating(() -> {

                        // А. Перевірка часу дії
                        if (elapsed[0] >= durationTicks) {
                            if (taskRef[0] != null) taskRef[0].cancel(); // Зупиняємо таск
                            return;
                        }

                        // Б. Перевірка чи гравець існує та онлайн
                        if (violatorId == null || !context.playerData().isOnline(violatorId)) {
                            if (taskRef[0] != null) taskRef[0].cancel(); // Зупиняємо, якщо вийшов
                            return;
                        }

                        // В. Логіка утримання (Якір)
                        Location current = context.playerData().getCurrentLocation(violatorId);

                        // Використовуємо 0.04 (0.2 блоку) для плавності. 0.01 занадто чутливе.
                        if (current.distanceSquared(anchorLocation) > 0.04) {
                            // Створюємо точку повернення
                            Location pullBack = anchorLocation.clone();
                            // Зберігаємо кут огляду гравця (дозволяємо крутити головою)
                            pullBack.setYaw(current.getYaw());
                            pullBack.setPitch(current.getPitch());

                            // Гасимо інерцію (щоб не ковзав)
                            context.entity().setVelocity(violatorId, new Vector(0, 0, 0));
                            // Повертаємо на місце
                            context.entity().teleport(violatorId, pullBack);
                        }

                        // Г. Збільшуємо лічильник (ми запускаємо раз на 2 тіка)
                        elapsed[0] += 2;

                    }, 0, 2); // Затримка 0, Період 2 тіка (0.1 сек)

                    // Візуалізація
                    context.effects().spawnParticle(Particle.SOUL, violatorLoc.clone().add(0, 1, 0), 40, 0.4, 0.4, 0.4);
                    context.effects().spawnParticle(Particle.LANDING_OBSIDIAN_TEAR, violatorLoc.clone().add(0, 0.5, 0), 10, 0.5, 0.1, 0.5);
                    context.effects().playSound(violatorLoc, Sound.BLOCK_ANVIL_PLACE, 1f, 0.5f);
                }

                Component violMsg = Component.text("Вас скуто кайданами! Рух неможливий.", NamedTextColor.RED);
                context.messaging().sendMessageToActionBar(violatorId, violMsg);

                if (context.playerData().isOnline(casterId)) {
                    Component casterMsg = Component.text("Покарання: Повна ізоляція (" + duration + "с)", NamedTextColor.GOLD);
                    context.messaging().sendMessageToActionBar(casterId, casterMsg);
                }
            }
        }
    }
}
