package me.vangoo.implementation.VisionaryPathway.abilities;

import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class ScanGaze extends Ability {
    private static final int RANGE = 5;

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
        return "При натисканні на гравця показує його HP, голод та броню. Для менших послідовностей: колір залежить від HP цілі.";
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

        if (beyonder.getSequence() < 9) {
            // Показ насичення
            float saturation = p.getSaturation();
            caster.sendMessage(ChatColor.GRAY + "Насичення: " + ChatColor.YELLOW + String.format("%.1f", saturation));

            // Показ активних ефектів
            Collection<PotionEffect> effects = p.getActivePotionEffects();
            if (!effects.isEmpty()) {
                caster.sendMessage(ChatColor.GRAY + "Активні ефекти:");
                for (PotionEffect effect : effects) {
                    String effectName = effect.getType().getName();
                    int duration = effect.getDuration() / 20; // конвертуємо тики в секунди
                    int amplifier = effect.getAmplifier() + 1; // рівень ефекту (починається з 0)

                    caster.sendMessage(ChatColor.GRAY + "  • " + ChatColor.LIGHT_PURPLE + effectName +
                            ChatColor.GRAY + " (" + ChatColor.WHITE + amplifier + ChatColor.GRAY + ") - " +
                            ChatColor.AQUA + duration + "с");
                }
            } else {
                caster.sendMessage(ChatColor.GRAY + "Активні ефекти: " + ChatColor.YELLOW + "відсутні");
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("Радіус", RANGE + " блоків");

        return abilityItemFactory.createItem(this, attributes);
    }

    @Override
    public int getCooldown() {
        return 10;
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
