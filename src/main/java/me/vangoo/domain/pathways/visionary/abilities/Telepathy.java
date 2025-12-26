package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Telepathy extends ActiveAbility {

    private static final int RANGE = 2;
    private static final int BASE_COST = 80;
    private static final int BASE_COOLDOWN = 60;
    private static final int WAIT_TIME_SECONDS = 5;

    private static final Material CATALYST = Material.FERMENTED_SPIDER_EYE;
    private static final Material REAGENT = Material.GLOWSTONE_DUST;

    @Override
    public String getName() { return "Телепатія"; }

    @Override
    public String getDescription(Sequence userSequence) {
        int seq = userSequence.level();
        int cost = (int) (BASE_COST / SequenceScaler.calculateMultiplier(seq, SequenceScaler.ScalingStrategy.WEAK));
        int cd = (int) (BASE_COOLDOWN / SequenceScaler.calculateMultiplier(seq, SequenceScaler.ScalingStrategy.MODERATE));

        return "Вимагає дотику. Ізолює розум цілі. Якщо ціль погодиться (Shift), ви дізнаєтесь 3 її сокровенні таємниці.";
    }

    @Override
    public int getSpiritualityCost() { return BASE_COST; }

    @Override
    public int getCooldown(Sequence userSequence) {
        return (int) (BASE_COOLDOWN / SequenceScaler.calculateMultiplier(
                userSequence.level(),
                SequenceScaler.ScalingStrategy.MODERATE
        ));
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(RANGE);

        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Потрібно підійти впритул до живої цілі.");
        }

        LivingEntity target = targetOpt.get();
        UUID tId = target.getUniqueId();

        if (tId.equals(context.getCasterId())) {
            return AbilityResult.failure("Не можна читати свої думки.");
        }

        if (!context.hasItem(CATALYST, 1) || !context.hasItem(REAGENT, 1)) {
            return AbilityResult.failure("Немає інгредієнтів (Свічка + Світлопил).");
        }

        // ВИПРАВЛЕННЯ: витрачаємо обидва інгредієнти
        context.consumeItem(CATALYST, 1);
        context.consumeItem(REAGENT, 1);

        int seq = context.getCasterBeyonder().getSequenceLevel();
        int waitTicks = WAIT_TIME_SECONDS * 20;

        // SCALING: тривалість ефектів залежить від послідовності
        double multiplier = SequenceScaler.calculateMultiplier(seq, SequenceScaler.ScalingStrategy.MODERATE);
        int scaledWaitTicks = (int) (waitTicks * multiplier);

        // Ізоляція цілі
        context.applyEffect(tId, PotionEffectType.BLINDNESS, scaledWaitTicks, 255);
        context.applyEffect(tId, PotionEffectType.SLOWNESS, scaledWaitTicks, 255);

        context.playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
        context.playSound(target.getLocation(), Sound.AMBIENT_CAVE, 1f, 1f);

        context.sendMessage(tId, ChatColor.DARK_GRAY + "░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░");
        context.sendMessage(tId, ChatColor.DARK_PURPLE + " 👁 ВАШ РОЗУМ ІЗОЛЬОВАНО 👁");
        context.sendMessage(tId, ChatColor.GRAY + " Хтось торкнувся вашої свідомості.");
        context.sendMessage(tId, "");
        context.sendMessage(tId, ChatColor.GREEN + " ЗАТИСНІТЬ [SHIFT] " + ChatColor.GRAY + "(5 сек) -> Згода (Лікування).");
        context.sendMessage(tId, ChatColor.RED + " НІЧОГО НЕ РОБІТЬ" + ChatColor.GRAY + " -> Опір (Біль + Дебафи).");
        context.sendMessage(tId, ChatColor.DARK_GRAY + "░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░");

        context.sendMessageToCaster(ChatColor.YELLOW + "Контакт встановлено. Очікування реакції...");

        context.monitorSneaking(tId, scaledWaitTicks, (accepted) -> {
            finishAbility(context, target, accepted, seq);
        });

        return AbilityResult.success();
    }

    private void finishAbility(IAbilityContext ctx, LivingEntity target, boolean accepted, int seq) {
        UUID tId = target.getUniqueId();
        double multiplier = SequenceScaler.calculateMultiplier(seq, SequenceScaler.ScalingStrategy.MODERATE);

        if (accepted) {
            // === ЗГОДА ===
            List<String> facts = collectSubconsciousSecrets(ctx, tId);

            // SCALING: бонуси сильніші для вищих послідовностей
            int sanityBonus = (int) (-10 * multiplier);
            int regenDuration = (int) (9600 * multiplier);
            int regenAmplifier = Math.min((int) multiplier - 1, 2); // 0-2

            ctx.updateSanityLoss(tId, sanityBonus);
            ctx.applyEffect(tId, PotionEffectType.REGENERATION, regenDuration, regenAmplifier);

            ctx.sendMessage(tId, ChatColor.GREEN + "✔ Ви впустили Візіонера. Розум прояснився.");
            ctx.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);

            ctx.sendMessageToCaster(ChatColor.GREEN + "Ціль відкрилась. Вихоплено фрагменти пам'яті.");
            displayResults(ctx, facts, true, target.getName());

        } else {
            // === ВІДМОВА ===
            List<String> facts = collectSurfaceThoughts(ctx, tId);

            // SCALING: покарання жорсткіше для вищих послідовностей
            int sanityPenalty = (int) (10 * multiplier);
            double damage = 1.0 * multiplier;
            int debuffDuration = (int) (2400 * multiplier);
            int debuffAmplifier = Math.min((int) multiplier - 1, 3); // 0-3

            ctx.updateSanityLoss(tId, sanityPenalty);
            ctx.damage(tId, damage);
            ctx.applyEffect(tId, PotionEffectType.SLOWNESS, debuffDuration, debuffAmplifier);
            ctx.applyEffect(tId, PotionEffectType.WEAKNESS, debuffDuration, debuffAmplifier);

            ctx.sendMessage(tId, ChatColor.RED + "✖ Ви виштовхнули вторгнення силою.");
            ctx.playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);

            ctx.sendMessageToCaster(ChatColor.RED + "Ціль опиралась. Доступні лише уривки.");
            displayResults(ctx, facts, false, target.getName());
        }
    }

    private void displayResults(IAbilityContext ctx, List<String> facts, boolean deep, String targetName) {
        if (facts.isEmpty()) {
            ctx.sendMessageToCaster(ChatColor.GRAY + "Думок не виявлено.");
            return;
        }

        String color = deep ? ChatColor.LIGHT_PURPLE.toString() : ChatColor.BLUE.toString();
        String type = deep ? "ГЛИБИННІ ТАЄМНИЦІ" : "ПОВЕРХНЕВІ ДУМКИ";

        ctx.sendMessageToCaster(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        ctx.sendMessageToCaster(color + ChatColor.BOLD + " " + type);
        ctx.sendMessageToCaster(ChatColor.GRAY + " Ціль: " + ChatColor.WHITE + targetName);
        ctx.sendMessageToCaster(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Завжди показуємо 3 випадкові факти
        Collections.shuffle(facts);
        int count = 0;

        for (String fact : facts) {
            if (count >= 3) break;

            // Якщо це факт з ендер-скринею, виводимо повний список
            if (fact.startsWith("ENDER_CHEST:")) {
                String[] parts = fact.split(":", 2);
                if (parts.length == 2) {
                    ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "┌ " + ChatColor.BOLD + "Вміст Ендер-скрині:");

                    String[] items = parts[1].split("\\|");
                    for (String item : items) {
                        if (!item.trim().isEmpty()) {
                            ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "│ " + ChatColor.LIGHT_PURPLE + "  • " + item.trim());
                        }
                    }

                    ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "└" + ChatColor.GRAY + " (всього: " + items.length + " типів)");
                }
            } else {
                ctx.sendMessageToCaster(ChatColor.GRAY + "• " + ChatColor.WHITE + fact);
            }

            count++;
        }

        ctx.sendMessageToCaster(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private List<String> collectSurfaceThoughts(IAbilityContext ctx, UUID tId) {
        List<String> facts = new ArrayList<>();
        facts.add("🏠 Дім: " + formatLoc(ctx.getBedSpawnLocation(tId)));
        facts.add("⌚ Час у грі: " + ctx.getPlayTimeHours(tId) + " год.");
        facts.add("🗡 Тримає в руці: " + ctx.getMainHandItemName(tId));
        facts.add("☠ Кількість смертей: " + ctx.getDeathsStatistic(tId));
        return facts;
    }

    private List<String> collectSubconsciousSecrets(IAbilityContext ctx, UUID tId) {
        List<String> secrets = new ArrayList<>();

        // Статистика
        secrets.add(ChatColor.GOLD + "Рівень досвіду: " + ctx.getExperienceLevel(tId));
        secrets.add(ChatColor.GOLD + "Засвоєння : " + ctx.getBeyonderMastery(tId));
        secrets.add(ChatColor.RED + "Вбито гравців: " + ctx.getPlayerKills(tId));

        // Прихована жадібність
        String greedAnalysis = ctx.analyzeGreed(tId);
        if (greedAnalysis != null && !greedAnalysis.isEmpty()) {
            secrets.add(greedAnalysis);
        } else {
            secrets.add(ChatColor.YELLOW + "Економічний профіль: " + ChatColor.GRAY + "Дані відсутні");
        }

        String deathLoc = formatLoc(ctx.getLastDeathLocation(tId));
        secrets.add(ChatColor.DARK_RED + "Місце останньої смерті: " + deathLoc);

        // Ендер-скриня як ОДИН факт
        List<String> enderItems = ctx.getEnderChestContents(tId, 999);

        if (enderItems.isEmpty()) {
            secrets.add(ChatColor.DARK_PURPLE + "Ендер-скриня: " + ChatColor.GRAY + "Пусто");
        } else {
            // Об'єднуємо всі предмети в один рядок через роздільник
            String combined = String.join(" | ", enderItems);
            secrets.add("ENDER_CHEST:" + combined);
        }

        return secrets;
    }

    private String formatLoc(org.bukkit.Location loc) {
        if (loc == null) return "Невідомо";
        return loc.getWorld().getName() + " [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]";
    }
}