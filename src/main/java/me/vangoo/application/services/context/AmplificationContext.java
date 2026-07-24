package me.vangoo.application.services.context;

import me.vangoo.application.services.AmplificationManager;
import me.vangoo.domain.abilities.context.IAmplificationContext;

import java.util.UUID;

public class AmplificationContext implements IAmplificationContext {

    private final AmplificationManager amplificationManager;

    public AmplificationContext(AmplificationManager amplificationManager) {
        this.amplificationManager = amplificationManager;
    }

    @Override
    public void amplifyDamage(UUID playerId, double multiplier, int durationSeconds) {
        amplificationManager.amplifyDamage(playerId, multiplier, durationSeconds);
    }

    @Override
    public double getDamageMultiplier(UUID playerId) {
        return amplificationManager.getDamageMultiplier(playerId);
    }
}
