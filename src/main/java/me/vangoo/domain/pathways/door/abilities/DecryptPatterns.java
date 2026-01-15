// src/main/java/me/vangoo/domain/pathways/door/abilities/AbilitySpy.java
package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.Sequence;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;


public class DecryptPatterns extends PermanentPassiveAbility {
    private static final double DETECTION_RADIUS = 30.0;

    @Override
    public String getName() {
        return "Розшифровка патернів";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Пасивно відстежує використання здібностей іншими гравцями " +
                "в радіусі " + (int)DETECTION_RADIUS + " блоків. " +
                "Інформація відображається в action bar.";
    }

    @Override
    public void tick(IAbilityContext context) {
        // Цій здібності не потрібен tick - вона працює через події
    }

    @Override
    public void onActivate(IAbilityContext context) {
        Bukkit.getLogger().info("прив");
        // Підписуємося на ability events коли гравець заходить
        context.subscribeToAbilityEvents(event -> {
            if (event instanceof AbilityDomainEvent.AbilityUsed used) {
                handleAbilityUsed(context, used);
            }
        });
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        // Відписуємося автоматично через cleanup в PassiveAbilityManager
    }

    /**
     * Обробляє подію використання здібності
     */
    private void handleAbilityUsed(IAbilityContext context, AbilityDomainEvent.AbilityUsed event) {
        Player owner = context.getCasterPlayer();

        // Не показувати власні здібності
        if (event.casterId().equals(owner.getUniqueId())) {
            return;
        }

        // Перевірити відстань
        Player target = Bukkit.getPlayer(event.casterId());
        if (target == null || !target.isOnline()) {
            return;
        }

        if (owner.getLocation().distance(target.getLocation()) > DETECTION_RADIUS) {
            return;
        }

        // Форматувати повідомлення
        String message = formatAbilityMessage(target, event);

        // Показати в action bar
        owner.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(message)
        );
    }

    /**
     * Форматує повідомлення про використану здібність
     */
    private String formatAbilityMessage(Player target, AbilityDomainEvent.AbilityUsed event) {
        ChatColor nameColor = ChatColor.YELLOW;
        ChatColor abilityColor = event.isOffPathway() ? ChatColor.LIGHT_PURPLE : ChatColor.AQUA;

        return String.format("%s%s %s використав %s%s",
                nameColor, target.getName(),
                ChatColor.GRAY,
                abilityColor, event.abilityName()
        );
    }
}