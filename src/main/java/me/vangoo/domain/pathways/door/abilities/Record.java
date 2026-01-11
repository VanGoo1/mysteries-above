// src/main/java/me/vangoo/domain/pathways/door/abilities/Record.java
package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Door Sequence 6: Record Ability
 *
 * Обмеження:
 * - Ймовірність успіху залежить від послідовності, на якій отримується здібність
 * - Здібності seq 4 і вище дуже важко записати
 * - Кількість записаних здібностей обмежена масtery
 */
public class Record extends ActiveAbility {
    private static final int RECORDING_DURATION_SECONDS = 10;
    private static final double DETECTION_RADIUS = 15.0;
    private static final int COST = 80;
    private static final int COOLDOWN = 60;

    // Базові ліміти для послідовностей
    private static final int BASE_DEMIGOD_LIMIT = 1;  // Seq 0-4
    private static final int BASE_MID_LIMIT = 8;      // Seq 5-6
    private static final int BASE_LOW_LIMIT = 20;     // Seq 7-9

    @Override
    public String getName() {
        return "Запис Здібності";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        Beyonder caster = null; // Не маємо доступу тут, покажемо базові значення

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
        Beyonder caster = context.getCasterBeyonder();

        // Перевірка ліміту записаних здібностей
        int currentCount = caster.getOffPathwayActiveAbilities().size();
        int maxAllowed = calculateMaxRecordedAbilities(caster);

        if (currentCount >= maxAllowed) {
            return AbilityResult.failure(String.format(
                    "Досягнуто ліміт записаних здібностей (%d/%d). Видаліть старі для запису нових.",
                    currentCount, maxAllowed
            ));
        }

        List<Player> nearbyPlayers = context.getNearbyPlayers(DETECTION_RADIUS);

        if (nearbyPlayers.isEmpty()) {
            return AbilityResult.failure("Немає гравців поблизу для запису");
        }

        context.openChoiceMenu(
                "Виберіть ціль для запису",
                nearbyPlayers,
                this::createPlayerHead,
                selectedPlayer -> startRecording(context, selectedPlayer)
        );

        // Return deferred - spirituality will be consumed when recording actually happens
        return AbilityResult.deferred();
    }

    /**
     * Розрахувати максимальну кількість записаних здібностей на основі mastery
     */
    private int calculateMaxRecordedAbilities(Beyonder caster) {
        int sequence = caster.getSequenceLevel();
        double mastery = caster.getMasteryValue();

        // Визначаємо базовий ліміт за послідовністю
        int baseLimit;
        if (sequence <= 4) {
            // Напівбоги (0-4)
            baseLimit = BASE_DEMIGOD_LIMIT;
        } else if (sequence <= 6) {
            // Середні (5-6)
            baseLimit = BASE_MID_LIMIT;
        } else {
            // Низькі (7-9)
            baseLimit = BASE_LOW_LIMIT;
        }

        // Для напівбогів при full mastery додаємо бонус
        if (sequence <= 4 && mastery >= 100) {
            return 2; // Full mastery дає 2 записи для напівбогів
        }

        // Mastery впливає на ліміт: 0% mastery = baseLimit, 100% mastery = baseLimit * 2
        // Використовуємо просте лінійне масштабування
        double masteryMultiplier = 1.0 + (mastery / 100.0);

        int finalLimit = (int) Math.ceil(baseLimit * masteryMultiplier);

        return Math.max(1, finalLimit); // Мінімум 1 здібність завжди
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
        Player caster = context.getCaster();
        Beyonder casterBeyonder = context.getCasterBeyonder();

        Beyonder targetBeyonder = context.getBeyonderFromEntity(target.getUniqueId());
        if (targetBeyonder == null) {
            context.sendMessageToCaster(ChatColor.RED + "Ціль не є Потойбічним!");
            return;
        }

        // Показуємо iconic фразу
        caster.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GOLD + "✦ Я прийшов, я побачив, я записав ✦")
        );

        // ============================================
        // КРОК 1: Спробувати знайти в історії (останні 10 сек)
        // ============================================
        Optional<AbilityDomainEvent> recentEvent = context.getLastAbilityEvent(
                target.getUniqueId(),
                RECORDING_DURATION_SECONDS
        );

        if (recentEvent.isPresent() && recentEvent.get() instanceof AbilityDomainEvent.AbilityUsed used) {
            // Знайшли в історії - записуємо негайно
            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
            context.sendMessageToCaster(String.format(
                    "%sЗнайдено останню здібність %s%s%s з історії!",
                    ChatColor.YELLOW,
                    ChatColor.AQUA, used.abilityName(),
                    ChatColor.YELLOW
            ));

            finalizeRecording(context, target, targetBeyonder, used);
            return;
        }

        // ============================================
        // КРОК 2: Не знайшли - чекаємо нову (10 сек)
        // ============================================
        AtomicReference<AbilityDomainEvent.AbilityUsed> recordedEvent = new AtomicReference<>();

        context.subscribeToAbilityEvents(
                event -> {
                    if (event instanceof AbilityDomainEvent.AbilityUsed used) {
                        if (used.casterId().equals(target.getUniqueId())) {
                            recordedEvent.set(used);

                            // Миттєве повідомлення про запис
                            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                            context.sendMessageToCaster(ChatColor.GREEN + "✓ Зафіксовано: " +
                                    ChatColor.AQUA + used.abilityName());

                            return true; // Відписатися
                        }
                    }
                    return false;
                },
                RECORDING_DURATION_SECONDS * 20
        );

        caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        context.sendMessageToCaster(String.format(
                "%sОчікування здібності від %s%s%s...",
                ChatColor.YELLOW,
                ChatColor.AQUA, target.getName(),
                ChatColor.YELLOW
        ));

        // Після таймауту - фіналізація
        context.scheduleDelayed(() -> {
            finalizeRecording(context, target, targetBeyonder, recordedEvent.get());
        }, RECORDING_DURATION_SECONDS * 20L);
    }

    private void finalizeRecording(
            IAbilityContext context,
            Player target,
            Beyonder targetBeyonder,
            AbilityDomainEvent.AbilityUsed recordedEvent
    ) {
        Player caster = context.getCaster();
        Beyonder casterBeyonder = context.getCasterBeyonder();

        if (recordedEvent == null) {
            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            context.sendMessageToCaster(ChatColor.RED +
                    target.getName() + " не використав жодної здібності");
            return;
        }

        Optional<Ability> abilityOpt = targetBeyonder.getAbilityByName(recordedEvent.abilityName());

        if (abilityOpt.isEmpty()) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Не вдалося знайти здібність: " + recordedEvent.abilityName());
            return;
        }

        Ability ability = abilityOpt.get();

        if (!(ability instanceof ActiveAbility activeAbility)) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Можна записати тільки активні здібності!");
            return;
        }

        // Перевірка ліміту (подвійна перевірка на всяк випадок)
        int currentCount = casterBeyonder.getOffPathwayActiveAbilities().size();
        int maxAllowed = calculateMaxRecordedAbilities(casterBeyonder);

        if (currentCount >= maxAllowed) {
            context.sendMessageToCaster(ChatColor.RED +
                    String.format("Досягнуто ліміт записаних здібностей (%d/%d)!", currentCount, maxAllowed));
            return;
        }

        // Перевірка чи вже є ця здібність
        if (casterBeyonder.getAbilityByName(ability.getName()).isPresent()) {
            context.sendMessageToCaster(ChatColor.RED +
                    "У вас вже є ця здібність!");
            return;
        }

        // ============================================
        // ПЕРЕВІРКА ШАНСУ УСПІХУ на основі послідовності здібності
        // ============================================

        // Знаходимо послідовність, на якій ця здібність отримується
        int abilitySequence = findAbilitySequence(targetBeyonder, ability);

        if (abilitySequence == -1) {
            // Якщо не знайшли, використовуємо поточну послідовність цілі
            abilitySequence = targetBeyonder.getSequenceLevel();
        }

        // Використовуємо SequenceBasedSuccessChance:
        // Кастер намагається записати здібність певної послідовності
        // Чим вища послідовність (нижче число) - тим важче
        int casterSequence = casterBeyonder.getSequenceLevel();

        // Створюємо шанс успіху: кастер проти "опору" послідовності здібності
        SequenceBasedSuccessChance successChance =
                new SequenceBasedSuccessChance(casterSequence, abilitySequence);

        if (!successChance.rollSuccess()) {
            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            context.sendMessageToCaster(String.format(
                    "%sНе вдалося записати здібність! (Шанс: %s)",
                    ChatColor.RED,
                    successChance.getFormattedChance()
            ));

            // Показуємо інформацію про складність
            if (abilitySequence <= 4) {
                context.sendMessageToCaster(ChatColor.YELLOW +
                        "⚠ Здібності напівбогів надзвичайно важко записати!");
            }

            return;
        }

        // ============================================
        // УСПІШНИЙ ЗАПИС
        // ============================================

        OneTimeUseAbility oneTimeAbility = new OneTimeUseAbility(activeAbility);
        boolean added = casterBeyonder.addOffPathwayAbility(oneTimeAbility);

        if (!added) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Не вдалося додати здібність (можливо вона вже існує)");
            return;
        }

        // Consume spirituality and grant mastery NOW (deferred execution complete)
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            // This shouldn't happen since we checked at the beginning, but handle it anyway
            context.sendMessageToCaster(ChatColor.RED +
                    "Недостатньо духовності для завершення запису!");
            casterBeyonder.removeAbility(oneTimeAbility.getIdentity());
            return;
        }

        // Успіх
        caster.playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        caster.playSound(caster.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);

        context.sendMessageToCaster(String.format(
                "%s✓ Записано здібність: %s%s %s(одноразова)",
                ChatColor.GREEN,
                ChatColor.AQUA, recordedEvent.abilityName(),
                ChatColor.GRAY
        ));

        // Показуємо інформацію про ліміт
        int newCount = casterBeyonder.getOffPathwayActiveAbilities().size();
        context.sendMessageToCaster(String.format(
                "%sЗаписано здібностей: %s%d/%d",
                ChatColor.GRAY,
                ChatColor.YELLOW, newCount, maxAllowed
        ));
    }

    /**
     * Знайти послідовність, на якій ця здібність отримується
     */
    private int findAbilitySequence(Beyonder beyonder, Ability ability) {
        // Шукаємо здібність у шляху гравця
        for (int seq = 0; seq <= 9; seq++) {
            List<Ability> abilities = beyonder.getPathway().GetAbilitiesForSequence(seq);
            for (Ability a : abilities) {
                if (a.getIdentity().equals(ability.getIdentity())) {
                    return seq;
                }
            }
        }
        return -1; // Не знайдено
    }
}