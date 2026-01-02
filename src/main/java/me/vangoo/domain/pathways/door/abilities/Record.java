// src/main/java/me/vangoo/domain/pathways/door/abilities/Record.java
package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.Sequence;
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
 * ВИПРАВЛЕНО: Тепер спочатку перевіряє останню здібність з історії (10 сек),
 * потім чекає нову протягом залишку часу
 */
public class Record extends ActiveAbility {
    private static final int RECORDING_DURATION_SECONDS = 10;
    private static final double DETECTION_RADIUS = 15.0;
    private static final int COST = 80;
    private static final int COOLDOWN = 60;

    @Override
    public String getName() {
        return "Запис Здібності";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Записує останню здібність вибраного гравця (до 10 сек назад) " +
                "або чекає нову протягом " + RECORDING_DURATION_SECONDS + " секунд.\n" +
                ChatColor.GRAY + "Радіус: " + (int)DETECTION_RADIUS + " блоків";
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

        return AbilityResult.success();
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

        Beyonder targetBeyonder = context.getBeyonderFromEntity(target.getUniqueId());
        if (targetBeyonder == null) {
            context.sendMessageToCaster(ChatColor.RED + "Ціль не є Потойбічним!");
            return;
        }

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

        Beyonder ownerBeyonder = context.getCasterBeyonder();

        // Check if caster already has this ability (in pathway or off-pathway)
        if (ownerBeyonder.getAbilityByName(ability.getName()).isPresent()) {
            context.sendMessageToCaster(ChatColor.RED +
                    "У вас вже є ця здібність!");
            return;
        }

        OneTimeUseAbility oneTimeAbility = new OneTimeUseAbility(activeAbility);
        boolean added = ownerBeyonder.addOffPathwayAbility(oneTimeAbility);

        if (!added) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Не вдалося додати здібність (можливо вона вже існує)");
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
    }
}