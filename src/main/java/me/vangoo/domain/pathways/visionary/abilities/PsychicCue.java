package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class PsychicCue extends ActiveAbility {
    private static final int RANGE = 3;
    private static final int DURATION_TICKS = 60 * 20;
    private static final int BASE_COOLDOWN = 60;
    private static final int COST = 150;

    @Override
    public String getName() {
        return "Психічний сигнал";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Навіює цілі психологічні установки через прямий контакт. " +
                "Діє " + (DURATION_TICKS / 20) + " секунд.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return (int) (BASE_COOLDOWN / SequenceScaler.calculateMultiplier(
                userSequence.level(), SequenceScaler.ScalingStrategy.MODERATE));
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.getTargetedEntity(RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targetOpt = context.getTargetedEntity(RANGE);

        if (targetOpt.isEmpty() || !(targetOpt.get() instanceof Player target)) {
            return AbilityResult.failure("Ціль має бути гравцем поруч");
        }

        context.openChoiceMenu(
                "Психічний сигнал",
                Arrays.asList(PsychicCueType.values()),
                this::createMenuItem,
                cue -> applyCue(context, target, cue)
        );

        return AbilityResult.success();
    }

    private ItemStack createMenuItem(PsychicCueType type) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(type.getDisplayName());
            meta.setLore(Collections.singletonList(type.getDescription()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyCue(IAbilityContext ctx, Player target, PsychicCueType type) {
        switch (type) {
            case HUNGER -> ctx.subscribeToEvent(
                    PlayerItemConsumeEvent.class,
                    e -> e.getPlayer().equals(target),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Їжа викликає огиду...");
                    },
                    DURATION_TICKS
            );

            case SLOTH -> {
                target.setSprinting(false);
                ctx.subscribeToEvent(
                        PlayerMoveEvent.class,
                        e -> e.getPlayer().equals(target) && e.getPlayer().isSprinting(),
                        e -> e.getPlayer().setSprinting(false),
                        DURATION_TICKS
                );
                ctx.subscribeToEvent(
                        PlayerToggleSprintEvent.class,
                        e -> e.getPlayer().equals(target) && e.isSprinting(),
                        e -> {
                            e.setCancelled(true);
                            ctx.sendMessage(target.getUniqueId(),
                                    ChatColor.ITALIC + "Ноги занадто важкі...");
                        },
                        DURATION_TICKS
                );
            }

            case PACIFISM -> ctx.subscribeToEvent(
                    EntityDamageByEntityEvent.class,
                    e -> e.getDamager().equals(target),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Агресія покинула вас...");
                    },
                    DURATION_TICKS
            );

            case SILENCE -> ctx.subscribeToEvent(
                    AsyncPlayerChatEvent.class,
                    e -> e.getPlayer().equals(target),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Слова застрягають...");
                    },
                    DURATION_TICKS
            );

            case APATHY -> ctx.subscribeToEvent(
                    PlayerInteractEvent.class,
                    e -> e.getPlayer().equals(target) &&
                            e.hasBlock() &&
                            e.getAction().name().contains("RIGHT"),
                    e -> {
                        e.setCancelled(true);
                        ctx.sendMessage(target.getUniqueId(),
                                ChatColor.ITALIC + "Байдуже до всього...");
                    },
                    DURATION_TICKS
            );
        }

        showSuccessEffects(ctx, target, type);
    }

    private void showSuccessEffects(IAbilityContext ctx, Player target, PsychicCueType type) {
        ctx.sendMessageToCaster(ChatColor.DARK_PURPLE + "Навіяно: " + type.getDisplayName());
        ctx.playSoundToCaster(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.6f);
        ctx.spawnParticle(Particle.WITCH, target.getEyeLocation(), 15, 0.4, 0.4, 0.4);
        ctx.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.8f);
    }

    enum PsychicCueType {
        HUNGER("Голод", "Заборонити їсти", Material.BREAD, ChatColor.GOLD),
        SLOTH("Млявість", "Заборонити бігати", Material.LEATHER_BOOTS, ChatColor.GRAY),
        PACIFISM("Пацифізм", "Заборонити атакувати", Material.IRON_SWORD, ChatColor.AQUA),
        SILENCE("Оніміння", "Заборонити чат", Material.PAPER, ChatColor.YELLOW),
        APATHY("Апатія", "Заборонити взаємодію", Material.BARRIER, ChatColor.DARK_GRAY);

        private final String name;
        private final String description;
        private final Material icon;
        private final ChatColor color;

        PsychicCueType(String name, String description, Material icon, ChatColor color) {
            this.name = name;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() {
            return color + name;
        }

        public String getDescription() {
            return ChatColor.GRAY + description;
        }

        public Material getIcon() {
            return icon;
        }
    }
}