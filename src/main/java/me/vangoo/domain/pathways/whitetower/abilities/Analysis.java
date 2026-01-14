package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Analysis extends ActiveAbility {

    private static final int ANALYSIS_DELAY_SECONDS = 2;
    private static final double DETECTION_RADIUS = 20.0;
    private static final int COST = 120;
    private static final int COOLDOWN = 120;
    private static final int MAX_REMEMBERED_ABILITIES = 10;

    // --- НАЛАШТУВАННЯ ШАНСІВ ---
    private static final double BASE_CHANCE = 0.40; // Базовий шанс 50%
    private static final double SEQUENCE_DIFF_MODIFIER = 0.10; // +/- 10% за кожен рівень різниці
    private static final double RECIPE_KNOWLEDGE_BONUS_PER_RECIPE = 0.05;
    private static final double MAX_RECIPE_BONUS = 0.25;

    @Override
    public String getName() {
        return "Аналіз";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Дозволяє просканувати ціль та скопіювати здібність.\n" +
                ChatColor.GRAY + "Базовий шанс: 40%\n" +
                ChatColor.YELLOW + "⚠ Шанс змінюється від різниці рівнів та знань.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    // ... (performExecution та startAnalysisPhase без змін) ...

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // ... (Код вибору гравця з попередньої відповіді) ...
        Beyonder caster = context.getCasterBeyonder();
        int currentCount = caster.getOffPathwayActiveAbilities().size();
        if (currentCount >= MAX_REMEMBERED_ABILITIES) {
            return AbilityResult.failure("Ліміт пам'яті досягнуто!");
        }

        List<Player> nearbyPlayers = context.getNearbyPlayers(DETECTION_RADIUS);
        if (nearbyPlayers.isEmpty()) return AbilityResult.failure("Немає цілей поблизу.");

        context.openChoiceMenu(
                "КРОК 1: Виберіть ціль",
                nearbyPlayers,
                player -> createPlayerHead(player, context),
                selectedPlayer -> startAnalysisPhase(context, selectedPlayer)
        );
        return AbilityResult.deferred();
    }

    private void startAnalysisPhase(IAbilityContext context, Player target) {
        // ... (Візуал та затримка з попередньої відповіді) ...
        // Для економії місця тут скорочено, логіка та сама
        Player caster = context.getCaster();
        context.playConeEffect(caster.getEyeLocation(), caster.getLocation().getDirection(), 30, 5, Particle.ENCHANT, 40);
        context.scheduleDelayed(() -> openAbilitySelectionMenu(context, target, context.getBeyonderFromEntity(target.getUniqueId())), ANALYSIS_DELAY_SECONDS * 20L);
    }

    // =========================================================================
    // КРОК 3: МЕНЮ ЗДІБНОСТЕЙ (Оновлено розрахунок для іконок)
    // =========================================================================

    private void openAbilitySelectionMenu(IAbilityContext context, Player target, Beyonder targetBeyonder) {
        if (!target.isOnline()) return;

        List<Ability> targetAbilities = new ArrayList<>(targetBeyonder.getAbilities());

        List<Ability> availableAbilities = targetAbilities.stream()
                .filter(a -> targetBeyonder.getSequenceLevel() <= findAbilitySequence(targetBeyonder, a))
                .filter(this::canRememberAbility)
                .sorted(Comparator.comparingInt(a -> findAbilitySequence(targetBeyonder, a)))
                .toList();

        if (availableAbilities.isEmpty()) {
            context.sendMessageToCaster(ChatColor.RED + "Здібностей для копіювання не виявлено.");
            return;
        }

        context.openChoiceMenu(
                "КРОК 2: Шанс (База 50%)", // Підказка в заголовку
                availableAbilities,
                ability -> createAbilityIcon(ability, targetBeyonder, context),
                selectedAbility -> attemptToCopyAbility(context, targetBeyonder, selectedAbility)
        );
    }

    // =========================================================================
    // КРОК 4: СПРОБА КОПІЮВАННЯ (Нова формула)
    // =========================================================================

    private void attemptToCopyAbility(IAbilityContext context, Beyonder targetBeyonder, Ability ability) {
        Beyonder casterBeyonder = context.getCasterBeyonder();
        Player caster = context.getCaster();

        if (casterBeyonder.getAbilityByName(ability.getName()).isPresent()) {
            context.sendMessageToCaster(ChatColor.RED + "Ви вже знаєте цю здібність!");
            return;
        }

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.sendMessageToCaster(ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.publishAbilityUsedEvent(this);
        // --- НОВИЙ РОЗРАХУНОК ---
        int abilitySeq = findAbilitySequence(targetBeyonder, ability);
        int casterSeq = casterBeyonder.getSequenceLevel();
        int knownRecipes = context.getKnownRecipeCount(targetBeyonder.getPathway().getName());

        double finalChance = calculateTotalChance(casterSeq, abilitySeq, knownRecipes);

        // Розрахунок складових для красивого виводу
        double seqDiff = (abilitySeq - casterSeq) * SEQUENCE_DIFF_MODIFIER;
        double recipeBonus = Math.min(knownRecipes * RECIPE_KNOWLEDGE_BONUS_PER_RECIPE, MAX_RECIPE_BONUS);

        context.sendMessageToCaster(String.format(
                "\n%s=== АНАЛІЗ: %s ===\n" +
                        "%sБазовий шанс: %s%.0f%%\n" +
                        "%sРізниця рівнів: %s%+.0f%%\n" +
                        "%sБонус знань: %s%+.0f%%\n" +
                        "%s--------------------\n" +
                        "%sФІНАЛЬНИЙ ШАНС: %s%.1f%%",
                ChatColor.GOLD, ability.getName().toUpperCase(),
                ChatColor.GRAY, ChatColor.WHITE, BASE_CHANCE * 100,
                ChatColor.GRAY, (seqDiff >= 0 ? ChatColor.GREEN : ChatColor.RED), seqDiff * 100,
                ChatColor.GRAY, ChatColor.GREEN, recipeBonus * 100,
                ChatColor.DARK_GRAY,
                ChatColor.GOLD, getColorForChance(finalChance), finalChance * 100
        ));

        // RNG Check
        if (Math.random() > finalChance) {
            caster.playSound(caster.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
            context.sendMessageToCaster(ChatColor.RED + "× Невдача! Структура нестабільна.");
            return;
        }

        // Success
        boolean added = casterBeyonder.addOffPathwayAbility(ability);
        if (added) {
            caster.playSound(caster.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            context.playVortexEffect(caster.getLocation(), 2, 1, Particle.END_ROD, 30);
            context.sendMessageToCaster(ChatColor.GREEN + "✔ Здібність успішно скопійована!");
        }
    }

    // =========================================================================
    // ЦЕНТРАЛІЗОВАНА ЛОГІКА РОЗРАХУНКУ
    // =========================================================================

    /**
     * Головна формула шансу
     */
    private double calculateTotalChance(int casterSeq, int abilitySeq, int knownRecipes) {
        // 1. База 50%
        double chance = BASE_CHANCE;

        // 2. Модифікатор різниці послідовностей
        // У LotM: 9 - слабкий, 0 - сильний.
        // Якщо Кастер(5) копіює Абілку(9): (9 - 5) = +4. +20% до шансу (легше).
        // Якщо Кастер(9) копіює Абілку(5): (5 - 9) = -4. -20% до шансу (важче).
        double seqModifier = (abilitySeq - casterSeq) * SEQUENCE_DIFF_MODIFIER;
        chance += seqModifier;

        // 3. Бонус від рецептів (тільки плюс)
        double recipeBonus = Math.min(knownRecipes * RECIPE_KNOWLEDGE_BONUS_PER_RECIPE, MAX_RECIPE_BONUS);
        chance += recipeBonus;

        // 4. Ліміти (мін 10%, макс 95%)
        return Math.max(0.10, Math.min(0.95, chance));
    }

    // =========================================================================
    // GUI ТА ІНШЕ
    // =========================================================================

    private ItemStack createAbilityIcon(Ability ability, Beyonder target, IAbilityContext context) {
        Material mat = switch (ability.getType()) {
            case ACTIVE -> Material.BLAZE_POWDER;
            case TOGGLEABLE_PASSIVE -> Material.COMPARATOR;
            case PERMANENT_PASSIVE -> Material.BOOK;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + ability.getName());

            int abilitySeq = findAbilitySequence(target, ability);
            int casterSeq = context.getCasterBeyonder().getSequenceLevel();
            int recipes = context.getKnownRecipeCount(target.getPathway().getName());

            // Використовуємо ту саму формулу
            double finalChance = calculateTotalChance(casterSeq, abilitySeq, recipes);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Рівень послідовності: " + abilitySeq);
            lore.add("");
            lore.add(ChatColor.GRAY + "Тип: " + ChatColor.WHITE + getAbilityTypeDisplay(ability));
            lore.add(ChatColor.GRAY + "Шанс: " + getColorForChance(finalChance) + String.format("%.0f%%", finalChance * 100));

            if (finalChance < 0.5) {
                lore.add(ChatColor.RED + "⚠ Ризиковано!");
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Натисніть для копіювання");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ... (решта допоміжних методів: createPlayerHead, findAbilitySequence і т.д. без змін) ...

    private ItemStack createPlayerHead(Player player, IAbilityContext context) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.AQUA + player.getName());
            head.setItemMeta(meta);
        }
        return head;
    }

    private int findAbilitySequence(Beyonder beyonder, Ability ability) {
        for (int seq = 9; seq >= 0; seq--) {
            List<Ability> abilities = beyonder.getPathway().GetAbilitiesForSequence(seq);
            for (Ability a : abilities) {
                if (a.getIdentity().equals(ability.getIdentity())) return seq;
            }
        }
        return 9;
    }

    private boolean canRememberAbility(Ability ability) {
        return ability.getType() == AbilityType.ACTIVE ||
                ability.getType() == AbilityType.TOGGLEABLE_PASSIVE ||
                ability.getType() == AbilityType.PERMANENT_PASSIVE;
    }

    private String getAbilityTypeDisplay(Ability ability) {
        return switch (ability.getType()) {
            case ACTIVE -> "Активна";
            case TOGGLEABLE_PASSIVE -> "Пасивна (Switch)";
            case PERMANENT_PASSIVE -> "Пасивна (Perm)";
        };
    }

    private ChatColor getColorForChance(double chance) {
        if (chance >= 0.7) return ChatColor.GREEN;
        if (chance >= 0.4) return ChatColor.YELLOW;
        return ChatColor.RED;
    }
}