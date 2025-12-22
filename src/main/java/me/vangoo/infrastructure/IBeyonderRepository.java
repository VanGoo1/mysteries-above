package me.vangoo.infrastructure;

import me.vangoo.domain.entities.Beyonder;

import java.util.Map;
import java.util.UUID;

public interface IBeyonderRepository {
    boolean add(Beyonder beyonder);
    boolean remove(UUID playerId);
    void saveAll();
    Beyonder get(UUID playerId);
    boolean update(UUID playerId, Beyonder beyonder);
    Map<UUID, Beyonder> getAll();
}
