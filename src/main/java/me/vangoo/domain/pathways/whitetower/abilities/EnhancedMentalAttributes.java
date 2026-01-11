package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.text.DecimalFormat;

public class EnhancedMentalAttributes extends PermanentPassiveAbility {

    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private static final int XP_INTERVAL_TICKS = 600; // 10 секунд (20 тіків * 30)
    private static final int XP_AMOUNT = 3; // Скільки досвіду давати раз на 10 сек

    @Override
    public String getName() {
        return "Покращені Розумові Якості";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Пасивно усуває дезорієнтацію, дає досвід і показує ХП та захист цілі.";
    }

    @Override
    public void tick(IAbilityContext context) {
        Player player = context.getCaster();
        if (player == null || !player.isOnline()) return;

        // 1. MENTAL CLARITY (Миттєве очищення розуму)
        // Видаляємо ефекти, що заважають "читати" ситуацію
        if (player.hasPotionEffect(PotionEffectType.NAUSEA)) { // Nausea
            player.removePotionEffect(PotionEffectType.NAUSEA);
        }
        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        if (player.hasPotionEffect(PotionEffectType.DARKNESS)) { // 1.19+ ефект вардена
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }

        // 2. PASSIVE LEARNING (Накопичення знань)
        // Використовуємо ticksLived для таймінгу без створення нових змінних
        if (player.getTicksLived() % XP_INTERVAL_TICKS == 0) {
            player.giveExp(XP_AMOUNT);
            // Тихий звук перегортання сторінки, щоб гравець розумів, що процес йде
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.5f);
        }

        // 3. ANALYTICAL SIGHT (Сканування поглядом)
        // Робимо це кожні 5 тіків (0.25 сек), щоб не спамити рейтрейсом кожен тік
        if (player.getTicksLived() % 5 == 0) {
            analyzeTarget(player, context);
        }
    }

    private void analyzeTarget(Player player, IAbilityContext context) {
        // Пускаємо промінь з очей на 15 блоків
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                15.0, // Дистанція
                0.5,  // Товщина променя
                entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(player.getUniqueId())
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            double health = target.getHealth();
            double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) != null
                    ? target.getAttribute(Attribute.MAX_HEALTH).getValue()
                    : 0;
            double armor = target.getAttribute(Attribute.ARMOR) != null
                    ? target.getAttribute(Attribute.ARMOR).getValue()
                    : 0;

            // Форматуємо повідомлення: [Name] HP: 10/20 | Def: 5
            Component info = Component.text()
                    .append(Component.text("Ціль: ", NamedTextColor.GRAY))
                    .append(Component.text(target.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" | ХП: ", NamedTextColor.GRAY))
                    .append(Component.text(DF.format(health) + "/" + DF.format(maxHealth),
                            health < maxHealth / 3 ? NamedTextColor.RED : NamedTextColor.GREEN))
                    .append(Component.text(" | Захист: ", NamedTextColor.GRAY))
                    .append(Component.text(DF.format(armor), NamedTextColor.AQUA))
                    .build();

            // Відправляємо в Action Bar (над слотами інвентаря)
            context.sendMessageToActionBar(info);
        }
    }
}