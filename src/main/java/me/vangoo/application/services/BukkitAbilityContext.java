package me.vangoo.application.services;


import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.*;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.context.*;
import me.vangoo.domain.abilities.context.*;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.*;
import org.bukkit.entity.*;

import java.util.*;
import java.util.logging.Logger;


public class BukkitAbilityContext implements IAbilityContext {
    private final Player caster;
    private final MysteriesAbovePlugin plugin;
    private final CooldownManager cooldownManager;
    private final BeyonderService beyonderService;
    private final AbilityLockManager lockManager;
    private final GlowingEntities glowingEntities;
    private final EffectManager effectManager;
    private final Logger LOGGER;
    private final RampageManager rampageManager;
    private final TemporaryEventManager temporaryEventManager;
    private final PassiveAbilityManager passiveAbilityManager;
    private final DomainEventPublisher eventPublisher;
    private final RecipeUnlockService recipeUnlockService;
    private final PotionManager potionManager;

    private IVisualEffectsContext visualEffectsContext;
    private ISchedulingContext schedulingContext;
    private IDataContext dataContext;
    private IBeyonderContext beyonderContext;
    private IUIContext uiContext;
    private ITargetContext targetContext;
    private IEventContext eventContext;
    private ICooldownContext cooldownContext;
    private IRampageContext rampageContext;
    private IEntityContext entityContext;
    private IGlowingContext glowingContext;
    private IMessagingContext messagingContext;

    public BukkitAbilityContext(
            Player caster,
            MysteriesAbovePlugin plugin,
            CooldownManager cooldownManager,
            BeyonderService beyonderService,
            AbilityLockManager lockManager, GlowingEntities glowingEntities, EffectManager effectManager,
            RampageManager rampageManager, TemporaryEventManager temporaryEventManager, PassiveAbilityManager passiveAbilityManager, DomainEventPublisher eventPublisher,
            RecipeUnlockService recipeUnlockService, PotionManager potionManager) {
        this.caster = caster;
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.beyonderService = beyonderService;
        this.lockManager = lockManager;
        this.glowingEntities = glowingEntities;
        this.effectManager = effectManager;
        this.LOGGER = plugin.getLogger();
        this.rampageManager = rampageManager;
        this.temporaryEventManager = temporaryEventManager;
        this.passiveAbilityManager = passiveAbilityManager;
        this.eventPublisher = eventPublisher;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
    }

    // ==========================================
    // CASTER
    // ==========================================

    @Override
    public UUID getCasterId() {
        return caster.getUniqueId();
    }

    @Override
    public Beyonder getCasterBeyonder() {
        return beyonderService.getBeyonder(caster.getUniqueId());
    }

    @Override
    public Location getCasterLocation() {
        return caster.getLocation();
    }

    @Override
    public Player getCasterPlayer() {
        return caster;
    }

    @Override
    public Location getCasterEyeLocation() {
        return caster.getEyeLocation();
    }

    @Override
    public IVisualEffectsContext effects() {
        if (visualEffectsContext == null) {
            visualEffectsContext = new VisualEffectsContext(effectManager, plugin);
        }
        return visualEffectsContext;
    }

    @Override
    public ISchedulingContext scheduling() {
        if (schedulingContext == null) {
            schedulingContext = new SchedulingContext(plugin);
        }
        return schedulingContext;
    }

    @Override
    public IDataContext playerData() {
        if (dataContext == null) {
            dataContext = new DataContext();
        }
        return dataContext;
    }

    @Override
    public IBeyonderContext beyonder() {
        if (beyonderContext == null) {
            beyonderContext = new BeyonderContext(beyonderService, passiveAbilityManager, recipeUnlockService, potionManager);
        }
        return beyonderContext;
    }

    @Override
    public IUIContext ui() {
        if (uiContext == null) {
            uiContext = new UIContext(caster, plugin);
        }
        return uiContext;
    }

    @Override
    public ITargetContext targeting() {
        if (targetContext == null) {
            targetContext = new TargetContext(caster);
        }
        return targetContext;
    }

    @Override
    public IEventContext events() {
        if (eventContext == null) {
            eventContext = new EventContext(eventPublisher, plugin, temporaryEventManager);
        }
        return eventContext;
    }

    @Override
    public ICooldownContext cooldown() {
        if (cooldownContext == null) {
            cooldownContext = new CooldownContext(cooldownManager, lockManager);
        }
        return cooldownContext;
    }

    @Override
    public IRampageContext rampage() {
        if (rampageContext == null) {
            rampageContext = new RampageContext(rampageManager);
        }
        return rampageContext;
    }

    @Override
    public IEntityContext entity() {
        if (entityContext == null) {
            entityContext = new EntityContext(plugin);
        }
        return entityContext;
    }

    @Override
    public IGlowingContext glowing() {
        if (glowingContext == null) {
            glowingContext = new GlowingContext(glowingEntities, plugin, LOGGER);
        }
        return glowingContext;
    }

    @Override
    public IMessagingContext messaging() {
        if (messagingContext == null) {
            messagingContext = new MessagingContext(plugin, scheduling());
        }
        return messagingContext;
    }
}