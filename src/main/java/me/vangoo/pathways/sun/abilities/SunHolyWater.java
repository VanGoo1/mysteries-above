package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.UUID;

/**
 * Sequence 7: Свята вода Сонця. Вікі: благословляє звичайну воду на екзорцизм — виганяє
 * злих духів, відганяє холод, очищує привидів. Реалізовано як ефект здібності (без нового
 * кастомного предмета в custom-items.yml): освячує пляшку води в руці на місці й вивільняє
 * миттєвий сплеск екзорцизму навколо кастера, сильніший по "темних" pathway ({@link HolyAffinity}),
 * ніж по звичайній нежиті — саме так вікі описує різницю Purification/Exorcism.
 */
public class SunHolyWater extends ActiveAbility {

    private static final double RADIUS = 6.0;
    private static final int BASE_DAMAGE = 8;
    private static final int COOLDOWN = 20;

    @Override
    public String getName() {
        return "Свята вода Сонця";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fОсвячує пляшку води в руці: вивільняє сплеск екзорцизму навколо (радіус §e%.0f §fбл).\n " +
                        "§7Шкода: §c%d §7по нежиті, §c%.0f%% §7більше — по темних шляхах. Відганяє холод/послаблення союзникам.",
                RADIUS, damage, (HolyAffinity.damageMultiplier(true) / HolyAffinity.damageMultiplier(false) - 1) * 100
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 40;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected boolean canExecute(IAbilityContext context) {
        return findWaterBottle(context.getCasterPlayer().getInventory()) != null;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        ItemStack bottle = findWaterBottle(caster.getInventory());
        if (bottle == null) {
            return AbilityResult.failure("Потрібна пляшка води в інвентарі");
        }

        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        Location center = context.getCasterLocation();
        int baseDamage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        ItemStack toConsume = bottle.clone();
        toConsume.setAmount(1);
        context.entity().consumeItem(casterId, toConsume);

        int purified = 0;
        for (LivingEntity entity : context.targeting().getNearbyEntities(RADIUS)) {
            if (entity.getUniqueId().equals(casterId)) continue;

            if (entity instanceof Player ally && !HolyTargetClassifier.isDarkOrUndead(ally, context)) {
                cleanseCold(context, ally.getUniqueId());
                continue;
            }

            boolean darkTarget = isDarkPathway(context, entity);
            double multiplier = HolyAffinity.damageMultiplier(darkTarget) * context.amplification().getDamageMultiplier(casterId);
            int damage = (int) Math.ceil(baseDamage * multiplier);

            context.entity().damage(entity.getUniqueId(), damage);
            context.entity().applyPotionEffect(entity.getUniqueId(), PotionEffectType.WEAKNESS, 60, 0);
            purified++;
        }

        cleanseCold(context, casterId);
        playHolyWaterEffect(context, center);

        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "☀ Свята вода Сонця освячена — вигнано цілей: " + purified);

        return AbilityResult.success();
    }

    private boolean isDarkPathway(IAbilityContext context, LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        var beyonder = context.beyonder().getBeyonder(player.getUniqueId());
        return beyonder != null && HolyAffinity.isDark(beyonder.getPathway().getName());
    }

    private void cleanseCold(IAbilityContext context, UUID targetId) {
        context.entity().removePotionEffect(targetId, PotionEffectType.SLOWNESS);
        context.entity().removePotionEffect(targetId, PotionEffectType.DARKNESS);
    }

    private ItemStack findWaterBottle(PlayerInventory inventory) {
        ItemStack mainHand = inventory.getItemInMainHand();
        if (isWaterBottle(mainHand)) return mainHand;

        for (ItemStack item : inventory.getContents()) {
            if (isWaterBottle(item)) return item;
        }
        return null;
    }

    private boolean isWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        return meta.getBasePotionType() == PotionType.WATER;
    }

    private void playHolyWaterEffect(IAbilityContext context, Location center) {
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.3f);
        context.effects().playCircleEffect(center, RADIUS, Particle.SPLASH, 20);
        context.effects().playExplosionRingEffect(center.clone().add(0, 1, 0), RADIUS * 0.5, Particle.DUST, gold);
        context.effects().playSound(center, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.4f);
        context.effects().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
    }
}
