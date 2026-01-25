package me.vangoo.domain.abilities.core;

import me.vangoo.domain.abilities.context.*;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface IAbilityContext {

    // ==================== CASTER ====================

    UUID getCasterId();

    Beyonder getCasterBeyonder();

    Location getCasterLocation();

    Player getCasterPlayer();

    Location getCasterEyeLocation();

    IVisualEffectsContext effects();
    ISchedulingContext scheduling();
    IDataContext playerData();
    IBeyonderContext beyonder();
    IUIContext ui();
    ITargetContext targeting();
    IEventContext events();
    ICooldownContext cooldown();
    IRampageContext rampage();
    IEntityContext entity();
    IGlowingContext glowing();
    IMessagingContext messaging();
}
