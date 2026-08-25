package me.vangoo.infrastructure.organizations;

import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.InstitutionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.structure.Mirror;
import org.bukkit.entity.Entity;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Кишеньковий світ храмів: усі церкви стоять в одному порожньому світі на сітці по X,
 * кожна на власній кам'яній основі з майданчиком-виходом перед входом. Каркас світу —
 * {@link DuelArenaProvider}.
 *
 * <p>Світ спільний навмисно: сайт, сховище й священик у решті коду ключовані ЛИШЕ
 * `institutionId` ({@link ChurchSiteService#claim}, `ChurchPriestService`), тож храм на
 * гравця чи світ на церкву довелось би переписувати півпідсистеми заради нічого — до
 * храму й так ходять поодинці.
 *
 * <p>Заборона «плагін не ставить блоків» стосується звичайного світу (рельєф, чужа
 * забудова, лаг-спайк на завантаженні чанка). Тут порожньо, тож `Structure#place` — саме
 * той інструмент; див. `.claude/rules/church-structures.md`.
 */
public class ChurchWorldProvider {

    public static final String WORLD_NAME = "mysteries_churches";

    private static final int GROUND_Y = 64;
    /** Ширина кам'яної облямівки навколо будівлі — по ній гравець обходить храм. */
    private static final int APRON = 8;
    private static final int WALL_HEIGHT = 6;
    /** Наскільки перед фасадом стоїть точка прибуття. */
    private static final int PAD_OFFSET = 4;
    /** Наскільки вбік від точки прибуття стоїть камінь-вихід. */
    private static final int PAD_SIDE_SHIFT = 2;

    /**
     * Просідання будівлі відносно рівня землі. Храм Вічної Ночі тепер зберігається разом
     * із підвалом однією структурою, тож його треба опустити рівно на висоту підвалу —
     * інакше підвал стоїть НАД землею, а не під нею.
     */
    private static final Map<String, Integer> DEPTH_OFFSETS = Map.of(
            "church-evernight", -3);

    /** Скільки шарів каменю доливати під заново запечатану землю. */
    private static final int SEAL_DEPTH = 4;

    /**
     * Частини, які {@code Structure#place} не розкриє сам: підвал колись був зчеплений із
     * храмом через jigsaw, а паста jigsaw не розгортає.
     *
     * <p>Станом на зараз `church_evernight_basement.nbt` у датапаку НЕМАЄ — храм
     * перезбережено разом із підвалом однією структурою (висота 43 → 47, рівно на 4 шари
     * підвалу). Запис лишається як робочий механізм для наступної багатошматкової
     * будівлі: відсутній NBT — не помилка, {@link #placeExtraPart} тихо виходить.
     */
    private static final Map<String, int[]> EXTRA_PARTS = Map.of(
            "church-evernight", new int[]{10, -4, 14});
    private static final Map<String, String> EXTRA_PART_KEYS = Map.of(
            "church-evernight", "church_evernight_basement");

    private final Plugin plugin;
    private final InstitutionRegistry registry;
    private final ChurchSiteService sites;
    private final int spacing;
    private final Random random = new Random();

    /** institutionId → точка прибуття. Порахована з геометрії, не персиститься. */
    private final Map<String, Location> arrivals = new HashMap<>();
    /** institutionId → блок каменя-виходу (лодестон). */
    private final Map<String, Location> exitPads = new HashMap<>();

    public ChurchWorldProvider(Plugin plugin, InstitutionRegistry registry,
                               ChurchSiteService sites, int spacing) {
        this.plugin = plugin;
        this.registry = registry;
        this.sites = sites;
        this.spacing = spacing;
    }

    public boolean isChurchWorld(World world) {
        return world != null && WORLD_NAME.equals(world.getName());
    }

    public Optional<Location> arrivalOf(String institutionId) {
        return Optional.ofNullable(arrivals.get(institutionId)).map(Location::clone);
    }

    /** Чи саме цей блок — камінь-вихід якогось храму. */
    public boolean isExitPad(Location loc) {
        if (loc == null || !isChurchWorld(loc.getWorld())) {
            return false;
        }
        for (Location pad : exitPads.values()) {
            if (pad.getBlockX() == loc.getBlockX() && pad.getBlockY() == loc.getBlockY()
                    && pad.getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    /** Камені-виходи (institutionId → блок) — для партиклів над ними. */
    public Map<String, Location> exitPads() {
        return Map.copyOf(exitPads);
    }

    /**
     * Створити світ (якщо треба), поставити храми (якщо треба) і заявити їх. Кличеться
     * один раз на `onEnable`, ДО `spawnAllNpcs()` — інакше священикам ніде спавнитись.
     *
     * <p>Усе синхронно: заявка читає мітку священика з САМОГО ШАБЛОНУ, а не з пасти
     * (див. {@link #priestAnchorOf}), тож чекати на сутності не треба й точки прибуття
     * готові одразу після виклику.
     */
    public void initialize() {
        World world = getOrCreateWorld();
        List<Institution> churches = registry.churches().stream()
                .filter(c -> registry.isSpawnEnabled(c.id()))
                .toList();
        for (int i = 0; i < churches.size(); i++) {
            buildSlot(world, churches.get(i).id(), i * spacing);
        }
    }

    private World getOrCreateWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            return existing;
        }
        World world = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .generator(new EmptyChunkGenerator())
                .createWorld();
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(18000L);
        world.setSpawnLocation(0, GROUND_Y + 1, 0);
        return world;
    }

    /**
     * Один слот сітки: основа, храм, майданчик-вихід. Ідемпотентність тримає блок-печатка
     * під основою — розмір будівлі й вміст її кутів для цього не годяться (кут структури
     * може бути повітрям).
     */
    private void buildSlot(World world, String institutionId, int originX) {
        Structure structure = loadStructure(churchKeyOf(institutionId));
        if (structure == null) {
            plugin.getLogger().warning("Church structure for " + institutionId
                    + " is missing from the datapack; its temple will not exist");
            return;
        }
        BlockVector size = structure.getSize();
        int depth = DEPTH_OFFSETS.getOrDefault(institutionId, 0);
        int stampY = GROUND_Y + depth - 2;

        if (world.getBlockAt(originX, stampY, 0).getType() != Material.BEDROCK) {
            buildGround(world, originX, size, depth);
            structure.place(new Location(world, originX, GROUND_Y + depth, 0),
                    true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, random);
            placeExtraPart(world, institutionId, originX, depth);
            sealGroundAroundBuilding(world, originX, size);
            world.getBlockAt(originX, stampY, 0).setType(Material.BEDROCK);
            // Сайт міг лишитись від попередньої розкладки (координати переживають
            // видалення світу в church-sites.json), а `claim` мовчки відмовляє, коли храм
            // цього типу вже заявлений — священик тоді спавнився б у старій точці, тобто
            // в камені. Перекладений храм заявляється наново.
            sites.forget(institutionId);
        }

        // Заявляємо не лише щойно поставлений храм: слот, який колись запечатали, але не
        // встигли заявити, інакше лишався б зламаним НАЗАВЖДИ — печатка не дає перекласти
        // будівлю, а без сайту немає ні священика, ні точки прибуття.
        if (sites.siteOf(institutionId).isEmpty()) {
            claimFromTemplate(world, structure, institutionId, originX, depth);
        }

        // Майданчик рахується ПІСЛЯ заявки: бік входу знає лише сайт (кут мітки
        // священика). При повторному запуску сайт приїжджає з church-sites.json, тож
        // перерахунок дає ту саму точку.
        sites.siteOf(institutionId).ifPresent(site -> {
            rememberArrival(institutionId, world, originX, size, site.yaw());
            buildExitPad(world, institutionId);
        });
    }

    /**
     * Заявка храму за міткою з ШАБЛОНУ, а не за сутністю, яку наплодила паста.
     *
     * <p>Так було не завжди, і зміна не косметична. Раніше плагін після пасти шукав мітку
     * серед сутностей світу — з відкладеним стартом і кількома спробами, бо свіжа сутність
     * потрапляє в списки чанка не миттєво. Ловився ж інший, невиправний випадок: мітка з
     * пасти могла не з'явитись у світі ВЗАГАЛІ (на живому сервері так стабільно пропадала
     * мітка Церкви Блазня — разом із усіма іншими сутностями її структури). Храм тоді не
     * діставав ні сайту, ні священика, ні точки прибуття, святиня мовчки відмовляла в
     * телепорті, а печатка не давала перекласти будівлю — тобто церква ламалась назавжди.
     *
     * <p>Шаблон таких станів не має: мітка лежить у самому NBT, і {@code getEntities()}
     * віддає її зі структурно-відносними координатами й кутом. Світова точка — це просто
     * початок слоту плюс зміщення, тож заявка стала синхронною й детермінованою.
     */
    private void claimFromTemplate(World world, Structure structure, String institutionId,
                                   int originX, int depth) {
        Optional<ChurchAnchor> anchor = priestAnchorOf(structure, institutionId);
        if (anchor.isEmpty()) {
            plugin.getLogger().warning("Church " + institutionId + " has no lotm_role_priest"
                    + " marker inside church_"
                    + institutionId.replaceFirst("^church-", "").replace('-', '_')
                    + ".nbt; it gets no priest and no arrival point, and the shrine leading"
                    + " there will refuse to teleport. Re-run tools/nbt/StructurePatch.java"
                    + " patch on that structure.");
            return;
        }
        Location relative = anchor.get().marker().getLocation();
        Location spot = new Location(world,
                originX + relative.getX(), GROUND_Y + depth + relative.getY(), relative.getZ(),
                relative.getYaw(), relative.getPitch());
        sites.claim(institutionId, spot, anchor.get().box());
    }

    /**
     * Мітка священика в шаблоні. {@code Structure#getEntities()} збирає сутності з NBT і
     * ставить їх у структурно-відносні координати, не додаючи у світ, — саме те, що
     * потрібно: теги й кут читаються тим самим {@link ChurchAnchor}, що й у світі.
     *
     * <p>Виняток тут не гіпотетичний: сутності храмів збережені новішим клієнтом, і на
     * 1.21.1 їхній NBT читається як є. Одна крива сутність не має ламати старт сервера.
     */
    private Optional<ChurchAnchor> priestAnchorOf(Structure structure, String institutionId) {
        try {
            for (Entity entity : structure.getEntities()) {
                Optional<ChurchAnchor> anchor = ChurchAnchor.of(entity)
                        .filter(ChurchAnchor::isPriest);
                if (anchor.isPresent()) {
                    return anchor;
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to read entities of "
                    + churchKeyOf(institutionId) + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private void placeExtraPart(World world, String institutionId, int originX, int depth) {
        int[] offset = EXTRA_PARTS.get(institutionId);
        if (offset == null) {
            return;
        }
        Structure part = loadStructure(
                new NamespacedKey("mysteries", EXTRA_PART_KEYS.get(institutionId)));
        if (part == null) {
            return; // храм перезбережено однією структурою — окремої частини вже нема
        }
        part.place(new Location(world, originX + offset[0], GROUND_Y + depth + offset[1], offset[2]),
                true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, random);
    }

    /**
     * Кам'яна основа під будівлею та облямівка навколо неї + бар'єр, щоб не впасти в
     * порожнечу. Під просілим храмом камінь іде до самого низу будівлі, інакше поряд із
     * підвалом зяяла б діра в порожнечу.
     */
    private void buildGround(World world, int originX, BlockVector size, int depth) {
        int minX = originX - APRON;
        int maxX = originX + size.getBlockX() + APRON;
        int minZ = -APRON;
        int maxZ = size.getBlockZ() + APRON;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean underBuilding = x >= originX && x < originX + size.getBlockX()
                        && z >= 0 && z < size.getBlockZ();
                if (underBuilding) {
                    continue; // там усе заповнить сама структура
                }
                world.getBlockAt(x, GROUND_Y, z).setType(Material.POLISHED_ANDESITE);
                for (int y = GROUND_Y - 1; y >= GROUND_Y + depth - 1; y--) {
                    world.getBlockAt(x, y, z).setType(Material.STONE);
                }
            }
        }
        for (int y = GROUND_Y + 1; y <= GROUND_Y + WALL_HEIGHT; y++) {
            for (int x = minX; x <= maxX; x++) {
                world.getBlockAt(x, y, minZ).setType(Material.BARRIER);
                world.getBlockAt(x, y, maxZ).setType(Material.BARRIER);
            }
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(minX, y, z).setType(Material.BARRIER);
                world.getBlockAt(maxX, y, z).setType(Material.BARRIER);
            }
        }
    }

    /**
     * Гравець має з'являтись ПЕРЕД дверима, а не збоку від будівлі.
     *
     * <p>Бік входу ніде не записаний, але його видно з кута мітки священика: жрець стоїть
     * біля вівтаря й дивиться вздовж нави на двері. Тож напрям його погляду — і є напрям
     * на вхід, а точка прибуття лежить за фасадом на тому боці. Гравця розвертаємо на
     * 180° від жерця, щоб він одразу бачив храм.
     */
    /**
     * Затягує землю там, де будівля не дійшла до країв свого габаритного паралелепіпеда.
     * Без цього між облямівкою й стінами храму лишається кільце порожнечі, у яке гравець
     * просто провалюється.
     *
     * <p>Заливати основу ДО пасти не можна: 4 з 5 храмів на 51-79% складаються зі
     * structure void, а таких клітинок паста не чіпає — кімнати лишились би замурованими
     * каменем. Тому заливаємо після, і не суцільно, а розливом від облямівки: усе, куди
     * розлив дійшов, — зовні будівлі за визначенням, бо стіни його зупиняють. Внутрішні
     * порожнини (сходи в підвал, нава) до розливу недосяжні й лишаються цілими.
     */
    private void sealGroundAroundBuilding(World world, int originX, BlockVector size) {
        int minX = originX - APRON;
        int maxX = originX + size.getBlockX() + APRON;
        int minZ = -APRON;
        int maxZ = size.getBlockZ() + APRON;

        Deque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        // Стартуємо з облямівки — вона гарантовано зовні будівлі.
        for (int x = minX; x <= maxX; x++) {
            enqueue(queue, seen, x, minZ);
            enqueue(queue, seen, x, maxZ);
        }
        for (int z = minZ; z <= maxZ; z++) {
            enqueue(queue, seen, minX, z);
            enqueue(queue, seen, maxX, z);
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int x = cell[0];
            int z = cell[1];
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                continue;
            }
            boolean onApron = x < originX || x >= originX + size.getBlockX()
                    || z < 0 || z >= size.getBlockZ();
            if (!onApron) {
                // Усередині габаритів будівлі твердий блок — це її стіна: далі не йдемо.
                if (world.getBlockAt(x, GROUND_Y, z).getType().isSolid()) {
                    continue;
                }
                fillGap(world, x, z);
            }
            // Облямівку не заливаємо (вона вже є) — але проходимо крізь неї, інакше розлив
            // не дістався б до дір: суцільна тверда облямівка відрізає їх від старту.
            enqueue(queue, seen, x + 1, z);
            enqueue(queue, seen, x - 1, z);
            enqueue(queue, seen, x, z + 1);
            enqueue(queue, seen, x, z - 1);
        }
    }

    private void fillGap(World world, int x, int z) {
        world.getBlockAt(x, GROUND_Y, z).setType(Material.POLISHED_ANDESITE);
        for (int y = GROUND_Y - 1; y >= GROUND_Y - SEAL_DEPTH; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                break; // дійшли до стелі підвалу — глибше не ліземо
            }
            world.getBlockAt(x, y, z).setType(Material.STONE);
        }
    }

    private static void enqueue(Deque<int[]> queue, Set<Long> seen, int x, int z) {
        if (seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL))) {
            queue.add(new int[] {x, z});
        }
    }

    private void rememberArrival(String institutionId, World world, int originX,
                                 BlockVector size, float priestYaw) {
        int[] out = outwardOf(priestYaw);
        double centreX = originX + size.getBlockX() / 2.0;
        double centreZ = size.getBlockZ() / 2.0;
        double faceX = out[0] == 0 ? centreX
                : (out[0] < 0 ? originX : originX + size.getBlockX());
        double faceZ = out[1] == 0 ? centreZ
                : (out[1] < 0 ? 0 : size.getBlockZ());

        arrivals.put(institutionId, new Location(world,
                Math.floor(faceX + out[0] * PAD_OFFSET) + 0.5, GROUND_Y + 1,
                Math.floor(faceZ + out[1] * PAD_OFFSET) + 0.5,
                priestYaw + 180f, 0f));
    }

    /** Куди дивиться жрець: 0°→+Z, 90°→−X, 180°→−Z, 270°→+X. */
    private static int[] outwardOf(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
            case 1 -> new int[] {-1, 0};
            case 2 -> new int[] {0, -1};
            case 3 -> new int[] {1, 0};
            default -> new int[] {0, 1};
        };
    }

    /**
     * Камінь-вихід стоїть НА рівні ніг гравця (не втоплений у підлогу) і трохи вбік від
     * точки прибуття — щоб не затуляв дорогу до храму, але лишався в полі зору.
     */
    private void buildExitPad(World world, String institutionId) {
        Location arrival = arrivals.get(institutionId);
        if (arrival == null) {
            return;
        }
        int[] out = outwardOf(arrival.getYaw() + 180f);
        int[] side = {-out[1], out[0]};
        int padX = arrival.getBlockX() + side[0] * PAD_SIDE_SHIFT;
        int padZ = arrival.getBlockZ() + side[1] * PAD_SIDE_SHIFT;

        world.getBlockAt(padX, GROUND_Y, padZ).setType(Material.STONE_BRICKS);
        world.getBlockAt(padX, GROUND_Y + 1, padZ).setType(Material.LODESTONE);
        world.getBlockAt(padX + side[0], GROUND_Y + 1, padZ + side[1])
                .setType(Material.SOUL_LANTERN);
        world.getBlockAt(padX - side[0], GROUND_Y + 1, padZ - side[1])
                .setType(Material.SOUL_LANTERN);
        exitPads.put(institutionId, new Location(world, padX, GROUND_Y + 1, padZ));
    }

    private static NamespacedKey churchKeyOf(String institutionId) {
        return new NamespacedKey("mysteries",
                "church_" + institutionId.replaceFirst("^church-", "").replace('-', '_'));
    }

    /**
     * Датапак може бути не встановлений — тоді структур немає взагалі. Це не виняток, а
     * очікуваний стан сервера без пака, тож повертаємо {@code null}, а гучний варнінг про
     * відсутній пак пише {@link #initialize()} по кожному храму.
     */
    private Structure loadStructure(NamespacedKey key) {
        try {
            return Bukkit.getStructureManager().loadStructure(key);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to load structure " + key + ": " + e.getMessage());
            return null;
        }
    }

    private static final class EmptyChunkGenerator extends ChunkGenerator {
    }
}
