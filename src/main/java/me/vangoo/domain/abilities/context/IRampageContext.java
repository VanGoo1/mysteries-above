package me.vangoo.domain.abilities.context;

import java.util.UUID;

public interface IRampageContext {
    boolean rescueFromRampage(UUID casterId, UUID targetId);
}
