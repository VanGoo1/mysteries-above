package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MysticalReenactment extends ActiveAbility {

    @Override
    public String getName() {
        return "Містична Реконструкція";
    }

    @Override
    public int getSpiritualityCost() {
        return 200;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 60;
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Показує історію змін блоків та скринь (останні 5 хв).";
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        final UUID casterId = context.getCasterId();
        Location center = context.playerData().getCurrentLocation(casterId);

        if (center == null) {
            return AbilityResult.failure("Неможливо отримати локацію гравця.");
        }

        context.messaging().sendMessageToActionBar(casterId, Component.text("Зчитування містичних слідів...", NamedTextColor.AQUA));
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);

        // Затримка для запису БД
        context.scheduling().scheduleDelayed(() -> {
            context.scheduling().runAsync(() -> {
                List<RecordedEvent> events = context.events().getPastEvents(center, 15, 300); // Зменшив радіус до 15

                context.scheduling().scheduleDelayed(() -> {
                    if (events.isEmpty()) {
                        context.messaging().sendMessageToActionBar(casterId, Component.text("Слідів не знайдено.", NamedTextColor.GRAY));
                        return;
                    }

                    spawnScanEffect(context, center);
                    context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);

                    // === ОБМЕЖЕННЯ КІЛЬКОСТІ ===
                    // Показуємо максимум 5 записів, щоб не було "вежі" з тексту
                    int maxEventsToShow = 5;

                    int shownCount = 0;
                    Location lastLoc = null;
                    double verticalOffset = 0;

                    for (RecordedEvent event : events) {
                        if (shownCount >= maxEventsToShow) break;

                        // Логіка стеку: якщо координати ті самі, піднімаємо текст
                        if (lastLoc != null && event.getLocation().distanceSquared(lastLoc) < 1.0) {
                            verticalOffset += 0.25;
                        } else {
                            verticalOffset = 0;
                            lastLoc = event.getLocation();
                            // Якщо це новий блок, скидаємо лічильник для нього (опціонально)
                            // Але тут ми просто обмежуємо загальну кількість для чистоти
                        }

                        visualizeEvent(context, event, verticalOffset);
                        shownCount++;
                    }

                    context.messaging().sendMessage(casterId, "§7Показано " + shownCount + " останніх дій.");

                }, 0L);
            });
        }, 40L); // 2 секунди затримки

        return AbilityResult.success();
    }

    private void visualizeEvent(IAbilityContext context, RecordedEvent event, double yOffset) {
        Location loc = event.getLocation();
        long minutesAgo = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - event.getTimestamp());
        String timeStr = (minutesAgo == 0) ? "зараз" : minutesAgo + "хв";

        // Формуємо текст
        // event.getDescription() вже має колір (ми додали його в Handler)
        Component text = Component.text(event.getDescription())
                .append(Component.text(" (" + timeStr + ")", NamedTextColor.DARK_GRAY));

        // Спавнимо трохи вище, щоб текст не перекривав скриню
        Location displayLoc = loc.clone().add(0.5, 1.3 + yOffset, 0.5);

        context.messaging().spawnTemporaryHologram(displayLoc, text, 200L); // 10 секунд

        // Маленька частинка
        context.effects().spawnParticle(Particle.END_ROD, displayLoc, 1);
    }

    private void spawnScanEffect(IAbilityContext context, Location center) {
        // Простий ефект кола
        for (int i = 0; i < 360; i += 20) {
            double angle = Math.toRadians(i);
            context.effects().spawnParticle(Particle.FIREWORK,
                    center.clone().add(Math.cos(angle) * 5, 1, Math.sin(angle) * 5),
                    1);
        }
    }
}