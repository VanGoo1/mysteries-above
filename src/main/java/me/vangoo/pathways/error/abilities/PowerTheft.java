package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.DreamStealerTheft;
import me.vangoo.domain.valueobjects.PrometheusTheft;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Посл. 6 (Прометей): «Крадіжка сили» — у жертви забирається конкретна містична сила
 * і на 10 хвилин стає силою злодія; сама жертва не кличе її 30 хвилин.
 *
 * <p>Посл. 5 (Крадій снів) — той самий рух руки, більший розмах: 80 м замість 50, 30 хв
 * володіння замість 10, 60 хв пригнічення замість 30, а непокликана сила висить до 6 год,
 * доки злодій не кастує її вперше ({@code TheftLedger.markUsed} з {@code AbilityExecutor}).
 *
 * <p>Ключове правило вікі — «чим краще розумієш ціль, тим легше вкрасти саме те, що треба»:
 * розблоковані рецепти шляху жертви і показують назви в меню, і піднімають шанс
 * ({@link PrometheusTheft#successChance}). Хто шляху не знає — тицяє в безіменні вогники.
 *
 * <p>Часом володіє {@code TheftLedger} (абсолютні мітки, переживають релог і рестарт);
 * здібність лише записує крадіжку через {@code context.beyonder().stealAbility(...)}.
 */
public class PowerTheft extends ActiveAbility {

    /** Скільки тіків вогники обертаються довкола цілі, поки відкрите меню сил. */
    private static final int MOTES_DURATION_TICKS = 200;

    /** Ціль крадіжки: гравець-Потойбічний або міфічна істота свого шляху. */
    private record Target(UUID id, String name, Beyonder beyonder, Player player) {}

    /** Посл. 5 (Крадій снів) і сильніше: далі, довше, з резервним строком до першого касту. */
    private static boolean dreamTier(Sequence sequence) {
        return sequence.level() <= 5;
    }

    private static double range(Sequence sequence) {
        return dreamTier(sequence)
                ? DreamStealerTheft.POWER_THEFT_RANGE
                : PrometheusTheft.POWER_THEFT_RANGE;
    }

    /** Пункт меню вогників: конкретна сила або слот «Божевілля» ({@code power == null}). */
    private record Slot(Ability power) {}

    @Override
    public String getName() {
        return "Крадіжка сили";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        boolean dream = dreamTier(userSequence);
        long hold = dream ? DreamStealerTheft.POWER_STOLEN_DURATION_MILLIS
                : PrometheusTheft.STOLEN_DURATION_MILLIS;
        long suppression = dream ? DreamStealerTheft.POWER_SUPPRESSION_DURATION_MILLIS
                : PrometheusTheft.SUPPRESSION_DURATION_MILLIS;
        return "Виймає з чужого Потойбічного — гравця або міфічної істоти — одну його силу\n"
                + "§7в радіусі " + (int) range(userSequence) + " блоків, навіть крізь стіну.\n"
                + "§7Вкрадене служить вам " + hold / 60_000
                + " хв, а жертва не зможе використовувати його " + suppression / 60_000 + " хв.\n"
                + (dream ? "§7Невикористана вкрадена сила буде у вас "
                        + DreamStealerTheft.POWER_UNUSED_HOLD_MILLIS / 3_600_000
                        + " год.\n" : "")
                + "§7Сили, шляху яких ви досконало не знаєте, видно лише безіменними вогниками.\n"
                + "§7Замість сили можна взяти на себе чуже божевілля.\n"
                + "§cНевдача теж забирає духовність і ставить відкат.";
    }

    @Override
    public int getSpiritualityCost() {
        return PrometheusTheft.POWER_THEFT_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return PrometheusTheft.powerTheftCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (context.beyonder().hasStolenAbility(casterId)) {
            return AbilityResult.failure("Ви вже тримаєте чужу силу — вона не пустить другу.");
        }

        List<Target> targets = collectTargets(context, casterId,
                range(context.getCasterBeyonder().getSequence()));
        if (targets.isEmpty()) {
            return AbilityResult.failure("Поблизу немає чужої сили, яку можна взяти.");
        }

        context.ui().openChoiceMenu(
                "Крадіжка сили: ціль",
                targets,
                this::createTargetIcon,
                target -> openPowerMenu(context, target));
        return AbilityResult.deferred();
    }

    /** Гравці-Потойбічні і міфічні істоти в радіусі — істота теж носій сили свого шляху. */
    private List<Target> collectTargets(IAbilityContext context, UUID casterId, double range) {
        List<Target> targets = new ArrayList<>();
        for (Player player : context.targeting().getNearbyPlayers(range)) {
            if (player.getUniqueId().equals(casterId)) {
                continue;
            }
            Beyonder beyonder = context.beyonder().getBeyonder(player.getUniqueId());
            if (beyonder != null) {
                targets.add(new Target(player.getUniqueId(), player.getName(), beyonder, player));
            }
        }
        for (LivingEntity entity : context.targeting().getNearbyEntities(range)) {
            if (entity instanceof Player) {
                continue;
            }
            context.beyonder().getCreatureBeyonder(entity.getUniqueId()).ifPresent(beyonder ->
                    targets.add(new Target(entity.getUniqueId(),
                            ChatColor.stripColor(entity.getName()), beyonder, null)));
        }
        return targets;
    }

    private void openPowerMenu(IAbilityContext context, Target target) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        Beyonder victim = target.beyonder();
        if (context.playerData().getCurrentLocation(victimId) == null) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Ціль зникла.");
            return;
        }

        List<Slot> slots = new ArrayList<>(victim.getAbilities().stream()
                .filter(a -> a.getType() != AbilityType.PERMANENT_PASSIVE)
                .map(Slot::new)
                .toList());
        if (victim.getSanityLossScale() > 0) {
            slots.add(new Slot(null));
        }
        if (slots.isEmpty()) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "У цієї цілі нема чого взяти.");
            return;
        }

        Color pathwayColor = PathwayBranding.liquidOf(victim.getPathway().getName());
        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        context.effects().playOrbitingMotes(victimId,
                List.of(pathwayColor, PathwayBranding.liquidOf("Error"), Color.WHITE),
                0.8, MOTES_DURATION_TICKS);
        context.effects().playSound(victimLocation, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 0.7f);

        boolean revealed = PrometheusTheft.revealsAbilityNames(
                context.beyonder().getUnlockedRecipesCount(casterId, victim.getPathway().getName()));

        context.ui().openChoiceMenu(
                "Крадіжка сили: вогники",
                slots,
                slot -> slot.power() == null
                        ? createCorruptionIcon(context, victim)
                        : createPowerIcon(context, slot.power(), victim, pathwayColor, revealed),
                slot -> attemptTheft(context, target, slot.power()));
    }

    private void attemptTheft(IAbilityContext context, Target target, Ability power) {
        UUID casterId = context.getCasterId();
        Beyonder caster = context.getCasterBeyonder();
        Beyonder victim = target.beyonder();
        UUID victimId = target.id();

        if (context.playerData().getCurrentLocation(victimId) == null) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Ціль зникла.");
            return;
        }
        if (power != null && caster.getAbilityByName(power.getName()).isPresent()) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Ця сила у вас уже є.");
            return;
        }
        if (!AbilityResourceConsumer.consumeResources(this, caster, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, caster);

        Location casterLocation = context.playerData().getCurrentLocation(casterId);
        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playConeEffect(context.playerData().getEyeLocation(casterId),
                victimLocation.toVector().subtract(casterLocation.toVector()).normalize(),
                25, 4, Particle.ENCHANT, 30);
        context.effects().playSound(casterLocation, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.6f);

        int knownRecipes = context.beyonder()
                .getUnlockedRecipesCount(casterId, victim.getPathway().getName());
        double chance = dreamTier(caster.getSequence())
                ? DreamStealerTheft.successChance(knownRecipes, victim.getSequence())
                : PrometheusTheft.successChance(knownRecipes, victim.getSequence());

        if (Math.random() >= chance) {
            context.effects().playExplosionRingEffect(casterLocation, 1.5, Particle.DUST,
                    new Particle.DustOptions(errorColor, 1.2f));
            context.effects().playSound(casterLocation, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            context.messaging().sendMessage(casterId, ChatColor.RED + "Сила вислизнула — ви взяли порожнечу.");
            return;
        }

        context.effects().playTravelingBeam(victimLocation.clone().add(0, 1.2, 0),
                casterLocation.clone().add(0, 1.2, 0), errorColor,
                () -> {
                    if (power == null) {
                        takeCorruption(context, target);
                    } else {
                        grant(context, target, power);
                    }
                });
    }

    /**
     * Слот «Божевілля» (вікі 3a.11): злодій забирає в жертви частину зіпсутості —
     * її глузд світлішає рівно настільки, наскільки темніє його власний.
     */
    private void takeCorruption(IAbilityContext context, Target target) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        int amount = Math.min(
                PrometheusTheft.corruptionTransfer(context.getCasterBeyonder().getSequence()),
                target.beyonder().getSanityLossScale());
        if (amount <= 0) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Божевілля вислизнуло — брати вже нічого.");
            return;
        }

        context.beyonder().updateSanityLoss(victimId, -amount);
        context.beyonder().updateSanityLoss(casterId, amount);

        Location casterLocation = context.playerData().getCurrentLocation(casterId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playAlertHalo(casterLocation, errorColor);
        context.effects().playFadingAura(casterLocation, errorColor, 60);
        context.effects().playSound(casterLocation, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.8f);
        context.effects().playSound(casterLocation, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.9f, 0.6f);
        context.messaging().sendMessageToActionBar(casterId, Component.text(
                ChatColor.DARK_RED + "Чуже божевілля осіло у вас: +" + amount));

        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        if (victimLocation != null) {
            context.effects().playGlowingDust(victimLocation.clone().add(0, 1, 0), errorColor);
            context.effects().playSound(victimLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
        }
        if (target.player() != null) {
            context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE
                    + "У голові раптом прояснішало — частину темряви хтось забрав собі.");
        }
    }

    /** Перекладання сили в злодія — рівно в мить прильоту променя. */
    private void grant(IAbilityContext context, Target target, Ability power) {
        UUID casterId = context.getCasterId();
        UUID victimId = target.id();
        Beyonder caster = context.getCasterBeyonder();
        if (caster == null || !caster.addOffPathwayAbility(power)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Сила не втрималась у вас.");
            return;
        }

        if (dreamTier(caster.getSequence())) {
            context.beyonder().stealAbility(casterId, victimId, power.getIdentity(),
                    DreamStealerTheft.POWER_UNUSED_HOLD_MILLIS,
                    DreamStealerTheft.POWER_STOLEN_DURATION_MILLIS,
                    DreamStealerTheft.POWER_SUPPRESSION_DURATION_MILLIS);
        } else {
            context.beyonder().stealAbility(casterId, victimId, power.getIdentity());
        }
        context.beyonder().updateBeyonder(casterId);

        Location casterLocation = context.playerData().getCurrentLocation(casterId);
        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playGlowingDust(casterLocation.clone().add(0, 1, 0), errorColor);
        context.effects().playFadingAura(casterLocation, errorColor, 40);
        context.effects().playSound(casterLocation, Sound.ITEM_TRIDENT_RETURN, 0.9f, 1.4f);
        context.effects().playSound(casterLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        context.messaging().sendMessageToActionBar(casterId, Component.text(
                ChatColor.LIGHT_PURPLE + "Вкрадено: " + power.getName()));

        Location victimLocation = context.playerData().getCurrentLocation(victimId);
        if (victimLocation != null) {
            context.effects().playAlertHalo(victimLocation, errorColor);
            context.effects().playSound(victimLocation, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 0.5f);
        }
        if (target.player() != null) {
            context.messaging().sendMessage(victimId, ChatColor.DARK_PURPLE
                    + "Щось витягли з вас: «" + power.getName() + "» більше не відгукується.");
        }
    }

    /** Голова гравця або череп істоти — щоб у меню було видно, у кого лізеш. */
    private ItemStack createTargetIcon(Target target) {
        boolean isPlayer = target.player() != null;
        ItemStack icon = new ItemStack(isPlayer ? Material.PLAYER_HEAD : Material.SKELETON_SKULL);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        if (meta instanceof SkullMeta skullMeta && isPlayer) {
            skullMeta.setOwningPlayer(target.player());
        }
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + target.name());
        if (!isPlayer) {
            meta.setLore(List.of(PathwayBranding.textOf(target.beyonder().getPathway().getName())
                    + target.beyonder().getPathway().getName()
                    + ChatColor.GRAY + ", Посл. " + target.beyonder().getSequence().level()));
        }
        icon.setItemMeta(meta);
        return icon;
    }

    /** Слот «Божевілля» — видно лише тоді, коли жертві є що віддати. */
    private ItemStack createCorruptionIcon(IAbilityContext context, Beyonder victim) {
        ItemStack item = new ItemStack(Material.FERMENTED_SPIDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int amount = Math.min(
                PrometheusTheft.corruptionTransfer(context.getCasterBeyonder().getSequence()),
                victim.getSanityLossScale());
        meta.setDisplayName(ChatColor.DARK_RED + "Божевілля");
        meta.setLore(List.of(
                ChatColor.GRAY + "Забирає в цілі " + amount + " зіпсутості —",
                ChatColor.GRAY + "і саджає її у вас.",
                "",
                ChatColor.YELLOW + "▶ Натисніть, щоб узяти на себе"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Знайома сила показує ім'я, незнайома — лише безіменний вогник кольору свого шляху
     * (вікі 3a.2); увімкнена просто зараз блищить (вікі 3a.4).
     */
    private ItemStack createPowerIcon(IAbilityContext context, Ability power, Beyonder victim,
                                      Color pathwayColor, boolean revealed) {
        ItemStack item = new ItemStack(revealed ? Material.BLAZE_POWDER : Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (meta instanceof FireworkEffectMeta fireworkMeta) {
            fireworkMeta.setEffect(FireworkEffect.builder().withColor(pathwayColor).build());
        }
        ChatColor nameColor = PathwayBranding.textOf(victim.getPathway().getName());
        meta.setDisplayName(revealed
                ? nameColor + power.getName()
                : nameColor + "Безіменний вогник");

        List<String> lore = new ArrayList<>();
        if (context.beyonder().isAbilityActivated(victim.getPlayerId(), power.getIdentity())) {
            meta.setEnchantmentGlintOverride(true);
            lore.add(ChatColor.AQUA + "Горить просто зараз");
        }
        if (!revealed) {
            lore.add(ChatColor.GRAY + "Ви не варили зілль цього шляху —");
            lore.add(ChatColor.GRAY + "що саме тут, невідомо.");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "▶ Натисніть, щоб украсти");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
