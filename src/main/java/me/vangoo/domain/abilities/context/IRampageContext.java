package me.vangoo.domain.abilities.context;

import java.util.UUID;

public interface IRampageContext {
    boolean rescueFromRampage(UUID casterId, UUID targetId);

    /**
     * Чи гравець зараз у рейміджі (втратив контроль).
     *
     * <p>Потрібно здібностям, для яких рейміджер — інша категорія цілі, а не звичайний гравець:
     * вікі шляху Смерті прямо каже, що рейміджери рахуються мертвими душами (Око Смерті).
     */
    boolean isInRampage(UUID playerId);
}
