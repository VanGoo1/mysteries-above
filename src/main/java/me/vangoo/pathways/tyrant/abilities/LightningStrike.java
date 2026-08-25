package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sequence 5: Удар Блискавки. Єдина блискавка Океанського Співця, що не потребує
 * посередника. Два виходи (Shift+каст перемикає):
 * <p>
 * <b>Удар</b> — сріблясто-білий розряд у ціль на лінії зору, а якщо перед очима нікого —
 * у найближчого ворога позаду (кастується самим поглядом). Паралізує, подвоює шкоду
 * нежиті («очищувальна» природа) і розповзається «електричними змійками» на сусідів.
 * <p>
 * <b>Насичення</b> — блискавка вливається у зброю в руці: наступні кілька ударів
 * додають розряд і параліч. Вікно — тривалість підписки на подію, лічильник ударів
 * живе в самій підписці, тож ані сесії, ані реєстру не треба.
 */
public class LightningStrike extends ActiveAbility {

    private static final int BASE_DAMAGE = 9;
    private static final int BASE_INFUSED_DAMAGE = 4;
    private static final double PURIFICATION_MULTIPLIER = 2.0;  // нежить і злі духи
    private static final double CHAIN_DAMAGE_RATIO = 0.5;
    private static final int MAX_CHAINS = 3;
    private static final double CHAIN_RADIUS = 5.0;
    private static final double MAX_RANGE = 25.0;
    private static final int PARALYSIS_TICKS = 60;
    private static final int INFUSION_TICKS = 600;  // 30 с
    private static final int INFUSION_HITS = 5;
    private static final int COOLDOWN = 30;
    private static final int SPIRITUALITY_COST = 200;

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    private enum Mode {
        STRIKE("Удар"), INFUSE("Насичення");

        final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    @Override
    public String getName() {
        return "Удар Блискавки";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int infused = scaleValue(BASE_INFUSED_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fСріблясто-біла блискавка — сила шторму.\n" +
                        "§bУдар§f: §c%d §fшкоди по цілі на лінії зору §7(або по найближчому ворогові позаду)§f, " +
                        "параліч, §c×2 §fпо нежиті й «електричні змійки» на §b%d §fсусідів.\n" +
                        "§bНасичення§f: зброя в руці на §b%d с §fдодає §c%d §fшкоди й параліч " +
                        "наступним §b%d §fударам.\n§7Shift+каст — перемкнути режим.",
                damage, MAX_CHAINS, INFUSION_TICKS / 20, infused, INFUSION_HITS);
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }
        UUID casterId = context.getCasterId();

        // Перемикання режиму — без ціни й кулдауну.
        if (context.playerData().isSneaking(casterId)) {
            Mode next = modes.getOrDefault(casterId, Mode.STRIKE).next();
            modes.put(casterId, next);
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("⚡ Режим блискавки: " + next.displayName, NamedTextColor.AQUA));
            context.effects().playSoundForPlayer(casterId, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
            return AbilityResult.deferred();
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();
        return modes.getOrDefault(casterId, Mode.STRIKE) == Mode.INFUSE
                ? castInfusion(context, player, sequence)
                : castStrike(context, player, sequence);
    }

    private AbilityResult castStrike(IAbilityContext context, Player player, Sequence sequence) {
        Optional<LivingEntity> target = context.targeting().getTargetedEntity(MAX_RANGE)
                .or(() -> nearestOther(context, player));  // «удар з-за спини»: ціль поза полем зору
        if (target.isEmpty()) {
            return AbilityResult.failure("Поблизу немає цілі");
        }

        LivingEntity primary = target.get();
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        smite(context, player, primary, damage, color);

        // «Електричні змійки»: розряд перекидається на сусідів навіть якщо основну ціль убито.
        Location origin = primary.getLocation();
        primary.getWorld().getNearbyLivingEntities(origin, CHAIN_RADIUS).stream()
                .filter(e -> !e.getUniqueId().equals(primary.getUniqueId()))
                .filter(e -> !e.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)))
                .limit(MAX_CHAINS)
                .forEach(chained -> {
                    context.effects().playBeamEffect(origin.clone().add(0, 1, 0),
                            chained.getLocation().add(0, 1, 0), Particle.END_ROD, 0.15, 6);
                    smite(context, player, chained, (int) Math.round(damage * CHAIN_DAMAGE_RATIO), color);
                });

        context.effects().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.2f);
        return AbilityResult.success();
    }

    private AbilityResult castInfusion(IAbilityContext context, Player player, Sequence sequence) {
        UUID casterId = context.getCasterId();
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        int bonus = scaleValue(BASE_INFUSED_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        AtomicInteger charges = new AtomicInteger(INFUSION_HITS);

        context.events().subscribeToTemporaryEvent(
                casterId,
                EntityDamageByEntityEvent.class,
                event -> charges.get() > 0 && isMeleeHitBy(event, casterId),
                event -> {
                    if (charges.decrementAndGet() < 0) return;
                    LivingEntity victim = (LivingEntity) event.getEntity();
                    smite(context, player, victim, bonus, color);
                    context.messaging().sendMessageToActionBar(casterId,
                            Component.text("⚡ Заряди: " + charges.get(), NamedTextColor.AQUA));
                },
                INFUSION_TICKS
        );

        Location hand = player.getLocation().add(0, 1.2, 0);
        context.effects().playHelixEffect(player.getLocation(), player.getLocation().clone().add(0, 2.2, 0),
                Particle.ELECTRIC_SPARK, 30);
        context.effects().playFadingAura(hand, color, 30);
        context.effects().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 0.9f, 1.5f);
        context.messaging().sendMessage(casterId, "§bЗброя насичена блискавкою.");
        return AbilityResult.success();
    }

    /** Розряд по одній цілі: шкода (×2 нежиті), параліч і сам удар блискавки. */
    static void smite(IAbilityContext context, Player caster, LivingEntity victim, int damage, Color color) {
        // getCategory() на 1.21+ кидає UnsupportedOperationException — нежить визначаємо тегом.
        double finalDamage = Tag.ENTITY_TYPES_UNDEAD.isTagged(victim.getType())
                ? damage * PURIFICATION_MULTIPLIER
                : damage;
        victim.damage(finalDamage, caster);
        UUID victimId = victim.getUniqueId();
        context.entity().applyPotionEffect(victimId, PotionEffectType.SLOWNESS, PARALYSIS_TICKS, 2);
        context.entity().applyPotionEffect(victimId, PotionEffectType.WEAKNESS, PARALYSIS_TICKS, 0);

        context.effects().playLightningBolt(victim.getLocation(), color);
        context.effects().playExplosionRingEffect(victim.getLocation(), 1.4, Particle.DUST,
                new Particle.DustOptions(color, 1.3f));
    }

    /** Найближча жива сутність довкола, окрім самого кастера. */
    private Optional<LivingEntity> nearestOther(IAbilityContext context, Player caster) {
        return context.targeting().getNearbyEntities(MAX_RANGE).stream()
                .filter(e -> !e.getUniqueId().equals(caster.getUniqueId()))
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(caster.getLocation())));
    }

    private static boolean isMeleeHitBy(EntityDamageByEntityEvent event, UUID casterId) {
        return event.getEntity() instanceof LivingEntity
                && event.getDamager() instanceof Player damager
                && damager.getUniqueId().equals(casterId)
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
    }
}
