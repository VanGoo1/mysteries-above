package me.vangoo.infrastructure.organizations;

import me.vangoo.domain.organizations.ShrineAssignment;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Marker;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Palette;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Ставить святині біля сіл — по одній на село й рівно по одній на церкву на весь світ.
 *
 * <p><b>Чому плагін, а не датапак.</b> Пул будинків села безпам'ятний за побудовою: кожен
 * конектор вулиці кидає кубик незалежно, тож датапак не може ні обмежити «одна на село»,
 * ні гарантувати, що кожна церква десь з'явиться, ні тим паче тримати унікальність на
 * весь світ. Усе це — питання СТАНУ, а стан є лише в плагіна ({@link ShrineRegistry}).
 *
 * <p>Це свідомий виняток із заборони «плагін не ставить блоків у звичайному світі»
 * (`.claude/rules/church-structures.md`). Заборона стосувалась пасти ХРАМУ 39×51×64 у
 * згенерований рельєф; тут будівля 5×4×5, ставиться лише на рівний майданчик у щойно
 * згенерованому чанку й лише п'ять разів за весь час життя світу.
 */
public class VillageShrinePlacer {

    private static final NamespacedKey SHRINE_KEY = new NamespacedKey("mysteries", "village_shrine");

    /** Усі п'ять ванільних варіантів села — біом заздалегідь невідомий. */
    private static final Structure[] VILLAGES = {
            Structure.VILLAGE_PLAINS, Structure.VILLAGE_DESERT, Structure.VILLAGE_SAVANNA,
            Structure.VILLAGE_SNOWY, Structure.VILLAGE_TAIGA};

    /**
     * Ґрунт, на який можна ставити. Білий список, а не «будь-який твердий»: інакше святиня
     * сідала б на дах будинку, на бруківку вулиці чи на воду під льодом.
     */
    private static final Set<Material> GROUND = Set.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT,
            Material.PODZOL, Material.MYCELIUM, Material.MOSS_BLOCK, Material.MUD,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.STONE,
            Material.SNOW_BLOCK, Material.POWDER_SNOW, Material.TERRACOTTA);

    private final Plugin plugin;
    private final ShrineRegistry shrines;
    private final ShrineService shrineService;
    private final double villageSpacingGuard;
    private final Random random = new Random();
    private boolean warned;

    public VillageShrinePlacer(Plugin plugin, ShrineRegistry shrines,
                               ShrineService shrineService, double villageSpacingGuard) {
        this.plugin = plugin;
        this.shrines = shrines;
        this.shrineService = shrineService;
        this.villageSpacingGuard = villageSpacingGuard;
    }

    /**
     * Перевірка на старті: чи взагалі здатен плагін ставити святині. Без неї поламаний
     * (чи просто не переставлений) датапак виявляється лише тоді, коли гравець уперше
     * дійде до села — і виглядає як «святині чомусь не спавняться». Саме так стара копія
     * `village_shrine.nbt` із лодестоном замість маяка тихо вимикала їх усі.
     */
    public void validateSetup() {
        org.bukkit.structure.Structure shrine =
                Bukkit.getStructureManager().loadStructure(SHRINE_KEY);
        if (shrine == null) {
            plugin.getLogger().warning("Structure " + SHRINE_KEY + " is missing from the"
                    + " datapack; no village shrines will appear. Copy mysteries-datapack"
                    + " into <world>/datapacks and restart.");
            return;
        }
        if (altarOffset(shrine).isEmpty()) {
            plugin.getLogger().warning("village_shrine.nbt carries no "
                    + shrineService.focusBlock() + "; village shrines are disabled until it"
                    + " matches church.shrine.focus-block. The datapack copy on this server"
                    + " is probably older than the plugin.");
            return;
        }
        plugin.getLogger().info("Village shrines ready: " + missingChurches().size()
                + " church(es) still waiting for one, guard radius "
                + (int) villageSpacingGuard + " blocks.");
    }

    /** Чи лишились церкви без святині. Найдешевша перевірка — нею гейтиться весь прохід. */
    public boolean stillNeedsShrines() {
        return !missingChurches().isEmpty();
    }

    /**
     * Щойно згенерований чанк. Ставимо, тільки якщо в ньому справді є село, поблизу ще
     * немає святині й лишились церкви без своєї.
     */
    public void considerChunk(Chunk chunk) {
        if (!stillNeedsShrines() || !hasVillage(chunk)) {
            return;
        }
        // Блоки під час ChunkLoadEvent не ставимо: чанк саме зараз вантажиться. Наступного
        // тика він уже цілком у світі.
        Bukkit.getScheduler().runTask(plugin, () -> place(chunk));
    }

    private void place(Chunk chunk) {
        List<String> missing = missingChurches();
        if (missing.isEmpty()) {
            return; // за той тик встигла стати остання
        }
        org.bukkit.structure.Structure shrine =
                Bukkit.getStructureManager().loadStructure(SHRINE_KEY);
        if (shrine == null) {
            warnOnce("Structure " + SHRINE_KEY + " is missing from the datapack;"
                    + " no village shrines will appear");
            return;
        }
        // Вівтар шукаємо в ПАЛІТРІ структури, тобто ДО пасти. Інакше кожна невдача лишала
        // б у селі будівлю без мітки — незаявлену, неклікабельну й таку, що не заважає
        // наступному чанку поставити ще одну поряд.
        Optional<BlockVector> altarOffset = altarOffset(shrine);
        if (altarOffset.isEmpty()) {
            warnOnce("village_shrine.nbt has no " + shrineService.focusBlock()
                    + " inside; village shrines are disabled until it matches"
                    + " church.shrine.focus-block");
            return;
        }
        BlockVector size = shrine.getSize();
        Optional<Block> spot = findFlatSpot(chunk, size);
        if (spot.isEmpty()) {
            return; // рівного майданчика в цьому чанку немає — спробуємо в наступному селі
        }
        Location ground = spot.get().getLocation();
        if (shrines.hasNear(ground, villageSpacingGuard)) {
            return; // це село вже має святиню, просто прийшов сусідній його чанк
        }

        String institutionId = ShrineAssignment
                .pick(missing, ground.getBlockX(), ground.getBlockZ())
                .orElse(missing.get(0));
        Location origin = ground.clone().add(0, 1, 0);
        // Поворот завжди NONE. `Structure#place` повертає будівлю НАВКОЛО точки-початку,
        // тож зайнятий об'єм зсувається, і всі offset'и (вівтар, межі захисту) починають
        // брехати — саме через це три чверті святинь ставились без мітки. Святиня
        // симетрична, тож поворот їй нічого не давав.
        shrine.place(origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, random);

        BlockVector offset = altarOffset.get();
        // Скло просто над маяком фарбує його промінь у колір церкви — саме цей стовп
        // світла й видно здалеку, тож він мусить бути «свій» для кожного храму.
        origin.getWorld().getBlockAt(
                        origin.getBlockX() + offset.getBlockX(),
                        origin.getBlockY() + offset.getBlockY() + 1,
                        origin.getBlockZ() + offset.getBlockZ())
                .setType(shrineService.beamGlassOf(institutionId));

        Location markerSpot = origin.clone().add(
                offset.getBlockX() + 0.5, offset.getBlockY() + 2.0, offset.getBlockZ() + 0.5);
        spawnMarker(markerSpot, institutionId, origin, size);
        shrines.add(institutionId, markerSpot);
        plugin.getLogger().info("Village shrine of " + institutionId + " placed at "
                + markerSpot.getBlockX() + "," + markerSpot.getBlockY() + "," + markerSpot.getBlockZ());
    }

    /** Зміщення вівтаря від початку структури, прочитане з палітри — світу не торкається. */
    private Optional<BlockVector> altarOffset(org.bukkit.structure.Structure shrine) {
        Material focus = shrineService.focusBlock();
        for (Palette palette : shrine.getPalettes()) {
            for (BlockState block : palette.getBlocks()) {
                if (block.getType() == focus) {
                    return Optional.of(new BlockVector(block.getX(), block.getY(), block.getZ()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Поламаний пак або конфіг інакше залив би лог однаковим варнінгом на кожен чанк
     * села — а сіл на карті тисячі.
     */
    private void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }

    /**
     * Мітку ставить плагін, а не NBT: церква вже відома, і тег лягає одразу. Мітка з
     * пасти з'явилась би лише наступного тика, і її довелось би ловити.
     */
    private void spawnMarker(Location spot, String institutionId, Location origin, BlockVector size) {
        Marker marker = spot.getWorld().spawn(spot, Marker.class);
        marker.addScoreboardTag(ChurchAnchor.TAG_ANCHOR);
        marker.addScoreboardTag("lotm_role_" + ChurchAnchor.ROLE_SHRINE);
        marker.addScoreboardTag(ChurchAnchor.churchTag(institutionId));
        marker.addScoreboardTag(boxTag(spot, origin, size));
    }

    /**
     * Межі захисту рахуються з реальної геометрії пасти, а не з константи: художня версія
     * шрайна може бути іншого розміру, і жодних правок у коді це вимагати не має.
     * Півширина береться квадратною (максимум по обох осях) — з запасом, зате без
     * припущень про форму будівлі.
     */
    private static String boxTag(Location marker, Location origin, BlockVector size) {
        int offsetX = marker.getBlockX() - origin.getBlockX();
        int offsetZ = marker.getBlockZ() - origin.getBlockZ();
        int half = Math.max(
                Math.max(offsetX, size.getBlockX() - 1 - offsetX),
                Math.max(offsetZ, size.getBlockZ() - 1 - offsetZ));
        int down = marker.getBlockY() - origin.getBlockY();
        int up = size.getBlockY() - 1 - down;
        return "lotm_box_" + half + "_" + down + "_" + Math.max(up, 0);
    }

    /**
     * Рівний майданчик під усю основу святині, цілком усередині цього чанка — щоб не
     * смикати завантаження сусідніх. Вимагаємо однакову висоту під кожною клітинкою:
     * святиня на схилі виглядала б висячою.
     */
    private Optional<Block> findFlatSpot(Chunk chunk, BlockVector size) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int dx = 0; dx + size.getBlockX() <= 16; dx++) {
            for (int dz = 0; dz + size.getBlockZ() <= 16; dz++) {
                Block corner = flatGroundAt(world, baseX + dx, baseZ + dz, size);
                if (corner != null) {
                    return Optional.of(corner);
                }
            }
        }
        return Optional.empty();
    }

    private Block flatGroundAt(World world, int x, int z, BlockVector size) {
        Block first = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!GROUND.contains(first.getType())) {
            return null;
        }
        for (int dx = 0; dx < size.getBlockX(); dx++) {
            for (int dz = 0; dz < size.getBlockZ(); dz++) {
                Block ground = world.getHighestBlockAt(x + dx, z + dz,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (ground.getY() != first.getY() || !GROUND.contains(ground.getType())) {
                    return null;
                }
                // Над майданчиком не має бути нічого твердого: трава й квіти зникнуть
                // самі (у NBT шрайна повітря описане явно), а от стіна будинку — ні.
                for (int dy = 1; dy <= size.getBlockY(); dy++) {
                    if (world.getBlockAt(x + dx, ground.getY() + dy, z + dz).getType().isSolid()) {
                        return null;
                    }
                }
            }
        }
        return first;
    }

    private static boolean hasVillage(Chunk chunk) {
        for (Structure village : VILLAGES) {
            if (!chunk.getStructures(village).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Церкви, які ще чекають на свою святиню. Фільтр «має храм» тут не зайвий: церква без
     * будівлі в кишеньковому світі дістала б святиню, що мовчки нікуди не веде.
     */
    private List<String> missingChurches() {
        return shrineService.usableChurchIds().stream()
                .filter(id -> !shrines.has(id))
                .toList();
    }
}
