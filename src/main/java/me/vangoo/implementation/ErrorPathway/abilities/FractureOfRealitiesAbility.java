package me.vangoo.implementation.ErrorPathway.abilities;

import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.WorldCreator;

import java.util.ArrayList;
import java.util.List;

public class FractureOfRealitiesAbility extends Ability {

    private static final int RADIUS = 8;
    private static final int DURATION_TICKS = 80; // 4 секунди

    @Override
    public String getName() {
        return "Fracture of Realities";
    }

    @Override
    public String getDescription() {
        return "Створює аномалію навколо";
    }

    @Override
    public int getSpiritualityCost() {
        return 70;
    }

    @Override
    public void execute(Player caster, Beyonder beyonder) {
        Location center = caster.getLocation().add(0, 1.2, 0);
        // ефект частинок
        Bukkit.getScheduler().runTaskAsynchronously(

                plugin,
                () -> {
                    for (int i = 0; i < DURATION_TICKS; i += 4) {
                        double angle = (i / 4) * Math.PI / 8;
                        double x = Math.cos(angle) * RADIUS;
                        double z = Math.sin(angle) * RADIUS;
                        center.getWorld().spawnParticle(
                                Particle.WHITE_ASH,
                                center.clone().add(x, 0, z),
                                10, 0.2, 0.2, 0.2, 0);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
        );

        // накладаємо ефекти на гравців у радіусі
        for (Entity ent : caster.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
            if (!(ent instanceof LivingEntity target) || target.equals(caster)) continue;
            // Confusion (сплінт)
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DURATION_TICKS, 1));
            // випадкове телепортування в межах радіусу
            double dx = (Math.random() * 2 - 1) * RADIUS;
            double dz = (Math.random() * 2 - 1) * RADIUS;
            Location newLoc = center.clone().add(dx, 0, dz);
            // зберігаємо висоту поверхні
            newLoc.setY(caster.getWorld().getHighestBlockYAt(newLoc) + 1);
            target.teleport(newLoc);
        }
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c" + getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Сотворює аномалію навколо");
        lore.add(ChatColor.WHITE + "Радіус: " + RADIUS);
        lore.add(ChatColor.WHITE + "Тривалість: " + DURATION_TICKS / 20 + "c");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
