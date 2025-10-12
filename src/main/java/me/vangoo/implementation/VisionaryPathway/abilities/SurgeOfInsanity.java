package me.vangoo.implementation.VisionaryPathway.abilities;

import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.SphereEffect;
import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurgeOfInsanity extends Ability {
    private static final int RANGE = 5;
    private static final int COOLDOWN = 30;
    private static final int EFFECT_DURATION = 10 * 20; // 10 секунд


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


        // Встановлюємо тривалість блокування здібностей в секундах
        final int ABILITY_LOCK_SECONDS = 10;

        if (beyonder.getSpirituality() < getSpiritualityCost()) {
            caster.sendMessage("§cНедостатньо духовної сили!");
            return false;
        }

        // Візуальний ефект
        SphereEffect sphereEffect = new SphereEffect(plugin.getEffectManager());
        sphereEffect.setEntity(caster);
        sphereEffect.radius = RANGE;
        sphereEffect.particle = Particle.DRAGON_BREATH;
        sphereEffect.color = Color.PURPLE;
        sphereEffect.duration = 1500;
        sphereEffect.start();

        for (Entity entity : caster.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (entity instanceof Player target && !entity.equals(caster)) {

                // Слабкість і сліпота накладаються на ВСІХ гравців
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, EFFECT_DURATION, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, EFFECT_DURATION, 0));

                // Перевіряємо, чи є ціль Потойбічним
                if (plugin.getBeyonderManager().GetBeyonder(target.getUniqueId()) != null) {
                    // Якщо так, блокуємо їй здібності через AbilityManager
                    target.sendMessage("§5Ваш розум затьмарюється! Вам важко контролювати свої сили.");

                    // --- ОСНОВНА ЗМІНА ---
                    // Викликаємо метод для блокування гравця, який ми раніше додали в AbilityManager
                    plugin.getAbilityManager().lockPlayer(target, ABILITY_LOCK_SECONDS);
                    // ---------------------

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
        attributes.put("Кулдаун", COOLDOWN + "с");
        attributes.put("Тривалість ефектів", (EFFECT_DURATION / 20) + "с");
        return abilityItemFactory.createItem(this, attributes);
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }
}