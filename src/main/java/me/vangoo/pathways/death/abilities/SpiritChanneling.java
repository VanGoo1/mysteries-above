package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.RetinueServant;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritGuideLore;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import me.vangoo.pathways.common.SoulWard;
import me.vangoo.pathways.common.UndeadEntities;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Sequence 7: Прикликання духів.
 *
 * <p>Вікі: «Their core ability is Spirit Channeling… They can use different Spirits to actualize
 * different kinds of effects» — і водночас «a Spirit Channeling done by a Spirit Medium can involve
 * only 1 Spirit». Звідси форма: ОДНА здібність + меню вибору духа
 * ({@link SpiritChannelingMenu}), а не окрема здібність на кожного духа.
 *
 * <p>Каст лише відкриває меню й повертає {@code deferred()}: духовність, засвоєння й кулдаун
 * списуються тільки в мить, коли дух справді покликаний ({@link #channelIllusoryEye}).
 * Ефекти живуть у {@link SpiritChannelingRunner}, тут — лише вхід і облік ресурсів.
 *
 * <p>Вікі окремо зазначає, що медіум, на відміну від Танцівниці, не може «Summoning Dance»
 * притягти далеких прихованих духів — тому все прикликання прив'язане до місця кастера.
 */
public class SpiritChanneling extends ActiveAbility {

    /** Послідовність, з якої меню відкриває розділ почту. */
    static final int GUIDE_SEQUENCE = 6;
    /** Сила зльоту й запас повільного падіння: висота відчуття, не балансна величина. */
    private static final double SOAR_FORWARD = 0.9;
    private static final double SOAR_UPWARD = 1.3;
    private static final int SOAR_SLOW_FALL_TICKS = 160;

    private final SpiritChannelingMenu menu = new SpiritChannelingMenu(this);

    /** Домени Морозної тіні — по одному на медіума. Інстанс-поле, ніколи не static. */
    private final Map<UUID, FrostShadowSession> frostSessions = new ConcurrentHashMap<>();

    /** Почет Посл. 6: спільний реєстр із Воскресінням, тому приходить конструктором. */
    private final UndeadRetinue retinue;

    public SpiritChanneling(UndeadRetinue retinue) {
        this.retinue = retinue;
    }

    @Override
    public String getName() {
        return "Допомога середніх духів";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return spiritsDescription(userSequence)
                + (userSequence.level() <= GUIDE_SEQUENCE ? retinueDescription(userSequence) : "");
    }

    /** Розділ почту з'являється в описі рівно тоді, коли з'являється в меню. */
    private String retinueDescription(Sequence userSequence) {
        return String.format(
                "\n\n§aВідкрито нових слуг:\n" +
                        "§f☠ Підкорення — уся безхазяйна нежить у %d бл вступає вам на службу\n" +
                        "§f  ✦ %d духовності, разом до %d слуг\n" +
                        "§f✦ Обмін духом — слуга гине, а вашу душу %d с не дістати\n" +
                        "§f  ✦ %d духовності\n" +
                        "§f🕊 Зліт — невидима сила підкидає вас угору, %d духовності\n",
                (int) SpiritGuideLore.subjugationRadius(userSequence),
                SpiritGuideLore.SUBJUGATION_COST,
                GatekeeperLore.retinueCap(userSequence),
                SpiritGuideLore.spiritSwapWardSeconds(userSequence),
                SpiritGuideLore.SPIRIT_SWAP_COST,
                SpiritGuideLore.SOAR_COST);
    }

    private String spiritsDescription(Sequence userSequence) {
        return String.format(
                "Ви кличете духа й просите його про послугу — за раз лише одного.\n\n" +
                        "§7Доступні духи:\n" +
                        "§f👁 Ілюзорне око — дух-споглядач висить над вами й обводить усе живе\n" +
                        "§f  ✦ радіус %d блоків, тримається %d с, бачите тільки ви\n" +
                        "§f❄ Морозна тінь — домен холоду ходить за вами\n" +
                        "§f  ✦ радіус %d блоків, %d с: Повільність, обмороження й %.1f урону/с\n" +
                        "§f  ✦ вам — крижаний обладунок і морозна коса (×%.1f до удару)\n" +
                        "§f🖐 Земляний дух — земля м'якне й хапає ціль, на яку ви дивитесь\n" +
                        "§f  ✦ до %d блоків, тримає %d с; коштує %d HP вашої крові\n" +
                        "§8(далеких прихованих духів медіум не притягує — лише тих, що поруч)",
                (int) SpiritMediumLore.illusoryEyeRadius(userSequence),
                SpiritMediumLore.illusoryEyeDurationSeconds(userSequence),
                (int) SpiritMediumLore.frostRadius(userSequence),
                SpiritMediumLore.frostDurationSeconds(userSequence),
                SpiritMediumLore.frostTickDamage(userSequence),
                SpiritMediumLore.scytheDamageMultiplier(userSequence),
                (int) SpiritMediumLore.earthRange(userSequence),
                SpiritMediumLore.earthHoldSeconds(userSequence),
                SpiritMediumLore.EARTH_SPIRIT_BLOOD_HP);
    }

    @Override
    public int getSpiritualityCost() {
        return SpiritMediumLore.ILLUSORY_EYE_COST;
    }

    /**
     * ponytail: кулдаун один на всіх духів (він лежить за {@code AbilityIdentity}), тож беремо
     * найсуворіший — Морозної тіні, 90 с проти 45 с в ока. Роздільний вимагав би
     * {@code setCooldown(ability, caster, seconds)} у {@code ICooldownContext} — той самий
     * компроміс, що вже зроблено у {@link VoiceOfTheDead}.
     */
    @Override
    public int getCooldown(Sequence userSequence) {
        return SpiritMediumLore.frostCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) {
            return AbilityResult.failure("Ви не в світі");
        }
        menu.open(context, caster);
        return AbilityResult.deferred();
    }

    /** Вибір у меню: дух приходить, і аж тепер списуються ресурси (deferred-потік). */
    void channelIllusoryEye(IAbilityContext context, Player caster) {
        if (!pay(context, caster, SpiritMediumLore.ILLUSORY_EYE_COST)) {
            return;
        }
        SpiritChannelingRunner.illusoryEye(context, caster,
                context.getCasterBeyonder().getSequence());
    }

    /** Морозна тінь: домен живе в часі, тож дух отримує сесію, а повторний виклик замінює її. */
    void channelFrostShadow(IAbilityContext context, Player caster) {
        if (!pay(context, caster, SpiritMediumLore.FROST_SHADOW_COST)) {
            return;
        }
        UUID casterId = caster.getUniqueId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        Color deathColor = PathwayBranding.liquidOf("Death");

        FrostShadowSession previous = frostSessions.remove(casterId);
        if (previous != null) {
            previous.cancel();
        }

        FrostShadowSession session = new FrostShadowSession(
                casterId,
                SpiritMediumLore.frostRadius(sequence),
                SpiritMediumLore.frostTickDamage(sequence),
                SpiritMediumLore.scytheDamageMultiplier(sequence),
                SpiritMediumLore.frostDurationSeconds(sequence),
                deathColor,
                context.events(),
                context.effects(),
                frostSessions);
        frostSessions.put(casterId, session);
        session.armScythe();
        BukkitTask task = context.scheduling().scheduleRepeating(session::tick,
                0L, FrostShadowSession.TICK_PERIOD_TICKS);
        session.bindTask(task);

        context.effects().playWaveEffect(caster.getLocation(),
                SpiritMediumLore.frostRadius(sequence), Particle.SNOWFLAKE, 20);
        context.effects().playRisingSpiral(caster.getLocation(), 3.0, 1.2, deathColor, 30);
        context.effects().playSound(caster.getLocation(), Sound.BLOCK_SOUL_SOIL_BREAK, 1.0f, 0.5f);
        context.messaging().sendMessage(casterId,
                "§b❄ Морозна тінь стала поруч: домен холоду, обладунок і коса");
    }

    /**
     * Земляний дух: єдиний, кому потрібна кров — «This Spirit needs their blood to be Channeled».
     * Ціль шукаємо ДО оплати (гроші за впійману порожнечу не беремо), кров'ю платимо після
     * духовності й ніколи насмерть — це ціна, а не самогубство.
     */
    void channelEarthSpirit(IAbilityContext context, Player caster) {
        Sequence sequence = context.getCasterBeyonder().getSequence();
        LivingEntity target = context.targeting()
                .getTargetedEntity(SpiritMediumLore.earthRange(sequence))
                .orElse(null);
        if (target == null) {
            context.messaging().sendMessage(caster.getUniqueId(),
                    "§8Земляний дух не бачить, кого хапати — дивіться на ціль.");
            return;
        }
        if (!pay(context, caster, SpiritMediumLore.EARTH_SPIRIT_COST)) {
            return;
        }
        caster.setHealth(Math.max(1.0,
                caster.getHealth() - SpiritMediumLore.EARTH_SPIRIT_BLOOD_HP));
        context.effects().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 0.7f);
        SpiritChannelingRunner.earthSpirit(context, caster, target, sequence);
    }

    /* ===================== ПОСЛ. 6: ПОЧЕТ НЕЖИТІ ===================== */

    /**
     * Підкорення нежиті (S1c/D2): уся безхазяйна нежить довкола вступає в почет. Кандидатів
     * шукаємо ДО оплати — за порожню площу грошей не беремо.
     */
    void subjugateUndead(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        double radius = SpiritGuideLore.subjugationRadius(sequence);

        List<Mob> candidates = new ArrayList<>();
        int room = GatekeeperLore.retinueCap(sequence) - retinue.size(casterId);
        for (LivingEntity nearby : context.targeting().getNearbyEntities(radius)) {
            if (candidates.size() >= room) break;
            if (!(nearby instanceof Mob undead)) continue;
            // getCategory() на 1.21+ кидає UnsupportedOperationException — нежить лише тегом.
            if (!UndeadEntities.isUndead(undead.getType())) continue;
            if (retinue.isLed(undead.getUniqueId())) continue;  // чужого слугу не забираємо
            candidates.add(undead);
        }

        if (candidates.isEmpty()) {
            context.messaging().sendMessage(casterId, room <= 0
                    ? "§8Почет уже повний."
                    : "§8Довкола немає безхазяйної нежиті.");
            return;
        }
        if (!pay(context, caster, SpiritGuideLore.SUBJUGATION_COST)) {
            return;
        }

        int size = 0;
        for (Mob undead : candidates) {
            size = retinue.enroll(context, casterId, undead, RetinueServant.Kind.SUBJUGATED, sequence);
        }

        Color deathColor = PathwayBranding.liquidOf("Death");
        context.effects().playExplosionRingEffect(caster.getLocation(), radius, Particle.DUST,
                new Particle.DustOptions(deathColor, 1.4f));
        context.effects().playWaveEffect(caster.getLocation(), radius, Particle.SOUL, 20);
        context.effects().playSound(caster.getLocation(), Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.5f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "☠ Нежить схилилась: "
                + ChatColor.WHITE + candidates.size() + ChatColor.GRAY
                + " (" + size + "/" + GatekeeperLore.retinueCap(sequence) + ")");
    }

    /**
     * Обмін духом (S1f): провідник міняється місцями зі слугою — «effectively immune to attacks
     * that target the Soul Body». Слуга розсипається, а щит тримається доти, доки стоїть тег
     * {@link SoulWard}; його читають і чужі шляхи (Нитки Маріонетиста), і Мова мертвих.
     */
    void spiritSwap(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        if (retinue.size(casterId) < SpiritGuideLore.SPIRIT_SWAP_SERVANT_COST) {
            context.messaging().sendMessage(casterId, "§8Мінятись нема з ким — почту немає.");
            return;
        }
        // Достатність перевіряємо ДО жертви: інакше слуга гине, а щита нема.
        if (!context.getCasterBeyonder().getSpirituality()
                .hasSufficient(SpiritGuideLore.SPIRIT_SWAP_COST)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "✗ Потрібно "
                    + SpiritGuideLore.SPIRIT_SWAP_COST + " духовності");
            return;
        }
        if (!retinue.sacrificeOne(casterId) || !pay(context, caster, SpiritGuideLore.SPIRIT_SWAP_COST)) {
            return;
        }

        int seconds = SpiritGuideLore.spiritSwapWardSeconds(
                context.getCasterBeyonder().getSequence());
        SoulWard.protect(caster);
        // Знімає щит таск: тег без строку пережив би сесію й став би безсмертям.
        context.scheduling().scheduleDelayed(() -> SoulWard.drop(caster), seconds * 20L);

        Color deathColor = PathwayBranding.liquidOf("Death");
        context.effects().playWardingShell(casterId, deathColor, 1.6, seconds * 20);
        context.effects().playSoulWisp(casterId, caster.getLocation(), deathColor);
        context.effects().playSound(caster.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.5f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_AQUA
                + "✦ Ви помінялись духом зі слугою: " + seconds + " с душа недосяжна");
    }

    /**
     * Зліт (S1k-2): «They can make the Spirit Guide swiftly soar, as if dragged by an unseen
     * force». Невидима сила підкидає — і вона ж не дає розбитись (повільне падіння), тож окремого
     * слухача падіння тут не треба.
     */
    void soar(IAbilityContext context, Player caster) {
        if (!pay(context, caster, SpiritGuideLore.SOAR_COST)) {
            return;
        }
        Location from = caster.getLocation().clone();
        caster.setVelocity(from.getDirection().setY(0).normalize()
                .multiply(SOAR_FORWARD).setY(SOAR_UPWARD));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                SOAR_SLOW_FALL_TICKS, 0, false, false, true));

        Color deathColor = PathwayBranding.liquidOf("Death");
        context.effects().playHelixEffect(from, from.clone().add(0, 4, 0), Particle.SCULK_SOUL, 20);
        context.effects().playGroundTrail(from, from.clone().add(from.getDirection()
                .setY(0).normalize().multiply(4)), deathColor, 40);
        context.effects().playSound(from, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.6f);
        context.messaging().sendMessage(caster.getUniqueId(), ChatColor.DARK_AQUA
                + "✦ Невидима сила підкинула вас угору");
    }

    int retinueSize(UUID ownerId) {
        return retinue.size(ownerId);
    }

    /**
     * Списує ресурси за конкретного духа. Базову ціну (і кулдаун) знімає
     * {@code AbilityResourceConsumer}, різницю дорожчого духа — доплата тут же; загальну
     * достатність перевіряємо ДО списання, щоб не взяти базу й не дати духа.
     */
    private boolean pay(IAbilityContext context, Player caster, int totalCost) {
        Beyonder medium = context.getCasterBeyonder();
        if (!medium.getSpirituality().hasSufficient(totalCost)
                || !AbilityResourceConsumer.consumeResources(this, medium, context)) {
            context.messaging().sendMessage(caster.getUniqueId(),
                    "§cНедостатньо духовності, щоб покликати духа.");
            return false;
        }
        int surcharge = totalCost - getSpiritualityCost();
        if (surcharge > 0) {
            medium.setSpirituality(medium.getSpirituality().decrement(surcharge));
        }
        context.events().publishAbilityUsedEvent(this, medium);
        context.beyonder().updateBeyonder(caster.getUniqueId());
        return true;
    }
}
