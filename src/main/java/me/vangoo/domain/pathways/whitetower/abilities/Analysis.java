
package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.application.services.BukkitAbilityContext;
import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class Analysis extends ActiveAbility {
    private static final int ANALYSIS_DURATION_SECONDS = 15;
    private static final double DETECTION_RADIUS = 20.0;
    private static final int COST = 120;
    private static final int COOLDOWN = 120;
    private static final int MAX_REMEMBERED_ABILITIES = 10;

    // Бонуси до шансу успіху
    private static final double RECIPE_KNOWLEDGE_BONUS_PER_RECIPE = 0.05; // +5% за кожен рецепт
    private static final double MAX_RECIPE_BONUS = 0.25; // Максимум +25%

    @Override
    public String getName() {
        return "Аналіз";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Глибокий аналіз сил Потойбічного з можливістю запам'ятати здібність назавжди.\n" +
                ChatColor.GRAY + "Радіус: " + (int)DETECTION_RADIUS + " блоків\n" +
                ChatColor.GRAY + "Тривалість аналізу: " + ANALYSIS_DURATION_SECONDS + " секунд\n" +
                ChatColor.YELLOW + "⚠ Шанс залежить від знання шляху цілі\n" +
                ChatColor.YELLOW + "⚠ Ліміт запам'ятованих здібностей: " + MAX_REMEMBERED_ABILITIES + "\n" +
                ChatColor.GOLD + "✦ Здібність зберігається НАЗАВЖДИ";
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
        Beyonder caster = context.getCasterBeyonder();

        // Перевірка ліміту запам'ятованих здібностей
        int currentCount = caster.getOffPathwayActiveAbilities().size();
        if (currentCount >= MAX_REMEMBERED_ABILITIES) {
            return AbilityResult.failure(String.format(
                    "Досягнуто ліміт запам'ятованих здібностей (%d/%d)!\n" +
                            "Використайте §e/pathway forget <ability>§r для звільнення місця.",
                    currentCount, MAX_REMEMBERED_ABILITIES
            ));
        }

        List<Player> nearbyPlayers = context.getNearbyPlayers(DETECTION_RADIUS);

        if (nearbyPlayers.isEmpty()) {
            return AbilityResult.failure("Немає Потойбічних поблизу для аналізу");
        }

        context.openChoiceMenu(
                "Виберіть ціль для аналізу",
                nearbyPlayers,
                // Використовуємо лямбду, щоб передати context у createPlayerHead
                player -> createPlayerHead(player, context),
                // Прибираємо (Player p), залишаємо просто p ->, щоб компілятор сам зрозумів тип
                selectedPlayer -> startAnalysis(context, selectedPlayer)
        );

        return AbilityResult.deferred();
    }

    private ItemStack createPlayerHead(Player player, IAbilityContext context) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.AQUA + player.getName());

        // Показати інформацію про ціль
        Beyonder targetBeyonder = player.getWorld().getPlayers().stream()
                .filter(p -> p.getUniqueId().equals(player.getUniqueId()))
                .findFirst()
                .map(p -> context.getBeyonderFromEntity(p.getUniqueId()))
                .orElse(null);

        if (targetBeyonder != null) {
            meta.setLore(List.of(
                    ChatColor.GRAY + "Шлях: " + ChatColor.YELLOW + targetBeyonder.getPathway().getName(),
                    ChatColor.GRAY + "Послідовність: " + ChatColor.GOLD + targetBeyonder.getSequenceLevel()
            ));
        }

        head.setItemMeta(meta);
        return head;
    }

    private void startAnalysis(IAbilityContext context, Player target) {
        Player caster = context.getCaster();
        Beyonder casterBeyonder = context.getCasterBeyonder();

        Beyonder targetBeyonder = context.getBeyonderFromEntity(target.getUniqueId());
        if (targetBeyonder == null) {
            context.sendMessageToCaster(ChatColor.RED + "Ціль не є Потойбічним!");
            return;
        }

        // Ефекти початку аналізу
        caster.playSound(caster.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        context.playSphereEffect(
                target.getLocation().add(0, 1, 0),
                2.0,
                Particle.ENCHANT,
                ANALYSIS_DURATION_SECONDS * 20
        );

        caster.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.LIGHT_PURPLE + "✦ Аналіз розпочато... ✦")
        );

        context.sendMessageToCaster(String.format(
                "%sПочинаю аналіз %s%s%s...\n" +
                        "%sШлях: %s%s %s| Послідовність: %s%d",
                ChatColor.GRAY,
                ChatColor.AQUA, target.getName(), ChatColor.GRAY,
                ChatColor.GRAY,
                ChatColor.YELLOW, targetBeyonder.getPathway().getName(),
                ChatColor.DARK_GRAY,
                ChatColor.GOLD, targetBeyonder.getSequenceLevel()
        ));

        // Показати бонус від знання рецептів
        double recipeBonus = calculateRecipeBonus(context, targetBeyonder);
        if (recipeBonus > 0) {
            context.sendMessageToCaster(String.format(
                    "%s✓ Бонус від знання шляху: %s+%.0f%%",
                    ChatColor.GREEN,
                    ChatColor.GOLD,
                    recipeBonus * 100
            ));
        }

        AtomicReference<AbilityDomainEvent.AbilityUsed> analyzedEvent = new AtomicReference<>();

        // Підписуємось на події здібностей
        context.subscribeToAbilityEvents(
                event -> {
                    if (event instanceof AbilityDomainEvent.AbilityUsed used) {
                        if (used.casterId().equals(target.getUniqueId())) {
                            analyzedEvent.set(used);

                            // Миттєве повідомлення про фіксацію
                            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                            context.sendMessageToCaster(ChatColor.GREEN + "✓ Здібність зафіксована: " +
                                    ChatColor.AQUA + used.abilityName());

                            // Візуальний ефект фіксації
                            context.playLineEffect(
                                    caster.getEyeLocation(),
                                    target.getEyeLocation(),
                                    Particle.END_ROD
                            );

                            return true; // Відписатися
                        }
                    }
                    return false;
                },
                ANALYSIS_DURATION_SECONDS * 20
        );

        // Періодичні нагадування під час аналізу
        for (int i = 5; i < ANALYSIS_DURATION_SECONDS; i += 5) {
            final int remaining = ANALYSIS_DURATION_SECONDS - i;
            context.scheduleDelayed(() -> {
                if (analyzedEvent.get() == null) {
                    caster.spigot().sendMessage(
                            ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.YELLOW + "⏳ Аналіз... " + remaining + "с")
                    );
                }
            }, i * 20L);
        }

        // Після завершення аналізу
        context.scheduleDelayed(() -> {
            finalizeAnalysis(context, target, targetBeyonder, analyzedEvent.get());
        }, ANALYSIS_DURATION_SECONDS * 20L);
    }

    private void finalizeAnalysis(
            IAbilityContext context,
            Player target,
            Beyonder targetBeyonder,
            AbilityDomainEvent.AbilityUsed analyzedEvent
    ) {
        Player caster = context.getCaster();
        Beyonder casterBeyonder = context.getCasterBeyonder();

        if (analyzedEvent == null) {
            caster.playSound(caster.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
            context.sendMessageToCaster(ChatColor.RED +
                    target.getName() + " не використав жодної здібності під час аналізу");
            return;
        }

        Optional<Ability> abilityOpt = targetBeyonder.getAbilityByName(analyzedEvent.abilityName());

        if (abilityOpt.isEmpty()) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Не вдалося знайти здібність: " + analyzedEvent.abilityName());
            return;
        }

        Ability ability = abilityOpt.get();

        // Перевірка чи можна запам'ятати цю здібність
        if (!canRememberAbility(ability)) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Неможливо запам'ятати цей тип здібності!");
            return;
        }

        // Перевірка чи вже є ця здібність
        if (casterBeyonder.getAbilityByName(ability.getName()).isPresent()) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Ви вже знаєте цю здібність!");
            return;
        }

        // Подвійна перевірка ліміту
        int currentCount = casterBeyonder.getOffPathwayActiveAbilities().size();
        if (currentCount >= MAX_REMEMBERED_ABILITIES) {
            context.sendMessageToCaster(ChatColor.RED +
                    String.format("Досягнуто ліміт запам'ятованих здібностей (%d/%d)!",
                            currentCount, MAX_REMEMBERED_ABILITIES));
            return;
        }

        // ============================================
        // РОЗРАХУНОК ШАНСУ УСПІХУ
        // ============================================

        int abilitySequence = findAbilitySequence(targetBeyonder, ability);
        if (abilitySequence == -1) {
            abilitySequence = targetBeyonder.getSequenceLevel();
        }

        int casterSequence = casterBeyonder.getSequenceLevel();

        // Базовий шанс на основі послідовностей
        SequenceBasedSuccessChance baseChance =
                new SequenceBasedSuccessChance(casterSequence, abilitySequence);

        double baseSuccessRate = baseChance.calculateChance();

        // Бонус від знання рецептів шляху
        double recipeBonus = calculateRecipeBonus(context, targetBeyonder);

        // Фінальний шанс (не може бути більше 95%)
        double finalSuccessRate = Math.min(0.95, baseSuccessRate + recipeBonus);

        // Показуємо інформацію про шанс
        context.sendMessageToCaster(String.format(
                "\n%s═══ АНАЛІЗ ЗАВЕРШЕНО ═══\n" +
                        "%sБазовий шанс: %s%.0f%%\n" +
                        "%sБонус від знання: %s+%.0f%%\n" +
                        "%sФінальний шанс: %s%.0f%%",
                ChatColor.GOLD,
                ChatColor.GRAY, ChatColor.WHITE, baseSuccessRate * 100,
                ChatColor.GRAY, ChatColor.GREEN, recipeBonus * 100,
                ChatColor.GRAY, ChatColor.AQUA, finalSuccessRate * 100
        ));

        // Перевірка успіху
        if (Math.random() >= finalSuccessRate) {
            caster.playSound(caster.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
            context.sendMessageToCaster(String.format(
                    "%sНе вдалося запам'ятати здібність!\n" +
                            "%sШанс був: %s%.0f%%",
                    ChatColor.RED,
                    ChatColor.GRAY, ChatColor.YELLOW, finalSuccessRate * 100
            ));

            // Показуємо підказку
            if (recipeBonus < MAX_RECIPE_BONUS) {
                context.sendMessageToCaster(ChatColor.YELLOW +
                        "💡 Підказка: Вивчіть більше рецептів шляху " +
                        targetBeyonder.getPathway().getName() + " для збільшення шансу!");
            }

            return;
        }

        // ============================================
        // УСПІШНЕ ЗАПАМ'ЯТОВУВАННЯ
        // ============================================

        Ability rememberedAbility = createRememberedAbility(ability);
        boolean added = casterBeyonder.addOffPathwayAbility(rememberedAbility);

        if (!added) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Не вдалося додати здібність");
            return;
        }

        // Споживаємо ресурси
        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.sendMessageToCaster(ChatColor.RED +
                    "Недостатньо духовності!");
            casterBeyonder.removeAbility(rememberedAbility.getIdentity());
            return;
        }

        // ============================================
        // ЕФЕКТИ УСПІХУ
        // ============================================

        caster.playSound(caster.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        caster.playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);

        context.playVortexEffect(
                caster.getLocation().add(0, 1, 0),
                3.0,
                2.0,
                Particle.ENCHANT,
                40
        );

        context.sendMessageToCaster(String.format(
                "\n%s✦✦✦ АНАЛІЗ УСПІШНИЙ! ✦✦✦\n" +
                        "%sЗапам'ятано здібність: %s%s\n" +
                        "%sТип: %s%s\n" +
                        "%sЗдібність %sНАЗАВЖДИ %sзбережена!\n" +
                        "%sЗапам'ятовано: %s%d/%d",
                ChatColor.GOLD,
                ChatColor.GRAY, ChatColor.AQUA, ability.getName(),
                ChatColor.GRAY, ChatColor.YELLOW, getAbilityTypeDisplay(ability),
                ChatColor.GRAY, ChatColor.GREEN, ChatColor.GRAY,
                ChatColor.GRAY, ChatColor.YELLOW,
                casterBeyonder.getOffPathwayActiveAbilities().size(),
                MAX_REMEMBERED_ABILITIES
        ));

        // Повідомлення цілі (якщо онлайн)
        if (target.isOnline()) {
            target.sendMessage(String.format(
                    "%s⚠ %s%s %sпроаналізував вашу здібність %s%s%s!",
                    ChatColor.YELLOW,
                    ChatColor.AQUA, caster.getName(),
                    ChatColor.GRAY,
                    ChatColor.AQUA, ability.getName(),
                    ChatColor.GRAY
            ));
        }
    }

    /**
     * Розрахувати бонус від знання рецептів шляху
     */
    private double calculateRecipeBonus(IAbilityContext context, Beyonder target) {
        String targetPathway = target.getPathway().getName();

        // Підраховуємо кількість відомих рецептів цього шляху (0-9)
        int knownRecipes = context.getKnownRecipeCount(targetPathway);

        double bonus = knownRecipes * RECIPE_KNOWLEDGE_BONUS_PER_RECIPE;
        return Math.min(bonus, MAX_RECIPE_BONUS);
    }

    /**
     * Створити запам'ятовану версію здібності
     */
    private Ability createRememberedAbility(Ability original) {
        // Для активних здібностей просто повертаємо копію
        if (original instanceof ActiveAbility activeAbility) {
            return activeAbility;
        }

        // Для пасивних здібностей також повертаємо як є
        // Вони будуть автоматично керуватись PassiveAbilityManager
        return original;
    }

    /**
     * Перевірити чи можна запам'ятати цю здібність
     */
    private boolean canRememberAbility(Ability ability) {
        // Можна запам'ятати майже всі типи здібностей
        // Виключаємо тільки специфічні системні здібності якщо потрібно
        return ability.getType() == AbilityType.ACTIVE ||
                ability.getType() == AbilityType.TOGGLEABLE_PASSIVE ||
                ability.getType() == AbilityType.PERMANENT_PASSIVE;
    }

    /**
     * Знайти послідовність здібності
     */
    private int findAbilitySequence(Beyonder beyonder, Ability ability) {
        for (int seq = 0; seq <= 9; seq++) {
            List<Ability> abilities = beyonder.getPathway().GetAbilitiesForSequence(seq);
            for (Ability a : abilities) {
                if (a.getIdentity().equals(ability.getIdentity())) {
                    return seq;
                }
            }
        }
        return -1;
    }

    /**
     * Отримати назву типу здібності для відображення
     */
    private String getAbilityTypeDisplay(Ability ability) {
        return switch (ability.getType()) {
            case ACTIVE -> "Активна";
            case TOGGLEABLE_PASSIVE -> "Пасивна (перемикач)";
            case PERMANENT_PASSIVE -> "Пасивна (постійна)";
        };
    }
}