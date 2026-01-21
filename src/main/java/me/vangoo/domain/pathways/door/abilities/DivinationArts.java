package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DivinationArts extends ActiveAbility {
    private int BASE_COST = 120;
    private final int BASE_COOLDOWN = 60;
    private final int ANTI_DIVINATION_UNLOCK_SEQUENCE = 7;
    private final int DIVINING_ROD_DURATION_TICKS = 1200; // 20 секунд замість 30

    private final List<PendulumQuestion> pendulumQuestions = new ArrayList<>();
    private final List<DivinationTarget> diviningRodTargets = new ArrayList<>();
    private final Random rng = new Random();
    private final Random chanceRng = new Random();

    public DivinationArts() {
        initPendulumQuestions();
        initDiviningRodTargets();
    }

    public DivinationArts(int spiritualityCost) {
        initPendulumQuestions();
        initDiviningRodTargets();
        BASE_COST = spiritualityCost;
    }

    // ========== ІНІЦІАЛІЗАЦІЯ ==========
    private void initPendulumQuestions() {
        pendulumQuestions.add(new PendulumQuestion(
                "Чи є поблизу діаманти?",
                ctx -> findNearbyOre(ctx, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE) != null
                        ? "Так — діаманти знайдено поблизу (в радіусі 50 блоків)"
                        : "Ні — діамантів не виявлено в околиці"
        ));

        pendulumQuestions.add(new PendulumQuestion(
                "Чи належить цей інгредієнт до мого шляху?",
                ctx -> {
                    Beyonder beyonder = ctx.getCasterBeyonder();
                    ItemStack handItem = ctx.playerData().getMainHandItem(ctx.getCasterId());

                    if (handItem.getType() == Material.AIR) {
                        return "Ні — ви нічого не тримаєте в руці";
                    }

                    // Використовуємо метод з контексту для перевірки інгредієнта
                    for (int seq = 9; seq >= 0; seq--) {
                        var ingredients = ctx.beyonder().getIngredientsForPotion(beyonder.getPathway(), Sequence.of(seq));
                        if (ingredients != null) {
                            for (ItemStack ingredient : ingredients) {
                                if (ingredient != null && ingredient.isSimilar(handItem)) {
                                    String seqName = beyonder.getPathway().getSequenceName(seq);
                                    return "Так — цей інгредієнт резонує з " + beyonder.getPathway().getName() +
                                            " (Послідовність " + seq + ": " + seqName + ")";
                                }
                            }
                        }
                    }
                    return "Ні — цей предмет не належить до шляху " + beyonder.getPathway().getName();
                }
        ));

        pendulumQuestions.add(new PendulumQuestion(
                "Чи є поблизу інші Beyonder'и?",
                ctx -> {
                    List<Player> nearbyPlayers = ctx.targeting().getNearbyPlayers(30);
                    int beyonderCount = 0;

                    for (Player p : nearbyPlayers) {
                        if (ctx.beyonder().isBeyonder(p.getUniqueId())) {
                            beyonderCount++;
                        }
                    }

                    if (beyonderCount == 0) {
                        return "Ні — навколо лише звичайні люди";
                    } else if (beyonderCount == 1) {
                        return "Так — відчувається присутність одного Beyonder'а";
                    } else {
                        return "Так — поблизу " + beyonderCount + " Beyonder'ів, будьте обережні";
                    }
                }
        ));

        pendulumQuestions.add(new PendulumQuestion(
                "Чи є тут сліди недавніх подій?",
                ctx -> {
                    Location loc = ctx.getCasterLocation();
                    List<RecordedEvent> events = ctx.events().getPastEvents(loc, 10, 300); // 5 хвилин

                    if (events.isEmpty()) {
                        return "Ні — це місце спокійне, нічого не відбувалося";
                    }

                    long recentEvents = events.stream()
                            .filter(e -> System.currentTimeMillis() - e.getTimestamp() < 60000) // Остання хвилина
                            .count();

                    if (recentEvents > 0) {
                        return "Так — духовні сліди свіжі, щось відбулося зовсім недавно";
                    } else {
                        return "Так — відчуваються відлуння минулих подій";
                    }
                }
        ));

        pendulumQuestions.add(new PendulumQuestion(
                "Чи має цей гравець високу послідовність?",
                ctx -> {
                    Optional<Player> targetOpt = ctx.targeting().getTargetedPlayer(30);

                    if (targetOpt.isEmpty()) {
                        return "Ні — ви не дивитесь ні на кого";
                    }

                    Player target = targetOpt.get();
                    Beyonder caster = ctx.getCasterBeyonder();

                    if (!ctx.beyonder().isBeyonder(target.getUniqueId())) {
                        return "Ні — це звичайна людина без духовної сили";
                    }

                    Beyonder targetBeyonder =  ctx.beyonder().getBeyonder(target.getUniqueId());
                    if (targetBeyonder == null) {
                        return "Невідомо — не вдається прочитати їхню ауру";
                    }

                    int targetSeq = targetBeyonder.getSequenceLevel();
                    int casterSeq = caster.getSequenceLevel();

                    if (targetSeq < casterSeq) {
                        return "Так — їхня духовна аура значно сильніша за вашу, будьте обережні!";
                    } else if (targetSeq == casterSeq) {
                        return "Можливо — вони на вашому рівні, рівний супротивник";
                    } else {
                        return "Ні — їхня сила слабша за вашу";
                    }
                }
        ));

        pendulumQuestions.add(new PendulumQuestion(
                "Чи готовий я до просування послідовності?",
                ctx -> {
                    Beyonder beyonder = ctx.getCasterBeyonder();

                    if (!beyonder.canAdvance()) {
                        double mastery = beyonder.getMastery().value();
                        if (mastery < 50.0) {
                            return "Ні — ваше засвоєння занадто низьке (" + String.format("%.1f%%", mastery) + "), потрібно більше практики";
                        } else if (mastery < 80.0) {
                            return "Майже — засвоєння " + String.format("%.1f%%", mastery) + ", ще трохи практики";
                        } else {
                            return "Майже — засвоєння високе (" + String.format("%.1f%%", mastery) + "), але досі недостатнє";
                        }
                    }

                    int currentSeq = beyonder.getSequenceLevel();
                    if (currentSeq == 0) {
                        return "Так — ви досягли вершини, але це кінець вашого шляху";
                    }

                    int spirituality = beyonder.getSpiritualityValue();
                    int maxSpirituality = beyonder.getMaxSpirituality();
                    double spiritualityPercent = (spirituality * 100.0) / maxSpirituality;

                    if (spiritualityPercent < 80.0) {
                        return "Так, але — ваша духовність занадто низька (" + String.format("%.0f%%", spiritualityPercent) + "), відновіться перед ритуалом";
                    }

                    return "Так — ви готові до ритуалу просування, знайдіть відповідне зілля";
                }
        ));
    }
    private void initDiviningRodTargets() {
        // Послідовність 9: Базові ресурси
        diviningRodTargets.add(new DivinationTarget("Залізо", 9, Material.IRON_INGOT, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE));
        diviningRodTargets.add(new DivinationTarget("Золото", 9, Material.GOLD_INGOT, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE));
        diviningRodTargets.add(new DivinationTarget("Редстоун", 9, Material.REDSTONE, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE));
        diviningRodTargets.add(new DivinationTarget("Лазурит", 9, Material.LAPIS_LAZULI, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE));
        diviningRodTargets.add(new DivinationTarget("Вугілля", 9, Material.COAL, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE));
        diviningRodTargets.add(new DivinationTarget("Портал Незер", 9, Material.OBSIDIAN, Material.NETHER_PORTAL));

        // Послідовність 8: + Смарагди
        diviningRodTargets.add(new DivinationTarget("Смарагди", 8, Material.EMERALD, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE));

        // Послідовність 7: + Діаманти
        diviningRodTargets.add(new DivinationTarget("Діаманти", 7, Material.DIAMOND, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE));

        // Послідовність 5: Древні уламки (ексклюзивно)
        diviningRodTargets.add(new DivinationTarget("Стародавні уламки", 5, Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS));
    }

    // ========== ЛОГІКА ЗДІБНОСТІ ==========

    @Override
    public String getName() {
        return "Мистецтво ворожіння";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Володіння мистецтвом гадання. Відкриває доступ до різних методів " +
                "передбачення: кристальний шар, астрологія, маятник, лозошукання та сонне провидіння." +
                "\n§7§oЛозошукання покращується з просуванням послідовності.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        openMainDivinationMenu(context);
        return AbilityResult.deferred();
    }

    // ========== МЕНЮ ==========

    private void openMainDivinationMenu(IAbilityContext ctx) {
        List<DivinationType> types = Arrays.asList(DivinationType.values());
        ctx.ui().openChoiceMenu(
                "Мистецтво Гадання",
                types,
                this::createDivinationTypeItem,
                type -> handleDivinationChoice(ctx, type)
        );
    }

    private ItemStack createDivinationTypeItem(DivinationType type) {
        ItemStack item = new ItemStack(type.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(type.color + type.displayName);
            meta.setLore(Collections.singletonList(ChatColor.GRAY + type.description));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleDivinationChoice(IAbilityContext ctx, DivinationType type) {
        switch (type) {
            case CRYSTAL_BALL -> performCrystalBallDivination(ctx);
            case ASTROLOGY -> performAstrologyDivination(ctx);
            case PENDULUM -> openPendulumMenu(ctx);
            case DIVINING_ROD -> openDiviningRodMenu(ctx);
            case DREAM_VISION -> openDreamVisionMenu(ctx);
        }
    }

    // ========== КЛЮЧОВИЙ МЕТОД ==========

    private boolean rollDivinationAgainstTarget(IAbilityContext ctx, UUID targetId) {
        UUID casterId = ctx.getCasterId();
        boolean active1 = ctx.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("Anti Divination"));
        boolean active2 = ctx.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("Anti-Divination"));
        boolean active3 = ctx.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("anti_divination"));
        boolean active4 = ctx.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("AntiDivination"));

        boolean isAntiToggledOn = active1 || active2 || active3 || active4;

        // Використовуємо новий спосіб отримати level через контекст
        int targetSeq = sequenceLevelOrDefault(ctx, targetId, 9);
        boolean isLevelAppropriate = targetSeq <= ANTI_DIVINATION_UNLOCK_SEQUENCE;
        boolean hasResistance = isAntiToggledOn && isLevelAppropriate;

        if (!hasResistance) {
            ctx.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "Шанс успіху гадання: " + ChatColor.AQUA + "100%"));
            return true;
        }

        int casterSeq = sequenceLevelOrDefault(ctx, casterId, 9);

        SequenceBasedSuccessChance seqChance = new SequenceBasedSuccessChance(casterSeq, targetSeq);
        double baseChance = seqChance.calculateChance();
        double finalChance = baseChance;

        int diff = seqChance.getSequenceDifference();
        boolean casterAdvantaged = seqChance.isCasterAdvantaged();

        if (casterAdvantaged) {
            double penalty = Math.min(0.2, diff * 0.03);
            finalChance = baseChance * (1.0 - penalty);
            finalChance = Math.max(0.75, finalChance);
        } else {
            double dynamic = 1.0 - 0.35 * diff;
            finalChance = baseChance * Math.max(0.05, dynamic);

            if (seqChance.isLargeDifference()) {
                finalChance = Math.min(finalChance, 0.5 * baseChance);
            }
        }

        finalChance = Math.max(0.0, Math.min(1.0, finalChance));

        ctx.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "Шанс успіху гадання: " +
                ChatColor.AQUA + String.format("%.0f%%", finalChance * 100)));

        return chanceRng.nextDouble() < finalChance;
    }

    /**
     * Helper: отримує sequence level через контекст або повертає дефолт,
     * якщо суб'єкт не є Beyonder або інформація недоступна.
     */
    private int sequenceLevelOrDefault(IAbilityContext ctx, UUID entityId, int defaultLevel) {
        if (entityId == null) return defaultLevel;

        // Якщо контекст має методи isBeyonder/getBeyonder — використовуємо їх
        try {
            if (ctx.beyonder().isBeyonder(entityId)) { // якщо IAbilityContext наслідує IBeyonderContext
                Beyonder b = ctx.beyonder().getBeyonder(entityId);
                if (b != null) {
                    return b.getSequenceLevel();
                }
            }
        } catch (NoSuchMethodError | AbstractMethodError e) {
            // Якщо IAbilityContext НЕ має цих методів в runtime — тихо падаємо в наступні варіанти
        } catch (Exception ignored) {}


        // Фінальний fallback
        return defaultLevel;
    }


    // ========== 1. КРИШТАЛЕВА КУЛЯ ==========

    private void performCrystalBallDivination(IAbilityContext ctx) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();
        UUID casterId = ctx.getCasterId();
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.messaging().sendMessageToActionBar(casterId, Component.text((ChatColor.RED + "Недостатньо духовності!")));
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        Player caster = ctx.getCasterPlayer();
        ctx.effects().playSoundForPlayer(casterId, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        ctx.effects().spawnParticle(Particle.END_ROD, ctx.getCasterLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5);

        List<Player> onlinePlayers = new ArrayList<>(ctx.targeting().getNearbyPlayers(10000));
        onlinePlayers.removeIf(p -> p.equals(caster));

        if (onlinePlayers.isEmpty()) {
            revealWeatherPrediction(ctx);
            return;
        }

        Player target = onlinePlayers.get(rng.nextInt(onlinePlayers.size()));
        boolean success = rollDivinationAgainstTarget(ctx, target.getUniqueId());

        if (success) {
            revealPlayerInfo(ctx, target);
        } else {
            revealWeatherPrediction(ctx);
        }
    }

    private void revealPlayerInfo(IAbilityContext ctx, Player target) {
        UUID casterId = ctx.getCasterId();
        for (int i = 0; i < 3; i++) {
            final int tick = i;
            ctx.scheduling().scheduleDelayed(() -> {
                ctx.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.5f + (tick * 0.2f));
                ctx.effects().spawnParticle(Particle.ENCHANT, ctx.getCasterLocation().add(0, 2, 0), 20, 0.3, 0.3, 0.3);
            }, i * 10L);
        }

        ctx.scheduling().scheduleDelayed(() -> {
            Map<String, String> analysis = ctx.playerData().getTargetAnalysis(target.getUniqueId());

            ctx.messaging().sendMessage(casterId,ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.messaging().sendMessage(casterId,ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🔮 БАЧЕННЯ КРИСТАЛЬНОГО ШАРУ");
            ctx.messaging().sendMessage(casterId,ChatColor.GRAY + "Ціль: " + ChatColor.WHITE + target.getName());
            ctx.messaging().sendMessage(casterId,"");

            analysis.forEach((key, value) -> ctx.messaging().sendMessage(casterId,ChatColor.GRAY + "  " + key + ": " + ChatColor.AQUA + value));

            ctx.messaging().sendMessage(casterId,ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.effects().playSoundForPlayer(casterId, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.8f);
        }, 30L);
    }

    private void revealWeatherPrediction(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();
        World world = ctx.getCasterPlayer().getWorld();
        long timeUntilClear = world.getClearWeatherDuration();
        long timeUntilRain = world.getWeatherDuration();
        boolean isRaining = world.hasStorm();

        ctx.scheduling().scheduleDelayed(() -> {
            ctx.messaging().sendMessage(casterId,ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.messaging().sendMessage(casterId,ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🔮 МЕТЕОРОЛОГІЧНЕ ПРОРОЦТВО");
            ctx.messaging().sendMessage(casterId,"");

            if (isRaining) {
                int minutesLeft = (int) (timeUntilClear / 20 / 60);
                ctx.messaging().sendMessage(casterId,ChatColor.GRAY + "Зараз: " + ChatColor.BLUE + "Дощ");
                ctx.messaging().sendMessage(casterId,ChatColor.GRAY + "Тривалість: " + ChatColor.AQUA + minutesLeft + " хв");
            } else {
                int minutesUntil = (int) (timeUntilRain / 20 / 60);
                ctx.messaging().sendMessage(casterId,ChatColor.GRAY + "Наступний дощ через: " + ChatColor.YELLOW + minutesUntil + " хв");
            }

            ctx.messaging().sendMessage(casterId,ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.effects().playSoundForPlayer(casterId,Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        }, 30L);
    }

    // ========== 2. АСТРОЛОГІЯ ==========

    private void performAstrologyDivination(IAbilityContext ctx) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();
        UUID casterId = ctx.getCasterId();
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.messaging().sendMessage(casterId,ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        for (int i = 0; i < 5; i++) {
            final int tick = i;
            ctx.scheduling().scheduleDelayed(() -> {
                Location loc = ctx.getCasterLocation().add(
                        Math.cos(tick) * 2, 2 + tick * 0.3, Math.sin(tick) * 2
                );
                ctx.effects().spawnParticle(Particle.END_ROD, loc, 5, 0.1, 0.1, 0.1);
            }, i * 5L);
        }

        ctx.effects().playSoundForPlayer(casterId,Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        boolean positive = rng.nextBoolean();

        ctx.scheduling().scheduleDelayed(() -> {
            if (positive) {
                ctx.entity().applyPotionEffect(ctx.getCasterId(), PotionEffectType.HASTE, 12000, 0);
                ctx.messaging().sendMessage(casterId,ChatColor.GREEN + "✦ Зірки прихильні до вас!");
                ctx.messaging().sendMessage(casterId,ChatColor.GRAY + "Квапливість +1 (10 хв)");
                ctx.effects().playSoundForPlayer(casterId,Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            } else {
                ctx.entity().applyPotionEffect(ctx.getCasterId(), PotionEffectType.WEAKNESS, 12000, 0);
                ctx.messaging().sendMessage(casterId,ChatColor.RED + "✦ Зірки застерігають...");
                ctx.messaging().sendMessage(casterId,ChatColor.GRAY + "Слабкість +1 (10 хв)");
                ctx.effects().playSoundForPlayer(casterId,Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1f);
            }
        }, 40L);
    }

    // ========== 3. МАЯТНИК ==========

    private void openPendulumMenu(IAbilityContext ctx) {
        ctx.ui().openChoiceMenu(
                "Духовний Маятник",
                pendulumQuestions,
                this::createPendulumQuestionItem,
                question -> performPendulumDivination(ctx, question)
        );
    }

    private ItemStack createPendulumQuestionItem(PendulumQuestion question) {
        ItemStack item = new ItemStack(Material.IRON_CHAIN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + question.question);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void performPendulumDivination(IAbilityContext ctx, PendulumQuestion question) {
        UUID casterId = ctx.getCasterId();
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }

        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        for (int i = 0; i < 4; i++) {
            ctx.scheduling().scheduleDelayed(
                    () -> ctx.effects().playSoundForPlayer(
                            casterId,
                            Sound.BLOCK_NOTE_BLOCK_BELL,
                            0.3f,
                            1.5f
                    ),
                    i * 8L
            );
        }

        ctx.scheduling().scheduleDelayed(() -> {
            String answer = question.logic.apply(ctx);

            ctx.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
            ctx.messaging().sendMessage(casterId, ChatColor.GOLD + "❓ " + question.question);
            ctx.messaging().sendMessage(casterId, "");
            ctx.messaging().sendMessage(casterId, ChatColor.YELLOW + answer);
            ctx.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");

            ctx.effects().playSoundForPlayer(
                    casterId,
                    Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                    1f,
                    1.2f
            );
        }, 40L);
    }


    // ========== 4. ЛОЗОШУКАННЯ ==========

    private void openDiviningRodMenu(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();
        Beyonder beyonder = ctx.getCasterBeyonder();

        int casterSequence = beyonder.getSequenceLevel();
        List<DivinationTarget> availableTargets = getAvailableTargetsForSequence(casterSequence);

        if (availableTargets.isEmpty()) {
            ctx.messaging().sendMessage(
                    casterId,
                    ChatColor.RED + "На вашому рівні немає доступних цілей для лозошукання"
            );
            return;
        }

        ctx.ui().openChoiceMenu(
                "Лозошукання",
                availableTargets,
                this::createDiviningRodTargetItem,
                target -> startDiviningRodTracking(ctx, target)
        );
    }


    /**
     * Отримати доступні цілі для лозошукання в залежності від послідовності
     * Послідовність 9: Базові ресурси (залізо, золото, редстоун, лазурит, вугілля, портал)
     * Послідовність 8: + Смарагди
     * Послідовність 7: + Діаманти
     * Послідовність 5 та нижче: + стародавні уламки
     */
    private List<DivinationTarget> getAvailableTargetsForSequence(int sequence) {
        // Послідовність 5 і нижче - всі ресурси включно зі стародавніми уламками
        if (sequence <= 5) {
            return diviningRodTargets.stream()
                    .filter(target -> target.requiredSequence >= 5)
                    .collect(Collectors.toList());
        }

        // Для вищих послідовностей - цілі з відповідним або вищим рівнем вимог
        return diviningRodTargets.stream()
                .filter(target -> target.requiredSequence >= sequence)
                .collect(Collectors.toList());
    }

    private ItemStack createDiviningRodTargetItem(DivinationTarget target) {
        ItemStack item = new ItemStack(target.iconMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Шукати: " + target.name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startDiviningRodTracking(IAbilityContext ctx, DivinationTarget target) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();
        UUID casterId = ctx.getCasterId();

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        Player caster = ctx.getCasterPlayer();

        ctx.messaging().sendMessage(casterId, ChatColor.GREEN + "🔍 Лозошукальний стрижень активовано!");
        ctx.messaging().sendMessage(casterId, ChatColor.GRAY + "Шукаємо: " + ChatColor.GOLD + target.name);

        ctx.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.3f);

        Location nearest = findNearestBlock(ctx, target);

        if (nearest == null) {
            ctx.messaging().sendMessage(casterId, ChatColor.YELLOW + "⚠ Нічого не знайдено в радіусі 50 блоків");
            return;
        }

        ctx.messaging().sendMessage(casterId, ChatColor.AQUA + "✓ Ціль виявлено! Стрілка вказує шлях...");

        final int[] ticks = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        final boolean[] completed = {false};

        Color arrowColor = getColorForTarget(target.name);
        Particle.DustOptions dustOptions = new Particle.DustOptions(arrowColor, 1.2f);

        holder[0] = ctx.scheduling().scheduleRepeating(() -> {
            if (!caster.isOnline() || completed[0]) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }

            Location playerLoc = caster.getLocation();
            double distance = playerLoc.distance(nearest);

            // ВИПРАВЛЕННЯ: Успішне завершення в радіусі 10 блоків
            if (distance <= 6.0) {
                completed[0] = true;
                if (holder[0] != null) holder[0].cancel();

                ctx.messaging().sendMessage(casterId, ChatColor.GREEN + "════════════════════════");
                ctx.messaging().sendMessage(casterId, ChatColor.GREEN + "" + ChatColor.BOLD + "✓ ВИ В РАЙОНІ ЦІЛІ!");
                ctx.messaging().sendMessage(casterId, ChatColor.GRAY + "Шукане: " + ChatColor.GOLD + target.name);
                ctx.messaging().sendMessage(casterId, ChatColor.YELLOW + "📍 Ресурс знаходиться в радіусі ~6 блоків");
                ctx.messaging().sendMessage(casterId, ChatColor.GREEN + "════════════════════════");
                ctx.effects().playSoundForPlayer(casterId, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                ctx.effects().playSoundForPlayer(casterId, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

                // Показуємо пульсуючий ефект навколо ГРАВЦЯ (а не точної локації)
                Particle.DustOptions finalDust = new Particle.DustOptions(Color.LIME, 2.0f);
                for (int i = 0; i < 5; i++) {
                    final int iteration = i;
                    ctx.scheduling().scheduleDelayed(() -> {
                        Location playerCenter = caster.getLocation();
                        for (int angle = 0; angle < 360; angle += 10) {
                            double rad = Math.toRadians(angle);
                            double radius = 2.0 + iteration * 0.5; // Зростаючий радіус
                            double x = Math.cos(rad) * radius;
                            double z = Math.sin(rad) * radius;
                            Location particleLoc = playerCenter.clone().add(x, 0, z);
                            caster.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, finalDust);
                        }
                        ctx.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f + (iteration * 0.2f));
                    }, i * 5L);
                }
                return;
            }

            // Максимальна тривалість - 60 секунд
            if (ticks[0]++ >= DIVINING_ROD_DURATION_TICKS) {
                if (holder[0] != null) holder[0].cancel();
                ctx.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "Лозошукання завершено (час вийшов)"));
                return;
            }

            // Стрілка вказує напрямок
            Vector direction = nearest.clone().add(0.5, 0.5, 0.5).toVector()
                    .subtract(caster.getEyeLocation().toVector()).normalize();
            Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
            right.normalize();
            Vector thickness = right.clone().multiply(0.15);

            World world = caster.getWorld();
            double sideOffset = 0.5;
            double forwardOffset = 0.5;
            double upOffset = -0.1;

            Location arrowStart = caster.getEyeLocation().clone()
                    .add(direction.clone().multiply(forwardOffset))
                    .add(right.clone().multiply(sideOffset))
                    .add(0, upOffset, 0);

            // Малюємо тіло стрілки
            for (int i = 0; i < 10; i++) {
                double offset = i * 0.25;
                Location point = arrowStart.clone().add(direction.clone().multiply(offset));
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
                world.spawnParticle(Particle.DUST, point.clone().add(thickness), 1, 0, 0, 0, 0, dustOptions);
                world.spawnParticle(Particle.DUST, point.clone().subtract(thickness), 1, 0, 0, 0, 0, dustOptions);
            }

            // Наконечник стрілки
            Location tipBase = arrowStart.clone().add(direction.clone().multiply(2.5));
            Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

            for (int i = 0; i < 5; i++) {
                double t = i / 5.0;
                Location tipPoint = tipBase.clone()
                        .add(direction.clone().multiply(-0.4 * t))
                        .add(perpendicular.clone().multiply(0.4 * (1 - t)));
                world.spawnParticle(Particle.DUST, tipPoint, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
            }

            for (int i = 0; i < 5; i++) {
                double t = i / 5.0;
                Location tipPoint = tipBase.clone()
                        .add(direction.clone().multiply(-0.4 * t))
                        .subtract(perpendicular.clone().multiply(0.4 * (1 - t)));
                world.spawnParticle(Particle.DUST, tipPoint, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
            }

            // Світловий ефект на кінчику
            if (ticks[0] % 2 == 0) {
                Location glowPoint = arrowStart.clone().add(direction.clone().multiply(1.5));
                world.spawnParticle(Particle.DUST, glowPoint, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.WHITE, 0.5f));
            }

            // ДОДАНО: Звуковий індикатор відстані
            if (ticks[0] % 20 == 0) { // Кожну секунду
                if (distance <= 15) {
                    ctx.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2.0f);
                } else if (distance <= 30) {
                    ctx.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BELL, 0.3f, 1.5f);
                }
            }

        }, 0L, 2L);
    }
    // ========== 5. СОННЕ ПРОВИДІННЯ ==========

    private void openDreamVisionMenu(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();
        List<Player> targets = ctx.targeting().getNearbyPlayers(10000);
        targets.removeIf(p -> p.equals(ctx.getCasterPlayer()));

        if (targets.isEmpty()) {
            ctx.messaging().sendMessageToActionBar(casterId,Component.text(ChatColor.YELLOW + "⚠ Немає гравців для спостереження"));
            return;
        }

        ctx.ui().openChoiceMenu(
                "Сонне Провидіння",
                targets,
                this::createDreamVisionPlayerItem,
                target -> startDreamVisionSpectate(ctx, target)
        );
    }

    private ItemStack createDreamVisionPlayerItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + player.getName());
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Спостерігати у сні"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startDreamVisionSpectate(IAbilityContext ctx, Player target) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();
        Player caster = ctx.getCasterPlayer();
        UUID casterId = ctx.getCasterId();

        GameMode originalMode = caster.getGameMode();
        Location originalLoc = caster.getLocation().clone();

        // Перевірка опору ПЕРЕД споживанням ресурсів
        if (!rollDivinationAgainstTarget(ctx, target.getUniqueId())) {
            ctx.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.RED + "✗ Спроба увійти в сон провалена, можливо, щось заважає?"));
            ctx.effects().playSoundForPlayer(casterId, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f);
            return;
        }

        // Споживаємо ресурси ТІЛЬКИ після успішної перевірки
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        ctx.effects().playSoundForPlayer(casterId, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        ctx.effects().spawnParticle(Particle.PORTAL, originalLoc, 50, 0.5, 0.5, 0.5);

        caster.setGameMode(GameMode.SPECTATOR);
        caster.teleport(target.getLocation());

        ctx.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "✦ Ви увійшли у сон " + target.getName());
        ctx.messaging().sendMessage(casterId, ChatColor.GRAY + "Час спостереження: 15 секунд");

        final BukkitTask[] trackingTask = new BukkitTask[1];
        final BukkitTask[] endTask = new BukkitTask[1];
        final boolean[] finished = {false};

        // Wrapper для безпечного завершення
        Runnable safeFinish = () -> {
            if (finished[0]) return;
            finished[0] = true;

            // Скасовуємо всі таски
            if (trackingTask[0] != null) trackingTask[0].cancel();
            if (endTask[0] != null) endTask[0].cancel();

            // Відписуємось від всіх подій для цього гравця
            ctx.events().unsubscribeAll(casterId);

            // Завершуємо сесію тільки якщо гравець онлайн
            if (caster.isOnline()) {
                endDreamVision(ctx, caster, originalMode, originalLoc);
            }
        };

        // КРИТИЧНО: Підписка на PlayerQuitEvent для збереження стану
        // КРИТИЧНО: Підписка на PlayerQuitEvent для збереження стану
        ctx.events().subscribeToTemporaryEvent(
                casterId,
                org.bukkit.event.player.PlayerQuitEvent.class,
                event -> {
                    // Явне приведення типу
                    if (event instanceof org.bukkit.event.player.PlayerQuitEvent quitEvent) {
                        return quitEvent.getPlayer().getUniqueId().equals(casterId);
                    }
                    return false;
                },
                event -> {
                    // Гравець виходить - НЕ відновлюємо режим тут!
                    finished[0] = true;
                    if (trackingTask[0] != null) trackingTask[0].cancel();
                    if (endTask[0] != null) endTask[0].cancel();
                },
                (int) (15 * 20L) // 15 секунд
        );
        // КРИТИЧНО: Підписка на PlayerJoinEvent для відновлення після рестарту
        // КРИТИЧНО: Підписка на PlayerJoinEvent для відновлення після рестарту
        ctx.events().subscribeToTemporaryEvent(
                casterId,
                org.bukkit.event.player.PlayerJoinEvent.class,
                event -> {
                    // Явне приведення типу
                    if (event instanceof org.bukkit.event.player.PlayerJoinEvent joinEvent) {
                        return joinEvent.getPlayer().getUniqueId().equals(casterId);
                    }
                    return false;
                },
                event -> {
                    // Явне приведення типу
                    if (event instanceof org.bukkit.event.player.PlayerJoinEvent joinEvent) {
                        Player rejoined = joinEvent.getPlayer();

                        // Якщо гравець все ще в spectator - це ознака незавершеного сну
                        if (rejoined.getGameMode() == GameMode.SPECTATOR) {
                            // Відновлюємо оригінальний режим
                            rejoined.setGameMode(originalMode);

                            // Телепортуємо назад якщо локація валідна
                            if (originalLoc != null && originalLoc.getWorld() != null) {
                                ctx.scheduling().scheduleDelayed(() -> {
                                    if (rejoined.isOnline()) {
                                        rejoined.teleport(originalLoc);
                                    }
                                }, 5L); // Невелика затримка для стабільності
                            }

                            ctx.effects().playSoundForPlayer(
                                    casterId,
                                    Sound.ENTITY_ENDERMAN_TELEPORT,
                                    1f,
                                    1.5f
                            );

                            ctx.messaging().sendMessage(
                                    casterId,
                                    ChatColor.YELLOW + "✦ Ви повернулись зі сну після перезаходу"
                            );
                        }

                        // Очищаємо всі підписки
                        ctx.events().unsubscribeAll(casterId);
                    }
                },
                (int) (60 * 20L) // 60 секунд - більший час для можливості перезайти
        );

        // Tracking task - слідкує за відстанню до цілі
        trackingTask[0] = ctx.scheduling().scheduleRepeating(() -> {
            if (finished[0]) return;

            // Якщо хтось з них офлайн - завершуємо
            if (!caster.isOnline() || !target.isOnline()) {
                safeFinish.run();
                return;
            }

            Location casterLoc = caster.getLocation();
            Location targetLoc = target.getLocation();

            // Перевірка світів
            if (!casterLoc.getWorld().equals(targetLoc.getWorld())) {
                caster.teleport(targetLoc);
            } else {
                double distance = casterLoc.distance(targetLoc);
                if (distance > 15) {
                    caster.teleport(targetLoc);
                }
            }
        }, 0L, 5L);

        // End task - автоматичне завершення через 15 секунд
        endTask[0] = ctx.scheduling().scheduleDelayed(safeFinish, 15 * 20L);
    }

    // ЗАМІНІТЬ існуючий метод endDreamVision на цей:
    private void endDreamVision(
            IAbilityContext ctx,
            Player caster,
            GameMode originalMode,
            Location originalLoc
    ) {
        UUID casterId = caster.getUniqueId();

        // Якщо гравець онлайн — відновлюємо одразу
        if (caster.isOnline()) {
            try {
                // Відновлюємо режим та телепортуємо назад
                caster.setGameMode(originalMode);
                if (originalLoc != null && originalLoc.getWorld() != null) {
                    caster.teleport(originalLoc);
                }

                ctx.effects().playSoundForPlayer(
                        casterId,
                        Sound.ENTITY_ENDERMAN_TELEPORT,
                        1f,
                        1.5f
                );

                ctx.effects().spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        originalLoc != null ? originalLoc : caster.getLocation(),
                        30,
                        0.5,
                        0.5,
                        0.5
                );

                ctx.messaging().sendMessage(
                        casterId,
                        ChatColor.GREEN + "✓ Ви повернулись із сну"
                );
            } catch (Exception ex) {
                Bukkit.getLogger().warning("Failed to end DreamVision for " + casterId + ": " + ex.getMessage());
            }
        }
        // Якщо оффлайн - PlayerJoinEvent listener подбає про відновлення
    }
    // ========== HELPER METHODS ==========

    private Color getColorForTarget(String name) {
        if(name.contains("Діамант")) return Color.AQUA;
        if(name.contains("Залізо")) return Color.SILVER;
        if(name.contains("Золото")) return Color.YELLOW;
        if(name.contains("Смарагд")) return Color.LIME;
        if(name.contains("Редстоун")) return Color.RED;
        if(name.contains("Лазурит")) return Color.BLUE;
        if(name.contains("Стародавні уламки")) return Color.fromRGB(128, 0, 128); // Фіолетовий для ancient debris
        return Color.GRAY;
    }

    private Location findNearestBlock(IAbilityContext ctx, DivinationTarget target) {
        Location start = ctx.getCasterLocation();
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        int radius = 50;

        // Шукаємо найближчий блок серед усіх можливих матеріалів цілі
        for (Material targetMat : target.targetMaterials) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Location loc = start.clone().add(x, y, z);

                        if (loc.getBlock().getType() == targetMat) {
                            double distance = start.distance(loc);

                            if (distance < nearestDistance) {
                                nearestDistance = distance;
                                nearest = loc;
                            }
                        }
                    }
                }
            }
        }

        return nearest;
    }
    private Location findNearbyBlock(IAbilityContext ctx, Material mat, int radius) {
        Location start = ctx.getCasterLocation();
        for(int x = -radius; x <= radius; x++) {
            for(int y = -radius; y <= radius; y++) {
                for(int z = -radius; z <= radius; z++) {
                    Location loc = start.clone().add(x, y, z);
                    if(loc.getBlock().getType() == mat) return loc;
                }
            }
        }
        return null;
    }

    private Location findNearbyOre(IAbilityContext ctx, Material... mats) {
        Location start = ctx.getCasterLocation();
        Set<Material> targets = new HashSet<>(Arrays.asList(mats));
        for(int x = -50; x <= 50; x++) {
            for(int y = -50; y <= 50; y++) {
                for(int z = -50; z <= 50; z++) {
                    Location loc = start.clone().add(x, y, z);
                    if(targets.contains(loc.getBlock().getType())) return loc;
                }
            }
        }
        return null;
    }

    // ========== INNER RECORDS ==========

    private enum DivinationType {
        CRYSTAL_BALL("Кришталева куля", Material.AMETHYST_CLUSTER, ChatColor.LIGHT_PURPLE, "Розкриває інформацію про гравців"),
        ASTROLOGY("Астрологія", Material.SPYGLASS, ChatColor.BLUE, "Передбачає удачу або невдачу"),
        PENDULUM("Духовний маятник", Material.IRON_CHAIN, ChatColor.GOLD, "Відповідає на питання 'Так' чи 'Ні'"),
        DIVINING_ROD("Лозошукання", Material.STICK, ChatColor.GREEN, "Пошук ресурсів та об'єктів"),
        DREAM_VISION("Сонне провидіння", Material.PHANTOM_MEMBRANE, ChatColor.DARK_AQUA, "Спостереження за гравцями у сні");

        final String displayName;
        final Material icon;
        final ChatColor color;
        final String description;

        DivinationType(String displayName, Material icon, ChatColor color, String description) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
            this.description = description;
        }
    }

    private record PendulumQuestion(String question, Function<IAbilityContext, String> logic) {}

    /**
     * Ціль лозошукання з вимогою до послідовності
     * @param name Назва ресурсу
     * @param requiredSequence Мінімальна послідовність для доступу (9 = найлегше, 0 = найважче)
     * @param iconMaterial Іконка в меню
     * @param targetMaterials Матеріали, які шукаємо
     */
    private record DivinationTarget(String name, int requiredSequence, Material iconMaterial, Material... targetMaterials) {
        DivinationTarget(String name, int requiredSequence, Material icon, Material singleTarget) {
            this(name, requiredSequence, icon, new Material[]{singleTarget});
        }
    }
}