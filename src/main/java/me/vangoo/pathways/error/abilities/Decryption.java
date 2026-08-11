package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.valueobjects.CryptologistInsight;
import me.vangoo.domain.valueobjects.DreamStealerTheft;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import me.vangoo.infrastructure.items.CustomItemFactory;
import me.vangoo.infrastructure.items.PotionItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Посл. 7: «Дешифрування» — Криптолог вичитує правду з того, на що дивиться.
 *
 * <p>Режим обирає приціл: жива ціль → кинутий містичний предмет → блок → зона.
 * Глибина відповіді залежить від того, скільки слідів РЕАЛЬНО існує
 * ({@link CryptologistInsight#depthFor}) — це не ворожіння, здібність нічого не вигадує.
 * Ціна глибокого читання — власний глузд кастера.
 */
public class Decryption extends ActiveAbility {

    private static final double TARGET_CIRCLE_RADIUS = 1.4;
    private static final int READ_EFFECT_TICKS = 40;
    private static final int GLOW_TICKS = 80;
    private static final int SCENE_HOLOGRAMS = 3;
    private static final long HOLOGRAM_TICKS = 200L;
    /** Скільки лежить слід до винуватця — щоб встигнути піти по ньому. */
    private static final int TRAIL_TICKS = 200;

    /** Кодеки без стану — читають мітку шляху прямо з NBT предмета. */
    private static final CharacteristicCodec CHARACTERISTICS = new CharacteristicCodec();
    private static final PotionItemFactory POTIONS = new PotionItemFactory();

    @Override
    public String getName() {
        return "Дешифрування";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви вичитуєте правду з того, на що дивитесь.\n" +
                "Жива ціль у межах " + (int) CryptologistInsight.CROSSHAIR_RANGE + " блоків: активні чари, "
                + "недавнє застосування сил Потойбіччя, стан розуму.\n"
                + "Блок у прицілі: сліди на місці, ім'я винуватця і слід по землі туди, "
                + "де він зараз.\n"
                + "Містичний предмет, кинутий на землю під прицілом: справжня мітка, "
                + "мітка шляху, рецепти, куди він іде, і замовляння на "
                + CryptologistInsight.INCANTATION_DURATION_SECONDS / 60 + " хв.\n"
                + "Погляд у порожнечу: сканування зони на "
                + (int) CryptologistInsight.ZONE_RADIUS + " блоків — скільки Потойбічних поруч, "
                + "де застосовували сили, які сховища розворушили.\n"
                + "Глибина відповіді залежить від кількості слідів (потрібно "
                + CryptologistInsight.depthThreshold(1, userSequence) + "/"
                + CryptologistInsight.depthThreshold(2, userSequence) + "/"
                + CryptologistInsight.depthThreshold(3, userSequence) + ") — здібність не вгадує.\n"
                + "Глибоке читання привертає увагу вищих сил: прибавка до "
                + CryptologistInsight.sanityLoss(3, userSequence) + " втрати контролю.";
    }

    @Override
    public int getSpiritualityCost() {
        return CryptologistInsight.DECRYPTION_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return CryptologistInsight.decryptionCooldownSeconds(userSequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // Режим обирає приціл: жива ціль → предмет на землі (M6) → блок → зона (M7).
        Optional<LivingEntity> target =
                context.targeting().getTargetedEntity(CryptologistInsight.CROSSHAIR_RANGE);
        if (target.isPresent()) {
            return readLiving(context, target.get());
        }

        Player caster = context.getCasterPlayer();
        Item dropped = aimedMysticalItem(caster);
        if (dropped != null) {
            return readItem(context, dropped);
        }

        Block aimed = caster != null
                ? caster.getTargetBlockExact((int) CryptologistInsight.CROSSHAIR_RANGE)
                : null;
        if (aimed != null) {
            return readScene(context, aimed);
        }
        return readZone(context);
    }

    /**
     * Режим D — зона. Шукає прихований МІСТИЦИЗМ навколо (Потойбічні поруч, свіжі застосування
     * сил, розворушені сховища), а не скарби — скарби це робота Вищої спостережливості.
     */
    private AbilityResult readZone(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        Location center = context.getCasterLocation();

        // Гравців і їхню історію читаємо ще в основному потоці — далі йде запит у БД.
        List<Location> mysticActivity = new ArrayList<>();
        int beyondersNear = 0;
        for (Player nearby : context.targeting().getNearbyPlayers(CryptologistInsight.ZONE_RADIUS)) {
            UUID nearbyId = nearby.getUniqueId();
            if (nearbyId.equals(casterId) || !context.beyonder().isBeyonder(nearbyId)) {
                continue;
            }
            beyondersNear++;
            if (!context.events().getAbilityEventHistory(nearbyId,
                    CryptologistInsight.TRACE_LOOKUP_WINDOW_SECONDS).isEmpty()) {
                mysticActivity.add(nearby.getLocation());
            }
        }
        int near = beyondersNear;

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("Сканування зони...", NamedTextColor.DARK_PURPLE));
        context.effects().playWaveEffect(center, CryptologistInsight.ZONE_RADIUS,
                Particle.ENCHANT, READ_EFFECT_TICKS);
        context.effects().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.9f);

        String casterName = context.getCasterPlayer() != null ? context.getCasterPlayer().getName() : null;
        context.scheduling().runAsync(() -> {
            // CoreProtect ходить у БД — синхронний запит підвісив би сервер.
            List<RecordedEvent> traces = context.events().getPastEvents(center,
                    (int) CryptologistInsight.ZONE_RADIUS,
                    CryptologistInsight.TRACE_LOOKUP_WINDOW_SECONDS);
            context.scheduling().scheduleDelayed(
                    () -> revealZone(context, casterId, sequence, near, mysticActivity,
                            foreignTraces(traces, casterName)), 0L);
        });
        return AbilityResult.success();
    }

    private void revealZone(IAbilityContext context, UUID casterId, Sequence sequence,
                            int beyondersNear, List<Location> mysticActivity,
                            List<RecordedEvent> traces) {
        // CoreProtect пише РЯДОК НА КОЖЕН СТАК, тож рахуємо унікальні сховища, а не транзакції:
        // інакше одна перекладена скриня читається як «розворушено 64».
        Map<String, RecordedEvent> containers = new LinkedHashMap<>();
        for (RecordedEvent trace : traces) {
            if (trace.getType() != RecordedEvent.EventType.CONTAINER_TRANSACTION) continue;
            Location loc = trace.getLocation();
            containers.putIfAbsent(
                    loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ(), trace);
        }
        List<RecordedEvent> disturbed = new ArrayList<>(containers.values());

        int evidence = beyondersNear * 2 + mysticActivity.size() + disturbed.size();
        int depth = CryptologistInsight.depthFor(evidence, sequence);
        if (depth == 0) {
            context.messaging().sendMessage(casterId,
                    ChatColor.GRAY + "✖ Зона мовчить — містицизму не чути");
            return;
        }

        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 1.0f);
        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "✦ Дешифровано зону"
                + ChatColor.DARK_GRAY + " (глибина " + depth + "/3)");
        // Кількість, ніколи імена: хто саме — це справа Вищої спостережливості.
        context.messaging().sendMessage(casterId, beyondersNear == 0
                ? ChatColor.GRAY + "  Потойбічних поруч не відчувається"
                : ChatColor.GRAY + "  Потойбічних поруч: " + ChatColor.AQUA + beyondersNear);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.2f);

        if (depth >= 2) {
            context.messaging().sendMessage(casterId, mysticActivity.isEmpty()
                    ? ChatColor.GRAY + "  Свіжих застосувань сил не чути"
                    : ChatColor.GRAY + "  Місць свіжої містичної активності: "
                            + ChatColor.LIGHT_PURPLE + mysticActivity.size());
            for (Location spot : mysticActivity) {
                context.effects().playAlertHalo(spot, errorColor);
                context.messaging().spawnTemporaryHologram(spot.clone().add(0, 2.4, 0),
                        Component.text("містична активність", NamedTextColor.LIGHT_PURPLE),
                        HOLOGRAM_TICKS);
            }
        }
        if (depth >= 3) {
            context.messaging().sendMessage(casterId, disturbed.isEmpty()
                    ? ChatColor.GRAY + "  Сховища ніхто не чіпав"
                    : ChatColor.GRAY + "  Сховищ, до яких лазили: " + ChatColor.LIGHT_PURPLE
                            + disturbed.size());
            for (int i = 0; i < Math.min(SCENE_HOLOGRAMS, disturbed.size()); i++) {
                RecordedEvent trace = disturbed.get(i);
                Location spot = trace.getLocation().clone().add(0.5, 0.5, 0.5);
                context.effects().playAlertHalo(spot, errorColor);
                context.messaging().spawnTemporaryHologram(spot.clone().add(0, 1.3, 0),
                        Component.text(trace.getDescription()), HOLOGRAM_TICKS);
            }
            context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.25f, 0.5f);
        }

        spendSanity(context, casterId, depth, sequence);
    }

    /** Режим A — жива ціль. Ніколи не розкриває шлях і Послідовність: це робота Спостережливості. */
    private AbilityResult readLiving(IAbilityContext context, LivingEntity target) {
        UUID casterId = context.getCasterId();
        Beyonder caster = context.getCasterBeyonder();
        UUID targetId = target.getUniqueId();

        List<PotionEffect> states = new ArrayList<>(target.getActivePotionEffects());
        boolean isBeyonder = context.beyonder().isBeyonder(targetId);
        List<AbilityDomainEvent> recentPowers = isBeyonder
                ? context.events().getAbilityEventHistory(targetId,
                        CryptologistInsight.TRACE_LOOKUP_WINDOW_SECONDS)
                : List.of();
        Beyonder victim = isBeyonder ? context.beyonder().getBeyonder(targetId) : null;
        int sanityScale = victim != null ? victim.getSanityLossScale() : 0;

        int evidence = states.size() + recentPowers.size()
                + (isBeyonder ? 2 : 0)
                + (sanityScale > 10 ? 2 : 0);
        int depth = CryptologistInsight.depthFor(evidence, caster.getSequence());
        if (depth == 0) {
            return AbilityResult.failure("✖ Слідів замало — Криптолог не вгадує");
        }

        playReadEffects(context, casterId, target, depth);

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_PURPLE + "✦ Дешифровано: " + ChatColor.WHITE + displayName(target)
                + ChatColor.DARK_GRAY + " (глибина " + depth + "/3)");
        lines.add(states.isEmpty()
                ? ChatColor.GRAY + "  Жодних чарів не діє"
                : ChatColor.GRAY + "  Діють чари: " + ChatColor.AQUA + describeStates(states));

        if (depth >= 2) {
            lines.add(recentPowers.isEmpty()
                    ? ChatColor.GRAY + "  Сил Потойбіччя останнім часом не застосовував"
                    : ChatColor.GRAY + "  Застосовував сили: " + ChatColor.LIGHT_PURPLE
                            + recentPowers.size() + "× , останнє — "
                            + lastPowerName(recentPowers));
        }
        if (depth >= 3) {
            lines.add(ChatColor.GRAY + "  Стан розуму: " + mindState(isBeyonder, sanityScale));
        }

        for (String line : lines) {
            context.messaging().sendMessage(casterId, line);
        }
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.2f);

        spendSanity(context, casterId, depth, caster.getSequence());
        return AbilityResult.success();
    }

    /**
     * Режим C — містичний предмет, кинутий на землю перед собою. У руці читати нічим:
     * руки зайняті предметом самої здібності — ним і кастують.
     * Нічого не вигадує: справжня мітка береться з NBT, а походження — з рецептів,
     * які вже існують; жодного стеження за власником предмета.
     */
    private AbilityResult readItem(IAbilityContext context, Item dropped) {
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        ItemStack item = dropped.getItemStack();

        String id = CustomItemFactory.getCustomItemId(item).orElse(null);
        Marked marked = markOf(item);
        List<UnlockedRecipe> recipes = context.beyonder().findRecipesUsing(item);

        int evidence = 2 + (id != null ? 2 : 0) + (marked != null ? 2 : 0)
                + 3 * Math.min(2, recipes.size());
        int depth = CryptologistInsight.depthFor(evidence, sequence);
        if (depth == 0) {
            return AbilityResult.failure("✖ Предмет мовчить — читати нема чого");
        }

        Color errorColor = PathwayBranding.liquidOf("Error");
        Location where = dropped.getLocation().clone().add(0, 0.4, 0);
        context.effects().playScriptureAura(where, errorColor, READ_EFFECT_TICKS);
        context.effects().playCircleEffect(where, TARGET_CIRCLE_RADIUS, Particle.ENCHANT, READ_EFFECT_TICKS);
        context.effects().playSound(where, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.8f + 0.2f * depth);
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 1.0f);

        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "✦ Дешифровано предмет"
                + ChatColor.DARK_GRAY + " (глибина " + depth + "/3)");
        if (id != null) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "  Справжня мітка: "
                    + ChatColor.AQUA + id);
        }
        if (marked != null) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "  " + marked.kind() + ": "
                    + ChatColor.AQUA + marked.pathway() + ChatColor.GRAY + ", Послідовність "
                    + ChatColor.AQUA + marked.sequence());
        }
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.2f);

        if (depth >= 2) {
            context.messaging().sendMessage(casterId, recipes.isEmpty()
                    ? ChatColor.GRAY + "  Походження невідоме — у жоден рецепт не входить"
                    : ChatColor.GRAY + "  Іде в рецепти: " + ChatColor.LIGHT_PURPLE
                            + describeRecipes(recipes));
        }
        if (depth >= 3) {
            // ponytail: замовляння підсилює шкоду кастера, а не сам предмет — хука на силу
            // ефекту кастомних предметів у кодовій базі немає (див. план error-seq7.md, режим C).
            context.amplification().amplifyDamage(casterId,
                    CryptologistInsight.incantationDamageMultiplier(sequence),
                    CryptologistInsight.INCANTATION_DURATION_SECONDS);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "  Ви вимовляєте замовляння — "
                    + ChatColor.LIGHT_PURPLE + "×"
                    + String.format("%.2f", CryptologistInsight.incantationDamageMultiplier(sequence))
                    + ChatColor.GRAY + " сили на "
                    + CryptologistInsight.INCANTATION_DURATION_SECONDS / 60 + " хв");
            context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.25f, 0.5f);
        }

        spendSanity(context, casterId, depth, sequence);
        return AbilityResult.success();
    }

    /** Кинутий предмет плагіну під прицілом — читати в руці нічим, руки зайняті кастом. */
    private Item aimedMysticalItem(Player caster) {
        if (caster == null) {
            return null;
        }
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTraceEntities(eye, eye.getDirection(),
                CryptologistInsight.CROSSHAIR_RANGE, 0.6, entity -> entity instanceof Item);
        if (hit == null || !(hit.getHitEntity() instanceof Item dropped)) {
            return null;
        }
        ItemStack stack = dropped.getItemStack();
        return CustomItemFactory.isCustomItem(stack) || markOf(stack) != null ? dropped : null;
    }

    /** Шлях і Послідовність, вписані в NBT предмета (Характеристика чи зілля); інакше null. */
    private Marked markOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        Optional<Characteristic> characteristic = CHARACTERISTICS.read(item);
        if (characteristic.isPresent()) {
            return new Marked("Характеристика шляху", characteristic.get().pathwayName(),
                    characteristic.get().sequence());
        }
        if (POTIONS.isPathwayPotion(item)) {
            return new Marked("Зілля шляху", POTIONS.getPathwayName(item),
                    POTIONS.getSequence(item).level());
        }
        return null;
    }

    private String describeRecipes(List<UnlockedRecipe> recipes) {
        List<String> parts = new ArrayList<>();
        for (UnlockedRecipe recipe : recipes) {
            parts.add(recipe.pathwayName() + " Посл. " + recipe.sequence());
        }
        return String.join(", ", parts);
    }

    /** Мітка шляху, вшита в предмет. */
    private record Marked(String kind, String pathway, int sequence) {}

    /**
     * Режим B — місце. На відміну від Містичної Реконструкції (аудит: «що тут змінилось»),
     * тут питання інше — ХТО це зробив і де він зараз.
     */
    private AbilityResult readScene(IAbilityContext context, Block block) {
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        Location scene = block.getLocation().add(0.5, 0.5, 0.5);

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("Зчитування слідів...", NamedTextColor.DARK_PURPLE));
        context.effects().playSound(scene, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.9f);

        String casterName = context.getCasterPlayer() != null ? context.getCasterPlayer().getName() : null;
        context.scheduling().runAsync(() -> {
            // CoreProtect ходить у БД — синхронний запит підвісив би сервер.
            List<RecordedEvent> traces = context.events().getPastEvents(scene,
                    CryptologistInsight.TRACE_LOOKUP_RADIUS,
                    CryptologistInsight.TRACE_LOOKUP_WINDOW_SECONDS);
            context.scheduling().scheduleDelayed(
                    () -> revealScene(context, casterId, sequence, scene,
                            foreignTraces(traces, casterName)), 0L);
        });
        return AbilityResult.success();
    }

    private void revealScene(IAbilityContext context, UUID casterId, Sequence sequence,
                             Location scene, List<RecordedEvent> traces) {
        int depth = CryptologistInsight.depthFor(traces.size(), sequence);
        if (depth == 0) {
            context.messaging().sendMessage(casterId,
                    ChatColor.GRAY + "✖ Слідів замало — Криптолог не вгадує");
            return;
        }

        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playAlertHalo(scene, errorColor);
        context.effects().playCircleEffect(scene, TARGET_CIRCLE_RADIUS, Particle.ENCHANT, READ_EFFECT_TICKS);
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 1.0f);

        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "✦ Дешифровано місце"
                + ChatColor.DARK_GRAY + " (глибина " + depth + "/3)");
        context.messaging().sendMessage(casterId, ChatColor.GRAY + "  Тут хтось був: "
                + ChatColor.AQUA + traces.size() + ChatColor.GRAY + " слідів за останні "
                + CryptologistInsight.TRACE_LOOKUP_WINDOW_SECONDS / 60 + " хв");

        for (int i = 0; i < Math.min(SCENE_HOLOGRAMS, traces.size()); i++) {
            RecordedEvent trace = traces.get(i);
            context.messaging().spawnTemporaryHologram(
                    trace.getLocation().clone().add(0.5, 1.3 + 0.25 * i, 0.5),
                    Component.text(trace.getDescription()), HOLOGRAM_TICKS);
        }
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.2f);

        if (depth >= 2) {
            String culprit = culpritName(traces);
            context.messaging().sendMessage(casterId, culprit == null
                    ? ChatColor.GRAY + "  Винуватця не впізнати"
                    : ChatColor.GRAY + "  Винуватець: " + ChatColor.LIGHT_PURPLE + culprit);

            Player pursued = culprit != null ? Bukkit.getPlayerExact(culprit) : null;
            if (depth >= 3 && pursued != null && pursued.getWorld().equals(scene.getWorld())) {
                // Слід по землі, а не промінь у повітрі: гравець має бачити, КУДИ йти.
                context.effects().playGroundTrail(scene, pursued.getLocation(), errorColor, TRAIL_TICKS);
                context.effects().playAlertHalo(pursued.getLocation(), errorColor);
                context.messaging().sendMessage(casterId, ChatColor.GRAY + "  Слід веде туди, де він зараз");
            } else if (depth >= 3) {
                context.messaging().sendMessage(casterId, ChatColor.GRAY + "  Слід обривається — його тут немає");
            }
        }
        if (depth >= 3) {
            context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.25f, 0.5f);
        }

        spendSanity(context, casterId, depth, sequence);
    }

    /**
     * Чужі сліди. Власні дії кастера доказом не є (інакше він «розкриває» сам себе), як і
     * службові записи CoreProtect ("#hopper", "#tnt", "#furnace") — воронка, що перекладає
     * предмети сама, не «лазила в сховище».
     */
    private List<RecordedEvent> foreignTraces(List<RecordedEvent> traces, String casterName) {
        List<RecordedEvent> foreign = new ArrayList<>();
        for (RecordedEvent trace : traces) {
            String actor = trace.getActor();
            if (actor == null || actor.isBlank() || actor.startsWith("#")) continue;
            if (casterName != null && actor.equalsIgnoreCase(casterName)) continue;
            foreign.add(trace);
        }
        return foreign;
    }

    /** Найсвіжіший слід із іменем; службові записи CoreProtect ("#tnt") ігноруються. */
    private String culpritName(List<RecordedEvent> traces) {
        for (RecordedEvent trace : traces) {
            String actor = trace.getActor();
            if (actor != null && !actor.isBlank() && !actor.startsWith("#")) {
                return actor;
            }
        }
        return null;
    }

    private void spendSanity(IAbilityContext context, UUID casterId, int depth, Sequence sequence) {
        int sanityCost = CryptologistInsight.sanityLoss(depth, sequence);
        if (sanityCost > 0) {
            context.beyonder().updateSanityLoss(casterId, sanityCost);
        }
    }

    private void playReadEffects(IAbilityContext context, UUID casterId, LivingEntity target, int depth) {
        Color errorColor = PathwayBranding.liquidOf("Error");
        context.effects().playCircleEffect(target.getLocation(), TARGET_CIRCLE_RADIUS,
                Particle.ENCHANT, READ_EFFECT_TICKS);
        context.effects().playGlowingDust(target.getEyeLocation(), errorColor);
        context.glowing().setGlowing(target.getUniqueId(), casterId,
                PathwayBranding.textOf("Error"), GLOW_TICKS);

        context.effects().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE,
                0.6f, 0.8f + 0.2f * depth);
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 1.0f);
        if (depth >= 3) {
            // Вікі: глибоке дешифрування привертає увагу високорівневих істот.
            context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.25f, 0.5f);
        }
    }

    private String describeStates(List<PotionEffect> states) {
        List<String> names = new ArrayList<>();
        for (PotionEffect effect : states) {
            names.add(effect.getType().getKey().getKey().replace('_', ' ')
                    + " " + (effect.getAmplifier() + 1));
        }
        return String.join(", ", names);
    }

    private String lastPowerName(List<AbilityDomainEvent> history) {
        // Історія віддається найновішим уперед (DomainEventPublisher рве цикл на старих подіях).
        AbilityDomainEvent last = history.get(0);
        return last instanceof AbilityDomainEvent.AbilityUsed used ? used.abilityName() : "невідоме";
    }

    private String mindState(boolean isBeyonder, int sanityScale) {
        if (!isBeyonder) return ChatColor.GRAY + "звичайний, без тріщин";
        if (sanityScale > 80) return ChatColor.DARK_RED + "на межі шаленства (" + sanityScale + "/100)";
        if (sanityScale > 40) return ChatColor.RED + "розхитаний (" + sanityScale + "/100)";
        if (sanityScale > 10) return ChatColor.YELLOW + "з тріщинами (" + sanityScale + "/100)";
        return ChatColor.GREEN + "ясний (" + sanityScale + "/100)";
    }

    private String displayName(LivingEntity target) {
        if (target instanceof Player player) return player.getName();
        return target.getType().name().replace('_', ' ').toLowerCase();
    }

    /**
     * Посл. 5: пасивна гілка «Дешифрування» — Крадій снів бачить шов чужої ілюзії.
     *
     * <p>Ілюзія, сплетена поруч, тримається рівно
     * {@link DreamStealerTheft#ILLUSION_DISPEL_SECONDS} с: рівно стільки, скільки Хейзел
     * потрібно було, щоб зрозуміти, що вона була в ілюзії. Далі власник дізнається і саму
     * здібність, і того, хто її кинув (підсвітка кольором шляху).
     *
     * <p>Ефекти самої ілюзії на власникові знімати не треба — нудоту, сліпоту й темряву вже
     * зчищає {@code MentalFortitude}, успадкована з Посл. 6.
     *
     * <p>Окремий клас, а не гілка в {@link Decryption}: пасив вимагає {@code tick()}, якого
     * в активної здібності немає. Той самий файл — та сама здібність за змістом.
     */
    public static class IllusionDispel extends PermanentPassiveAbility {

        /**
         * ponytail: ілюзії пізнаються за назвою здібності — спільного маркера в кодовій базі
         * немає. Маркер-інтерфейс заради трьох класів дорожчий за цей список; додавай сюди
         * назву, коли з'явиться нова ілюзорна здібність.
         */
        private static final Set<String> ILLUSION_ABILITIES = Set.of(
                "Створення ілюзій",   // Блазень, Посл. 7
                "Перевтілення",       // Блазень — чужа личина
                "Видозміна",          // Провидець
                "Підміна думок");     // Помилка, Посл. 8

        private static final double NOTICE_RANGE = 32.0;
        private static final int LOOKUP_WINDOW_SECONDS = 10;
        private static final long CHECK_PERIOD_MS = 1000;
        private static final int GLOW_TICKS = 100;
        private static final double RING_RADIUS = 2.0;

        // Інстанс здібності спільний для шляху — стан тримаємо по гравцях.
        private final Map<UUID, Long> lastCheck = new ConcurrentHashMap<>();
        /** Мітка часу останньої вже розкритої ілюзії — щоб не повторювати ту саму щосекунди. */
        private final Map<UUID, Long> revealed = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "Дешифрування ілюзій";
        }

        @Override
        public String getDescription(Sequence userSequence) {
            return "Чужа ілюзія в межах " + (int) NOTICE_RANGE + " блоків тримається на вас лише "
                    + DreamStealerTheft.ILLUSION_DISPEL_SECONDS + " с.\n";
        }

        @Override
        public void tick(IAbilityContext context) {
            UUID viewerId = context.getCasterId();
            long now = System.currentTimeMillis();

            Long last = lastCheck.get(viewerId);
            if (last != null && now - last < CHECK_PERIOD_MS) return;
            lastCheck.put(viewerId, now);

            long alreadyRevealed = revealed.getOrDefault(viewerId, 0L);
            AbilityDomainEvent newest = null;
            Player illusionist = null;
            for (Player nearby : context.targeting().getNearbyPlayers(NOTICE_RANGE)) {
                if (nearby.getUniqueId().equals(viewerId)) continue;
                AbilityDomainEvent illusion = lastIllusion(context, nearby.getUniqueId(), now,
                        alreadyRevealed);
                if (illusion != null && (newest == null || illusion.occurredAt() > newest.occurredAt())) {
                    newest = illusion;
                    illusionist = nearby;
                }
            }
            if (newest == null) return;

            revealed.put(viewerId, newest.occurredAt());
            reveal(context, viewerId, illusionist, newest);
        }

        /** Найсвіжіша ілюзія цього гравця, що вже дозріла до розшифрування; інакше null. */
        private AbilityDomainEvent lastIllusion(IAbilityContext context, UUID casterId, long now,
                                                long alreadyRevealed) {
            // Історія віддається найновішим уперед.
            for (AbilityDomainEvent event : context.events()
                    .getAbilityEventHistory(casterId, LOOKUP_WINDOW_SECONDS)) {
                if (!ILLUSION_ABILITIES.contains(event.abilityName())) continue;
                if (event.occurredAt() <= alreadyRevealed) return null;
                // Свіжіша за 2 с ілюзія ще тримається — шва поки не видно.
                long age = now - event.occurredAt();
                return age >= DreamStealerTheft.ILLUSION_DISPEL_SECONDS * 1000L ? event : null;
            }
            return null;
        }

        private void reveal(IAbilityContext context, UUID viewerId, Player illusionist,
                            AbilityDomainEvent event) {
            Color errorColor = PathwayBranding.liquidOf("Error");
            // playExplosionRingEffect приймає ЛИШЕ Particle.DUST — інший партикл валить тік.
            context.effects().playExplosionRingEffect(context.getCasterLocation(), RING_RADIUS,
                    Particle.DUST, new Particle.DustOptions(errorColor, 1.2f));
            context.effects().playDustMark(illusionist.getEyeLocation().add(0, 1.0, 0),
                    errorColor, 0.35, 1.0f, 12, 60);
            context.glowing().setGlowing(illusionist.getUniqueId(), viewerId,
                    PathwayBranding.textOf("Error"), GLOW_TICKS);

            context.effects().playSoundForPlayer(viewerId, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
            context.effects().playSoundForPlayer(viewerId, Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
                    0.6f, 0.8f);
            context.messaging().sendMessage(viewerId, ChatColor.DARK_PURPLE + "✦ Ілюзія розсипалась: "
                    + ChatColor.WHITE + event.abilityName() + ChatColor.GRAY + " — "
                    + ChatColor.LIGHT_PURPLE + illusionist.getName());
        }
    }
}
