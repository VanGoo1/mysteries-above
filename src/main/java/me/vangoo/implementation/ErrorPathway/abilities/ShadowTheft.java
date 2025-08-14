package me.vangoo.implementation.ErrorPathway.abilities;

import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;


public class ShadowTheft extends Ability {

    private static final int RANGE = 8;
    private static final int INVISIBILITY_DURATION = 60; // 3 seconds in ticks
    private static final int SPIRITUALITY_COST = 25;

    @Override
    public String getName() {
        return "Shadow Theft";
    }

    @Override
    public String getDescription() {
        return "Телепортуйтеся за спину ворога в радіусі 8 блоків та вкрадіть випадковий предмет. Стаєте невидимими на 3 секунди.";
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown() {
        return 120;
    }

    @Override
    public boolean execute(Player caster, Beyonder beyonder) {

        // Знаходимо найближчу ціль (гравця або моба)
        LivingEntity target = findNearestTarget(caster);

        if (target == null) {
            caster.sendMessage(ChatColor.RED + "Немає цілей в радіусі " + RANGE + " блоків!");
            return false;
        }

        // Ефект телепортації
        createTeleportEffect(caster.getLocation());

        // Телепортуємося за спину цілі
        Location behindTarget = getBehindLocation(target);
        caster.teleport(behindTarget);

        // Ефект появи
        createTeleportEffect(behindTarget);

        // Намагаємося вкрасти предмет
        boolean stolen = attemptTheft(caster, target);

        if (stolen) {
            // Успішна крадіжка - даємо невидимість
            caster.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INVISIBILITY,
                    INVISIBILITY_DURATION,
                    0,
                    false,
                    false
            ));

            String targetName = (target instanceof Player) ? target.getName() : target.getType().name();
            caster.sendMessage(ChatColor.GREEN + "Успішно вкрали предмет у " + targetName + "!");

            if (target instanceof Player) {
                ((Player) target).sendMessage(ChatColor.RED + "Хтось вкрав у вас предмет!");
                ((Player) target).playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
            }

            // Звук успіху
            caster.playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        } else {
            String targetName = (target instanceof Player) ? target.getName() : target.getType().name();
            caster.sendMessage(ChatColor.YELLOW + "Не вдалося нічого вкрасти у " + targetName);
            caster.playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        }

        // Витрачаємо духовність
        beyonder.DecrementSpirituality(SPIRITUALITY_COST);

        // Ефект тіней навколо гравця
        createShadowEffect(caster);
        return true;
    }

    private LivingEntity findNearestTarget(Player caster) {
        LivingEntity nearest = null;
        double minDistance = RANGE;

        // Шукаємо серед всіх живих сутностей поблизу
        for (Entity entity : caster.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == caster) continue;

            LivingEntity living = (LivingEntity) entity;

            // Перевіряємо тип сутності
            if (living instanceof Player) {
                Player player = (Player) living;
                if (player.getGameMode() == GameMode.SPECTATOR) continue;
            } else if (living instanceof Monster || living instanceof Animals) {
                // Дозволяємо атакувати мобів та тварин
            } else {
                // Пропускаємо інших сутностей (наприклад, Villager, ArmorStand тощо)
                continue;
            }

            double distance = caster.getLocation().distance(living.getLocation());
            if (distance < minDistance) {
                nearest = living;
                minDistance = distance;
            }
        }

        return nearest;
    }

    private Location getBehindLocation(LivingEntity target) {
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.getDirection().normalize();

        // Отримуємо позицію за спиною (протилежно напрямку погляду)
        Vector behind = direction.multiply(-1.5); // 1.5 блоки за спиною

        Location behindLoc = targetLoc.clone().add(behind);
        behindLoc.setY(targetLoc.getY()); // Залишаємо ту ж висоту

        // Перевіряємо, чи безпечна позиція
        if (!behindLoc.getBlock().getType().isSolid() &&
                !behindLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
            return behindLoc;
        }

        // Якщо позиція за спиною небезпечна, телепортуємося поруч
        return targetLoc.clone().add(1, 0, 1);
    }

    private boolean attemptTheft(Player thief, LivingEntity target) {
        if (target instanceof Player) {
            return stealFromPlayer(thief, (Player) target);
        } else {
            return stealFromMob(thief, target);
        }
    }

    private boolean stealFromPlayer(Player thief, Player target) {
        PlayerInventory inventory = target.getInventory();
        List<Integer> validSlots = new ArrayList<>();

        // Знаходимо всі слоти з предметами (крім броні)
        for (int i = 0; i < 36; i++) { // 36 слотів в основному інвентарі
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                validSlots.add(i);
            }
        }

        if (validSlots.isEmpty()) {
            return false;
        }

        // Випадково вибираємо предмет для крадіжки
        Random random = new Random();
        int randomSlot = validSlots.get(random.nextInt(validSlots.size()));
        ItemStack stolenItem = inventory.getItem(randomSlot);

        if (stolenItem == null) {
            return false;
        }

        // Вкрадаємо 1-3 предмети або весь стак, якщо він менший
        int amountToSteal = Math.min(stolenItem.getAmount(), random.nextInt(3) + 1);
        ItemStack theft = stolenItem.clone();
        theft.setAmount(amountToSteal);

        // Забираємо предмет у цілі
        if (stolenItem.getAmount() <= amountToSteal) {
            inventory.setItem(randomSlot, null);
        } else {
            stolenItem.setAmount(stolenItem.getAmount() - amountToSteal);
            inventory.setItem(randomSlot, stolenItem);
        }

        // Додаємо предмет злодію
        thief.getInventory().addItem(theft);

        return true;
    }

    private boolean stealFromMob(Player thief, LivingEntity mob) {
        Random random = new Random();
        World world = mob.getWorld();

        // Різні типи мобів дропають різні речі
        List<ItemStack> possibleDrops = new ArrayList<>();

        if (mob instanceof Zombie) {
            possibleDrops.add(new ItemStack(Material.ROTTEN_FLESH, random.nextInt(3) + 1));
            if (random.nextInt(100) < 5) possibleDrops.add(new ItemStack(Material.IRON_INGOT));
        } else if (mob instanceof Skeleton) {
            possibleDrops.add(new ItemStack(Material.BONE, random.nextInt(3) + 1));
            possibleDrops.add(new ItemStack(Material.ARROW, random.nextInt(3) + 1));
        } else if (mob instanceof Creeper) {
            possibleDrops.add(new ItemStack(Material.GUNPOWDER, random.nextInt(3) + 1));
        } else if (mob instanceof Spider) {
            possibleDrops.add(new ItemStack(Material.STRING, random.nextInt(3) + 1));
            if (random.nextInt(100) < 10) possibleDrops.add(new ItemStack(Material.SPIDER_EYE));
        } else if (mob instanceof Enderman) {
            if (random.nextInt(100) < 20) possibleDrops.add(new ItemStack(Material.ENDER_PEARL));
        } else if (mob instanceof Cow) {
            possibleDrops.add(new ItemStack(Material.LEATHER, random.nextInt(2) + 1));
        } else if (mob instanceof Sheep) {
            possibleDrops.add(new ItemStack(Material.WHITE_WOOL, random.nextInt(3) + 1));
        } else if (mob instanceof Pig) {
            possibleDrops.add(new ItemStack(Material.PORKCHOP, random.nextInt(2) + 1));
        } else if (mob instanceof Chicken) {
            possibleDrops.add(new ItemStack(Material.FEATHER, random.nextInt(2) + 1));
            if (random.nextInt(100) < 30) possibleDrops.add(new ItemStack(Material.EGG));
        } else {
            // Для інших мобів - випадкові базові ресурси
            Material[] basicItems = {Material.STICK, Material.COBBLESTONE, Material.DIRT};
            possibleDrops.add(new ItemStack(basicItems[random.nextInt(basicItems.length)], 1));
        }

        if (possibleDrops.isEmpty()) {
            return false;
        }

        // Вибираємо випадковий предмет
        ItemStack stolenItem = possibleDrops.get(random.nextInt(possibleDrops.size()));

        // Додаємо предмет злодію
        thief.getInventory().addItem(stolenItem);

        // Створюємо ефект, ніби щось вкрали з моба
        world.spawnParticle(Particle.WHITE_SMOKE, mob.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.CRIT, mob.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.ANGRY_VILLAGER, mob.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.1);

        return true;
    }

    private void createTeleportEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Фіолетові частинки телепортації
        world.spawnParticle(Particle.PORTAL, location, 50, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.WHITE_SMOKE, location, 20, 0.5, 0.5, 0.5, 0.1);
    }

    private void createShadowEffect(Player player) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= INVISIBILITY_DURATION || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                World world = loc.getWorld();
                if (world != null) {
                    // Темні частинки навколо гравця
                    world.spawnParticle(Particle.WHITE_SMOKE, loc, 5, 0.5, 1, 0.5, 0.02);
                    if (ticks % 10 == 0) {
                        world.spawnParticle(Particle.ANGRY_VILLAGER, loc, 3, 0.3, 0.5, 0.3, 0.1);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @Override
    public ItemStack getItem() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("Дальність", RANGE + "блоків");
        attributes.put("Невидимість", "3 секунди");

        return abilityItemFactory.createItem(this, attributes);
    }
}