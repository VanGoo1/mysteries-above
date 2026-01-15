package me.vangoo.application.services.context;

import me.vangoo.application.services.RampageManager;
import me.vangoo.domain.abilities.context.IRampageContext;

import java.util.UUID;

public class RampageContext implements IRampageContext {

    private final RampageManager rampageManager;

    public RampageContext(RampageManager rampageManager) {
        this.rampageManager = rampageManager;
    }

    @Override
    public boolean rescueFromRampage(UUID casterId, UUID targetId) {
        if (rampageManager.isInRampage(targetId)) {
            return rampageManager.rescueFromRampage(targetId, casterId);
        }
        return false;
    }
}
