package me.vangoo.application.services;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.core.IAbilityContext;
import org.bukkit.entity.Player;

import java.util.Objects;

public class AbilityContextFactory {

    private final MysteriesAbovePlugin plugin;
    private final CooldownManager cooldownManager;
    private final BeyonderService beyonderService;
    private final AbilityLockManager lockManager;
    private final GlowingEntities glowingEntities;
    private final EffectManager effectManager;
    private final RampageManager rampageManager;

    public AbilityContextFactory(
            MysteriesAbovePlugin plugin,
            CooldownManager cooldownManager,
            BeyonderService beyonderService,
            AbilityLockManager lockManager,
            GlowingEntities glowingEntities,
            EffectManager effectManager,
            RampageManager rampageManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.cooldownManager = Objects.requireNonNull(cooldownManager, "CooldownManager cannot be null");
        this.beyonderService = Objects.requireNonNull(beyonderService, "BeyonderService cannot be null");
        this.lockManager = Objects.requireNonNull(lockManager, "AbilityLockManager cannot be null");
        this.glowingEntities = Objects.requireNonNull(glowingEntities, "GlowingEntities cannot be null");
        this.effectManager = Objects.requireNonNull(effectManager, "EffectManager cannot be null");
        this.rampageManager = rampageManager;
    }


    public IAbilityContext createContext(Player caster) {
        return new BukkitAbilityContext(
                caster,
                plugin,
                cooldownManager,
                beyonderService,
                lockManager,
                glowingEntities,
                effectManager,
                rampageManager
        );
    }
}