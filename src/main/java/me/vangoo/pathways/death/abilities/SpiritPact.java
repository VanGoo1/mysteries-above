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
import me.vangoo.pathways.common.SpiritWorldCreatures;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Death Sequence 6: Домовленість з духами.
 *
 * <p>Раніше істот Світу Духів (S1g–S1j) можна було просто купити з меню Прикликання духів.
 * Тепер вікі береться буквально: «obtain help from certain Spirit World creatures» — істота
 * має щось важити, тож вона сама блукає Пеклом ({@code creatures.yml}, {@code pathway:
 * spirit-world}), а провідник її ЗНАХОДИТЬ і домовляється (shift-ПКМ по цілі).
 *
 * <p>Просте ПКМ (без цілі чи без sneak) відкриває {@link SpiritPactMenu} — накази почту
 * (слідувати / атакувати / стерегти / розпустити), які раніше жили в Прикликанні духів, і
 * приховання/появу почту. Той самий {@link UndeadRetinue}, що й у Прикликанні духів і
 * Воскресінні — почет один на провідника, незалежно від того, звідки прийшов слуга.
 */
public class SpiritPact extends ActiveAbility {

    /** Ближня, «домовляльна» дистанція — не бойове закляття, а розмова впритул. */
    private static final double CLAIM_RANGE = 8.0;
    /** Дальність, на якій провідник призначає ціль наказу. Темп подачі, не сила — тому стала. */
    private static final double COMMAND_RANGE = 32.0;

    private final UndeadRetinue retinue;
    private final SpiritPactMenu menu = new SpiritPactMenu(this);

    public SpiritPact(UndeadRetinue retinue) {
        this.retinue = retinue;
    }

    @Override
    public String getName() {
        return "Домовленість з духами";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Істоти Світу Духів блукають Пеклом самі — знайдіть їх і домовтесь.\n\n" +
                        "§7Домовленість (shift-ПКМ по істоті):\n" +
                        "§8Дальність — %d блоків, разом до %d слуг загалом\n\n" +
                        "§7Просте ПКМ відкриває накази для слуг:\n" +
                        "§fслідувати / убити ціль / стерегти місце / розпустити\n" +
                        "§fта приховання й появу — духи розчиняються в тінях",
                (int) CLAIM_RANGE,
                GatekeeperLore.retinueCap(userSequence));
    }

    @Override
    public int getSpiritualityCost() {
        return SpiritGuideLore.LIVING_SHADOW_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return SpiritGuideLore.pactCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isValid()) {
            return AbilityResult.failure("Ви не в світі");
        }
        if (caster.isSneaking()) {
            claimSpirit(context, caster);
        } else {
            menu.open(context, caster);
        }
        return AbilityResult.deferred();
    }

    /** Shift-ПКМ по істоті Світу Духів: домовляємось і забираємо її в почет. */
    private void claimSpirit(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        LivingEntity targeted = context.targeting().getTargetedEntity(CLAIM_RANGE).orElse(null);
        if (!(targeted instanceof Mob mob) || !SpiritWorldCreatures.isSpiritWorldCreature(mob)) {
            context.messaging().sendMessage(casterId,
                    "§8Тут немає духа Світу Духів, з ким домовлятись.");
            return;
        }
        if (retinue.isLed(mob.getUniqueId())) {
            context.messaging().sendMessage(casterId, "§8Цей дух уже комусь служить.");
            return;
        }
        if (retinue.isFull(casterId, sequence)) {
            context.messaging().sendMessage(casterId, "§8Слуг вже максимальна кількість.");
            return;
        }

        SpiritWorldCreature kind = SpiritWorldCreature.fromEntity(mob).orElse(null);
        int cost = kind != null ? kind.cost : SpiritGuideLore.LIVING_SHADOW_COST;
        if (!pay(context, caster, cost)) {
            return;
        }

        int size = retinue.enroll(context, casterId, mob, RetinueServant.Kind.PACT, sequence);
        Color deathColor = PathwayBranding.liquidOf("Death");
        context.effects().playRisingSpiral(mob.getLocation(), 2.4, 0.9, deathColor, 30);
        context.effects().playExplosionRingEffect(mob.getLocation(), 1.6, Particle.DUST,
                new Particle.DustOptions(deathColor, 1.2f));
        context.effects().playSound(mob.getLocation(), Sound.BLOCK_SOUL_SOIL_BREAK, 1.0f, 0.5f);
        String title = kind != null ? kind.title : "Дух Світу Духів";
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "☠ "
                + ChatColor.WHITE + title + ChatColor.DARK_GREEN + " погодилась вам служити"
                + ChatColor.GRAY + " (" + size + "/" + GatekeeperLore.retinueCap(sequence) + ")");
    }

    /** Накази почту безкоштовні: це команда, а не прикликання. */
    void orderFollow(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        context.messaging().sendMessage(casterId, retinue.follow(casterId)
                ? ChatColor.DARK_GREEN + "☠ Слуги йдуть за вами"
                : ChatColor.GRAY + "☠ Слуг немає");
    }

    void orderAttack(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        LivingEntity target = context.targeting().getTargetedEntity(COMMAND_RANGE).orElse(null);
        if (target == null) {
            context.messaging().sendMessage(casterId, "§8Дивіться на ціль перед наказом.");
            return;
        }
        if (!retinue.attack(casterId, target.getUniqueId())) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "☠ Слуг немає");
            return;
        }
        context.effects().playSound(target.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.5f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "☠ Слуги кинулись на "
                + ChatColor.WHITE + target.getName());
    }

    void orderGuard(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        if (!retinue.guard(casterId, caster.getLocation())) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "☠ Почту немає");
            return;
        }
        context.effects().playCircleEffect(caster.getLocation(), 2.0, Particle.SOUL, 20);
        context.messaging().sendMessage(casterId, ChatColor.DARK_GREEN + "☠ Почет стереже це місце");
    }

    void disbandRetinue(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        if (!retinue.release(casterId)) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "☠ Слуг немає");
        }
    }

    /** Приховання/поява: духи розчиняються в тінях і повертаються назад. */
    void toggleHide(IAbilityContext context, Player caster) {
        UUID casterId = caster.getUniqueId();
        if (!retinue.toggleHidden(context, casterId)) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "☠ Слуг немає");
            return;
        }
        context.messaging().sendMessage(casterId, retinue.isHidden(casterId)
                ? ChatColor.DARK_GRAY + "☠ Слуги розчинились в тінях"
                : ChatColor.DARK_GREEN + "☠ Слуг знову видно");
    }

    boolean retinueHidden(UUID ownerId) {
        return retinue.isHidden(ownerId);
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
        Beyonder guide = context.getCasterBeyonder();
        if (!guide.getSpirituality().hasSufficient(totalCost)
                || !AbilityResourceConsumer.consumeResources(this, guide, context)) {
            context.messaging().sendMessage(caster.getUniqueId(),
                    "§cНедостатньо духовності, щоб домовитись із духом.");
            return false;
        }
        int surcharge = totalCost - getSpiritualityCost();
        if (surcharge > 0) {
            guide.setSpirituality(guide.getSpirituality().decrement(surcharge));
        }
        context.events().publishAbilityUsedEvent(this, guide);
        context.beyonder().updateBeyonder(caster.getUniqueId());
        return true;
    }

    /**
     * Істоти Світу Духів (S1g–S1j): internal id пака (він же скорбоард-тег конкретної істоти,
     * другий поряд із {@link SpiritWorldCreatures#TAG}) і ціна домовленості.
     */
    enum SpiritWorldCreature {
        LIVING_SHADOW("living_shadow", "Жива тінь", SpiritGuideLore.LIVING_SHADOW_COST),
        SHADOW_PYTHON("shadow_swallowing_python", "Пітон, що ковтає тіні",
                SpiritGuideLore.SHADOW_PYTHON_COST),
        DEATH_KNIGHT("death_knight", "Лицар Смерті", SpiritGuideLore.DEATH_KNIGHT_COST),
        LAKE_GODDESS("lake_goddess", "Богиня Озера", SpiritGuideLore.LAKE_GODDESS_COST),
        DEATH_ENVOY("death_envoy", "Посланець Смерті", GatekeeperLore.DEATH_ENVOY_RECRUIT_COST),
        PUS_OF_MAN("pus_of_man", "Гній Людини", GatekeeperLore.PUS_OF_MAN_RECRUIT_COST),
        PALE_GIRL("pale_girl", "Бліда Дівчинка", GatekeeperLore.PALE_GIRL_RECRUIT_COST),
        SEA_BEASTS("sea_beasts", "Морська Примара", GatekeeperLore.SEA_BEASTS_RECRUIT_COST);

        final String tag;
        final String title;
        final int cost;

        SpiritWorldCreature(String tag, String title, int cost) {
            this.tag = tag;
            this.title = title;
            this.cost = cost;
        }

        static Optional<SpiritWorldCreature> fromEntity(Mob entity) {
            for (SpiritWorldCreature kind : values()) {
                if (entity.getScoreboardTags().contains(kind.tag)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }
}
