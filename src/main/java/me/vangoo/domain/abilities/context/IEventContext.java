package me.vangoo.domain.abilities.context;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.RecordedEvent;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface IEventContext {
    void publishAbilityUsedEvent(ActiveAbility activeAbility, Beyonder caster);

    void subscribeToAbilityEvents(Consumer<AbilityDomainEvent> handler);

    Optional<AbilityDomainEvent> getLastAbilityEvent(UUID casterId, int maxAgeSeconds);

    List<AbilityDomainEvent> getAbilityEventHistory(UUID casterId, int maxAgeSeconds);

    List<RecordedEvent> getPastEvents(Location location, int radius, int timeSeconds);
}
