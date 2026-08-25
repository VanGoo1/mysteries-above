package me.vangoo.pathways.error.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.CryptologistInsight;
import me.vangoo.domain.valueobjects.PrometheusTheft;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SwindlerInfluence;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.infrastructure.items.CustomItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Спостережливість Помилки — одна здібність на всі рівні, реєструється лише на Посл. 9.
 * Можливості відкриваються за Послідовністю кастера (менший рівень = сильніший):
 * <ul>
 *   <li>≤ 9 — цінні руди r6 + цінності гравців r10;</li>
 *   <li>≤ 8 — читання наміру того, на кого дивишся;</li>
 *   <li>≤ 7 — підсвічування блоків зі свіжими слідами (їх потім читає Дешифрування);</li>
 *   <li>≤ 6 — чуття скарбів: контейнери, дропи й чужі інвентарі на 50 блоків, руда на 16.</li>
 * </ul>
 */
public class SuperiorObservation extends PermanentPassiveAbility {
    protected static final String IDENTITY = "error_observation";
    private static final double DETECTION_RADIUS = 10.0;
    // Скан руд — по сфері блоків, тож радіус менший: 10 → ~9k getBlockAt на кожного гравця.
    private static final int ORE_SCAN_RADIUS = 6;

    private static final int GAZE_TIER = 8;
    private static final int EVIDENCE_TIER = 7;
    private static final int TREASURE_TIER = 6;
    /** Скільки схованок підсвічуємо за прохід — інакше 50 блоків дають хмару міток. */
    private static final int MAX_TREASURE_MARKS = 6;
    private static final int TREASURE_MARK_TICKS = 100;
    private static final int GLOW_TICKS = 60;
    private static final long GAZE_INTERVAL_MS = 500;
    private static final long EVIDENCE_INTERVAL_MS =
            CryptologistInsight.EVIDENCE_MARK_INTERVAL_SECONDS * 1000L;
    /** Скільки блоків-слідів підсвічуємо за прохід — щоб місце побоїща не стало ялинкою. */
    private static final int MAX_EVIDENCE_MARKS = 8;
    /** Мітка лежить кілька секунд: одного кадру пилу гравець просто не помічає. */
    private static final int EVIDENCE_MARK_TICKS = 120;

    // Цінні руди
    private static final Set<Material> VALUABLE_ORES = Set.of(
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS,
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE
    );

    // Цінні предмети в інвентарі — значення це назва типу для мітки «можливі типи» (вікі).
    private static final Map<Material, String> VALUABLE_ITEMS = Map.of(
            Material.DIAMOND, "діаманти",
            Material.EMERALD, "смарагди",
            Material.NETHERITE_INGOT, "незерит",
            Material.NETHERITE_SCRAP, "незерит",
            Material.ECHO_SHARD, "ехо-осколки",
            Material.ELYTRA, "елітри",
            Material.TOTEM_OF_UNDYING, "тотеми",
            Material.ENCHANTED_GOLDEN_APPLE, "золоті яблука",
            Material.NETHER_STAR, "зорі Незеру"
    );

    // Стан — на кастера: об'єкт здібності спільний для всього шляху, тож спільне поле
    // тікало б N× за тік при N гравцях.
    private final Map<UUID, Long> lastScanTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastGazed = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGazeTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEvidenceTime = new ConcurrentHashMap<>();
    private static final long SCAN_INTERVAL_MS = 5000; // 5 секунд між скануваннями

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of(IDENTITY);
    }

    @Override
    public String getName() {
        return "Покращена Спостережливість";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        String base = "Ви інстинктивно відчуваєте цінні руди в радіусі 6 блоків "
                + "та цінні предмети в гравців у радіусі 10 блоків.";
        if (userSequence.level() > GAZE_TIER) return base;
        String gaze = base + "\nПогляд на істоту в межах "
                + (int) SwindlerInfluence.OBSERVATION_GAZE_RANGE + " блоків читає її намір:"
                + "чи вона цілиться у вас і чи вона Потойбічна.\nПрироду Потойбічного "
                + "сильнішого за вас розгледіти не вдається.";
        if (userSequence.level() > EVIDENCE_TIER) return gaze;
        String evidence = gaze + "\nБлоки зі свіжими слідами в радіусі "
                + (int) CryptologistInsight.EVIDENCE_MARK_RADIUS + " блоків самі впадають у око "
                + "(оновлюється кожні " + CryptologistInsight.EVIDENCE_MARK_INTERVAL_SECONDS
                + " с)";
        if (userSequence.level() > TREASURE_TIER) return evidence;
        return evidence + "\nШлях до схованкок з цінностями"
                + "буде видно у радіусі " + (int) PrometheusTheft.VALUABLES_RADIUS
                + " блоків із приблизною вартістю й типом, а руда — у радіусі "
                + (int) PrometheusTheft.ORE_SCAN_RADIUS + ".";
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        Beyonder caster = context.getCasterBeyonder();
        scanValuables(context, casterId, caster == null ? 9 : caster.getSequenceLevel());

        if (caster == null) return;
        if (caster.getSequenceLevel() <= GAZE_TIER) {
            readGaze(context, casterId);
        }
        if (caster.getSequenceLevel() <= EVIDENCE_TIER) {
            markEvidence(context, casterId);
        }
    }

    /**
     * Посл. ≤ 7: підсвічує блоки, на яких лишились свіжі сліди. Запит іде в БД CoreProtect,
     * тож ходимо асинхронно й рідко — синхронний лук-ап тут підвісив би сервер.
     */
    private void markEvidence(IAbilityContext context, UUID casterId) {
        long now = System.currentTimeMillis();
        Long last = lastEvidenceTime.get(casterId);
        if (last != null && now - last < EVIDENCE_INTERVAL_MS) return;
        lastEvidenceTime.put(casterId, now);

        Location center = context.getCasterLocation();
        context.scheduling().runAsync(() -> {
            List<RecordedEvent> traces = context.events().getPastEvents(center,
                    (int) CryptologistInsight.EVIDENCE_MARK_RADIUS,
                    CryptologistInsight.TRACE_LOOKUP_WINDOW_SECONDS);
            context.scheduling().scheduleDelayed(() -> showEvidence(context, casterId, traces), 0L);
        });
    }

    private void showEvidence(IAbilityContext context, UUID casterId, List<RecordedEvent> traces) {
        Color errorColor = PathwayBranding.liquidOf("Error");
        Player caster = Bukkit.getPlayer(casterId);
        String casterName = caster == null ? null : caster.getName();
        Set<String> seen = new HashSet<>();
        for (RecordedEvent trace : traces) {
            // Власні сліди кастера не підсвічуємо — здібність шукає ЧУЖУ активність.
            if (casterName != null && casterName.equalsIgnoreCase(trace.getActor())) continue;
            Location loc = trace.getLocation();
            if (!seen.add(loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ())) continue;
            // Нерухома мітка, що лежить кілька секунд: слідів буває до 8 одразу — має бути
            // помітно й достатньо довго, щоб встигнути глянути, але не «ялинка». Бачить лише
            // сам кастер — інакше чужі сліди підсвічувались би всім гравцям поблизу.
            context.effects().playDustMarkFor(casterId, loc.clone().add(0.5, 0.6, 0.5), errorColor,
                    0.2, 1.0f, 6, EVIDENCE_MARK_TICKS);
            if (seen.size() >= MAX_EVIDENCE_MARKS) return;
        }
    }

    private void scanValuables(IAbilityContext context, UUID casterId, int sequenceLevel) {
        long currentTime = System.currentTimeMillis();
        Long lastScan = lastScanTime.get(casterId);
        if (lastScan != null && currentTime - lastScan < SCAN_INTERVAL_MS) {
            return;
        }
        lastScanTime.put(casterId, currentTime);

        boolean treasureTier = sequenceLevel <= TREASURE_TIER;
        Location casterLoc = context.getCasterLocation();

        // Збір інформації про цінності
        List<String> detectedItems = new ArrayList<>();

        // 1. Перевірка цінних руд навколо
        int oreRadius = treasureTier ? (int) PrometheusTheft.ORE_SCAN_RADIUS : ORE_SCAN_RADIUS;
        int oreCount = scanForValuableOres(casterLoc, oreRadius);
        if (oreCount > 0) {
            detectedItems.add(ChatColor.GOLD + "Цінні руди поблизу (" + oreCount + ")");
        }

        // 2. Перевірка цінних предметів у гравців
        double playerRadius = treasureTier ? PrometheusTheft.VALUABLES_RADIUS : DETECTION_RADIUS;
        Map<String, Treasure> playerTreasures = scanNearbyPlayers(context, playerRadius);
        for (Map.Entry<String, Treasure> entry : playerTreasures.entrySet()) {
            Treasure treasure = entry.getValue();
            detectedItems.add(ChatColor.AQUA + entry.getKey() + ": " + treasure.value()
                    + " цінностей (" + valueLabel(treasure.value()) + ": " + treasure.type() + ")");
        }

        // 3. Посл. ≤ 6: схованки — контейнери й дропи на 50 блоків
        if (treasureTier) {
            senseTreasures(context, casterId, casterLoc, detectedItems);
        }

        // Відправка повідомлення
        if (!detectedItems.isEmpty()) {
            String message = ChatColor.YELLOW + "⚠ Виявлено: " + ChatColor.GRAY + String.join(", ", detectedItems);
            context.messaging().sendMessageToActionBar(casterId, Component.text(message));
        }
    }

    /**
     * Посл. ≤ 8: читає намір того, на кого кастер дивиться — лише при зміні цілі.
     */
    private void readGaze(IAbilityContext context, UUID casterId) {
        long now = System.currentTimeMillis();
        Long last = lastGazeTime.get(casterId);
        if (last != null && now - last < GAZE_INTERVAL_MS) return;
        lastGazeTime.put(casterId, now);

        Optional<LivingEntity> targetOpt =
                context.targeting().getTargetedEntity(SwindlerInfluence.OBSERVATION_GAZE_RANGE);
        if (targetOpt.isEmpty()) {
            lastGazed.remove(casterId);
            return;
        }

        LivingEntity target = targetOpt.get();
        UUID previous = lastGazed.get(casterId);
        if (previous != null && previous.equals(target.getUniqueId())) return;
        lastGazed.put(casterId, target.getUniqueId());

        readTarget(context, casterId, target);
    }

    private void readTarget(IAbilityContext context, UUID casterId, LivingEntity target) {
        StringBuilder line = new StringBuilder();
        line.append(ChatColor.GRAY).append("👁 ").append(ChatColor.WHITE).append(displayName(target));

        if (target instanceof Mob mob && mob.getTarget() != null
                && casterId.equals(mob.getTarget().getUniqueId())) {
            line.append(ChatColor.RED).append("  ⚔ цілиться у вас");
        }

        ChatColor glowColor = ChatColor.GRAY;
        if (target instanceof Player && context.beyonder().isBeyonder(target.getUniqueId())) {
            Beyonder caster = context.getCasterBeyonder();
            Beyonder victim = context.beyonder().getBeyonder(target.getUniqueId());
            // Розкриваємо лише рівних або слабших: більший рівень = слабша Послідовність.
            if (victim != null && caster != null && victim.getSequenceLevel() >= caster.getSequenceLevel()) {
                String pathwayName = victim.getPathway().getName();
                glowColor = PathwayBranding.textOf(pathwayName);
                line.append(ChatColor.GRAY).append("  ✦ ").append(glowColor).append(pathwayName)
                        .append(ChatColor.GRAY).append(" [Посл. ").append(victim.getSequenceLevel()).append("]");
            } else {
                line.append(ChatColor.DARK_PURPLE).append("  ✦ Потойбічний, природа прихована");
            }
        }

        context.messaging().sendMessageToActionBar(casterId,
                LegacyComponentSerializer.legacySection().deserialize(line.toString()));
        context.glowing().setGlowing(target.getUniqueId(), casterId, glowColor, GLOW_TICKS);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3f, 1.6f);
    }

    private String displayName(LivingEntity target) {
        if (target instanceof Player player) return player.getName();
        return target.getType().name().replace('_', ' ').toLowerCase();
    }

    /**
     * Сканує блоки навколо на наявність цінних руд
     */
    private int scanForValuableOres(Location center, int radius) {
        int count = 0;

        World world = center.getWorld();
        if (world == null) return 0;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Оптимізований скан (не перевіряємо кожен блок, лише в сфері)
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Перевірка відстані (сфера замість куба)
                    if (x*x + y*y + z*z > radius*radius) continue;

                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);

                    if (VALUABLE_ORES.contains(block.getType())) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Сканує інвентарі гравців поблизу
     * @return Map<Ім'я гравця, Кількість цінностей>
     */
    private Map<String, Treasure> scanNearbyPlayers(IAbilityContext context, double radius) {
        Map<String, Treasure> result = new HashMap<>();

        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(radius);

        for (Player player : nearbyPlayers) {
            Treasure treasure = appraise(player.getLocation(), player.getInventory().getContents());

            if (treasure != null) {
                result.put(player.getName(), treasure);
            }
        }

        return result;
    }

    /**
     * Посл. ≤ 6: чуття скарбів. Контейнери беремо через тайл-ентіті завантажених чанків —
     * куб 101×101×101 на радіус 50 це ~10⁶ блоків за прохід і гарантований лаг.
     */
    private void senseTreasures(IAbilityContext context, UUID casterId, Location center,
                                List<String> detectedItems) {
        List<Treasure> found = findTreasures(center, PrometheusTheft.VALUABLES_RADIUS);
        if (found.isEmpty()) return;

        found.sort(Comparator.comparingInt(Treasure::value).reversed());
        Treasure best = found.get(0);
        detectedItems.add(ChatColor.LIGHT_PURPLE + "Схованки: " + found.size()
                + " (" + valueLabel(best.value()) + ": " + best.type() + ")");

        // Приватна мітка й слід — бачить лише кастер, інакше вказали б на схованку всім поруч.
        Color errorColor = PathwayBranding.liquidOf("Error");
        for (int i = 0; i < Math.min(MAX_TREASURE_MARKS, found.size()); i++) {
            context.effects().playDustMarkFor(casterId, found.get(i).location(), errorColor,
                    0.25, 1.0f, 6, TREASURE_MARK_TICKS);
        }
        Treasure nearest = Collections.min(found,
                Comparator.comparingDouble(t -> t.location().distanceSquared(center)));
        context.effects().playGroundTrailFor(casterId, center, nearest.location(), errorColor,
                TREASURE_MARK_TICKS);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.8f);
    }

    private List<Treasure> findTreasures(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return List.of();

        List<Treasure> found = new ArrayList<>();
        double radiusSquared = radius * radius;
        int chunkRange = (int) Math.ceil(radius / 16.0);
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                if (!world.isChunkLoaded(centerChunkX + dx, centerChunkZ + dz)) continue;
                Chunk chunk = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                for (BlockState state : chunk.getTileEntities(
                        block -> block.getLocation().distanceSquared(center) <= radiusSquared, false)) {
                    if (!(state instanceof Container container)) continue;
                    Treasure treasure = appraise(state.getLocation().add(0.5, 0.6, 0.5),
                            container.getInventory().getContents());
                    if (treasure != null) found.add(treasure);
                }
            }
        }

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Item drop)) continue;
            Treasure treasure = appraise(drop.getLocation(), new ItemStack[]{drop.getItemStack()});
            if (treasure != null) found.add(treasure);
        }

        return found;
    }

    /** Приблизна вартість і найпомітніший тип уміщеного; {@code null} — нічого цінного. */
    private Treasure appraise(Location location, ItemStack[] contents) {
        int value = 0;
        int biggestStack = 0;
        String type = null;

        for (ItemStack item : contents) {
            if (item == null) continue;
            String itemType = valuableType(item);
            if (itemType == null) continue;
            value += item.getAmount();
            if (item.getAmount() > biggestStack) {
                biggestStack = item.getAmount();
                type = itemType;
            }
        }

        return value > 0 ? new Treasure(location, value, type) : null;
    }

    /**
     * Назва типу цінності або {@code null}, якщо предмет нічого не вартий. Крім ванільного
     * списку сюди входить будь-який предмет плагіну (інгредієнти, зілля, Характеристики) —
     * саме їх і краде Матеріальна Крадіжка, тож чуття мусить їх бачити.
     */
    private String valuableType(ItemStack item) {
        String vanilla = VALUABLE_ITEMS.get(item.getType());
        if (vanilla != null) return vanilla;
        if (!CustomItemFactory.isCustomItem(item) || AbilityItemFactory.isAbilityItem(item)) return null;
        String name = item.getItemMeta().getDisplayName();
        return name.isEmpty() ? "предмети Потойбіччя" : ChatColor.stripColor(name);
    }

    private String valueLabel(int value) {
        if (value >= PrometheusTheft.TREASURE_RICH) return "багата";
        if (value >= PrometheusTheft.TREASURE_MEDIUM) return "середня";
        return "дрібниця";
    }

    private record Treasure(Location location, int value, String type) {
    }

    @Override
    public void cleanUp() {
        lastScanTime.clear();
        lastGazed.clear();
        lastGazeTime.clear();
        lastEvidenceTime.clear();
    }
}
