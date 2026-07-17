package me.vangoo.pathways.common.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.rituals.RitualCatalog;
import me.vangoo.domain.rituals.RitualRecipe;
import me.vangoo.domain.rituals.RitualType;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Candle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Посл. 9 (Fool / Door / WhiteTower): Ритуальна магія.
 * Фізичний вівтар: запалені свічки в радіусі 3 бл. Вибір ритуалу — меню;
 * хід — RitualSession (заклинання по літерах); ефект — RitualEffectRunner.
 * Прогрес: Посл. 9 — 3 ритуали, 8 — 5, 7 — усі 7 (RitualCatalog).
 */
public class RitualMagic extends ActiveAbility {

    private static final int BASE_COST = 100;
    private static final int BASE_COOLDOWN = 60;
    private static final int ALTAR_RADIUS = 3;
    private static final int ABORT_SANITY_LOSS = 2;

    private final RitualEffectRunner runner = new RitualEffectRunner();
    private final Map<UUID, RitualSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Ритуальна магія";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int available = RitualCatalog.availableFor(userSequence.level()).size();
        return "Знання ритуальної магії: вівтар зі свічок, заклинання й прохання до " +
                "прихованих сутностей. Доступно ритуалів: " + available + " із 7. " +
                "\n§7§oПоставте й запаліть свічки поруч, тоді кастуйте.";
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
        Location altar = context.getCasterLocation();
        int litCandles = countLitCandles(altar);

        if (litCandles < 3) {
            return AbilityResult.failure("Вівтар не готовий: потрібно щонайменше 3 запалені свічки в радіусі "
                    + ALTAR_RADIUS + " бл (зараз: " + litCandles + ")");
        }

        int level = context.getCasterBeyonder().getSequenceLevel();
        List<RitualRecipe> available = RitualCatalog.availableFor(level).stream()
                .filter(r -> litCandles >= r.candlesRequired())
                .toList();

        if (available.isEmpty()) {
            return AbilityResult.failure("Замало свічок для доступних ритуалів");
        }

        context.ui().openChoiceMenu("Ритуальна магія", available,
                this::createRitualItem,
                recipe -> startRitual(context, altar, recipe));
        return AbilityResult.deferred();
    }

    private ItemStack createRitualItem(RitualRecipe recipe) {
        ItemStack item = new ItemStack(iconFor(recipe.type()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + recipe.displayName());
            meta.setLore(List.of(
                    ChatColor.GRAY + recipe.description(),
                    ChatColor.DARK_GRAY + "Свічок: " + recipe.candlesRequired()
                            + (recipe.ingredients().isEmpty() ? "" : " • Інгредієнти: " + ingredientsLine(recipe))
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String ingredientsLine(RitualRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        recipe.ingredients().forEach((mat, count) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(count).append("x ").append(ukName(mat));
        });
        return sb.toString();
    }

    // Українські назви інгредієнтів ритуалів (для меню й повідомлень).
    private static final Map<String, String> INGREDIENT_NAMES_UK = Map.of(
            "GOLD_INGOT", "золотий зливок",
            "GOLD_NUGGET", "золотий самородок",
            "IRON_INGOT", "залізний зливок",
            "NETHERITE_SCRAP", "незеритовий уламок",
            "DIAMOND", "алмаз",
            "BONE", "кістка",
            "AMETHYST_SHARD", "уламок аметисту",
            "LAPIS_LAZULI", "ляпіс-лазур"
    );

    private static String ukName(String materialName) {
        return INGREDIENT_NAMES_UK.getOrDefault(materialName,
                materialName.toLowerCase().replace('_', ' '));
    }

    private Material iconFor(RitualType type) {
        return switch (type) {
            case LUCK_PRAYER -> Material.RABBIT_FOOT;
            case SANCTIFICATION -> Material.ANVIL;
            case SACRIFICE -> Material.FLINT_AND_STEEL;
            case BESTOWMENT -> Material.NETHERITE_SCRAP;
            case MEDIUMSHIP -> Material.BONE;
            case MIRROR_DIVINATION -> Material.AMETHYST_SHARD;
            case SPIRIT_WALL -> Material.LAPIS_LAZULI;
        };
    }

    private void startRitual(IAbilityContext context, Location altar, RitualRecipe recipe) {
        UUID casterId = context.getCasterId();
        Beyonder beyonder = context.getCasterBeyonder();
        Player player = context.getCasterPlayer();
        if (player == null) return;
        player.closeInventory();

        // М'яка перевірка інгредієнтів ДО списання духовності (щоб не витрачати її дарма).
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            Material mat = Material.valueOf(entry.getKey());
            if (!player.getInventory().containsAtLeast(new ItemStack(mat), entry.getValue())) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Бракує інгредієнтів: "
                        + entry.getValue() + "x " + ukName(entry.getKey()));
                return;
            }
        }

        // Духовність — плата за сам обряд (списується на старті разом із кулдауном).
        // Інгредієнти й жертву НЕ чіпаємо тут: їх спишемо лише по завершенні заклинання,
        // щоб гравець устиг узяти потрібне в головну руку під час читання.
        if (!AbilityResourceConsumer.consumeResources(this, beyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, beyonder);

        if (recipe.requiresHandSacrifice()) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY
                    + "Візьміть жертву в головну руку до кінця заклинання.");
        }

        // Повторний каст замінює сесію власника.
        RitualSession previous = activeSessions.remove(casterId);
        if (previous != null) previous.cancel();

        RitualSession session = new RitualSession(casterId, altar,
                RitualIncantations.linesFor(recipe.type()),
                () -> {
                    activeSessions.remove(casterId);
                    completeRitual(context, altar, recipe);
                },
                reason -> {
                    activeSessions.remove(casterId);
                    applyBacklash(context, recipe, reason);
                });
        BukkitTask task = context.scheduling().scheduleRepeating(session::tick, 0L, 1L);
        session.bindTask(task);
        activeSessions.put(casterId, session);
    }

    /**
     * Списання інгредієнтів / жертви й запуск ефекту — ЛИШЕ по завершенні заклинання.
     * Так предмет із головної руки не «згорає» в мить кліку по меню.
     */
    private void completeRitual(IAbilityContext context, Location altar, RitualRecipe recipe) {
        UUID casterId = context.getCasterId();
        Player player = context.getCasterPlayer();
        if (player == null) return;

        // Інгредієнти могли зникнути під час заклинання — перевіряємо ще раз.
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            Material mat = Material.valueOf(entry.getKey());
            if (!player.getInventory().containsAtLeast(new ItemStack(mat), entry.getValue())) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Обряд згас: бракувало інгредієнтів ("
                        + entry.getValue() + "x " + ukName(entry.getKey()) + ").");
                return;
            }
        }

        ItemStack sacrificed = null;
        if (recipe.requiresHandSacrifice()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                context.messaging().sendMessage(casterId, ChatColor.RED
                        + "Обряд згас: у головній руці не було жертви.");
                return;
            }
            sacrificed = hand.clone();
            sacrificed.setAmount(1);
        }

        // Списуємо інгредієнти / жертву САМЕ зараз.
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            player.getInventory().removeItem(new ItemStack(Material.valueOf(entry.getKey()), entry.getValue()));
        }
        if (recipe.requiresHandSacrifice()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
            }
        }

        runner.run(recipe, context, altar, sacrificed);
    }

    private void applyBacklash(IAbilityContext context, RitualRecipe recipe, String reason) {
        UUID casterId = context.getCasterId();
        context.getCasterBeyonder().increaseSanityLoss(ABORT_SANITY_LOSS);
        if (recipe.type() == RitualType.LUCK_PRAYER) {
            context.entity().applyPotionEffect(casterId,
                    org.bukkit.potion.PotionEffectType.UNLUCK, 2400, 0);
        }
        context.messaging().sendMessage(casterId, ChatColor.RED + "✗ Ритуал зірвано (" + reason
                + ") — відкат вдарив по розуму.");
    }

    private int countLitCandles(Location center) {
        World world = center.getWorld();
        if (world == null) return 0;
        int count = 0;
        for (int x = -ALTAR_RADIUS; x <= ALTAR_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -ALTAR_RADIUS; z <= ALTAR_RADIUS; z++) {
                    var data = world.getBlockAt(center.getBlockX() + x,
                            center.getBlockY() + y, center.getBlockZ() + z).getBlockData();
                    if (data instanceof Candle candle && candle.isLit()) count++;
                }
            }
        }
        return count;
    }

    @Override
    public void cleanUp() {
        activeSessions.values().forEach(RitualSession::cancel);
        activeSessions.clear();
    }
}
