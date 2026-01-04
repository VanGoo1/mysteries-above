// src/main/java/me/vangoo/domain/pathways/door/abilities/Record.java
package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

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
        // Ефект відкриття "Книги"
        context.playSoundToCaster(Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);

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
            context.playSoundToCaster(Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.5f);
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
            playCaptureAnimation(context, target, caster); // Анімація "витягування"
            caster.playSound(caster.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.5f); // Звук заряду

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

        // Запуск візуального ефекту "Сканування" (кружляння рун навколо цілі)
        startScanningEffect(context, target, RECORDING_DURATION_SECONDS);

        context.subscribeToAbilityEvents(
                event -> {
                    if (event instanceof AbilityDomainEvent.AbilityUsed used) {
                        if (used.casterId().equals(target.getUniqueId())) {
                            recordedEvent.set(used);

                            // Миттєве повідомлення про запис
                            caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
                            playCaptureAnimation(context, target, caster); // Анімація захоплення

                            context.sendMessageToCaster(ChatColor.GREEN + "✓ Зафіксовано: " +
                                    ChatColor.AQUA + used.abilityName());

                            return true; // Відписатися
                        }
                    }
                    return false;
                },
                RECORDING_DURATION_SECONDS * 20
        );

        caster.playSound(caster.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.5f);
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
            caster.spawnParticle(Particle.SMOKE, caster.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
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
        playSuccessEffect(context, caster); // Ефект успішного запису

        context.sendMessageToCaster(String.format(
                "%s✓ Записано здібність: %s%s %s(одноразова)",
                ChatColor.GREEN,
                ChatColor.AQUA, recordedEvent.abilityName(),
                ChatColor.GRAY
        ));
    }

    // ============================================
    // VISUAL EFFECTS METHODS
    // ============================================

    /**
     * Малює магічні руни навколо цілі, поки йде запис
     */
    private void startScanningEffect(IAbilityContext context, Player target, int durationSeconds) {
        context.scheduleRepeating(new Runnable() {
            double angle = 0;
            int ticks = 0;
            final int maxTicks = durationSeconds * 20;

            @Override
            public void run() {
                if (ticks >= maxTicks || !target.isOnline()) return;

                Location loc = target.getLocation().add(0, 1, 0);
                double radius = 1.2;

                // Дві спіралі
                for (int i = 0; i < 2; i++) {
                    double currAngle = angle + (i * Math.PI);
                    double x = Math.cos(currAngle) * radius;
                    double z = Math.sin(currAngle) * radius;

                    // Руни з чарівного столу (класика для запису/книг)
                    target.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(x, Math.sin(angle * 2) * 0.5, z),
                            1, 0, 0, 0, 0);
                }

                // Рідкісні частинки "душі/есенції"
                if (ticks % 5 == 0) {
                    target.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 1, 0.3, 0.5, 0.3, 0.02);
                }

                angle += 0.15;
                ticks += 2;
            }
        }, 0L, 2L);
    }

    /**
     * Анімація переміщення есенції від цілі до заклинателя (момент захоплення)
     */
    private void playCaptureAnimation(IAbilityContext context, Player target, Player caster) {
        Location start = target.getLocation().add(0, 1, 0);
        Location end = caster.getLocation().add(0, 1, 0);
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = start.distance(end);

        // Промінь, що летить до гравця
        for (double d = 0; d < distance; d += 0.5) {
            final double progress = d;
            // Затримка для ефекту руху
            context.scheduleDelayed(() -> {
                Location point = start.clone().add(direction.clone().normalize().multiply(progress));
                caster.getWorld().spawnParticle(Particle.WITCH, point, 2, 0.1, 0.1, 0.1, 0);
                caster.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }, (long) (d * 1.5));
        }
    }

    /**
     * Ефект успішного запису (книга записана)
     */
    private void playSuccessEffect(IAbilityContext context, Player caster) {
        Location loc = caster.getLocation().add(0, 1.5, 0);

        // Звук успіху
        caster.playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        caster.playSound(caster.getLocation(), Sound.ITEM_BOOK_PUT, 1.0f, 1.0f);

        // Вибух магії навколо гравця
        caster.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.5, 0.5, 0.5, 0.5);
        caster.getWorld().spawnParticle(Particle.WITCH, loc, 20, 0.5, 0.5, 0.5, 0.2);
    }
}