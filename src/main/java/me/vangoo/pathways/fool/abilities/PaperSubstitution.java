package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.DollBatch;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sequence 7: Magician — Paper Figurine Substitution (Заміна паперовою лялькою).
 *
 * <p>Реворк: каст створює партію паперових ляльок ({@link DollBatch}), списуючи
 * папір; ляльки накопичуються й зберігаються. Shift+ПКМ — тогл «захисту»: поки
 * увімкнено та є ляльки, черговий важкий удар поглинає одна лялька, а гравець
 * телепортується навмання поблизу. Вимкнув — ляльки не витрачаються.
 *
 * <p>Поглинання удару керує глобальний {@code FoolCombatListener} (надійніше за
 * event-підписку). Стан — інстанс-поля (екземпляр спільний для pathway).
 */
public class PaperSubstitution extends ActiveAbility {

    private static final int BASE_COST = 60;
    private static final int BASE_COOLDOWN = 8;

    private final Map<UUID, Integer> dollCount = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> protecting = ConcurrentHashMap.newKeySet();
    // Підписка на поглинання удару під ВЛАСНИМ ключем (живе, поки ввімкнено захист).
    private final Map<UUID, UUID> absorbSubscriptions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Заміна паперовою лялькою";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "ПКМ — скласти партію з " + DollBatch.dollsPerCast(userSequence) + " паперових ляльок (" +
                DollBatch.paperCost(userSequence) + " паперу, накопичуються до " +
                DollBatch.maxStored(userSequence) + "). Shift+ПКМ — увімкнути/вимкнути захист: " +
                "важкий удар поглинає лялька, і ви телепортуєтесь навмання поблизу.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Shift+ПКМ — безкоштовний тогл захисту (deferred → без витрат і кулдауну).
        if (context.playerData().isSneaking(casterId)) {
            toggleProtection(context, casterId);
            return AbilityResult.deferred();
        }

        // Звичайний каст — скласти партію ляльок (коштує духовність + кулдаун).
        return craftBatch(context, casterId);
    }

    private void toggleProtection(IAbilityContext context, UUID casterId) {
        if (protecting.remove(casterId)) {
            stopAbsorb(context, casterId);
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("📜 Захист вимкнено", NamedTextColor.GRAY));
            context.effects().playSoundForPlayer(casterId, Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.4f);
            return;
        }
        if (dollCount.getOrDefault(casterId, 0) <= 0) {
            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("📜 Немає ляльок — спершу складіть партію", NamedTextColor.RED));
            return;
        }
        protecting.add(casterId);
        startAbsorb(context, casterId);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("📜 Захист увімкнено (" + dollCount.get(casterId) + " ляльок)", NamedTextColor.GOLD));
        context.effects().playSoundForPlayer(casterId, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.5f);
    }

    /** Підписка на важкий удар по кастеру (власний ключ) — поки ввімкнено захист. */
    private void startAbsorb(IAbilityContext context, UUID casterId) {
        stopAbsorb(context, casterId); // не дублюємо
        UUID subKey = UUID.randomUUID();
        absorbSubscriptions.put(casterId, subKey);
        context.events().subscribeToTemporaryEvent(subKey,
                org.bukkit.event.entity.EntityDamageEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId)
                        && e.getFinalDamage() >= DollBatch.DAMAGE_THRESHOLD,
                e -> {
                    if (absorb(context, casterId)) e.setCancelled(true);
                },
                Integer.MAX_VALUE
        );
    }

    private void stopAbsorb(IAbilityContext context, UUID casterId) {
        UUID subKey = absorbSubscriptions.remove(casterId);
        if (subKey != null) context.events().unsubscribeAll(subKey);
    }

    private AbilityResult craftBatch(IAbilityContext context, UUID casterId) {
        Player player = context.getCasterPlayer();
        if (player == null) return AbilityResult.failure("Гравець недоступний");

        Sequence seq = context.getCasterBeyonder().getSequence();
        int max = DollBatch.maxStored(seq);
        int current = dollCount.getOrDefault(casterId, 0);
        if (current >= max) {
            return AbilityResult.failure("Ви вже накопичили максимум ляльок (" + max + ")");
        }

        int paperCost = DollBatch.paperCost(seq);
        if (countPaper(player) < paperCost) {
            return AbilityResult.failure("Потрібно " + paperCost + " паперу в інвентарі");
        }

        int batch = DollBatch.dollsPerCast(seq);
        int added = Math.min(batch, max - current);
        // Списуємо папір пропорційно доданим лялькам (не переповнюємо).
        int consume = added * DollBatch.PAPER_PER_DOLL;
        removePaper(player, consume);
        dollCount.put(casterId, current + added);

        // Цікавий процес складання: партикли паперу кружляють навколо гравця.
        playCraftAnimation(context, casterId);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("📜 Складено " + added + " ляльок (усього " + (current + added) + ")",
                        NamedTextColor.GOLD));
        return AbilityResult.success();
    }

    private void playCraftAnimation(IAbilityContext context, UUID casterId) {
        context.effects().playSoundForPlayer(casterId, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        for (int i = 0; i < 8; i++) {
            int tick = i;
            context.scheduling().scheduleDelayed(() -> {
                Location loc = context.playerData().getCurrentLocation(casterId);
                if (loc == null) return;
                double angle = tick * 0.8;
                double px = Math.cos(angle) * 0.8;
                double pz = Math.sin(angle) * 0.8;
                Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(240, 240, 240), 0.8f);
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.DUST,
                            loc.clone().add(px, 1.0 + tick * 0.1, pz), 2, 0.05, 0.05, 0.05, 0, white);
                }
            }, tick * 2L);
        }
    }

    /**
     * Поглинає важкий удар: списує ляльку, спавнить фігурку й телепортує гравця
     * навмання поблизу. Викликається з власної підписки {@link #startAbsorb}.
     *
     * @return true, якщо удар поглинуто (тоді підписка скасовує шкоду)
     */
    private boolean absorb(IAbilityContext context, UUID casterId) {
        int dolls = dollCount.getOrDefault(casterId, 0);
        if (!protecting.contains(casterId) || dolls <= 0) return false;
        Player player = context.getCasterPlayer();
        if (player == null) return false;

        dolls--;
        dollCount.put(casterId, dolls);

        Location origin = player.getLocation();
        spawnScatter(context, origin);

        Location escape = findRandomSafeNearby(origin, DollBatch.TELEPORT_RADIUS);
        if (escape != null) {
            escape.setYaw(origin.getYaw());
            escape.setPitch(origin.getPitch());
            player.teleport(escape);
            if (escape.getWorld() != null) {
                escape.getWorld().spawnParticle(Particle.END_ROD, escape.clone().add(0, 1, 0), 12, 0.3, 0.4, 0.3);
                escape.getWorld().playSound(escape, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
            }
        }

        player.sendActionBar(Component.text("📜 Лялька прийняла удар! (лишилось " + dolls + ")",
                NamedTextColor.GREEN));
        if (dolls <= 0) {
            protecting.remove(casterId);
            stopAbsorb(context, casterId);
            player.sendActionBar(Component.text("📜 Ляльки скінчились — захист знято", NamedTextColor.GRAY));
        }
        return true;
    }

    /**
     * На місці, звідки гравця «висмикнуло», лишаються тільки білі партикли, що розсипаються
     * донизу — жодної стойки чи предмета паперу. Кілька хвиль, щоб виглядало як розпад ляльки.
     */
    private void spawnScatter(IAbilityContext context, Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        world.playSound(origin, Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 0.8f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(245, 245, 245), 1.1f);
        for (int wave = 0; wave < 5; wave++) {
            int w = wave;
            context.scheduling().scheduleDelayed(() -> {
                if (origin.getWorld() == null) return;
                Location at = origin.clone().add(0, 1.4 - w * 0.25, 0);
                origin.getWorld().spawnParticle(Particle.DUST, at, 18, 0.35, 0.15, 0.35, 0, white);
                origin.getWorld().spawnParticle(Particle.CLOUD, at, 6, 0.3, 0.1, 0.3, 0.01);
            }, w * 2L);
        }
    }

    private Location findRandomSafeNearby(Location origin, int radius) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double dx = ThreadLocalRandom.current().nextDouble(-radius, radius);
            double dz = ThreadLocalRandom.current().nextDouble(-radius, radius);
            Location check = origin.clone().add(dx, 0, dz);
            if (isSafe(check)) return check;
            Location up = check.clone().add(0, 1, 0);
            if (isSafe(up)) return up;
        }
        return null;
    }

    private boolean isSafe(Location loc) {
        if (loc.getWorld() == null) return false;
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && ground.getType().isSolid()
                && feet.getType() != Material.LAVA;
    }

    private int countPaper(Player player) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == Material.PAPER) total += it.getAmount();
        }
        return total;
    }

    private void removePaper(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != Material.PAPER) continue;
            int take = Math.min(it.getAmount(), remaining);
            it.setAmount(it.getAmount() - take);
            remaining -= take;
        }
    }

    @Override
    public void cleanUp() {
        dollCount.clear();
        protecting.clear();
        absorbSubscriptions.clear();
    }
}
