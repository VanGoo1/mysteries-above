package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SequenceScaler.ScalingStrategy;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DragonScale extends ActiveAbility {

    // Базові значення (умовні для Seq 9), щоб на Seq 6 вони стали збалансованими
    private static final int BASE_DURATION_SECONDS = 21;
    // На Seq 6 це буде: 20 * 1.45 ≈ 29-30 секунд.
    // На Seq 4 (Напівбог): 20 * 1.75 = 35 секунд.

    private static final int BASE_COST = 150;


    private static final int BASE_COOLDOWN = 75;


    @Override
    public String getName() {
        return "Луска дракона";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int duration = calculateDuration(userSequence.level());
        return "Психологічна маніфестація луски дракона. Дає " +
                "Опір II та Вогнестійкість II на " + duration + " секунд.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        // Кулдаун трохи зменшується з рівнем (Divine strategy для зменшення часу)
        // Але не менше 60 секунд
        double multiplier = SequenceScaler.calculateMultiplier(userSequence.level(), ScalingStrategy.WEAK);
        return Math.max(60, (int) (BASE_COOLDOWN / multiplier));
    }

    private int calculateDuration(int sequence) {
        // Використовуємо MODERATE (15% за рівень сили)
        // Seq 6 (Power 3): 1.0 + 0.45 = 1.45x -> ~29 сек
        // Seq 0 (Power 9): 1.0 + 1.35 = 2.35x -> ~47 сек
        double multiplier = SequenceScaler.calculateMultiplier(sequence, ScalingStrategy.MODERATE);
        return (int) (BASE_DURATION_SECONDS * multiplier);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCaster();
        Beyonder beyonder = context.getCasterBeyonder();
        int sequenceVal = beyonder.getSequence().level();

        // Додаткова перевірка: Здібність доступна тільки з 6-ї послідовності
        // (Хоча зазвичай це перевіряється на етапі видачі здібності, тут для надійності)
        if (sequenceVal > 6) {
            return AbilityResult.failure("Ваш рівень послідовності занизький для цієї форми.");
        }

        Spirituality sp = beyonder.getSpirituality();
        if (sp.current() < BASE_COST) {
            return AbilityResult.failure("Недостатньо духовності для матеріалізації луски.");
        }

        int durationSeconds = calculateDuration(sequenceVal);
        int durationTicks = durationSeconds * 20;

        // Resistance II (-40% вхідної шкоди)
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 1));
        // Fire Resistance (Імунітет до лави/вогню)
        caster.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, durationTicks, 0));

        // Візуал: Більш "важкий" звук
        context.playSoundToCaster(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f);
        context.playSoundToCaster(Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.5f);

        caster.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Психологічна луска вкриває ваше тіло!");

        // Візуал: Ефект дихання дракона (Dragon Breath particles)
        caster.getWorld().spawnParticle(Particle.DRAGON_BREATH, caster.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.02);

        beyonder.setSpirituality(new Spirituality(sp.current() - BASE_COST, sp.maximum()));

        return AbilityResult.success();
    }
}