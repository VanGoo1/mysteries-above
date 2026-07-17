package me.vangoo.pathways.common.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.rituals.RitualEffectMath;
import me.vangoo.domain.rituals.RitualRecipe;
import me.vangoo.domain.rituals.SacrificeAppraiser;
import me.vangoo.domain.rituals.SacrificeKind;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Stateless-хореографія ефектів ритуалів (запускається після успішного заклинання).
 * Балансові базові числа — у {@link RitualEffectMath} (domain); тут лише скейл через
 * SequenceScaler і Bukkit-ефекти.
 */
public class RitualEffectRunner {

    private static final double WALL_RADIUS = 6.0;

    private final Random rng = new Random();

    public void run(RitualRecipe recipe, IAbilityContext context, Location altarCenter, ItemStack sacrificedItem) {
        switch (recipe.type()) {
            case LUCK_PRAYER -> runLuck(context);
            case SANCTIFICATION -> runSanctify(context);
            case SACRIFICE -> runSacrifice(context, sacrificedItem);
            case BESTOWMENT -> runBestowment(context);
            case MEDIUMSHIP -> runMediumship(context, altarCenter);
            case MIRROR_DIVINATION -> runMirror(context, altarCenter);
            case SPIRIT_WALL -> runSpiritWall(context, altarCenter);
        }
    }

    private void runLuck(IAbilityContext context) {
        Beyonder b = context.getCasterBeyonder();
        int duration = scale(RitualEffectMath.LUCK_BASE_TICKS, b.getSequence());
        context.entity().applyPotionEffect(context.getCasterId(), PotionEffectType.LUCK, duration, 0);
        context.messaging().sendMessage(context.getCasterId(),
                ChatColor.GREEN + "✦ Сутності почули вас: удача на " + (duration / 1200) + " хв.");
    }

    private void runSanctify(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || !(hand.getItemMeta() instanceof Damageable meta) || meta.getDamage() == 0) {
            context.messaging().sendMessage(context.getCasterId(),
                    ChatColor.YELLOW + "✦ Предмет у руці не потребує освячення.");
            return;
        }
        int repair = scale(RitualEffectMath.SANCTIFY_BASE_DURABILITY, context.getCasterBeyonder().getSequence());
        meta.setDamage(Math.max(0, meta.getDamage() - repair));
        hand.setItemMeta(meta);
        context.messaging().sendMessage(context.getCasterId(),
                ChatColor.GREEN + "✦ Предмет освячено: відновлено до " + repair + " міцності.");
    }

    private void runSacrifice(IAbilityContext context, ItemStack sacrificed) {
        if (sacrificed == null || sacrificed.getType() == Material.AIR) return;
        Beyonder b = context.getCasterBeyonder();
        SacrificeKind kind = classifySacrifice(context, sacrificed);
        int restored = scale(SacrificeAppraiser.spiritualityFor(kind), b.getSequence());
        Spirituality sp = b.getSpirituality();
        b.setSpirituality(sp.increment(Math.min(restored, sp.maximum() - sp.current())));
        context.messaging().sendMessage(context.getCasterId(),
                ChatColor.GREEN + "✦ Жертву прийнято: +" + restored + " духовності.");
    }

    public SacrificeKind classifySacrifice(IAbilityContext context, ItemStack item) {
        Beyonder b = context.getCasterBeyonder();
        for (int seq = 9; seq >= 0; seq--) {
            List<ItemStack> ingredients =
                    context.beyonder().getIngredientsForPotion(b.getPathway(), Sequence.of(seq));
            if (ingredients == null) continue;
            for (ItemStack ingredient : ingredients) {
                if (ingredient != null && ingredient.isSimilar(item)) {
                    return SacrificeKind.PATHWAY_INGREDIENT;
                }
            }
        }
        Material m = item.getType();
        if (m == Material.DIAMOND || m == Material.EMERALD
                || m == Material.NETHERITE_INGOT || m == Material.NETHERITE_SCRAP) {
            return SacrificeKind.PRECIOUS;
        }
        if (m == Material.GOLD_INGOT || m == Material.IRON_INGOT
                || m == Material.GOLD_BLOCK || m == Material.IRON_BLOCK
                || m == Material.AMETHYST_SHARD) {
            return SacrificeKind.VALUABLE;
        }
        return SacrificeKind.TRIFLE;
    }

    private void runBestowment(IAbilityContext context) {
        Beyonder b = context.getCasterBeyonder();
        int level = b.getSequenceLevel();
        UUID casterId = context.getCasterId();
        if (level == 0) {
            context.messaging().sendMessage(casterId, ChatColor.YELLOW + "✦ Ви вже на вершині шляху.");
            return;
        }
        List<ItemStack> ingredients =
                context.beyonder().getIngredientsForPotion(b.getPathway(), Sequence.of(level - 1));
        if (ingredients == null || ingredients.isEmpty()) {
            context.messaging().sendMessage(casterId, ChatColor.YELLOW + "✦ Сутності мовчать — дарунку немає.");
            return;
        }
        double chance = RitualEffectMath.bestowmentChance(level);
        if (rng.nextDouble() >= chance) {
            context.messaging().sendMessage(casterId, ChatColor.YELLOW + "✦ Сутності прийняли дар, але не відповіли.");
            return;
        }
        ItemStack gift = ingredients.get(rng.nextInt(ingredients.size())).clone();
        gift.setAmount(1);
        Player player = context.getCasterPlayer();
        player.getInventory().addItem(gift)
                .values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        context.messaging().sendMessage(casterId, ChatColor.GREEN + "✦ Брама відчинилась — ви отримали дарунок!");
    }

    private void runMediumship(IAbilityContext context, Location altar) {
        printEvents(context, altar, 15, 8, "🕯 ГОЛОСИ ДУХІВ");
    }

    private void runMirror(IAbilityContext context, Location altar) {
        printEvents(context, altar, 10, 10, "🪞 ДЗЕРКАЛО МИНУЛОГО");
    }

    private void printEvents(IAbilityContext context, Location altar, int radius, int limit, String header) {
        UUID casterId = context.getCasterId();
        int window = scale(RitualEffectMath.EVENTS_BASE_WINDOW_SECONDS, context.getCasterBeyonder().getSequence());
        List<RecordedEvent> events = new ArrayList<>(context.events().getPastEvents(altar, radius, window));
        events.sort(Comparator.comparingLong(RecordedEvent::getTimestamp).reversed());

        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
        context.messaging().sendMessage(casterId, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + header);
        if (events.isEmpty()) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "Це місце мовчить — слідів не лишилось.");
        }
        long now = System.currentTimeMillis();
        for (RecordedEvent e : events.subList(0, Math.min(limit, events.size()))) {
            long minutesAgo = Math.max(0, (now - e.getTimestamp()) / 60000);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "• " + minutesAgo + " хв тому — "
                    + ChatColor.AQUA + e.getDescription());
        }
        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
    }

    private void runSpiritWall(IAbilityContext context, Location altar) {
        UUID casterId = context.getCasterId();
        int duration = scale(RitualEffectMath.WALL_BASE_TICKS, context.getCasterBeyonder().getSequence());
        context.messaging().sendMessage(casterId,
                ChatColor.AQUA + "✦ Стіна духовності постала на " + (duration / 20) + " с.");

        final int[] elapsed = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            World world = altar.getWorld();
            elapsed[0] += 10;
            if (world == null || elapsed[0] >= duration) {
                holder[0].cancel();
                return;
            }
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24;
                world.spawnParticle(Particle.ENCHANT,
                        altar.clone().add(Math.cos(angle) * WALL_RADIUS, 1.0, Math.sin(angle) * WALL_RADIUS),
                        1, 0, 0.4, 0);
            }
            for (Entity e : world.getNearbyEntities(altar, WALL_RADIUS, 4, WALL_RADIUS)) {
                if (e instanceof Monster monster) {
                    Vector away = monster.getLocation().toVector().subtract(altar.toVector()).setY(0);
                    if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                    monster.setVelocity(away.normalize().multiply(0.6).setY(0.2));
                }
            }
            Player caster = Bukkit.getPlayer(casterId);
            if (caster != null && caster.getWorld().equals(world)
                    && caster.getLocation().distance(altar) <= WALL_RADIUS) {
                caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, false));
            }
        }, 0L, 10L);
    }

    private int scale(int base, Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.MODERATE);
        return (int) Math.ceil(base * multiplier);
    }
}
