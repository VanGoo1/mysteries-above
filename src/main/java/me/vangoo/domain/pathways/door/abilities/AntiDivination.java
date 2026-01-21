package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.UUID;


public class AntiDivination extends ToggleablePassiveAbility {

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of("anti_divination");
    }

    @Override
    public String getName() {
        return "[Пасивна] Протидія ворожінню";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Захист від чужого ворожіння. " +
                "Коли активовано, інші не зможуть використовувати методи гадання на вас. ";
    }

    @Override
    public void onEnable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "Ви захищені від чужого ворожіння"));

        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);

        context.effects().spawnParticle(
                Particle.END_ROD,
                context.getCasterLocation().add(0, 1.5, 0),
                30, 0.5, 0.5,
                0.1
        );

        context.effects().spawnParticle(
                Particle.ENCHANT,
                context.getCasterLocation().add(0, 1, 0),
                20,
                0.3, 0.5, 0.3
        );
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "Ви знову вразливі до ворожіння"));

        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);

        context.effects().spawnParticle(
                Particle.SMOKE,
                context.getCasterLocation().add(0, 1, 0),
                15,
                0.3, 0.3, 0.3
        );
    }

    @Override
    public void tick(IAbilityContext context) {
        Location loc = context.getCasterLocation();
        if (loc.getWorld() != null) {
            if (loc.getWorld().getFullTime() % 40 == 0) {
                context.effects().spawnParticle(
                        Particle.ENCHANT,
                        loc.add(0, 1.1, 0),
                        2,
                        0.1, 0.15, 0.1
                );
            }
        }
    }
}
