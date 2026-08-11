package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SpiritMediumLore;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Death Sequence 7: Голос мертвих.
 *
 * <p>Вікі: «A Spirit Medium is able to directly Channel recently deceased Spirits, such as fallen
 * enemies, in order to obtain even more information from them.» Душа загиблого висить на місці
 * смерті 30 хв ({@link LingeringSouls}) і видима лише медіуму — саме її тут і допитують.
 *
 * <p>Знання з мертвого виходить рецептом зілля, і рецепт ПЕРЕЇЖДЖАЄ, а не копіюється: жертва
 * його втрачає. Тому ферма рецептів «друг помирає по колу» неможлива — кожен рецепт друг може
 * подарувати рівно раз, і собі його вже не поверне.
 *
 * <p>Друга гілка — вікіна «It can also be used on living creatures… but this method holds a lot of
 * danger for them»: якщо в прицілі живий гравець, медіум читає його досьє, а сильніша
 * Послідовність відбиває допит і б'є по розсудку самого медіума
 * ({@link SpiritMediumLore#voiceBacklashSanity}). Опір тут навмисно детермінований, а не роль
 * пайплайну ({@code getSequenceCheckTarget}): той відкидає каст ДО {@code performExecution} і не
 * дає куди повісити відкат розсудку.
 */
public class VoiceOfTheDead extends ActiveAbility {

    /** Скільки душа догоряє, віддавши знання (тіки). */
    private static final int FAREWELL_TICKS = 40;

    private final LingeringSouls souls;

    public VoiceOfTheDead(LingeringSouls souls) {
        this.souls = souls;
    }

    @Override
    public String getName() {
        return "Голос мертвих";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Ви говорите з душею цілі.\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§f☠ Мертві: душу видно в радіусі %d блоків\n" +
                        "§f  ✦ Душа віддає рецепт зілля — найцінніший із тих, яких ви не знаєте\n" +
                        "§c  ✦ Рецепт саме ПЕРЕХОДИТЬ до вас: ціль його втрачає\n" +
                        "§f❤ Живі: гравець у прицілі за %d блоків віддає своє досьє\n" +
                        "§c  ✦ Сильніша Послідовність відбиває допит: до %d збільшення порчи по вам\n",
                (int) SpiritMediumLore.voiceDeadRadius(userSequence),
                (int) SpiritMediumLore.voiceLivingRange(userSequence),
                SpiritMediumLore.voiceBacklashSanity(userSequence, Sequence.of(0)));
    }

    @Override
    public int getSpiritualityCost() {
        return SpiritMediumLore.VOICE_COST;
    }

    /**
     * ponytail: кулдаун один на дві гілки — беремо суворіший, мертвий (120 с проти 60 с у живої).
     * Роздільний вимагав би {@code setCooldown(ability, caster, seconds)} у {@link
     * me.vangoo.domain.abilities.context.ICooldownContext}; заводити API заради одного числа
     * не варто — якщо живий допит відчується задовгим, тоді й розширимо контекст.
     */
    @Override
    public int getCooldown(Sequence userSequence) {
        return SpiritMediumLore.voiceDeadCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();
        if (caster == null) {
            return AbilityResult.failure("Ви не в світі");
        }
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();

        // Живий у прицілі має пріоритет: він тут і зараз, душа ж нікуди не подінеться.
        Optional<Player> living = context.targeting()
                .getTargetedEntity(SpiritMediumLore.voiceLivingRange(sequence))
                .filter(Player.class::isInstance)
                .map(Player.class::cast);
        if (living.isPresent()) {
            return interrogateLiving(context, casterId, sequence, living.get());
        }

        Location origin = caster.getLocation();
        Optional<LingeringSouls.Soul> nearest =
                souls.near(origin, SpiritMediumLore.voiceDeadRadius(sequence), casterId).stream()
                        .min(Comparator.comparingDouble(s -> s.location().distanceSquared(origin)));
        if (nearest.isEmpty()) {
            return AbilityResult.failure("Поблизу немає душ, що затримались");
        }
        LingeringSouls.Soul soul = nearest.get();

        Optional<UnlockedRecipe> taken = context.beyonder().stealRecipe(soul.victimId(), casterId);
        if (taken.isEmpty()) {
            // Душу не споживаємо: вона мовчить не тому, що вже віддала, — їй просто нічого дати.
            return AbilityResult.failure("Душа " + soul.victimName()
                    + " не знає нічого, чого не знаєте ви");
        }

        souls.consume(soul.victimId());
        playInterrogation(context, caster, soul, taken.get());
        return AbilityResult.success();
    }

    /**
     * Допит живого: духовність і кулдаун списуються в обох випадках — спроба вже відбулась.
     * Сильніша ціль не віддає нічого й б'є по розсудку медіума.
     */
    private AbilityResult interrogateLiving(IAbilityContext context, UUID casterId,
                                            Sequence sequence, Player target) {
        UUID targetId = target.getUniqueId();
        Color deathColor = PathwayBranding.liquidOf("Death");
        Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);

        int backlash = targetBeyonder == null
                ? 0
                : SpiritMediumLore.voiceBacklashSanity(sequence, targetBeyonder.getSequence());
        if (backlash > 0) {
            context.beyonder().updateSanityLoss(casterId, backlash);
            context.effects().playAlertHalo(context.getCasterEyeLocation().clone().add(0, 0.8, 0),
                    deathColor);
            context.effects().playFadingAura(context.getCasterLocation(), deathColor, FAREWELL_TICKS);
            context.effects().playSoundForPlayer(casterId, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.6f);
            context.messaging().sendMessage(targetId, ChatColor.DARK_AQUA
                    + "✦ Чужа воля торкнулась вашого духу — і відсахнулась");
            return AbilityResult.successWithMessage(ChatColor.DARK_RED
                    + "✦ Дух цієї цілі сильніший за ваш: допит відкинуто, розсудок -" + backlash);
        }

        context.effects().playTravelingBeam(context.getCasterEyeLocation(), target.getEyeLocation(),
                deathColor, () -> {
                    context.effects().playFadingAura(target.getLocation(), deathColor, FAREWELL_TICKS);
                    context.effects().playSoundForPlayer(casterId,
                            Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.7f);
                    reportDossier(context, casterId, target, targetBeyonder);
                });
        context.messaging().sendMessage(targetId, ChatColor.DARK_AQUA
                + "✦ Хтось говорив із вашим духом — і ви не змогли змовчати");
        return AbilityResult.success();
    }

    /** Досьє: те, що дух цілі знає про власне тіло, звички й останню смерть. */
    private void reportDossier(IAbilityContext context, UUID casterId, Player target,
                               Beyonder targetBeyonder) {
        var data = context.playerData();
        UUID targetId = target.getUniqueId();

        context.messaging().sendMessage(casterId, ChatColor.DARK_AQUA
                + "════ Дух каже про " + ChatColor.WHITE + target.getName() + ChatColor.DARK_AQUA
                + " ════");
        context.messaging().sendMessage(casterId, ChatColor.GRAY + "✦ Шлях: " + ChatColor.WHITE
                + (targetBeyonder == null
                ? "звичайна людина"
                : targetBeyonder.getPathway().getName() + ", Послідовність "
                        + targetBeyonder.getSequenceLevel()));
        context.messaging().sendMessage(casterId, ChatColor.GRAY + String.format(
                "✦ Тіло: %.1f/%.1f ❤ · ситість %d", data.getHealth(targetId),
                data.getMaxHealth(targetId), data.getFoodLevel(targetId)));
        context.messaging().sendMessage(casterId, ChatColor.GRAY + String.format(
                "✦ Убивав гравців: %d · сам гинув: %d · у світі %d год",
                data.getPlayerKills(targetId), data.getDeathsCount(targetId),
                data.getPlayTimeHours(targetId)));
        context.messaging().sendMessage(casterId, ChatColor.GRAY + "✦ У руці: "
                + ChatColor.WHITE + data.getMainHandItemName(targetId));
        context.messaging().sendMessage(casterId, ChatColor.GRAY + "✦ Ліжко: "
                + ChatColor.WHITE + describe(data.getBedSpawnLocation(targetId)));
        context.messaging().sendMessage(casterId, ChatColor.GRAY + "✦ Остання смерть: "
                + ChatColor.WHITE + describe(data.getLastDeathLocation(targetId)));
    }

    /** Координати у вигляді, придатному для читання; невідоме місце — прочерк. */
    private String describe(Location location) {
        if (location == null || location.getWorld() == null) {
            return "невідомо";
        }
        return String.format("%s %d, %d, %d", location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Аура на душі + промінь від неї до медіума; знання приходить у мить влучання. */
    private void playInterrogation(IAbilityContext context, Player caster,
                                   LingeringSouls.Soul soul, UnlockedRecipe taken) {
        Color deathColor = PathwayBranding.liquidOf("Death");
        UUID casterId = caster.getUniqueId();
        Location from = soul.location().clone().add(0, 1.0, 0);

        context.effects().playFadingAura(soul.location(), deathColor, FAREWELL_TICKS);
        context.effects().playSound(soul.location(), Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.6f);
        context.effects().playTravelingBeam(from, caster.getEyeLocation(), deathColor, () -> {
            context.effects().playSoundForPlayer(casterId,
                    Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.7f);
            context.messaging().sendMessage(casterId, ChatColor.DARK_AQUA + "✦ Душа "
                    + ChatColor.WHITE + soul.victimName() + ChatColor.DARK_AQUA
                    + " віддала знання: " + ChatColor.GOLD + taken);
        });

        // Жертві — якщо онлайн; messaging сам мовчить для офлайнових.
        context.messaging().sendMessage(soul.victimId(), ChatColor.DARK_RED
                + "✦ Хтось говорив із вашою душею — рецепт " + ChatColor.GOLD + taken
                + ChatColor.DARK_RED + " вислизнув із пам'яті");
    }
}

// Свідомо БЕЗ cleanUp(): стану на кастера здібність не тримає, а реєстр душ спільний на шлях.
