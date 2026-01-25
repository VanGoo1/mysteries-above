package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DreamTraversal extends ActiveAbility {

    private static final int BASE_RANGE = 500;
    private static final int TELEPORT_DELAY_SECONDS = 5;
    private static final int COST = 200;
    private static final int COOLDOWN = 60;

    @Override
    public String getName() { return "Блукання у снах"; }

    @Override
    public String getDescription(Sequence userSequence) {
        // Використовуємо наш новий метод розрахунку
        int range = calculateRange(userSequence);
        return String.format(
                "Проникніть у сни сплячого гравця в радіусі %d блоків. Через %d секунд ви телепортуєтесь до нього. Ви можете обрати конкретну ціль зі списку сплячих.",
                range, TELEPORT_DELAY_SECONDS
        );
    }

    @Override
    public int getSpiritualityCost() { return COST; }

    @Override
    public int getCooldown(Sequence userSequence) { return COOLDOWN; }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Sequence casterSequence = context.getCasterBeyonder().getSequence();
        int searchRange = calculateRange(casterSequence);

        List<Player> sleepingPlayers = findSleepingPlayers(context, searchRange);

        if (sleepingPlayers.isEmpty()) {
            return AbilityResult.failure("Немає сплячих гравців у радіусі " + searchRange + " блоків");
        }

        if (sleepingPlayers.size() == 1) {
            // Передаємо UUID — пізніше під час виконання не будемо викликати context.getPlayer(...)
            startTeleportSequence(context, sleepingPlayers.getFirst().getUniqueId(), sleepingPlayers.getFirst().getName(), false);
            return AbilityResult.deferred();
        }

        // Якщо кілька — показуємо меню вибору (передаємо Player-об'єкти для побудови UI, але у callback'у передаєм UUID)
        openDreamTargetMenu(context, sleepingPlayers);

        return AbilityResult.deferred();
    }
    private int calculateRange(Sequence sequence) {
        int currentLevel = sequence.level();

        // Якщо раптом рівень нижчий за 5 (наприклад 6, 7 - хоча здібність з 5-ї),
        // то використовуємо базу 500.
        if (currentLevel > 5) return BASE_RANGE;

        // Різниця рівнів. Для 5-ї = 0, для 4-ї = 1, для 3-ї = 2.
        int power = 5 - currentLevel;

        // Формула: 500 * 2^(різниця).
        // Seq 5: 500 * 1 = 500
        // Seq 4: 500 * 2 = 1000
        // Seq 3: 500 * 4 = 2000
        return BASE_RANGE * (int) Math.pow(2, power);
    }
    private List<Player> findSleepingPlayers(IAbilityContext context, int range) {
        Location casterLoc = context.getCasterLocation();
        return context.targeting().getNearbyPlayers(range).stream()
                .filter(Player::isSleeping)
                .sorted(Comparator.comparingDouble(p -> p.getLocation().distance(casterLoc)))
                .collect(Collectors.toList());
    }

    private void openDreamTargetMenu(IAbilityContext context, List<Player> targets) {
        context.messaging().sendMessage(context.getCasterId(), ChatColor.DARK_PURPLE + "✦ Ви відчуваєте " + targets.size() + " сплячих свідомостей...");
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_BEACON_AMBIENT, 0.7f, 1.5f);

        // Ми передаємо у меню самі Player-об'єкти (щоб створити гарні ItemStack'и),
        // але при виборі віддаємо лише UUID та ім'я (щоб уникнути подальшого звернення до Bukkit)
        Function<Player, ItemStack> mapper = p -> createTargetMenuItem(context, p.getUniqueId(), p.getName(), p.getLocation());

        context.ui().openChoiceMenu(
                "Блукання у снах",
                targets,
                mapper,
                (Player chosen) -> startTeleportSequence(context, chosen.getUniqueId(), chosen.getName(),true)
        );
    }

    private ItemStack createTargetMenuItem(IAbilityContext context, UUID targetId, String knownName, Location knownLocation) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + knownName);

            Location bedLoc = context.playerData().getBedSpawnLocation(context.getCasterId());
            String bedInfo = bedLoc != null
                    ? String.format("%s [%d, %d, %d]", bedLoc.getWorld().getName(), bedLoc.getBlockX(), bedLoc.getBlockY(), bedLoc.getBlockZ())
                    : "Невідомо";

            long hoursPlayed = context.playerData().getPlayTimeHours(context.getCasterId());
            String mainHand = context.playerData().getMainHandItemName(context.getCasterId());
            int playerKills = context.playerData().getPlayerKills(context.getCasterId());

            String distanceText = "Невідомо";
            Location casterLoc = context.getCasterLocation();
            if (knownLocation != null && casterLoc != null && knownLocation.getWorld().equals(casterLoc.getWorld())) {
                distanceText = (int)Math.round(casterLoc.distance(knownLocation)) + "м";
            }

            List<String> lore = Arrays.asList(
                    ChatColor.GRAY + "Відстань: " + ChatColor.WHITE + distanceText,
                    ChatColor.GRAY + "Світ: " + ChatColor.WHITE + (bedLoc != null ? bedLoc.getWorld().getName() : (knownLocation != null ? knownLocation.getWorld().getName() : "Невідомо")),
                    ChatColor.GRAY + "Дім: " + ChatColor.WHITE + bedInfo,
                    ChatColor.GRAY + "Годин гри: " + ChatColor.WHITE + hoursPlayed,
                    ChatColor.GRAY + "Озброєння: " + ChatColor.WHITE + mainHand,
                    ChatColor.GRAY + "Вбивств: " + ChatColor.WHITE + playerKills,
                    "",
                    ChatColor.DARK_PURPLE + "✦ Клацніть щоб увійти у сон"
            );

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Почати послідовність телепортації з затримкою.
     * Тепер передаємо UUID та відоме ім'я (щоб не звертатись за Player об'єктами пізніше).
     */
    private void startTeleportSequence(IAbilityContext context, UUID targetId, String targetName, boolean isExecutedFromMenu) {
        Beyonder casterBeyonder = context.getCasterBeyonder();
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.messaging().sendMessage(context.getCasterId(), ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        if (isExecutedFromMenu) {
            context.events().publishAbilityUsedEvent(this, casterBeyonder);
        }
        // Закриваємо меню у кастера
        Player caster = context.getCasterPlayer();
        caster.closeInventory();

        context.messaging().sendMessage(context.getCasterId(), ChatColor.DARK_PURPLE + "✦ Ви проникаєте у сон " + ChatColor.WHITE + targetName + ChatColor.DARK_PURPLE + "...");
        context.messaging().sendMessage(targetId, "" + ChatColor.GRAY + ChatColor.ITALIC + "У вашому сні з'являється невідома присутність...");

        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 0.8f);
        context.effects().spawnParticle(Particle.PORTAL, context.getCasterLocation().add(0, 1, 0), 50, 0.5, 1, 0.5);

        showCountdown(context, TELEPORT_DELAY_SECONDS);

        context.scheduling().scheduleDelayed(() -> {
            // Під час виконання ми НЕ викликаємо context.getPlayer(...)
            // Натомість знаходимо кращу доступну локацію через методи контексту
            Optional<Location> maybeTeleportLoc = resolveTeleportLocation(context, targetId);

            if (maybeTeleportLoc.isEmpty()) {
                context.messaging().sendMessage(context.getCasterId(), ChatColor.RED + "✗ Не вдалося знайти придатну локацію для телепортації (ціль недоступна).");
                context.effects().playSoundForPlayer(context.getCasterId(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }

            Location teleportLoc = maybeTeleportLoc.get();

            // Ефекти перед телепортацією (у кастера)
            context.effects().spawnParticle(Particle.PORTAL, context.getCasterLocation().add(0, 1, 0), 100, 0.5, 1, 0.5);
            context.effects().playSound(context.getCasterLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);

            // Додаємо невеликий випадковий офсет поруч із ціллю
            teleportLoc = teleportLoc.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2);

            context.entity().teleport(context.getCasterId(), teleportLoc);

            // Ефекти після телепортації
            context.effects().spawnParticle(Particle.PORTAL, teleportLoc.clone().add(0, 1, 0), 100, 0.5, 1, 0.5);
            context.effects().spawnParticle(Particle.SOUL_FIRE_FLAME, teleportLoc, 30, 0.5, 0.5, 0.5);
            context.effects().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);
            context.effects().playSound(teleportLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.5f);

            context.messaging().sendMessage(context.getCasterId(), ChatColor.GREEN + "✓ Ви проникли у світ снів " + targetName);
            context.messaging().sendMessage(targetId, ChatColor.DARK_PURPLE + "✦ У ваш сон хтось увійшов...");

            context.entity().applyPotionEffect(context.getCasterId(), PotionEffectType.BLINDNESS, 20, 0);

        }, TELEPORT_DELAY_SECONDS * 20L);
    }

    /**
     * Шукає найкращу доступну локацію для телепортації для даного targetId.
     * Порядок:
     * 1) bed spawn
     * 2) last death location
     * 3) якщо ні — пошук серед nearby players в великому радіусі (щоб отримати current Player.location)
     */
    private Optional<Location> resolveTeleportLocation(IAbilityContext context, UUID targetId) {
        // 1) Bed spawn
        Location bed = context.playerData().getBedSpawnLocation(targetId);
        if (bed != null) return Optional.of(bed);

        // 2) Last death
        Location lastDeath = context.playerData().getLastDeathLocation(targetId);
        if (lastDeath != null) return Optional.of(lastDeath);

        // 3) Пошук серед nearby players (великий радіус) — тільки якщо гравець близько до кастера
        List<Player> nearby = context.targeting().getNearbyPlayers(3000);
        for (Player p : nearby) {
            if (p.getUniqueId().equals(targetId)) {
                return Optional.of(p.getLocation());
            }
        }

        // 4) Нічого не знайдено
        return Optional.empty();
    }

    private void showCountdown(IAbilityContext context, int seconds) {
        for (int i = 1; i <= seconds; i++) {
            final int currentSecond = i;
            final int remaining = seconds - i + 1;

            context.scheduling().scheduleDelayed(() -> {
                float pitch = 0.8f + (currentSecond * 0.2f);
                context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, pitch);

                int particleCount = 10 + (currentSecond * 5);
                context.effects().spawnParticle(Particle.SOUL, context.getCasterLocation().add(0, 1, 0), particleCount, 0.3, 0.5, 0.3);

                if (remaining <= 3) {
                    context.messaging().sendMessage(context.getCasterId(), ChatColor.DARK_PURPLE + "✦ " + ChatColor.WHITE + remaining + "...");
                }

            }, currentSecond * 20L);
        }
    }
}
