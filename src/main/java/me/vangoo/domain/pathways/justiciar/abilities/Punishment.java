package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punishment — тільки зміна режиму покарання.
 *
 * - ПКМ: переключити режим (показ у actionbar)
 * - Реальний застосунок покарання відбувається автоматично через registerViolation(...)
 */
public class Punishment extends ActiveAbility {

    public enum PunishmentMode {
        DISABLE_ABILITIES("Заблокувати здібності (10 хв)"),
        TELEPORT_STRIKE("Телепорт-удар (9 ❤ чистого урону)"),
        IMMOBILIZE("Обездвижити (30 с)");

        private final String desc;
        PunishmentMode(String desc) { this.desc = desc; }
        public String getDesc() { return desc; }
    }

    // Обраний режим для кожного кастера (зберігається в пам'яті)
    private static final Map<UUID, PunishmentMode> chosenMode = new ConcurrentHashMap<>();

    // Кулдаун покарання: зберігаємо час останнього застосування для кожної пари (кастер -> порушник)
    private static final Map<String, Long> punishmentCooldowns = new ConcurrentHashMap<>();
    private static final long PUNISHMENT_COOLDOWN_MS = 60_000; // 1 хвилина

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

    /**
     * Виконується при використанні здібності Punishment.
     * Просто перемикає режим на ПКМ.
     */
    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        UUID casterId = caster.getUniqueId();

        // ПКМ — перемикання режиму (не витрачає ресурси, не накладає кулдаун)
        PunishmentMode cur = chosenMode.getOrDefault(casterId, PunishmentMode.TELEPORT_STRIKE);
        PunishmentMode next = nextMode(cur);
        chosenMode.put(casterId, next);

        // Показати в actionbar замість чату
        String message = ChatColor.GOLD + "⚖ Режим покарання: " + ChatColor.YELLOW + next.getDesc();
        sendActionBar(caster, message);

        // Легкий звуковий/візуальний фідбек
        context.spawnParticle(Particle.SMOKE, caster.getLocation().add(0,1,0), 12, 0.3,0.3,0.3);
        context.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);

        // Повертаємо success щоб код виконався, але з нульовими витратами (встановлені в методах вище)
        return AbilityResult.success();
    }

    private static PunishmentMode nextMode(PunishmentMode cur) {
        PunishmentMode[] vals = PunishmentMode.values();
        return vals[(cur.ordinal() + 1) % vals.length];
    }

    /**
     * Викликається з PowerProhibition коли зафіксовано порушення.
     * Тепер: одразу застосовує вибране покарання (якщо кастер має вибране),
     * і повідомляє в actionbar та робить візуальні/звукові ефекти.
     *
     * ВАЖЛИВО: Покарання застосовується тільки раз на хвилину для кожної пари (кастер -> порушник)
     *
     * @param context контекст здібності (для доступу до lockAbilities, ефектів, звуків)
     * @param casterId UUID кастера (той, хто встановив заборону)
     * @param violatorId UUID гравця що порушив
     * @param action опис порушення (текст)
     */
    public static void registerViolation(IAbilityContext context, UUID casterId, UUID violatorId, String action) {
        // Перевіряємо кулдаун покарання
        String cooldownKey = casterId.toString() + ":" + violatorId.toString();
        long currentTime = System.currentTimeMillis();
        Long lastPunishmentTime = punishmentCooldowns.get(cooldownKey);

        if (lastPunishmentTime != null && (currentTime - lastPunishmentTime) < PUNISHMENT_COOLDOWN_MS) {
            // Покарання на кулдауні - тільки повідомляємо про порушення
            Player caster = context.getCaster().getServer().getPlayer(casterId);
            Player violator = context.getCaster().getServer().getPlayer(violatorId);

            if (violator != null && violator.isOnline()) {
                sendActionBar(violator, ChatColor.YELLOW + "⚖ Порушення зафіксовано: " + action);
            }

            if (caster != null && caster.isOnline()) {
                long timeLeft = (PUNISHMENT_COOLDOWN_MS - (currentTime - lastPunishmentTime)) / 1000;
                sendActionBar(caster, ChatColor.GRAY + "⚖ Покарання на кулдауні ще " + timeLeft + "с");
            }
            return;
        }

        // Отримуємо кастера та ціль
        Player caster = context.getCaster().getServer().getPlayer(casterId);
        Player violator = context.getCaster().getServer().getPlayer(violatorId);

        // Якщо кастер відсутній чи не онлайн — немає кого інформувати, але покарання все одно може застосуватись:
        PunishmentMode mode = chosenMode.getOrDefault(casterId, PunishmentMode.TELEPORT_STRIKE);

        // Якщо violator offline — нічого не робимо
        if (violator == null || !violator.isOnline()) {
            if (caster != null && caster.isOnline()) {
                sendActionBar(caster, ChatColor.YELLOW + "Порушення зафіксовано, але порушник офлайн.");
            }
            return;
        }

        // Нотифікація в actionbar (що сталося)
        String violMsg = ChatColor.RED + "⚖ Ви порушили: " + ChatColor.YELLOW + action;
        violMsg += ChatColor.GRAY + " | Покарання: " + ChatColor.YELLOW + mode.getDesc();
        sendActionBar(violator, violMsg);

        if (caster != null && caster.isOnline()) {
            String casterMsg = ChatColor.GOLD + "⚖ " + ChatColor.YELLOW + violator.getName() +
                    ChatColor.GRAY + " порушив: " + ChatColor.YELLOW + action +
                    ChatColor.GRAY + " | Покарання: " + ChatColor.YELLOW + mode.getDesc();
            sendActionBar(caster, casterMsg);
        }

        // Застосовуємо покарання одразу
        applyPunishment(context, casterId, violatorId, mode);

        // Зберігаємо час застосування покарання
        punishmentCooldowns.put(cooldownKey, currentTime);

        // Автоматично видаляємо з мапи після закінчення кулдауну (щоб не росла безкінечно)
        context.scheduleDelayed(() -> punishmentCooldowns.remove(cooldownKey), (PUNISHMENT_COOLDOWN_MS / 50) + 20);
    }

    /**
     * Застосування покарання. Використовує context.lockAbilities(...) для DISABLE_ABILITIES.
     */
    private static void applyPunishment(IAbilityContext context, UUID casterId, UUID violatorId, PunishmentMode mode) {
        Player caster = context.getCaster().getServer().getPlayer(casterId);
        Player violator = context.getCaster().getServer().getPlayer(violatorId);

        if (violator == null || !violator.isOnline()) return;

        Beyonder casterB = caster == null ? null : context.getBeyonderFromEntity(casterId);
        Beyonder violB = context.getBeyonderFromEntity(violatorId);
        int casterSeq = casterB == null ? 9 : casterB.getSequenceLevel();
        int violSeq = violB == null ? 9 : violB.getSequenceLevel();

        double powerModifier = Math.max(0.5, 1.0 + (5 - Math.max(casterSeq, violSeq)) * 0.1);

        switch (mode) {
            case DISABLE_ABILITIES -> {
                int durationSeconds = 10 * 60; // 10 хв
                // Виклик методу контексту, який ти сказав що є:
                context.lockAbilities(violatorId, durationSeconds);

                // Візуальні/звукові ефекти
                context.spawnParticle(Particle.WITCH, violator.getLocation().add(0,1,0), 30, 0.3,0.3,0.3);
                context.playSound(violator.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);

                // actionbar (щоб не спамити чат)
                sendActionBar(violator, ChatColor.RED + "⚖ Ваші здібності заблоковані на 10 хв!");
                if (caster != null && caster.isOnline()) {
                    sendActionBar(caster, ChatColor.GOLD + "⚖ Здібності " + ChatColor.YELLOW + violator.getName() + ChatColor.GRAY + " заблоковано (10 хв)");
                }
            }

            case TELEPORT_STRIKE -> {
                Location targetLoc = violator.getLocation();
                // Телепортуємо кастера (якщо він онлайн)
                if (caster != null && caster.isOnline()) {
                    caster.teleport(targetLoc.clone().add(0,1,0));
                    context.spawnParticle(Particle.EXPLOSION, targetLoc.clone().add(0,1,0), 1, 0,0,0);
                    context.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 0.8f);
                } else {
                    // Якщо кастер офлайн — просто показати ефект біля жертви
                    context.spawnParticle(Particle.EXPLOSION, targetLoc.clone().add(0,1,0), 1, 0,0,0);
                    context.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 0.8f);
                }

                double baseDamage = 18.0 * powerModifier;
                // Наносимо шкоду (враховуємо причину - caster може бути null)
                if (caster != null && caster.isOnline()) {
                    violator.damage(baseDamage, caster);
                } else {
                    violator.damage(baseDamage);
                }

                sendActionBar(violator, ChatColor.DARK_RED + "Вас вдарило покарання!");
                if (caster != null && caster.isOnline()) sendActionBar(caster, ChatColor.GOLD + "Ви виконали Телепорт-удар (" + (int)baseDamage + " dmg)");
            }

            case IMMOBILIZE -> {
                int duration = 30;
                int amp = Math.max(1, (int)Math.round(powerModifier));
                violator.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration * 20, amp, true, true));
                violator.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration * 20, 128, true, false));

                context.spawnParticle(Particle.SOUL, violator.getLocation().add(0,1,0), 40, 0.4,0.4,0.4);
                context.playSound(violator.getLocation(), Sound.ENTITY_SHULKER_CLOSE, 1f, 1f);

                sendActionBar(violator, ChatColor.RED + "Ви тимчасово обездвижені!");
                if (caster != null && caster.isOnline()) sendActionBar(caster, ChatColor.GOLD + "Покарання: Обездвиження (" + duration + "с)");
            }
        }
    }

    /* -------------------------
       Невеликий хелпер для actionbar
       ------------------------- */
    private static void sendActionBar(Player p, String message) {
        if (p == null || !p.isOnline()) return;
        try {
            // Spigot / Bungee actionbar
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (NoClassDefFoundError | NoSuchMethodError ex) {
            // fallback: звичайний чат (на випадок старої платформи)
            p.sendMessage(message);
        }
    }
}