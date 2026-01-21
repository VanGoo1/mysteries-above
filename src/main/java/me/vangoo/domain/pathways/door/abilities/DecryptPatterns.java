// src/main/java/me/vangoo/domain/pathways/door/abilities/DecryptPatterns.java
package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;

import java.util.UUID;

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
        // Підписуємося на ability events коли гравець заходить
        context.events().subscribeToAbilityEvents(event -> {
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
        UUID ownerId = context.getCasterId();

        // Не показувати власні здібності
        if (event.casterId().equals(ownerId)) {
            return;
        }

        // Перевірити чи цільовий гравець онлайн
        if (!context.playerData().isOnline(event.casterId())) {
            return;
        }

        // Перевірити відстань
        Location ownerLocation = context.playerData().getCurrentLocation(ownerId);
        Location targetLocation = context.playerData().getCurrentLocation(event.casterId());

        if (ownerLocation == null || targetLocation == null) {
            return;
        }

        // ВИПРАВЛЕННЯ: Перевірка на той самий світ перед вимірюванням відстані
        if (!ownerLocation.getWorld().equals(targetLocation.getWorld())) {
            return; // Гравці в різних світах - не показувати повідомлення
        }

        // Тепер безпечно вимірюємо відстань
        if (ownerLocation.distance(targetLocation) > DETECTION_RADIUS) {
            return;
        }

        // Отримати ім'я цільового гравця
        String targetName = context.playerData().getName(event.casterId());
        if (targetName == null || targetName.isEmpty()) {
            return;
        }

        // Форматувати повідомлення
        Component message = formatAbilityMessage(targetName, event);

        // Показати в action bar
        context.messaging().sendMessageToActionBar(ownerId, message);
    }

    /**
     * Форматує повідомлення про використану здібність
     */
    private Component formatAbilityMessage(String targetName, AbilityDomainEvent.AbilityUsed event) {
        NamedTextColor nameColor = NamedTextColor.YELLOW;
        TextColor abilityColor = event.isOffPathway()
                ? TextColor.fromHexString("#FF55FF") // Light purple
                : NamedTextColor.AQUA;

        return Component.text(targetName, nameColor)
                .append(Component.text(" використав ", NamedTextColor.GRAY))
                .append(Component.text(event.abilityName(), abilityColor));
    }
}