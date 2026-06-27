package me.vangoo.infrastructure.creatures;

import me.vangoo.domain.creatures.CreatureDefinition;
import org.bukkit.entity.LivingEntity;

/**
 * Шов вигляду істоти. Зараз єдина реалізація — {@link VanillaAppearance} (ванільний моб + ім'я +
 * розмір + екіп). Майбутнє: ModelEngineAppearance — підміна без зміни логіки спавну/дропу.
 */
public interface CreatureAppearance {
    void apply(LivingEntity entity, CreatureDefinition def);
}
