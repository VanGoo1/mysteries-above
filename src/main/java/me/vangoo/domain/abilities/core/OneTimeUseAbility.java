// src/main/java/me/vangoo/domain/abilities/core/OneTimeUseAbility.java
package me.vangoo.domain.abilities.core;

import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;

/**
 * Domain: Wrapper для одноразових здібностей
 *
 * Обгортає будь-яку активну здібність та робить її одноразовою.
 * Після успішного використання автоматично видаляє себе.
 *
 * Для здібностей-перемикачів (toggle abilities) дозволяє два використання:
 * одне для активації, одне для деактивації.
 *
 * Identity: "onetime:original_name" для розпізнавання при deserialisation
 */
public class OneTimeUseAbility extends ActiveAbility {
    private final ActiveAbility wrappedAbility;
    private int usesRemaining;

    public OneTimeUseAbility(ActiveAbility ability) {
        this.wrappedAbility = ability;
        // For abilities with 0 cost and toggle behavior, allow 2 uses
        // (once to enable, once to disable)
        this.usesRemaining = (ability.getSpiritualityCost() == 0) ? 2 : 1;
    }

    @Override
    public String getName() {
        return wrappedAbility.getName();
    }

    @Override
    public String getDescription(Sequence userSequence) {
        String usageNote = usesRemaining > 1
            ? "(Можна використати " + usesRemaining + " рази)"
            : "(Одноразова здібність)";
        return wrappedAbility.getDescription(userSequence) +
                "\n§c§o" + usageNote;
    }

    @Override
    public int getSpiritualityCost() {
        return wrappedAbility.getSpiritualityCost();
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return wrappedAbility.getCooldown(userSequence);
    }

    @Override
    public AbilityIdentity getIdentity() {
        // Префікс для розпізнавання одноразових здібностей
        return AbilityIdentity.of("onetime:" + wrappedAbility.getIdentity().id());
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // Викликаємо обгорнуту здібність
        AbilityResult result = wrappedAbility.performExecution(context);

        // Якщо успішно - зменшуємо кількість використань
        if (result.isSuccess()) {
            usesRemaining--;

            if (usesRemaining <= 0) {
                // Більше немає використань - видаляємо
                context.removeOffPathwayAbility(getIdentity());
                context.sendMessageToCaster(
                        "§7Одноразова здібність §e" + getName() + " §7була використана");
            } else {
                // Ще залишилися використання
                context.sendMessageToCaster(
                        "§7Використань залишилось: §e" + usesRemaining);
            }
        }

        return result;
    }

    @Override
    protected boolean canExecute(IAbilityContext context) {
        return wrappedAbility.canExecute(context);
    }

    @Override
    public void cleanUp() {
        wrappedAbility.cleanUp();
    }

    /**
     * Отримати оригінальну здібність (для debugging/testing)
     */
    public ActiveAbility getWrappedAbility() {
        return wrappedAbility;
    }

    /**
     * Перевірити чи є це одноразова версія певної здібності
     */
    public boolean wraps(AbilityIdentity abilityName) {
        return wrappedAbility.getIdentity().equals(abilityName);
    }
}