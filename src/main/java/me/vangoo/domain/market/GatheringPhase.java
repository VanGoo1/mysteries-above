package me.vangoo.domain.market;

/** Фази події-збору. Єдиний дозволений цикл: IDLE → ANNOUNCED → OPEN → CLOSING → IDLE. */
public enum GatheringPhase {
    IDLE, ANNOUNCED, OPEN, CLOSING;

    public boolean canTransitionTo(GatheringPhase next) {
        return switch (this) {
            case IDLE -> next == ANNOUNCED;
            // ANNOUNCED → IDLE: скасування, якщо ніхто не погодився прийти
            case ANNOUNCED -> next == OPEN || next == IDLE;
            case OPEN -> next == CLOSING;
            case CLOSING -> next == IDLE;
        };
    }
}
