package me.vangoo.pathways.fool.abilities;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.MasteryProgressionCalculator;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Посл. 6: Безликий — Перевтілення (реворк).
 * Справжнє маскування: скін (Paper profile), нік у табі й чаті БУДЬ-ЯКОГО гравця,
 * що колись був на сервері. Без ліміту часу; шкода НЕ знімає маскування.
 * Підтримка коштує 20 духовності/с — вичерпалась → маскування спадає.
 * Профіль не персистентний (як у GatheringAnonymizer): релогін повертає справжній вигляд.
 */
public class Shapeshifting extends ActiveAbility {

    private static final int COST_PER_SECOND = 20;
    private static final int BASE_COOLDOWN = 10;
    private static final int MENU_LIMIT = 53;
    private static final String TEXTURES_PROPERTY = "textures";

    // Інстанс-реєстр живих маскувань (НЕ static — правило сесій).
    private final Map<UUID, MaskSession> activeMasks = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Перевтілення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Скопіюйте скін та ім'я будь-якого гравця, що колись був на сервері. " +
                "Без ліміту часу; шкода не знімає маскування. Підтримка: " +
                COST_PER_SECOND + " духовності/с. Повторний каст знімає личину.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST_PER_SECOND;
    }

    @Override
    public int getPeriodicCost() {
        return COST_PER_SECOND;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        if (activeMasks.containsKey(casterId)) {
            Player player = context.getCasterPlayer();
            unmask(player, casterId, "свідоме зняття");
            context.cooldown().setCooldown(this, casterId);
            return AbilityResult.success();
        }

        List<OfflinePlayer> candidates = Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null && !p.getUniqueId().equals(casterId))
                .sorted(Comparator.comparingLong(OfflinePlayer::getLastPlayed).reversed())
                .limit(MENU_LIMIT)
                .toList();

        if (candidates.isEmpty()) {
            return AbilityResult.failure("Немає личин: на сервері ще ніхто не бував");
        }

        context.ui().openChoiceMenu("Перевтілення: оберіть личину", candidates,
                this::createHeadItem,
                target -> activateMask(context, target));
        return AbilityResult.deferred();
    }

    private ItemStack createHeadItem(OfflinePlayer target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GOLD + target.getName());
            meta.setLore(List.of(ChatColor.GRAY + (target.isOnline() ? "Зараз онлайн" : "Був на сервері")));
            head.setItemMeta(meta);
        }
        return head;
    }

    private void activateMask(IAbilityContext context, OfflinePlayer target) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCasterPlayer();
        if (caster == null) return;
        caster.closeInventory();

        if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, context.getCasterBeyonder());

        String disguiseName = target.getName();
        PlayerProfile originalProfile = caster.getPlayerProfile();

        // Скін: копіюємо textures цілі, якщо кешовані; інакше — лише ім'я (фолбек зі спеки).
        PlayerProfile masked = caster.getPlayerProfile();
        masked.setName(disguiseName);
        PlayerProfile targetProfile = target.getPlayerProfile();
        // Синхронне заповнення з локального кешу Paper (без блокуючого запиту в Mojang) —
        // офлайн-гравці не мають "живого" GameProfile, тож без цього textures частіше порожні,
        // навіть якщо гравець колись реально заходив і скін уже закешовано сервером.
        targetProfile.completeFromCache();
        Optional<ProfileProperty> textures = targetProfile.getProperties().stream()
                .filter(p -> p.getName().equals(TEXTURES_PROPERTY))
                .findFirst();
        if (textures.isPresent()) {
            masked.setProperty(textures.get());
        } else {
            masked.removeProperty(TEXTURES_PROPERTY);
            context.messaging().sendMessage(casterId, ChatColor.YELLOW
                    + "⚠ Скін цієї личини не збережено на сервері — скопійовано лише ім'я.");
        }
        caster.setPlayerProfile(masked);
        caster.setDisplayName(disguiseName);
        caster.setPlayerListName(disguiseName);

        Location loc = caster.getLocation();
        caster.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4);
        caster.playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.0f);
        caster.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GOLD + "🎭 Ви — " + disguiseName));

        // Дрейн 20/с. Захоплений context несе ідентичність ЦЬОГО кастера — допустимо
        // (той самий патерн, що PsychologicalInvisibility).
        BukkitTask task = context.scheduling().scheduleRepeating(() -> {
            Player p = Bukkit.getPlayer(casterId);
            if (p == null || !p.isOnline()) {
                // Профіль скинеться сам при релогіні — просто чистимо сесію.
                MaskSession s = activeMasks.remove(casterId);
                if (s != null) s.task().cancel();
                return;
            }
            var beyonder = context.getCasterBeyonder();
            Spirituality sp = beyonder.getSpirituality();
            if (sp.current() < COST_PER_SECOND) {
                unmask(p, casterId, "виснаження духовності");
                return;
            }
            beyonder.setSpirituality(sp.decrement(COST_PER_SECOND));
            double masteryGain = MasteryProgressionCalculator.calculateMasteryGain(
                    COST_PER_SECOND, beyonder.getSequence());
            if (masteryGain > 0) {
                beyonder.setMastery(beyonder.getMastery().add(masteryGain));
            }
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GOLD + "🎭 " + disguiseName));
        }, 20L, 20L);

        activeMasks.put(casterId, new MaskSession(task, originalProfile, disguiseName));
    }

    private void unmask(Player player, UUID casterId, String reason) {
        MaskSession session = activeMasks.remove(casterId);
        if (session == null) return;
        session.task().cancel();

        if (player != null && player.isOnline()) {
            player.setPlayerProfile(session.originalProfile());
            player.setDisplayName(null);
            player.setPlayerListName(null);
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            player.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GRAY + "🎭 Личину знято • " + reason));
        }
    }

    @Override
    public void cleanUp() {
        for (UUID casterId : activeMasks.keySet()) {
            unmask(Bukkit.getPlayer(casterId), casterId, "вимкнення");
        }
        activeMasks.clear();
    }

    private record MaskSession(BukkitTask task, PlayerProfile originalProfile, String disguiseName) {
    }
}
