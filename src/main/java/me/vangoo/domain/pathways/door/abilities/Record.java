package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Door Sequence 6: Record Ability
 */
public class Record extends ActiveAbility {
    private static final int RECORDING_DURATION_SECONDS = 10;
    private static final double DETECTION_RADIUS = 15.0;
    private static final int COST = 80;
    private static final int COOLDOWN = 60;

    private static final int BASE_DEMIGOD_LIMIT = 1;
    private static final int BASE_MID_LIMIT = 8;
    private static final int BASE_LOW_LIMIT = 20;

    @Override
    public String getName() {
        return "Запис Здібності";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Записує останню здібність вибраного гравця (до 10 сек назад) " +
                "або чекає нову протягом " + RECORDING_DURATION_SECONDS + " секунд.\n" +
                ChatColor.GRAY + "Радіус: " + (int)DETECTION_RADIUS + " блоків\n" +
                ChatColor.YELLOW + "⚠ Шанс успіху залежить від потужності здібності\n" +
                ChatColor.YELLOW + "⚠ Кількість записаних здібностей обмежена";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Beyonder caster = context.beyonder().getBeyonder(casterId);

        int currentCount = caster.getOffPathwayActiveAbilities().size();
        int maxAllowed = calculateMaxRecordedAbilities(caster);

        if (currentCount >= maxAllowed) {
            return AbilityResult.failure(String.format(
                    "Досягнуто ліміт записаних здібностей (%d/%d). Видаліть старі для запису нових.",
                    currentCount, maxAllowed
            ));
        }

        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(DETECTION_RADIUS);

        if (nearbyPlayers.isEmpty()) {
            return AbilityResult.failure("Немає гравців поблизу для запису");
        }

        context.ui().openChoiceMenu(
                "Виберіть ціль для запису",
                nearbyPlayers,
                this::createPlayerHead,
                selectedPlayer -> startRecording(context, selectedPlayer)
        );

        return AbilityResult.deferred();
    }

    private int calculateMaxRecordedAbilities(Beyonder caster) {
        int sequence = caster.getSequenceLevel();
        double mastery = caster.getMasteryValue();

        int baseLimit;
        if (sequence <= 4) {
            baseLimit = BASE_DEMIGOD_LIMIT;
        } else if (sequence <= 6) {
            baseLimit = BASE_MID_LIMIT;
        } else {
            baseLimit = BASE_LOW_LIMIT;
        }

        if (sequence <= 4 && mastery >= 100) {
            return 2;
        }

        double masteryMultiplier = 1.0 + (mastery / 100.0);
        int finalLimit = (int) Math.ceil(baseLimit * masteryMultiplier);

        return Math.max(1, finalLimit);
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.YELLOW + player.getName());
        head.setItemMeta(meta);
        return head;
    }

    private void startRecording(IAbilityContext context, Player target) {
        UUID casterId = context.getCasterId();
        UUID targetId = target.getUniqueId();

        Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);
        if (targetBeyonder == null) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Ціль не є Потойбічним!");
            return;
        }

        // Action bar повідомлення
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✦ Я прийшов, я побачив, я записав ✦", NamedTextColor.GOLD)
        );

        // Перевіряємо останню здібність з історії
        Optional<AbilityDomainEvent> recentEvent = context.events().getLastAbilityEvent(
                targetId,
                RECORDING_DURATION_SECONDS
        );

        if (recentEvent.isPresent() && recentEvent.get() instanceof AbilityDomainEvent.AbilityUsed used) {
            Location casterLocation = context.playerData().getCurrentLocation(casterId);
            if (casterLocation != null) {
                context.effects().playSound(casterLocation, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
            }

            context.messaging().sendMessage(casterId, String.format(
                    "%sЗнайдено останню здібність %s%s%s з історії!",
                    ChatColor.YELLOW,
                    ChatColor.AQUA, used.abilityName(),
                    ChatColor.YELLOW
            ));

            finalizeRecording(context, targetId, targetBeyonder, used);
            return;
        }

        // Підписуємося на нові здібності
        AtomicReference<AbilityDomainEvent.AbilityUsed> recordedEvent = new AtomicReference<>();

        context.events().subscribeToAbilityEvents(
                event -> {
                    if (event instanceof AbilityDomainEvent.AbilityUsed used) {
                        if (used.casterId().equals(targetId)) {
                            recordedEvent.set(used);

                            Location casterLocation = context.playerData().getCurrentLocation(casterId);
                            if (casterLocation != null) {
                                context.effects().playSound(casterLocation, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                            }

                            context.messaging().sendMessage(casterId,
                                    ChatColor.GREEN + "✓ Зафіксовано: " + ChatColor.AQUA + used.abilityName());
                            return true;
                        }
                    }
                    return false;
                },
                RECORDING_DURATION_SECONDS * 20
        );

        Location casterLocation = context.playerData().getCurrentLocation(casterId);
        if (casterLocation != null) {
            context.effects().playSound(casterLocation, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        }

        String targetName = context.playerData().getName(targetId);
        context.messaging().sendMessage(casterId, String.format(
                "%sОчікування здібності від %s%s%s...",
                ChatColor.YELLOW,
                ChatColor.AQUA, targetName,
                ChatColor.YELLOW
        ));

        context.scheduling().scheduleDelayed(() -> {
            finalizeRecording(context, targetId, targetBeyonder, recordedEvent.get());
        }, RECORDING_DURATION_SECONDS * 20L);
    }

    private void finalizeRecording(
            IAbilityContext context,
            UUID targetId,
            Beyonder targetBeyonder,
            AbilityDomainEvent.AbilityUsed recordedEvent
    ) {
        UUID casterId = context.getCasterId();
        Beyonder casterBeyonder = context.beyonder().getBeyonder(casterId);
        Location casterLocation = context.playerData().getCurrentLocation(casterId);

        if (recordedEvent == null) {
            if (casterLocation != null) {
                context.effects().playSound(casterLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }

            String targetName = context.playerData().getName(targetId);
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + targetName + " не використав жодної здібності");
            return;
        }

        Optional<Ability> abilityOpt = targetBeyonder.getAbilityByName(recordedEvent.abilityName());

        if (abilityOpt.isEmpty()) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "Не вдалося знайти здібність: " + recordedEvent.abilityName());
            return;
        }

        Ability ability = abilityOpt.get();

        // --- БЛОКУВАННЯ ЗАБОРОНЕНИХ ЗДІБНОСТЕЙ (Record / Analysis) ---
        if (isForbiddenAbility(ability)) {
            if (casterLocation != null) {
                context.effects().playSound(casterLocation, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "Ця здібність занадто складна або абстрактна для запису!");
            return;
        }
        // -------------------------------------------------------------

        if (!(ability instanceof ActiveAbility activeAbility)) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "Можна записати тільки активні здібності!");
            return;
        }

        int currentCount = casterBeyonder.getOffPathwayActiveAbilities().size();
        int maxAllowed = calculateMaxRecordedAbilities(casterBeyonder);

        if (currentCount >= maxAllowed) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + String.format("Досягнуто ліміт записаних здібностей (%d/%d)!",
                            currentCount, maxAllowed));
            return;
        }

        if (casterBeyonder.getAbilityByName(ability.getName()).isPresent()) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "У вас вже є ця здібність!");
            return;
        }

        int abilitySequence = findAbilitySequence(targetBeyonder, ability);
        if (abilitySequence == -1) {
            abilitySequence = targetBeyonder.getSequenceLevel();
        }

        int casterSequence = casterBeyonder.getSequenceLevel();
        SequenceBasedSuccessChance successChance =
                new SequenceBasedSuccessChance(casterSequence, abilitySequence);

        if (!successChance.rollSuccess()) {
            if (casterLocation != null) {
                context.effects().playSound(casterLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }

            context.messaging().sendMessage(casterId, String.format(
                    "%sНе вдалося записати здібність! (Шанс: %s)",
                    ChatColor.RED,
                    successChance.getFormattedChance()
            ));

            if (abilitySequence <= 4) {
                context.messaging().sendMessage(casterId,
                        ChatColor.YELLOW + "⚠ Здібності напівбогів надзвичайно важко записати!");
            }
            return;
        }

        OneTimeUseAbility oneTimeAbility = new OneTimeUseAbility(activeAbility);
        boolean added = casterBeyonder.addOffPathwayAbility(oneTimeAbility);

        if (!added) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "Не вдалося додати здібність (можливо вона вже існує)");
            return;
        }

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "Недостатньо духовності для завершення запису!");
            casterBeyonder.removeAbility(oneTimeAbility.getIdentity());
            return;
        }

        if (casterLocation != null) {
            context.effects().playSound(casterLocation, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            context.effects().playSound(casterLocation, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
        }

        context.messaging().sendMessage(casterId, String.format(
                "%s✓ Записано здібність: %s%s %s(одноразова)",
                ChatColor.GREEN,
                ChatColor.AQUA, recordedEvent.abilityName(),
                ChatColor.GRAY
        ));

        int newCount = casterBeyonder.getOffPathwayActiveAbilities().size();
        context.messaging().sendMessage(casterId, String.format(
                "%sЗаписано здібностей: %s%d/%d",
                ChatColor.GRAY,
                ChatColor.YELLOW, newCount, maxAllowed
        ));
    }

    /**
     * Перевіряє, чи заборонено копіювати цю здібність.
     * Тут ми блокуємо "Аналіз" та рекурсивний "Запис".
     */
    private boolean isForbiddenAbility(Ability ability) {
        String name = ability.getName();
        String simpleClassName = ability.getClass().getSimpleName();

        // Блокуємо за назвою в коді (клас) або за відображуваним іменем
        // Analysis - клас, "Аналіз" - ім'я в методі getName()
        // Record - сам клас запису (щоб не записувати запис)

        return simpleClassName.equalsIgnoreCase("Analysis") ||
                name.equalsIgnoreCase("Аналіз") ||
                simpleClassName.equalsIgnoreCase("Spellcasting") ||
                name.equalsIgnoreCase("Створення заклинань");
    }

    private int findAbilitySequence(Beyonder beyonder, Ability ability) {
        for (int seq = 0; seq <= 9; seq++) {
            List<Ability> abilities = beyonder.getPathway().GetAbilitiesForSequence(seq);
            for (Ability a : abilities) {
                if (a.getIdentity().equals(ability.getIdentity())) {
                    return seq;
                }
            }
        }
        return -1;
    }
}