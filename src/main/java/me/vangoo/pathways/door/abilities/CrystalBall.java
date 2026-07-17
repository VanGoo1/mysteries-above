package me.vangoo.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.DivinationOdds;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Посл. 7 (Door): Кришталева куля — астрологічні повноваження (вікі LOTM):
 * 1) власні спогади (журнал недавніх подій за участі кастера, як сонне провидіння про себе);
 * 2) накладання силуетів — викриття маскування гравця в прицілі.
 */
public class CrystalBall extends ActiveAbility {

    private static final int BASE_COST = 120;
    private static final int BASE_COOLDOWN = 60;
    private static final int MEMORY_BASE_WINDOW_SECONDS = 900; // 15 хв, скейлиться
    private static final double SILHOUETTE_RANGE = 20.0;
    private static final int ANTI_DIVINATION_UNLOCK_SEQUENCE = 7;

    private final Random rng = new Random();

    @Override
    public String getName() {
        return "Кришталева куля";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Куля астролога: пригадайте власне минуле або накладіть силуети, " +
                "щоб викрити чуже маскування.";
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
        List<Mode> modes = List.of(Mode.MEMORY_JOURNAL, Mode.SILHOUETTE);
        context.ui().openChoiceMenu("Кришталева куля", modes, this::createMenuItem,
                mode -> handleChoice(context, mode));
        return AbilityResult.deferred();
    }

    private void handleChoice(IAbilityContext context, Mode mode) {
        Player caster = context.getCasterPlayer();
        if (caster != null) caster.closeInventory();
        switch (mode) {
            case MEMORY_JOURNAL -> runMemoryJournal(context);
            case SILHOUETTE -> runSilhouette(context);
        }
    }

    // ========== 1. ВЛАСНІ СПОГАДИ ==========

    private void runMemoryJournal(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Beyonder beyonder = context.getCasterBeyonder();
        if (!AbilityResourceConsumer.consumeResources(this, beyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, beyonder);
        playGazeEffect(context);

        int window = (int) Math.ceil(MEMORY_BASE_WINDOW_SECONDS * SequenceScaler.calculateMultiplier(
                beyonder.getSequenceLevel(), SequenceScaler.ScalingStrategy.MODERATE));

        Player caster = context.getCasterPlayer();
        String casterName = caster.getName();
        Location loc = context.getCasterLocation();

        List<AbilityDomainEvent> ownCasts = context.events().getAbilityEventHistory(casterId, window);
        List<RecordedEvent> worldEvents = new ArrayList<>(context.events().getPastEvents(loc, 30, window));
        worldEvents.removeIf(e -> !e.getDescription().contains(casterName));
        worldEvents.sort(Comparator.comparingLong(RecordedEvent::getTimestamp).reversed());

        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
        context.messaging().sendMessage(casterId,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "🔮 СПОГАДИ КРИШТАЛЕВОЇ КУЛІ");
        long now = System.currentTimeMillis();

        if (ownCasts.isEmpty() && worldEvents.isEmpty()) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "Туман минулого порожній.");
        }
        for (AbilityDomainEvent e : ownCasts.subList(0, Math.min(8, ownCasts.size()))) {
            long minutesAgo = Math.max(0, (now - e.occurredAt()) / 60000);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "• " + minutesAgo
                    + " хв тому — ви використали " + ChatColor.AQUA + e.abilityName());
        }
        for (RecordedEvent e : worldEvents.subList(0, Math.min(8, worldEvents.size()))) {
            long minutesAgo = Math.max(0, (now - e.getTimestamp()) / 60000);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "• " + minutesAgo
                    + " хв тому — " + ChatColor.AQUA + e.getDescription());
        }
        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
    }

    // ========== 2. НАКЛАДАННЯ СИЛУЕТІВ ==========

    private void runSilhouette(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(SILHOUETTE_RANGE);
        if (targetOpt.isEmpty()) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "🔮 Дивіться на гравця (до " + (int) SILHOUETTE_RANGE + " бл).");
            return;
        }
        Player target = targetOpt.get();

        if (resistsDivination(context, target)) {
            // Опір пройдено ціллю — духовність все одно витрачається (спроба була).
            if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
                return;
            }
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "🔮 Силуети розпливаються — щось заважає ворожінню.");
            return;
        }

        if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, context.getCasterBeyonder());
        playGazeEffect(context);

        String realName = target.getName();
        String profileName = target.getPlayerProfile().getName();
        String displayName = ChatColor.stripColor(target.getDisplayName());
        boolean disguised = (profileName != null && !realName.equals(profileName))
                || (displayName != null && !realName.equals(displayName));

        if (disguised) {
            context.messaging().sendMessage(casterId, ChatColor.GOLD + "🔮 Силуети НЕ збігаються! Перед вами "
                    + ChatColor.WHITE + (profileName != null ? profileName : displayName)
                    + ChatColor.GOLD + ", а насправді це " + ChatColor.RED + realName + ChatColor.GOLD + ".");
        } else {
            context.messaging().sendMessage(casterId, ChatColor.GREEN + "🔮 Силуети збігаються — "
                    + ChatColor.WHITE + realName + ChatColor.GREEN + " є тим, ким виглядає.");
        }
    }

    private boolean resistsDivination(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        boolean anti = context.beyonder().isAbilityActivated(targetId, new AntiDivination().getIdentity());
        if (!anti) return false;
        Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);
        if (targetBeyonder == null || targetBeyonder.getSequenceLevel() > ANTI_DIVINATION_UNLOCK_SEQUENCE) {
            return false;
        }
        double chance = new DivinationOdds(context.getCasterBeyonder().getSequenceLevel(),
                targetBeyonder.getSequenceLevel()).successProbability();
        return rng.nextDouble() >= chance;
    }

    private void playGazeEffect(IAbilityContext context) {
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        context.effects().spawnParticle(Particle.END_ROD,
                context.getCasterLocation().add(0, 1.5, 0), 25, 0.4, 0.4, 0.4);
    }

    private ItemStack createMenuItem(Mode mode) {
        ItemStack item = new ItemStack(mode.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(mode.color + mode.displayName);
            meta.setLore(List.of(ChatColor.GRAY + mode.description));
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum Mode {
        MEMORY_JOURNAL("Власні спогади", "Хронологія ваших недавніх дій і подій довкола вас",
                Material.WRITABLE_BOOK, ChatColor.AQUA),
        SILHOUETTE("Накладання силуетів", "Викрити, чи справжній вигляд гравця в прицілі",
                Material.AMETHYST_CLUSTER, ChatColor.LIGHT_PURPLE);

        final String displayName;
        final String description;
        final Material icon;
        final ChatColor color;

        Mode(String displayName, String description, Material icon, ChatColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }
    }
}
