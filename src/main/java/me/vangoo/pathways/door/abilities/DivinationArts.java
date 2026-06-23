package me.vangoo.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.DivinationOdds;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    // Інстанс-реєстри живих сесій (НЕ static): один екземпляр здібності спільний для свого Sequence.
    private final Map<UUID, DiviningRodSession> activeRods = new ConcurrentHashMap<>();
    private final Map<UUID, DreamVisionSession> activeDreams = new ConcurrentHashMap<>();

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

    @Override
    public void cleanUp() {
        activeRods.values().forEach(DiviningRodSession::cancel);
        activeRods.clear();
        activeDreams.values().forEach(DreamVisionSession::cancel);
        activeDreams.clear();
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
            tellActionBar(casterId, ChatColor.GRAY + "Шанс успіху гадання: " + ChatColor.AQUA + "100%");
            return true;
        }

        int casterSeq = sequenceLevelOrDefault(ctx, casterId, 9);

        // Чисте правило балансу (тестується без сервера): підсумковий шанс від різниці Sequence.
        double finalChance = new DivinationOdds(casterSeq, targetSeq).successProbability();

        tellActionBar(casterId, ChatColor.GRAY + "Шанс успіху гадання: " +
                ChatColor.AQUA + String.format("%.0f%%", finalChance * 100));

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
            tellActionBar(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        Player caster = ctx.getCasterPlayer();
        playSound(casterId, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        spawnParticle(ctx.getCasterLocation().add(0, 1.5, 0), Particle.END_ROD, 30, 0.5, 0.5, 0.5);

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
                playSound(casterId, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.5f + (tick * 0.2f));
                spawnParticle(ctx.getCasterLocation().add(0, 2, 0), Particle.ENCHANT, 20, 0.3, 0.3, 0.3);
            }, i * 10L);
        }

        ctx.scheduling().scheduleDelayed(() -> {
            Map<String, String> analysis = ctx.playerData().getTargetAnalysis(target.getUniqueId());

            tell(casterId, ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            tell(casterId, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🔮 БАЧЕННЯ КРИСТАЛЬНОГО ШАРУ");
            tell(casterId, ChatColor.GRAY + "Ціль: " + ChatColor.WHITE + target.getName());
            tell(casterId, "");

            analysis.forEach((key, value) -> tell(casterId, ChatColor.GRAY + "  " + key + ": " + ChatColor.AQUA + value));

            tell(casterId, ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            playSound(casterId, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.8f);
        }, 30L);
    }

    private void revealWeatherPrediction(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();
        World world = ctx.getCasterPlayer().getWorld();
        long timeUntilClear = world.getClearWeatherDuration();
        long timeUntilRain = world.getWeatherDuration();
        boolean isRaining = world.hasStorm();

        ctx.scheduling().scheduleDelayed(() -> {
            tell(casterId, ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            tell(casterId, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🔮 МЕТЕОРОЛОГІЧНЕ ПРОРОЦТВО");
            tell(casterId, "");

            if (isRaining) {
                int minutesLeft = (int) (timeUntilClear / 20 / 60);
                tell(casterId, ChatColor.GRAY + "Зараз: " + ChatColor.BLUE + "Дощ");
                tell(casterId, ChatColor.GRAY + "Тривалість: " + ChatColor.AQUA + minutesLeft + " хв");
            } else {
                int minutesUntil = (int) (timeUntilRain / 20 / 60);
                tell(casterId, ChatColor.GRAY + "Наступний дощ через: " + ChatColor.YELLOW + minutesUntil + " хв");
            }

            tell(casterId, ChatColor.LIGHT_PURPLE + "═══════════════════════════════");
            playSound(casterId, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        }, 30L);
    }

    // ========== 2. АСТРОЛОГІЯ ==========

    private void performAstrologyDivination(IAbilityContext ctx) {
        Beyonder casterBeyonder = ctx.getCasterBeyonder();
        UUID casterId = ctx.getCasterId();
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            tell(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        for (int i = 0; i < 5; i++) {
            final int tick = i;
            ctx.scheduling().scheduleDelayed(() -> {
                Location loc = ctx.getCasterLocation().add(
                        Math.cos(tick) * 2, 2 + tick * 0.3, Math.sin(tick) * 2
                );
                spawnParticle(loc, Particle.END_ROD, 5, 0.1, 0.1, 0.1);
            }, i * 5L);
        }

        playSound(casterId, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        boolean positive = rng.nextBoolean();

        ctx.scheduling().scheduleDelayed(() -> {
            if (positive) {
                ctx.entity().applyPotionEffect(ctx.getCasterId(), PotionEffectType.HASTE, 12000, 0);
                tell(casterId, ChatColor.GREEN + "✦ Зірки прихильні до вас!");
                tell(casterId, ChatColor.GRAY + "Квапливість +1 (10 хв)");
                playSound(casterId, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            } else {
                ctx.entity().applyPotionEffect(ctx.getCasterId(), PotionEffectType.WEAKNESS, 12000, 0);
                tell(casterId, ChatColor.RED + "✦ Зірки застерігають...");
                tell(casterId, ChatColor.GRAY + "Слабкість +1 (10 хв)");
                playSound(casterId, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1f);
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
            tell(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }

        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        for (int i = 0; i < 4; i++) {
            ctx.scheduling().scheduleDelayed(
                    () -> playSound(casterId, Sound.BLOCK_NOTE_BLOCK_BELL, 0.3f, 1.5f),
                    i * 8L
            );
        }

        ctx.scheduling().scheduleDelayed(() -> {
            String answer = question.logic.apply(ctx);

            tell(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
            tell(casterId, ChatColor.GOLD + "❓ " + question.question);
            tell(casterId, "");
            tell(casterId, ChatColor.YELLOW + answer);
            tell(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");

            playSound(casterId, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        }, 40L);
    }


    // ========== 4. ЛОЗОШУКАННЯ ==========

    private void openDiviningRodMenu(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();
        Beyonder beyonder = ctx.getCasterBeyonder();

        int casterSequence = beyonder.getSequenceLevel();
        List<DivinationTarget> availableTargets = getAvailableTargetsForSequence(casterSequence);

        if (availableTargets.isEmpty()) {
            tell(casterId, ChatColor.RED + "На вашому рівні немає доступних цілей для лозошукання");
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
            tell(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        tell(casterId, ChatColor.GREEN + "🔍 Лозошукальний стрижень активовано!");
        tell(casterId, ChatColor.GRAY + "Шукаємо: " + ChatColor.GOLD + target.name);

        playSound(casterId, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.3f);

        Location nearest = findNearestBlock(ctx, target);

        if (nearest == null) {
            tell(casterId, ChatColor.YELLOW + "⚠ Нічого не знайдено в радіусі 50 блоків");
            return;
        }

        tell(casterId, ChatColor.AQUA + "✓ Ціль виявлено! Стрілка вказує шлях...");

        // Один активний стрижень на власника: новий каст замінює попередній (і не лишає завислого таску).
        DiviningRodSession previous = activeRods.remove(casterId);
        if (previous != null) previous.cancel();

        DiviningRodSession session = new DiviningRodSession(
                casterId, target.name, nearest, getColorForTarget(target.name), DIVINING_ROD_DURATION_TICKS);
        BukkitTask task = ctx.scheduling().scheduleRepeating(session::tick, 0L, 2L);
        session.bindTask(task);
        activeRods.put(casterId, session);
    }
    // ========== 5. СОННЕ ПРОВИДІННЯ ==========

    private void openDreamVisionMenu(IAbilityContext ctx) {
        UUID casterId = ctx.getCasterId();
        List<Player> targets = ctx.targeting().getNearbyPlayers(10000);
        targets.removeIf(p -> p.equals(ctx.getCasterPlayer()));

        if (targets.isEmpty()) {
            tellActionBar(casterId, ChatColor.YELLOW + "⚠ Немає гравців для спостереження");
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

        // Перевірка опору ПЕРЕД споживанням ресурсів.
        if (!rollDivinationAgainstTarget(ctx, target.getUniqueId())) {
            tellActionBar(casterId, ChatColor.RED + "✗ Спроба увійти в сон провалена, можливо, щось заважає?");
            playSound(casterId, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f);
            return;
        }

        // Споживаємо ресурси ТІЛЬКИ після успішної перевірки.
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, ctx)) {
            tell(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        ctx.events().publishAbilityUsedEvent(this, casterBeyonder);

        // Ефект входу в сон.
        playSound(casterId, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        spawnParticle(originalLoc, Particle.PORTAL, 50, 0.5, 0.5, 0.5);

        caster.setGameMode(GameMode.SPECTATOR);
        caster.teleport(target.getLocation());

        tell(casterId, ChatColor.DARK_PURPLE + "✦ Ви увійшли у сон " + target.getName());
        tell(casterId, ChatColor.GRAY + "Час спостереження: 15 секунд");

        // Один сон на власника: новий каст завершує попередній (і відновлює гравця).
        DreamVisionSession previous = activeDreams.remove(casterId);
        if (previous != null) previous.cancel();

        DreamVisionSession session = new DreamVisionSession(
                casterId, target.getUniqueId(), originalMode, originalLoc, ctx.events());
        activeDreams.put(casterId, session);
        session.start();
    }

    // ========== HELPER METHODS ==========

    // --- Прямі Bukkit-обгортки замість context.messaging()/effects() (шар ефектів, Bukkit дозволено) ---

    private static void tell(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) player.sendMessage(message);
    }

    private static void tellActionBar(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }
    }

    private static void playSound(UUID playerId, Sound sound, float volume, float pitch) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }

    private static void spawnParticle(Location loc, Particle particle, int count,
                                      double offsetX, double offsetY, double offsetZ) {
        if (loc == null || loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ);
    }

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