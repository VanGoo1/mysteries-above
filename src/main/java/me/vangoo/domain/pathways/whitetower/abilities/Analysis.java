package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Analysis extends ActiveAbility {

    private static final int ANALYSIS_DELAY_SECONDS = 2;
    private static final double DETECTION_RADIUS = 20.0;
    private static final int COST = 200;
    private static final int COOLDOWN = 180;
    private static final int MAX_REMEMBERED_ABILITIES = 10;

    private static final double BASE_CHANCE = 0.20;
    private static final double SEQUENCE_DIFF_MODIFIER = 0.10;
    private static final double RECIPE_KNOWLEDGE_BONUS_PER_RECIPE = 0.05;
    private static final double MAX_RECIPE_BONUS = 0.25;

    public enum AnalysisMode {
        COPY("§aКопіювання", "§7Сканує ціль для копіювання здібності"),
        DELETE("§cВидалення", "§7Дозволяє видалити раніше скопійовану здібність");

        private final String displayName;
        private final String description;

        AnalysisMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public AnalysisMode next() {
            AnalysisMode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final Map<UUID, AnalysisMode> playerModes = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Аналіз";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Дозволяє просканувати ціль, скопіювати здібність або видалити її.\n" +
                ChatColor.GRAY + "Shift + ПКМ: Переключити режим\n" +
                ChatColor.GRAY + "ПКМ: Активувати режим\n" +
                ChatColor.YELLOW + "⚠ Шанс копіювання змінюється від різниці рівнів та знань.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId != null && context.playerData().isSneaking(casterId)) {
            AnalysisMode currentMode = getCurrentMode(casterId);
            AnalysisMode newMode = currentMode.next();
            playerModes.put(casterId, newMode);

            context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);

            context.messaging().sendMessageToActionBar(casterId,
                    Component.text("Режим Аналізу: ", NamedTextColor.WHITE)
                            .append(Component.text(newMode.getDisplayName() + " - " + newMode.getDescription()))
            );

            context.effects().spawnParticle(Particle.ENCHANT, context.playerData().getCurrentLocation(casterId).add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            return AbilityResult.deferred();
        }

        AnalysisMode mode = getCurrentMode(casterId);
        return switch (mode) {
            case COPY -> executeCopy(context);
            case DELETE -> executeDelete(context);
        };
    }

    private AbilityResult executeCopy(IAbilityContext context) {
        Beyonder caster = context.getCasterBeyonder();
        int currentCount = caster.getOffPathwayActiveAbilities().size();
        if (currentCount >= MAX_REMEMBERED_ABILITIES) {
            return AbilityResult.failure("Ліміт пам'яті досягнуто!");
        }

        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(DETECTION_RADIUS);
        if (nearbyPlayers.isEmpty()) return AbilityResult.failure("Немає цілей поблизу.");

        context.ui().openChoiceMenu(
                "КРОК 1: Виберіть ціль",
                nearbyPlayers,
                player -> createPlayerHead(player, context),
                selectedPlayer -> startAnalysisPhase(context, selectedPlayer)
        );
        return AbilityResult.deferred();
    }

    private AbilityResult executeDelete(IAbilityContext context) {
        Beyonder casterBeyonder = context.getCasterBeyonder();
        Set<Ability> copiedAbilitiesSet = casterBeyonder.getOffPathwayActiveAbilities();

        if (copiedAbilitiesSet.isEmpty()) {
            return AbilityResult.failure("У вас немає скопійованих здібностей для видалення.");
        }

        List<Ability> copiedAbilitiesList = new ArrayList<>(copiedAbilitiesSet);

        context.ui().openChoiceMenu(
                "ВИДАЛЕННЯ: Оберіть здібність",
                copiedAbilitiesList,
                this::createDeleteIcon,
                selectedAbility -> {
                    boolean removed = casterBeyonder.removeAbility(selectedAbility.getIdentity());
                    UUID casterId = context.getCasterId();
                    if (removed) {
                        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GREEN + "Здібність '" + selectedAbility.getName() + "' успішно видалена."));
                        context.effects().playSound(context.playerData().getCurrentLocation(casterId), Sound.BLOCK_ANVIL_DESTROY, 1f, 1.2f);
                        context.effects().playSphereEffect(context.playerData().getCurrentLocation(casterId), 1, Particle.SOUL, 30);
                    } else {
                        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.RED + "Не вдалося видалити здібність '" + selectedAbility.getName() + "'."));
                    }
                }
        );

        return AbilityResult.deferred();
    }

    private void startAnalysisPhase(IAbilityContext context, Player target) {
        UUID casterId = context.getCasterId();
        context.effects().playConeEffect(context.playerData().getEyeLocation(casterId), context.playerData().getCurrentLocation(casterId).getDirection(), 30, 5, Particle.ENCHANT, 40);
        context.scheduling().scheduleDelayed(() -> openAbilitySelectionMenu(context, target.getUniqueId(), context.beyonder().getBeyonder(target.getUniqueId())), ANALYSIS_DELAY_SECONDS * 20L);
    }

    private void openAbilitySelectionMenu(IAbilityContext context, UUID targetId, Beyonder targetBeyonder) {
        if (!context.playerData().isOnline(targetId)) return;
        UUID casterId = context.getCasterId();
        List<Ability> targetAbilities = new ArrayList<>(targetBeyonder.getAbilities());

        List<Ability> availableAbilities = targetAbilities.stream()
                .filter(a -> targetBeyonder.getSequenceLevel() <= findAbilitySequence(targetBeyonder, a))
                .filter(this::canRememberAbility)
                .sorted(Comparator.comparingInt(a -> findAbilitySequence(targetBeyonder, a)))
                .toList();

        if (availableAbilities.isEmpty()) {
            context.messaging().sendMessageToActionBar(casterId,Component.text(ChatColor.RED + "Здібностей для копіювання не виявлено."));
            return;
        }

        context.ui().openChoiceMenu(
                "КРОК 2: Шанс (База 20%)",
                availableAbilities,
                ability -> createAbilityIcon(ability, targetBeyonder, context),
                selectedAbility -> attemptToCopyAbility(context, targetBeyonder, selectedAbility)
        );
    }

    private void attemptToCopyAbility(IAbilityContext context, Beyonder targetBeyonder, Ability ability) {
        Beyonder casterBeyonder = context.getCasterBeyonder();
        UUID casterId = context.getCasterId();

        if (casterBeyonder.getAbilityByName(ability.getName()).isPresent()) {
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.RED + "Ви вже знаєте цю здібність!"));
            return;
        }

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.RED + "Недостатньо духовності!"));
            return;
        }
        context.events().publishAbilityUsedEvent(this, casterBeyonder);
        int abilitySeq = findAbilitySequence(targetBeyonder, ability);
        int casterSeq = casterBeyonder.getSequenceLevel();
        int knownRecipes = context.beyonder().getUnlockedRecipesCount(casterId, targetBeyonder.getPathway().getName());

        double finalChance = calculateTotalChance(casterSeq, abilitySeq, knownRecipes);

        double seqDiff = (abilitySeq - casterSeq) * SEQUENCE_DIFF_MODIFIER;
        double recipeBonus = Math.min(knownRecipes * RECIPE_KNOWLEDGE_BONUS_PER_RECIPE, MAX_RECIPE_BONUS);

        context.messaging().sendMessage(casterId, String.format(
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

        if (Math.random() > finalChance) {
            context.effects().playSound(context.playerData().getCurrentLocation(casterId), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.RED + "× Невдача! Структура нестабільна."));
            return;
        }

        boolean added = casterBeyonder.addOffPathwayAbility(ability);
        if (added) {
            context.effects().playSound(context.playerData().getCurrentLocation(casterId), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            context.effects().playVortexEffect(context.playerData().getCurrentLocation(casterId), 2, 1, Particle.END_ROD, 30);
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GREEN + "✔ Здібність успішно скопійована!"));
        }
    }

    private double calculateTotalChance(int casterSeq, int abilitySeq, int knownRecipes) {
        double chance = BASE_CHANCE;
        double seqModifier = (abilitySeq - casterSeq) * SEQUENCE_DIFF_MODIFIER;
        chance += seqModifier;
        double recipeBonus = Math.min(knownRecipes * RECIPE_KNOWLEDGE_BONUS_PER_RECIPE, MAX_RECIPE_BONUS);
        chance += recipeBonus;
        return Math.max(0.10, Math.min(0.95, chance));
    }

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
            Beyonder casterBeyonder = context.getCasterBeyonder();
            UUID casterId = context.getCasterId();
            int casterSeq = casterBeyonder.getSequenceLevel();
            int knownRecipes = context.beyonder().getUnlockedRecipesCount(casterId, target.getPathway().getName());

            double finalChance = calculateTotalChance(casterSeq, abilitySeq, knownRecipes);

            double seqDiffValue = (abilitySeq - casterSeq) * SEQUENCE_DIFF_MODIFIER;
            double recipeBonusValue = Math.min(knownRecipes * RECIPE_KNOWLEDGE_BONUS_PER_RECIPE, MAX_RECIPE_BONUS);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Рівень послідовності: " + abilitySeq);
            lore.add("");
            lore.add(ChatColor.GRAY + "Тип: " + ChatColor.WHITE + getAbilityTypeDisplay(ability));
            lore.add("");
            lore.add(ChatColor.WHITE + "Шанс копіювання:");
            lore.add(ChatColor.GRAY + "  База: " + ChatColor.WHITE + String.format("%.0f%%", BASE_CHANCE * 100));
            lore.add(ChatColor.GRAY + "  Різниця рівнів: " + (seqDiffValue >= 0 ? ChatColor.GREEN : ChatColor.RED) + String.format("%+.0f%%", seqDiffValue * 100));
            lore.add(ChatColor.GRAY + "  Бонус знань: " + ChatColor.GREEN + String.format("+%.0f%%", recipeBonusValue * 100));
            lore.add(ChatColor.GRAY + "Підсумок: " + getColorForChance(finalChance) + String.format("%.0f%%", finalChance * 100));

            if (finalChance < 0.5) {
                lore.add(ChatColor.RED + "⚠ Складна здібність!");
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Натисніть для копіювання");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

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

    private ItemStack createDeleteIcon(Ability ability) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Видалити: " + ChatColor.WHITE + ability.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Тип: " + getAbilityTypeDisplay(ability));
            lore.add("");
            lore.add(ChatColor.DARK_RED + "⚠ Цю дію неможливо скасувати!");
            lore.add(ChatColor.YELLOW + "▶ Натисніть щоб видалити");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private AnalysisMode getCurrentMode(UUID playerId) {
        return playerModes.getOrDefault(playerId, AnalysisMode.COPY);
    }

    @Override
    public void cleanUp() {
        playerModes.clear();
    }
}