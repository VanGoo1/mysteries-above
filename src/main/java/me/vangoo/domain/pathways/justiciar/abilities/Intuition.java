package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sequence 9 Arbiter: Danger Intuition
 */
public class Intuition extends ToggleablePassiveAbility {

    private static final double DETECTION_RADIUS = 20.0;
    private static final long WARNING_COOLDOWN_MS = 5000;

    private final Map<UUID, Long> warnedEntities = new HashMap<>();
    private int tickCounter = 0;

    @Override
    public String getName() {
        return "[Пасивна] Інтуїція Небезпеки";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Дозволяє передчувати небезпеку від озброєних гравців та агресивних мобів.";
    }

    @Override
    public void onEnable(IAbilityContext context) {
        warnedEntities.clear();
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
        context.sendMessageToCaster(ChatColor.GREEN + "Інтуїцію загострено.");
    }

    @Override
    public void onDisable(IAbilityContext context) {
        warnedEntities.clear();
        context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 2.0f);
        context.sendMessageToCaster(ChatColor.RED + "Інтуїцію послаблено.");
    }

    @Override
    public void tick(IAbilityContext context) {
        // Оптимізація: перевірка раз на 10 тіків (0.5 сек)
        if (tickCounter++ % 10 != 0) {
            return;
        }

        Player caster = context.getCasterPlayer();

        List<LivingEntity> nearbyEntities = context.getNearbyEntities(DETECTION_RADIUS);

        long currentTime = System.currentTimeMillis();

        for (LivingEntity entity : nearbyEntities) {
            // Фільтрація: пропускаємо самого себе
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }

            boolean isThreat = false;

            // 1. Перевірка Мобів
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                // Перевіряємо, чи моб націлений на кастера
                if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(caster.getUniqueId())) {
                    isThreat = true;
                }
            }
            // 2. Перевірка Гравців
            else if (entity instanceof Player) {
                Player potentialAttacker = (Player) entity;
                // Гравець загроза, якщо тримає зброю І дивиться на кастера
                if (isHoldingWeapon(potentialAttacker) && isLookingAt(potentialAttacker, caster)) {
                    isThreat = true;
                }
            }

            // Якщо загрозу виявлено
            if (isThreat) {
                // Перевіряємо кулдаун сповіщення для цієї конкретної сутності
                if (!warnedEntities.containsKey(entity.getUniqueId()) ||
                        (currentTime - warnedEntities.get(entity.getUniqueId()) > WARNING_COOLDOWN_MS)) {

                    sendWarning(context, caster, entity);
                    warnedEntities.put(entity.getUniqueId(), currentTime);
                }
            }
        }

        // Очищення старих записів з пам'яті
        if (warnedEntities.size() > 50) {
            warnedEntities.entrySet().removeIf(entry -> currentTime - entry.getValue() > WARNING_COOLDOWN_MS);
        }
    }

    private void sendWarning(IAbilityContext context, Player caster, LivingEntity threat) {
        double distance = caster.getLocation().distance(threat.getLocation());
        String name = threat.getName();
        ChatColor color = (threat instanceof Player) ? ChatColor.RED : ChatColor.YELLOW;

        // Звук залишаємо, щоб привернути увагу
        context.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

        // Формуємо повідомлення
        // Для ActionBar краще робити текст коротшим і лаконічнішим
        String message = ChatColor.DARK_RED + "⚠ ЗАГРОЗА: " +
                color + name +
                ChatColor.GRAY + " [" +
                ChatColor.GOLD + String.format("%.1f", distance) + "м" +
                ChatColor.GRAY + "]";

        // Відправляємо в ActionBar через Spigot API
        caster.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private boolean isHoldingWeapon(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return false;

        String typeName = item.getType().name();
        return typeName.endsWith("_SWORD") ||
                typeName.endsWith("_AXE") ||
                typeName.equals("TRIDENT") ||
                typeName.equals("BOW") ||
                typeName.equals("CROSSBOW");
    }

    private boolean isLookingAt(Player attacker, Player victim) {
        Location attackerLoc = attacker.getEyeLocation();
        Location victimLoc = victim.getEyeLocation();

        Vector toVictim = victimLoc.toVector().subtract(attackerLoc.toVector()).normalize();
        Vector direction = attackerLoc.getDirection();

        return direction.dot(toVictim) > 0.5;
    }
}