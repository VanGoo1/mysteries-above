package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;


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
        context.sendMessageToCaster(ChatColor.GRAY + "Ви захищені від чужого ворожіння");

        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        context.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);

        context.spawnParticle(
                Particle.END_ROD,
                context.getCasterLocation().add(0, 1.5, 0),
                30, 0.5, 0.5,
                0.1
        );

        context.spawnParticle(
                Particle.ENCHANT,
                context.getCasterLocation().add(0, 1, 0),
                20,
                0.3, 0.5, 0.3
        );
    }

    @Override
    public void onDisable(IAbilityContext context) {
        context.sendMessageToCaster(ChatColor.GRAY + "Ви знову вразливі до ворожіння");

        context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);

        context.spawnParticle(
                Particle.SMOKE,
                context.getCasterLocation().add(0, 1, 0),
                15,
                0.3, 0.3, 0.3
        );
    }

    @Override
    public void tick(IAbilityContext context) {
        if (context.getCaster().getTicksLived() % 40 == 0) {
            context.spawnParticle(
                    Particle.ENCHANT,
                    context.getCasterLocation().add(0, 1.1, 0),
                    2,
                    0.1, 0.15, 0.1
            );
        }
    }


}
