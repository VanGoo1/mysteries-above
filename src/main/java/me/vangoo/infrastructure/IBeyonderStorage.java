package me.vangoo.infrastructure;

import me.vangoo.domain.Beyonder;

import java.util.Map;
import java.util.UUID;

public interface IBeyonderStorage {
    boolean add(Beyonder beyonder);
    boolean remove(UUID playerId);
    void saveAll();
    Beyonder get(UUID playerId);
    boolean update(UUID playerId, Beyonder beyonder);
    Map<UUID, Beyonder> getAll();
}
