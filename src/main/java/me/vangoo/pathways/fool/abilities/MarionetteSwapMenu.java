package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;

/**
 * Окрема здібність Маріонетиста: відкриває меню (triumph-gui через {@code ctx.ui()}) зі
 * списком ваших живих маріонеток і дозволяє миттєво вселитись у будь-яку з них —
 * щоб швидко перемикатися між тілами.
 *
 * <p>Уся логіка свапу/реєстру маріонеток живе в {@link MarionettistControl}; ця здібність
 * лише будує меню та делегує вибір (тонкий адаптер). Інстанс {@link MarionettistControl}
 * інжектиться у конструктор у {@code Fool.initializeAbilities()}, тож обидві здібності
 * поділяють той самий стан.</p>
 */
public class MarionetteSwapMenu extends ActiveAbility {

    public static final AbilityIdentity IDENTITY = AbilityIdentity.of("marionette_swap_menu");

    private static final int BASE_COST       = 50;
    private static final int BASE_COOLDOWN_S = 6;

    private final MarionettistControl control;

    public MarionetteSwapMenu(MarionettistControl control) {
        this.control = control;
    }

    @Override public String getName() { return "Перемикання Маріонеток"; }

    @Override public AbilityIdentity getIdentity() { return IDENTITY; }

    @Override
    public String getDescription(Sequence seq) {
        return "Відкриває меню ваших живих маріонеток.\n" +
                "Оберіть будь-яку — і ви миттєво вселитесь у неї.\n\n" +
                "§7Дозволяє швидко перемикатися між тілами. Якщо ви вже всередині іншої " +
                "маріонетки — спершу автоматично повернетесь у своє тіло.";
    }

    @Override public int getSpiritualityCost() { return BASE_COST; }

    @Override
    public int getCooldown(Sequence seq) {
        return (int) (BASE_COOLDOWN_S / SequenceScaler.calculateMultiplier(
                seq.level(), SequenceScaler.ScalingStrategy.WEAK));
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext ctx) {
        // Меню та логіка свапу живуть у MarionettistControl (спільний стан).
        if (!control.openSwapMenu(ctx))
            return AbilityResult.failure("§cУ вас немає живих маріонеток.");
        return AbilityResult.success();
    }
}
