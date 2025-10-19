package me.vangoo.implementation.VisionaryPathway.abilities;

import de.slikey.effectlib.effect.ConeEffect;
import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class SurgeOfInsanity extends Ability {
    private static final int RANGE = 5;
    private static final int COOLDOWN = 30;
    private static final int EFFECT_DURATION = 10 * 20; // 10 секунд
    private static final int SANITY_INCREASE = 15;      // <-- НОВА КОНСТАНТА

    @Override
    public String getName() {
        return "Всплеск божевілля";
    }

    @Override
    public String getDescription() {
        return "В радіусі " + RANGE + " блоків на інших гравців накладається слабкість 1 і сліпота 1. Потойбічні в цьому радіусі також проявляють ознаки втрати контролю.";
    }

    @Override
    public int getSpiritualityCost() {
        return 90;
    }

    @Override
    public boolean execute(Player caster, Beyonder beyonder) {
        final int ABILITY_LOCK_SECONDS = 10;

        if (beyonder.getSpirituality() < getSpiritualityCost()) {
            caster.sendMessage("§cНедостатньо духовної сили!");
            return false;
        }

        // --- Візуальний ефект (без змін) ---
        ConeEffect coneEffect = new ConeEffect(plugin.getEffectManager());
        Location effectLocation = caster.getLocation().clone();
        effectLocation.setPitch(90);
        coneEffect.setLocation(effectLocation);
        coneEffect.particle = Particle.DRAGON_BREATH;
        coneEffect.radiusGrow = 0.2f; // Зроблено трохи швидшим для динамічності
        coneEffect.lengthGrow = 0.001f;
        coneEffect.duration = 1000;
        coneEffect.particles = 30;
        coneEffect.angularVelocity = Math.PI / 16;
        coneEffect.period = 1;
        coneEffect.start();
        // ------------------------------------------

        for (Entity entity : caster.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (entity instanceof Player target && !entity.equals(caster)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, EFFECT_DURATION, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, EFFECT_DURATION, 0));

                // --- ЗМІНА: Отримуємо об'єкт Beyonder один раз для ефективності ---
                Beyonder targetBeyonder = plugin.getBeyonderManager().GetBeyonder(target.getUniqueId());

                if (targetBeyonder != null) {
                    target.sendMessage("§5Ваш розум затьмарюється! Вам важко контролювати свої сили.");

                    // 1. Блокуємо здібності (існуюча логіка)
                    plugin.getAbilityManager().lockPlayer(target, ABILITY_LOCK_SECONDS);

                    // --- 2. НОВА ЛОГІКА: Підвищуємо втрату контролю ---
                    int currentSanityLoss = targetBeyonder.getSanityLossScale();
                    int newSanityLoss = currentSanityLoss + SANITY_INCREASE;
                    targetBeyonder.setSanityLossScale(newSanityLoss); // Використовуємо ваш сеттер

                    // Повідомляємо гравця про зміну
                    target.sendMessage(ChatColor.RED + "Ваша втрата контролю зросла: " + currentSanityLoss + " → " + newSanityLoss);
                    // ----------------------------------------------------

                } else {
                    target.sendMessage("§5Ви відчуваєте раптовий наплив божевілля!");
                }
            }
        }

        caster.sendMessage("§dВи вивільнили хвилю божевілля!");
        return true;
    }

    @Override
    public ItemStack getItem() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("Дальність", RANGE + " блоків");
        attributes.put("Тривалість ефектів", (EFFECT_DURATION / 20) + "с");
        return abilityItemFactory.createItem(this, attributes);
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }
}