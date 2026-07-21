package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;

/**
 * Спільний контракт кидка звичайного паперу для родини «Паперовий різак».
 *
 * <p>Реалізують {@link PaperCutter} (Seq 8, пасивна) та {@link PaperWeaponry}
 * (Seq 7, активна еволюція) — вони поділяють один {@code AbilityIdentity}, тож
 * у гравця присутня рівно одна з них. {@code PaperThrowListener} знаходить її
 * серед здібностей Beyonder'а і кидає папір цим методом.
 */
public interface PaperThrower {

    /**
     * Кидає один паперовий снаряд у напрямку погляду, якщо не на кулдауні.
     * Списує 1 папір із головної руки. Нічого не коштує (0 духовності).
     *
     * @return {@code true}, якщо кидок відбувся (для скасування ванільної інтеракції)
     */
    boolean throwPaper(IAbilityContext context);
}
