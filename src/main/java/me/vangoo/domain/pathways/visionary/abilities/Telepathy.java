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
    public String getName() {
        return "Телепатія";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int seq = userSequence.level();
        int cost = (int) (BASE_COST / SequenceScaler.calculateMultiplier(seq, SequenceScaler.ScalingStrategy.WEAK));
        int cd = (int) (BASE_COOLDOWN / SequenceScaler.calculateMultiplier(seq, SequenceScaler.ScalingStrategy.MODERATE));

        return "Вимагає дотику. Ізолює розум цілі. Якщо ціль погодиться (Shift), ви дізнаєтесь 3 її сокровенні таємниці.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

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
            return AbilityResult.failure("Немає інгредієнтів (Свічка + Світлопил + Ферментоване око).");
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

        // Прихована жадібність — тепер вся логіка тут
        String greedAnalysis = buildGreedAnalysis(ctx, tId);
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

    private static class ResourceData {
        final String name;
        final ChatColor color;
        final Material oreType;
        final List<Material> usageItems;
        final double value;

        int mined;
        int used;

        ResourceData(String name, ChatColor color, Material oreType, List<Material> usageItems, double value) {
            this.name = name;
            this.color = color;
            this.oreType = oreType;
            this.usageItems = usageItems;
            this.value = value;
            this.mined = 0;
            this.used = 0;
        }

        int getBalance() {
            return mined - used;
        }

        double getHoardingScore() {
            if (mined == 0) return 0.0;
            return (double) getBalance() / (double) mined * value;
        }
    }

    private String buildGreedAnalysis(IAbilityContext ctx, UUID tId) {
        // Визначення ресурсів — та сама логіка як раніше
        List<ResourceData> resources = new ArrayList<>();

        resources.add(new ResourceData(
                "Незерит",
                ChatColor.DARK_PURPLE,
                Material.ANCIENT_DEBRIS,
                Arrays.asList(
                        Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE,
                        Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL,
                        Material.NETHERITE_HOE, Material.NETHERITE_HELMET,
                        Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS,
                        Material.NETHERITE_BOOTS, Material.NETHERITE_BLOCK
                ),
                10.0
        ));

        resources.add(new ResourceData(
                "Алмази",
                ChatColor.AQUA,
                Material.DIAMOND_ORE,
                Arrays.asList(
                        Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE,
                        Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL,
                        Material.DIAMOND_HOE, Material.DIAMOND_HELMET,
                        Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS,
                        Material.DIAMOND_BOOTS, Material.DIAMOND_BLOCK,
                        Material.ENCHANTING_TABLE, Material.JUKEBOX
                ),
                5.0
        ));

        resources.add(new ResourceData(
                "Емеральди",
                ChatColor.GREEN,
                Material.EMERALD_ORE,
                Arrays.asList(Material.EMERALD_BLOCK),
                7.0
        ));

        resources.add(new ResourceData(
                "Золото",
                ChatColor.GOLD,
                Material.GOLD_ORE,
                Arrays.asList(
                        Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE,
                        Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL,
                        Material.GOLDEN_HOE, Material.GOLDEN_HELMET,
                        Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS,
                        Material.GOLDEN_BOOTS, Material.GOLD_BLOCK,
                        Material.GOLDEN_APPLE, Material.CLOCK,
                        Material.POWERED_RAIL
                ),
                3.0
        ));

        resources.add(new ResourceData(
                "Залізо",
                ChatColor.WHITE,
                Material.IRON_ORE,
                Arrays.asList(
                        Material.IRON_SWORD, Material.IRON_PICKAXE,
                        Material.IRON_AXE, Material.IRON_SHOVEL,
                        Material.IRON_HOE, Material.IRON_HELMET,
                        Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS,
                        Material.IRON_BOOTS, Material.IRON_BLOCK,
                        Material.BUCKET, Material.SHEARS,
                        Material.FLINT_AND_STEEL, Material.IRON_DOOR,
                        Material.IRON_TRAPDOOR, Material.CAULDRON,
                        Material.HOPPER, Material.MINECART,
                        Material.RAIL, Material.ANVIL
                ),
                1.0
        ));

        // Заповнюємо mined/used через контекст (викликом базових методів)
        for (ResourceData r : resources) {
            int mined = 0;
            try {
                mined = ctx.getMinedAmount(tId, r.oreType);
            } catch (Exception ignored) {
                mined = 0;
            }
            r.mined = Math.max(0, mined);

            int usedSum = 0;
            for (Material item : r.usageItems) {
                try {
                    usedSum += ctx.getUsedAmount(tId, item);
                } catch (Exception ignored) { /* skip */ }
            }
            r.used = Math.max(0, usedSum);
        }

        // Видаляємо ресурси без видобутку
        resources.removeIf(r -> r.mined == 0);
        if (resources.isEmpty()) return null;

        ResourceData most = resources.stream()
                .max(Comparator.comparingDouble(ResourceData::getHoardingScore))
                .orElse(null);
        if (most == null) return null;

        int balance = most.getBalance();
        String behavior;
        ChatColor behaviorColor;

        if (balance > most.mined * 0.7) {
            behavior = "Скнара";
            behaviorColor = ChatColor.DARK_RED;
        } else if (balance > most.mined * 0.3) {
            behavior = "Економний";
            behaviorColor = ChatColor.YELLOW;
        } else if (balance >= 0) {
            behavior = "Раціональний";
            behaviorColor = ChatColor.GREEN;
        } else {
            behavior = "Марнотратний";
            behaviorColor = ChatColor.RED;
        }

        return ChatColor.GOLD + "💰 Економічний профіль: " + behaviorColor + behavior +
                ChatColor.GRAY + "\n   └─ " + most.color + most.name +
                ChatColor.GRAY + ": знайдено " + ChatColor.WHITE + most.mined +
                ChatColor.GRAY + ", витрачено " + ChatColor.WHITE + most.used +
                ChatColor.GRAY + " (баланс: " + (balance >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED) +
                balance + ChatColor.GRAY + ")";
    }
}