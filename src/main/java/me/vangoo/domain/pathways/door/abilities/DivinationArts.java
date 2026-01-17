package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
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
    private final int DIVINING_ROD_DURATION_TICKS = 1200; // 20 секунд замість 60

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
                    List<Player> nearbyPlayers = ctx.getNearbyPlayers(30);
                    int beyonderCount = 0;

                    for (Player p : nearbyPlayers) {
                        if (ctx.isBeyonder(p.getUniqueId())) {
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
                    List<RecordedEvent> events = ctx.getPastEvents(loc, 10, 300); // 5 хвилин

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
                    Optional<Player> targetOpt = ctx.getTargetedPlayer(30);

                    if (targetOpt.isEmpty()) {
                        return "Ні — ви не дивитесь ні на кого";
                    }

                    Player target = targetOpt.get();
                    Beyonder caster = ctx.getCasterBeyonder();

                    if (!ctx.isBeyonder(target.getUniqueId())) {
                        return "Ні — це звичайна людина без духовної сили";
                    }

                    Optional<Integer> targetSeqOpt = ctx.getEntitySequenceLevel(target.getUniqueId());
                    if (targetSeqOpt.isEmpty()) {
                        return "Невідомо — не вдається прочитати їхню ауру";
                    }

                    int targetSeq = targetSeqOpt.get();
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
        ctx.openChoiceMenu(
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
        boolean active1 = ctx.isAbilityActivated(targetId, AbilityIdentity.of("Anti Divination"));
        boolean active2 = ctx.isAbilityActivated(targetId, AbilityIdentity.of("Anti-Divination"));
        boolean active3 = ctx.isAbilityActivated(targetId, AbilityIdentity.of("anti_divination"));
        boolean active4 = ctx.isAbilityActivated(targetId, AbilityIdentity.of("AntiDivination"));

        boolean isAntiToggledOn = active1 || active2 || active3 || active4;
        int targetSeq = ctx.getEntitySequenceLevel(targetId).orElse(9);
        boolean isLevelAppropriate = targetSeq <= ANTI_DIVINATION_UNLOCK_SEQUENCE;
        boolean hasResistance = isAntiToggledOn && isLevelAppropriate;

        if (!hasResistance) {
            ctx.sendMessageToCaster(ChatColor.GRAY + "Шанс успіху гадання: " + ChatColor.AQUA + "100%");
            return true;
        }

        UUID casterId = ctx.getCasterId();
        int casterSeq = ctx.getEntitySequenceLevel(casterId).orElse(9);

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

        ctx.sendMessageToCaster(ChatColor.GRAY + "Шанс успіху гадання: " +
                ChatColor.AQUA + String.format("%.0f%%", finalChance * 100));

        return chanceRng.nextDouble() < finalChance;
    }

    // ========== 1. КРИШТАЛЕВА КУЛЯ ==========

    private void performCrystalBallDivination(IAbilityContext ctx) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.publishAbilityUsedEvent(this);

        Player caster = ctx.getCasterPlayer();
        ctx.playSoundToCaster(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        ctx.spawnParticle(Particle.END_ROD, ctx.getCasterLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5);

        List<Player> onlinePlayers = new ArrayList<>(ctx.getNearbyPlayers(10000));
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
        for (int i = 0; i < 3; i++) {
            final int tick = i;
            ctx.scheduleDelayed(() -> {
                ctx.playSoundToCaster(Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.5f + (tick * 0.2f));
                ctx.spawnParticle(Particle.ENCHANT, ctx.getCasterLocation().add(0, 2, 0), 20, 0.3, 0.3, 0.3);
            }, i * 10L);
        }

        ctx.scheduleDelayed(() -> {
            Map<String, String> analysis = ctx.playerData().getTargetAnalysis(target.getUniqueId());

            ctx.sendMessageToCaster(ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🔮 БАЧЕННЯ КРИСТАЛЬНОГО ШАРУ");
            ctx.sendMessageToCaster(ChatColor.GRAY + "Ціль: " + ChatColor.WHITE + target.getName());
            ctx.sendMessageToCaster("");

            analysis.forEach((key, value) -> ctx.sendMessageToCaster(ChatColor.GRAY + "  " + key + ": " + ChatColor.AQUA + value));

            ctx.sendMessageToCaster(ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.8f);
        }, 30L);
    }

    private void revealWeatherPrediction(IAbilityContext ctx) {
        World world = ctx.getCasterPlayer().getWorld();
        long timeUntilClear = world.getClearWeatherDuration();
        long timeUntilRain = world.getWeatherDuration();
        boolean isRaining = world.hasStorm();

        ctx.scheduleDelayed(() -> {
            ctx.sendMessageToCaster(ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🔮 МЕТЕОРОЛОГІЧНЕ ПРОРОЦТВО");
            ctx.sendMessageToCaster("");

            if (isRaining) {
                int minutesLeft = (int) (timeUntilClear / 20 / 60);
                ctx.sendMessageToCaster(ChatColor.GRAY + "Зараз: " + ChatColor.BLUE + "Дощ");
                ctx.sendMessageToCaster(ChatColor.GRAY + "Тривалість: " + ChatColor.AQUA + minutesLeft + " хв");
            } else {
                int minutesUntil = (int) (timeUntilRain / 20 / 60);
                ctx.sendMessageToCaster(ChatColor.GRAY + "Наступний дощ через: " + ChatColor.YELLOW + minutesUntil + " хв");
            }

            ctx.sendMessageToCaster(ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            ctx.playSoundToCaster(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        }, 30L);
    }

    // ========== 2. АСТРОЛОГІЯ ==========

    private void performAstrologyDivination(IAbilityContext ctx) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.publishAbilityUsedEvent(this);

        for (int i = 0; i < 5; i++) {
            final int tick = i;
            ctx.scheduleDelayed(() -> {
                Location loc = ctx.getCasterLocation().add(
                        Math.cos(tick) * 2, 2 + tick * 0.3, Math.sin(tick) * 2
                );
                ctx.spawnParticle(Particle.END_ROD, loc, 5, 0.1, 0.1, 0.1);
            }, i * 5L);
        }

        ctx.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        boolean positive = rng.nextBoolean();

        ctx.scheduleDelayed(() -> {
            if (positive) {
                ctx.applyEffect(ctx.getCasterId(), PotionEffectType.HASTE, 12000, 0);
                ctx.sendMessageToCaster(ChatColor.GREEN + "✦ Зірки прихильні до вас!");
                ctx.sendMessageToCaster(ChatColor.GRAY + "Квапливість +1 (10 хв)");
                ctx.playSoundToCaster(Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            } else {
                ctx.applyEffect(ctx.getCasterId(), PotionEffectType.WEAKNESS, 12000, 0);
                ctx.sendMessageToCaster(ChatColor.RED + "✦ Зірки застерігають...");
                ctx.sendMessageToCaster(ChatColor.GRAY + "Слабкість +1 (10 хв)");
                ctx.playSoundToCaster(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1f);
            }
        }, 40L);
    }

    // ========== 3. МАЯТНИК ==========

    private void openPendulumMenu(IAbilityContext ctx) {
        ctx.openChoiceMenu(
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
        Beyonder casterBeyonder = ctx.getCasterBeyonder();

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.publishAbilityUsedEvent(this);

        for (int i = 0; i < 4; i++) {
            ctx.scheduleDelayed(() -> ctx.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_BELL, 0.3f, 1.5f), i * 8L);
        }

        ctx.scheduleDelayed(() -> {
            String answer = question.logic.apply(ctx);

            ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "═══════════════════════════════");
            ctx.sendMessageToCaster(ChatColor.GOLD + "❓ " + question.question);
            ctx.sendMessageToCaster("");
            ctx.sendMessageToCaster(ChatColor.YELLOW + answer);
            ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "═══════════════════════════════");

            ctx.playSoundToCaster(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        }, 40L);
    }

    // ========== 4. ЛОЗОШУКАННЯ ==========

    private void openDiviningRodMenu(IAbilityContext ctx) {
        Beyonder beyonder = ctx.getCasterBeyonder();
        int casterSequence = beyonder.getSequenceLevel();

        // Фільтруємо цілі в залежності від послідовності
        List<DivinationTarget> availableTargets = getAvailableTargetsForSequence(casterSequence);

        if (availableTargets.isEmpty()) {
            ctx.sendMessageToCaster(ChatColor.RED + "На вашому рівні немає доступних цілей для лозошукання");
            return;
        }

        ctx.openChoiceMenu(
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

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.publishAbilityUsedEvent(this);

        Player caster = ctx.getCasterPlayer();
        ctx.sendMessageToCaster(ChatColor.GREEN + "🔍 Лозошукальний стрижень активовано!");
        ctx.sendMessageToCaster(ChatColor.GRAY + "Шукаємо: " + ChatColor.GOLD + target.name);
        ctx.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.3f);
        Location nearest = findNearestBlock(ctx, target);

        if (nearest == null) {
            ctx.sendMessageToCaster(ChatColor.YELLOW + "⚠ Нічого не знайдено в радіусі 50 блоків");
            return;
        }

        ctx.sendMessageToCaster(ChatColor.AQUA + "✓ Ціль виявлено! Стрілка вказує шлях...");
        final int[] ticks = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        final boolean[] completed = {false};

        Color arrowColor = getColorForTarget(target.name);
        Particle.DustOptions dustOptions = new Particle.DustOptions(arrowColor, 1.2f);

        holder[0] = ctx.scheduleRepeating(() -> {
            if (!caster.isOnline() || completed[0]) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }

            Location playerLoc = caster.getLocation();
            double distance = playerLoc.distance(nearest);

            if (distance <= 5.0) {
                completed[0] = true;
                if (holder[0] != null) holder[0].cancel();

                ctx.sendMessageToCaster(ChatColor.GREEN + "════════════════════════");
                ctx.sendMessageToCaster(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ ВИ ДОСЯГЛИ ЦІЛІ!");
                ctx.sendMessageToCaster(ChatColor.GRAY + "Знайдено: " + ChatColor.GOLD + target.name);
                ctx.sendMessageToCaster(ChatColor.GREEN + "════════════════════════");
                ctx.playSoundToCaster(Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                ctx.playSoundToCaster(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

                Particle.DustOptions finalDust = new Particle.DustOptions(Color.LIME, 2.0f);
                for (int i = 0; i < 5; i++) {
                    final int iteration = i;
                    ctx.scheduleDelayed(() -> {
                        Location targetLoc = nearest.clone().add(0.5, 0.5, 0.5);
                        for (int angle = 0; angle < 360; angle += 10) {
                            double rad = Math.toRadians(angle);
                            double x = Math.cos(rad) * (1.0 + iteration * 0.2);
                            double z = Math.sin(rad) * (1.0 + iteration * 0.2);
                            Location particleLoc = targetLoc.clone().add(x, 0, z);
                            caster.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, finalDust);
                        }
                        ctx.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f + (iteration * 0.2f));
                    }, i * 5L);
                }
                return;
            }

            // Максимальна тривалість - 20 секунд (400 тіків)
            if (ticks[0]++ >= DIVINING_ROD_DURATION_TICKS) {
                if (holder[0] != null) holder[0].cancel();
                ctx.sendMessageToCaster(ChatColor.GRAY + "Лозошукання завершено (час вийшов)");
                return;
            }

            Vector direction = nearest.clone().add(0.5, 0.5, 0.5).toVector().subtract(caster.getEyeLocation().toVector()).normalize();
            Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
            right.normalize();
            Vector thickness = right.clone().multiply(0.15);
            World world = caster.getWorld();
            double sideOffset = 0.5; double forwardOffset = 0.5; double upOffset = -0.1;
            Location arrowStart = caster.getEyeLocation().clone().add(direction.clone().multiply(forwardOffset)).add(right.clone().multiply(sideOffset)).add(0, upOffset, 0);

            for (int i = 0; i < 10; i++) {
                double offset = i * 0.25;
                Location point = arrowStart.clone().add(direction.clone().multiply(offset));
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
                world.spawnParticle(Particle.DUST, point.clone().add(thickness), 1, 0, 0, 0, 0, dustOptions);
                world.spawnParticle(Particle.DUST, point.clone().subtract(thickness), 1, 0, 0, 0, 0, dustOptions);
            }

            Location tipBase = arrowStart.clone().add(direction.clone().multiply(2.5));
            Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
            for (int i = 0; i < 5; i++) {
                double t = i / 5.0;
                Location tipPoint = tipBase.clone().add(direction.clone().multiply(-0.4 * t)).add(perpendicular.clone().multiply(0.4 * (1 - t)));
                world.spawnParticle(Particle.DUST, tipPoint, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
            }
            for (int i = 0; i < 5; i++) {
                double t = i / 5.0;
                Location tipPoint = tipBase.clone().add(direction.clone().multiply(-0.4 * t)).subtract(perpendicular.clone().multiply(0.4 * (1 - t)));
                world.spawnParticle(Particle.DUST, tipPoint, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
            }
            if (ticks[0] % 2 == 0) {
                Location glowPoint = arrowStart.clone().add(direction.clone().multiply(1.5));
                world.spawnParticle(Particle.DUST, glowPoint, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 0.5f));
            }
        }, 0L, 2L);
    }

    // ========== 5. СОННЕ ПРОВИДІННЯ ==========

    private void openDreamVisionMenu(IAbilityContext ctx) {
        List<Player> targets = ctx.getNearbyPlayers(10000);
        targets.removeIf(p -> p.equals(ctx.getCasterPlayer()));

        if (targets.isEmpty()) {
            ctx.sendMessageToCaster(ChatColor.YELLOW + "⚠ Немає гравців для спостереження");
            return;
        }

        ctx.openChoiceMenu(
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

        GameMode originalMode = caster.getGameMode();
        Location originalLoc = caster.getLocation().clone();

        // Перевірка опору ПЕРЕД споживанням ресурсів
        if (!rollDivinationAgainstTarget(ctx, target.getUniqueId())) {
            ctx.sendMessageToCaster(ChatColor.RED + "✗ Спроба увійти в сон провалена, можливо, щось заважає?");
            ctx.playSoundToCaster(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f);
            return;
        }

        // Споживаємо ресурси ТІЛЬКИ після успішної перевірки
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            ctx.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.publishAbilityUsedEvent(this);

        ctx.playSoundToCaster(Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        ctx.spawnParticle(Particle.PORTAL, originalLoc, 50, 0.5, 0.5, 0.5);

        caster.setGameMode(GameMode.SPECTATOR);
        caster.teleport(target.getLocation());

        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "✦ Ви увійшли у сон " + target.getName());
        ctx.sendMessageToCaster(ChatColor.GRAY + "Час спостереження: 15 секунд");

        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = ctx.scheduleRepeating(() -> {
            if (!caster.isOnline() || !target.isOnline()) {
                if (holder[0] != null) holder[0].cancel();
                endDreamVision(ctx, caster, originalMode, originalLoc);
                return;
            }

            double distance = caster.getLocation().distance(target.getLocation());
            if (distance > 15) {
                caster.teleport(target.getLocation());
            }
        }, 0L, 5L);

        ctx.scheduleDelayed(() -> {
            if (holder[0] != null) holder[0].cancel();
            endDreamVision(ctx, caster, originalMode, originalLoc);
        }, 300L);
    }

    private void endDreamVision(IAbilityContext ctx, Player caster, GameMode originalMode, Location originalLoc) {
        caster.setGameMode(originalMode);
        caster.teleport(originalLoc);

        ctx.playSoundToCaster(Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
        ctx.spawnParticle(Particle.SOUL_FIRE_FLAME, originalLoc, 30, 0.5, 0.5, 0.5);

        ctx.sendMessageToCaster(ChatColor.GREEN + "✓ Ви повернулись із сну");
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
        return findNearbyBlock(ctx, target.targetMaterials[0], 50);
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