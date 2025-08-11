package me.vangoo.implementation.VisionaryPathway.abilities;

import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class ScanGaze extends Ability {
    private static final int RANGE = 5;

    // Запам'ятовуємо ціль, по якій гравець клікнув, щоб execute знав кого сканувати
    private final Map<UUID, LivingEntity> pendingTargets = new ConcurrentHashMap<>();

    public void setTargetFor(UUID casterId, LivingEntity target) {
        pendingTargets.put(casterId, target);
    }

    @Override
    public String getName() {
        return "Сканування поглядом";
    }

    @Override
    public String getDescription() {
        return "При натисканні на гравця показує його HP, голод та броню.";
    }

    @Override
    public int getSpiritualityCost() {
        return 5;
    }

    @Override
    public boolean execute(Player caster, Beyonder beyonder) {
        LivingEntity target = pendingTargets.remove(caster.getUniqueId());
        if (target == null) {
            // Fallback: спробувати знайти ціль по прицілу в радіусі 5 блоків
            target = getLookTarget(caster, RANGE);
        }

        if (!(target instanceof Player)) {
            caster.sendMessage(ChatColor.RED + "Немає цілі: клікніть по гравцю або наведіться ближче.");
            return false;
        }

        Player p = (Player) target;
        double hp = Math.round(p.getHealth() * 10.0) / 10.0;
        double maxHp = 20.0;
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHp = Math.round(p.getAttribute(Attribute.MAX_HEALTH).getValue() * 10.0) / 10.0;
        }
        int hunger = p.getFoodLevel();
        double armor = 0.0;
        if (p.getAttribute(Attribute.ARMOR) != null) {
            armor = Math.round(p.getAttribute(Attribute.ARMOR).getValue());
        }

        caster.sendMessage(ChatColor.AQUA + "Сканування " + ChatColor.WHITE + p.getName());
        caster.sendMessage(ChatColor.GRAY + "Здоров'я: " + ChatColor.YELLOW + hp + ChatColor.GRAY + " / " + ChatColor.YELLOW + maxHp);
        caster.sendMessage(ChatColor.GRAY + "Голод: " + ChatColor.YELLOW + hunger + ChatColor.GRAY + " / " + ChatColor.YELLOW + "20");
        caster.sendMessage(ChatColor.GRAY + "Броня: " + ChatColor.YELLOW + (int) armor + ChatColor.GRAY + " / " + ChatColor.YELLOW + "20");
        return true;
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + getName());
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "клік по гравцю для сканування",
                    ChatColor.GRAY + "Вартість: " + ChatColor.BLUE + "5",
                    ChatColor.GRAY + "Кулдаун: " + ChatColor.BLUE + "10с",
                    ChatColor.GRAY + "Радіус: " + ChatColor.BLUE + RANGE
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public int getCooldown() {
        return 200;
    }

    private LivingEntity getLookTarget(Player player, double range) {
        RayTraceResult res = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                range,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );
        if (res != null && res.getHitEntity() instanceof LivingEntity) {
            return (LivingEntity) res.getHitEntity();
        }

        return null;
    }

}
