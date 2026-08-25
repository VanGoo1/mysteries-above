package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.PrometheusTheft;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SwindlerInfluence;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.infrastructure.items.CustomItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Посл. 8: «Крадіжка матеріалів» — духовний матеріал сам іде до руки крізь стіну.
 *
 * <p>На відміну від {@link ShadowTheft}: без телепорту, без лінії зору й лише
 * інгредієнти плагіну — один предмет за каст, з кишень поруч або зі скринь за стіною.
 *
 * <p>Прометеїв тір (Посл. 6) розширює цю саму здібність: 50 блоків замість 5,
 * будь-який предмет замість самих лише інгредієнтів і другий режим — ПІДКИНУТИ.
 * Нового класу немає — тір вмикається за Послідовністю власника.
 */
public class MaterialTheft extends ActiveAbility {

    /** Послідовність, з якої вмикається Прометеїв тір. */
    private static final int PROMETHEUS_TIER = 6;

    /** Режим касту; перемикається сходом (лише на Прометеї). */
    private enum Mode {
        TAKE("ВЗЯТИ"), PLANT("ПІДКИНУТИ");

        final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        Mode next() {
            return this == TAKE ? PLANT : TAKE;
        }
    }

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Крадіжка матеріалів";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        if (userSequence.level() <= PROMETHEUS_TIER) {
            return "Притягує до руки будь-який предмет з відстані "
                    + (int) PrometheusTheft.MATERIAL_THEFT_RANGE
                    + " блоків — з чужих кишень або зі скрині, навіть крізь стіну.\n"
                    + "§7Shift + ПКМ: Переключити режим (ВЗЯТИ / ПІДКИНУТИ)\n"
                    + "§7ПІДКИНУТИ: предмет із другої руки тихо лягає в інвентар цілі, на яку ви дивитесь.\n";
        }
        return "Притягує один духовний матеріал з відстані " + (int) SwindlerInfluence.MATERIAL_THEFT_RANGE
                + " блоків — з чужих кишень або зі скрині, навіть крізь стіну. ";
    }

    @Override
    public int getSpiritualityCost() {
        return SwindlerInfluence.MATERIAL_THEFT_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return SwindlerInfluence.materialTheftCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCasterPlayer();
        Beyonder beyonder = context.getCasterBeyonder();
        boolean prometheus = beyonder != null && beyonder.getSequenceLevel() <= PROMETHEUS_TIER;
        double range = prometheus ? PrometheusTheft.MATERIAL_THEFT_RANGE : SwindlerInfluence.MATERIAL_THEFT_RANGE;

        if (prometheus) {
            if (context.playerData().isSneaking(casterId)) return switchMode(context, casterId);
            if (modes.get(casterId) == Mode.PLANT) return plant(context, caster, range);

            AbilityResult fire = stealFire(context, caster, range);
            if (fire != null) return fire;
        }

        Loot loot = findInPockets(context, casterId, range, prometheus);
        if (loot == null) loot = findInContainers(caster.getLocation(), range, prometheus);
        if (loot == null) {
            return AbilityResult.failure(prometheus
                    ? "✖ Поряд немає жодного предмета, який можна вкрасти"
                    : "✖ Поряд немає жодного духовного матеріалу");
        }

        ItemStack stolen = loot.take();

        context.effects().playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        if (loot.victimId() != null) {
            context.effects().playSoundForPlayer(loot.victimId(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.5f);
        }

        Location hand = caster.getLocation().clone().add(0, 1.2, 0);
        context.effects().playTravelingBeam(loot.source(), hand,
                PathwayBranding.liquidOf("Error"),
                () -> {
                    context.entity().giveItem(casterId, stolen);
                    context.effects().playGlowingDust(hand, PathwayBranding.liquidOf("Error"));
                    context.effects().playSound(hand, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                });

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✋ Матеріал іде до вашої руки").color(NamedTextColor.LIGHT_PURPLE));
        return AbilityResult.success();
    }

    private AbilityResult switchMode(IAbilityContext context, UUID casterId) {
        Mode newMode = modes.getOrDefault(casterId, Mode.TAKE).next();
        modes.put(casterId, newMode);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✋ Режим крадіжки: " + newMode.displayName).color(NamedTextColor.LIGHT_PURPLE));
        return AbilityResult.deferred();
    }

    /**
     * Режим ПІДКИНУТИ: предмет із руки тихо перекочовує в інвентар цілі, на яку
     * дивиться кастер. Жертва не отримує ні звуку, ні повідомлення.
     */
    private AbilityResult plant(IAbilityContext context, Player caster, double range) {
        // Каст іде з предмета здібності в руці, тож підкидаємо з ІНШОЇ руки.
        ItemStack main = caster.getInventory().getItemInMainHand();
        boolean fromOffHand = AbilityItemFactory.isAbilityItem(main);
        ItemStack inHand = fromOffHand ? caster.getInventory().getItemInOffHand() : main;
        if (inHand.getType().isAir()) {
            return AbilityResult.failure("✖ Візьміть предмет у другу руку");
        }
        if (ShadowTheft.isProtected(inHand)) return AbilityResult.failure("✖ Цей предмет не підкинути");

        HumanEntity target = context.targeting().getTargetedEntity(range)
                .filter(entity -> entity instanceof HumanEntity)
                .map(entity -> (HumanEntity) entity)
                .orElse(null);
        if (target == null) return AbilityResult.failure("✖ Наведіться на ціль з інвентарем");

        ItemStack planted = inHand.clone();
        int leftover = target.getInventory().addItem(planted).values().stream()
                .mapToInt(ItemStack::getAmount).sum();
        int placed = planted.getAmount() - leftover;
        if (placed <= 0) return AbilityResult.failure("✖ Інвентар цілі повний");
        inHand.setAmount(inHand.getAmount() - placed);

        Location hand = caster.getLocation().clone().add(0, 1.2, 0);
        context.effects().playTravelingBeam(hand, target.getLocation().add(0, 1.0, 0),
                PathwayBranding.liquidOf("Error"), null);
        context.effects().playSoundForPlayer(caster.getUniqueId(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 0.4f);
        context.messaging().sendMessageToActionBar(caster.getUniqueId(),
                Component.text("✋ Предмет тихо ліг у чужу кишеню").color(NamedTextColor.LIGHT_PURPLE));
        return AbilityResult.success();
    }

    /**
     * Вікі 3b.1: полум'я — теж власність. Гасить вогонь на істоті в радіусі або блок
     * полум'я поруч і віддає його кастеру вогняною кулею та короткою вогнетривкістю.
     *
     * <p>Має пріоритет над предметом: інакше на 50 блоках завжди знайдеться що вкрасти
     * і вогонь не дістався б ніколи.
     *
     * @return {@code null}, якщо горіти нема чому — тоді каст іде у звичайну крадіжку
     */
    private AbilityResult stealFire(IAbilityContext context, Player caster, double range) {
        Location source = snuffBurningEntity(context, caster, range);
        if (source == null) source = snuffFireBlock(caster.getLocation());
        if (source == null) return null;

        UUID casterId = caster.getUniqueId();
        Location hand = caster.getLocation().clone().add(0, 1.2, 0);
        context.effects().playHelixEffect(source, hand, Particle.FLAME, 20);
        context.effects().playGlowingDust(hand, PathwayBranding.liquidOf("Error"));
        context.effects().playSound(source, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
        context.effects().playSound(hand, Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.2f);

        context.entity().giveItem(casterId, new ItemStack(Material.FIRE_CHARGE));
        context.entity().applyPotionEffect(casterId, PotionEffectType.FIRE_RESISTANCE,
                PrometheusTheft.FIRE_THEFT_RESISTANCE_SECONDS * 20, 0);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("✋ Полум'я перейшло до вашої руки").color(NamedTextColor.GOLD));
        return AbilityResult.success();
    }

    /** Знімає вогонь із кастера або з першої палаючої істоти в радіусі. */
    private Location snuffBurningEntity(IAbilityContext context, Player caster, double range) {
        if (caster.getFireTicks() > 0) {
            caster.setFireTicks(0);
            return caster.getLocation().clone().add(0, 1.0, 0);
        }
        for (LivingEntity entity : context.targeting().getNearbyEntities(range)) {
            if (entity.getFireTicks() <= 0) continue;
            entity.setFireTicks(0);
            return entity.getLocation().clone().add(0, 1.0, 0);
        }
        return null;
    }

    /** Найближчий блок полум'я в радіусі: вогонь гасне, лава лишається — крадемо полум'я, не рельєф. */
    private Location snuffFireBlock(Location center) {
        int radius = (int) PrometheusTheft.FIRE_THEFT_RADIUS;
        double radiusSquared = radius * radius;
        Block origin = center.getBlock();
        Block nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = origin.getRelative(dx, dy, dz);
                    if (!isFire(block.getType())) continue;
                    double distance = block.getLocation().distanceSquared(center);
                    if (distance > radiusSquared || distance >= nearestDistance) continue;
                    nearestDistance = distance;
                    nearest = block;
                }
            }
        }

        if (nearest == null) return null;
        Location source = nearest.getLocation().add(0.5, 0.5, 0.5);
        if (nearest.getType() != Material.LAVA) nearest.setType(Material.AIR);
        return source;
    }

    private static boolean isFire(Material material) {
        return material == Material.FIRE || material == Material.SOUL_FIRE || material == Material.LAVA;
    }

    /** Кишені живих істот поруч — лінія зору не потрібна. */
    private Loot findInPockets(IAbilityContext context, UUID casterId, double range, boolean anyItem) {
        for (LivingEntity entity : context.targeting().getNearbyEntities(range)) {
            if (!(entity instanceof HumanEntity human) || human.getUniqueId().equals(casterId)) continue;
            Loot loot = pick(human.getInventory(), human.getLocation(), human.getUniqueId(), anyItem);
            if (loot != null) return loot;
        }
        return null;
    }

    /**
     * Скрині, бочки й інша тара в радіусі — крізь стіни.
     *
     * <p>Обхід по чанках, а не кубом: на 50 блоках куб — це ~10^6 блоків за каст
     * і зупинений тік сервера. Дальність усе одно міряємо чесно, по відстані.
     */
    private Loot findInContainers(Location center, double range, boolean anyItem) {
        World world = center.getWorld();
        if (world == null) return null;

        double rangeSquared = range * range;
        int chunkRange = (int) Math.ceil(range / 16.0);
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                if (!world.isChunkLoaded(centerChunkX + dx, centerChunkZ + dz)) continue;
                Chunk chunk = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                for (BlockState state : chunk.getTileEntities(
                        block -> block.getLocation().distanceSquared(center) <= rangeSquared, false)) {
                    if (!(state instanceof Container container)) continue;
                    Location source = state.getLocation().add(0.5, 0.5, 0.5);
                    Loot loot = pick(container.getInventory(), source, null, anyItem);
                    if (loot != null) return loot;
                }
            }
        }
        return null;
    }

    /**
     * Перший придатний предмет в інвентарі: на Прометеї — будь-який незахищений,
     * нижче — лише інгредієнт плагіну. Предмети здібностей не крадуться ніколи.
     */
    private Loot pick(Inventory inventory, Location source, UUID victimId, boolean anyItem) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) continue;
            boolean takeable = anyItem
                    ? !ShadowTheft.isProtected(item)
                    : CustomItemFactory.isCustomItem(item) && !AbilityItemFactory.isAbilityItem(item);
            if (takeable) {
                return new Loot(source, inventory, slot, victimId);
            }
        }
        return null;
    }

    private record Loot(Location source, Inventory inventory, int slot, UUID victimId) {
        /** Вилучає рівно один предмет зі слота джерела і повертає його. */
        ItemStack take() {
            ItemStack inSlot = inventory.getItem(slot);
            ItemStack stolen = inSlot.clone();
            stolen.setAmount(1);
            inSlot.setAmount(inSlot.getAmount() - 1);
            inventory.setItem(slot, inSlot.getAmount() > 0 ? inSlot : null);
            return stolen;
        }
    }
}
