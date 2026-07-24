package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Sequence 7: Резистентність до жаху. Постійна пасивка — щосекунди зрізає по 2 секунди
 * з тривалості дебафів страху/паніки (не знімає миттєво), прискорюючи їхній сплив.
 * Повільність більше не захищена — вважається фізичним, не ментальним ефектом.
 */
public class ResistanceToHorror extends PermanentPassiveAbility {

    private static final int TICK_PERIOD = 20; // раз на секунду
    private static final int REDUCTION_TICKS = 40; // -2с тривалості дебафу за спрацювання

    private static final PotionEffectType[] FEAR_EFFECTS = {
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.DARKNESS,
    };

    private int tickCounter = 0;

    @Override
    public String getName() {
        return "Резистентність до жаху";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fCвяте світло опирається страху: щосекунди зрізає по 2 секунди " +
                "з тривалості дебафів (Нудота, Сліпота, Слабкість, Морок), " +
                "прискорюючи їхній сплив.";
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (!context.playerData().isOnline(casterId)) return;

        tickCounter++;
        if (tickCounter % TICK_PERIOD != 0) return;

        for (PotionEffectType effect : FEAR_EFFECTS) {
            context.entity().reducePotionEffectDuration(casterId, effect, REDUCTION_TICKS);
        }
    }
}
