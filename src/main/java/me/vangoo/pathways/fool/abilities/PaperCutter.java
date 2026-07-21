package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.PaperThrowDamage;
import me.vangoo.domain.valueobjects.Sequence;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8: Clown — Paper Cutter (Паперовий різак).
 *
 * <p>Реворк: тепер це <b>пасивна</b> здатність. Тримаючи звичайний папір,
 * гравець кидає його (ПКМ) — паперовий снаряд завдає шкоди, що скейлиться з
 * послідовністю. Кидок безкоштовний, кулдаун 0.5с. Тригер ловить
 * {@code PaperThrowListener}; логіка кидка — {@link PaperThrows}; числа —
 * {@link PaperThrowDamage}.
 *
 * <p>Спільний {@link AbilityIdentity} з {@link PaperWeaponry} (Seq 7): при
 * просуванні 8→7 активна еволюція заміняє цю пасивку.
 */
public class PaperCutter extends PermanentPassiveAbility implements PaperThrower {

    public static final AbilityIdentity IDENTITY = AbilityIdentity.of("fool_paper_cutter");

    private final Map<UUID, Long> lastThrowTick = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Паперовий різак";
    }

    @Override
    public AbilityIdentity getIdentity() {
        return IDENTITY;
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int damage = PaperThrowDamage.damageFor(userSequence);
        return "Тримаючи звичайний папір, кидайте його (ПКМ) — паперовий кинджал " +
                "завдає " + damage + " шкоди. Безкоштовно, кулдаун 0.5с.";
    }

    @Override
    public void tick(IAbilityContext context) {
        // Пасивка керується тригером кидка (PaperThrowListener), періодичного тіку не потребує.
    }

    @Override
    public boolean throwPaper(IAbilityContext context) {
        return PaperThrows.throwOnce(context, lastThrowTick);
    }

    @Override
    public void cleanUp() {
        lastThrowTick.clear();
    }
}
