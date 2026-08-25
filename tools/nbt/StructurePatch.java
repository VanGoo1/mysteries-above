import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Офлайн-інструмент для структур храмів (`mysteries-datapack/data/mysteries/structure`).
 * Поза джаром — запускається як single-file: `java StructurePatch.java <команда> ...`.
 *
 * Команди:
 *   dump  <file.nbt>...            — розмір, DataVersion, сутності, jigsaw-блоки
 *   patch [--part] <file.nbt>...   — привести храм до конвенції (див. нижче)
 *   make-shrine <out.nbt>          — згенерувати шрайн для сіл (див. makeShrine)
 *
 * Що робить `patch` (ідемпотентно — можна ганяти після кожного перезбереження в грі):
 *   1. перейменовує блоки, яких немає на 1.21.1 (BLOCK_REMAP);
 *   2. викидає сміттєві сутності `minecraft:item` (дропи, що потрапили в збереження);
 *   3. доводить мітку до конвенції: теги `lotm_anchor` / `lotm_role_*` / `lotm_church_*` /
 *      `lotm_box_*` і `Rotation` з `data.yaw` — бо Bukkit бачить у сутності лише теги,
 *      не `data{}`; якщо мітки немає взагалі — ставить її на підлогу найближче до центру;
 *   4. прибирає вхідний jigsaw, що лишився від worldgen-підходу.
 *
 * `--part` — допоміжний шматок (підвал Вічної Ночі): лише кроки 1-2.
 */
public class StructurePatch {

    /** Блоки з 1.21.2+ , яких немає на 1.21.1 (набори властивостей у пар збігаються). */
    private static final Map<String, String> BLOCK_REMAP = Map.of(
            "minecraft:iron_chain", "minecraft:chain",
            "minecraft:oxidized_lightning_rod", "minecraft:lightning_rod",
            "minecraft:pale_oak_trapdoor", "minecraft:spruce_trapdoor");

    /**
     * Слід від часів, коли храм був worldgen-структурою й чіплявся до сітки сіл
     * jigsaw-ланцюгом. Тепер храм вставляє плагін у кишеньковий світ, тож будь-який
     * jigsaw усередині будівлі — сміття: `Structure#place` не перетворює його на
     * `final_state`, як це робив worldgen, і блок лишається видимим.
     */
    private static final String CHURCH_ENTRANCE_PREFIX = "mysteries:church_entrance";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage: StructurePatch <dump|patch> ...");
            return;
        }
        String command = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "dump" -> { for (String f : rest) dump(Path.of(f)); }
            case "patch" -> {
                boolean part = rest.length > 0 && rest[0].equals("--part");
                for (String f : part ? Arrays.copyOfRange(rest, 1, rest.length) : rest) {
                    patch(Path.of(f), part);
                }
            }
            case "make-shrine" -> makeShrine(Path.of(rest[0]));
            default -> System.out.println("unknown command: " + command);
        }
    }

    // ──────────────────────────── make-shrine ───────────────────────────

    /** 1.21.1 — цільова версія сервера. Пишемо саме її: DataFixer мігрує лише вперед. */
    private static final int TARGET_DATA_VERSION = 3955;

    /**
     * Генерує `village_shrine.nbt` — маленьку святиню 5×4×5, яку датапак підмішує у
     * ванільні пули `village/*&#47;houses`. Генерована, а не збережена в грі, з двох
     * причин: щоб механіку можна було перевірити ДО того, як буде намальована художня
     * версія, і щоб jigsaw-контракт із селом був у коді, а не в чиємусь спогаді.
     *
     * <p><b>Ні jigsaw, ні мітки тут навмисно немає.</b> Шрайн ставить ПЛАГІН біля села
     * ({@code VillageShrinePlacer}), а не ванільний worldgen через пул будинків:
     * <ul>
     *   <li>jigsaw потрібен був лише для стикування з вуличним шматком села; при
     *       {@code Structure#place} він не перетворюється на {@code final_state} і
     *       лишився б стирчати видимим блоком;</li>
     *   <li>мітку плагін ставить сам, одразу з тегом потрібної церкви — інакше довелось би
     *       ловити щойно народжену сутність наступного тика.</li>
     * </ul>
     *
     * <p>Отже NBT — чиста будівля. Художню версію зберігають структурним блоком і ганяють
     * через {@code patch --part}; єдина вимога до неї — блок-вівтар усередині
     * ({@code church.shrine.focus-block}, типово лодестон): плагін шукає саме його, щоб
     * знати, куди ставити мітку.
     */
    private static void makeShrine(Path out) throws IOException {
        int sx = 5;
        int sy = 5;
        int sz = 5;
        List<Object> palette = new ArrayList<>();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("DataVersion", TARGET_DATA_VERSION);
        root.put("size", new ArrayList<Object>(List.of(sx, sy, sz)));
        root.put("palette", palette);

        int air = stateIndexOf(palette, "minecraft:air");
        int bricks = stateIndexOf(palette, "minecraft:stone_bricks");
        int pedestal = stateIndexOf(palette, "minecraft:iron_block");
        int post = stateIndexOf(palette, "minecraft:stone_brick_wall");
        int lantern = stateIndexOf(palette, "minecraft:soul_lantern");
        int altar = stateIndexOf(palette, "minecraft:beacon");

        // Порожнеча теж має бути в структурі: не перелічені клітинки — structure void,
        // тобто трава й кущі лишились би стирчати всередині святині.
        Map<Long, Integer> cells = new LinkedHashMap<>();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    cells.put(pack(x, y, z), y == 0 ? bricks : air);
                }
            }
        }
        for (int[] corner : new int[][] {{0, 0}, {0, 4}, {4, 0}, {4, 4}}) {
            cells.put(pack(corner[0], 1, corner[1]), post);
            cells.put(pack(corner[0], 2, corner[1]), post);
            cells.put(pack(corner[0], 3, corner[1]), lantern);
        }
        // Піраміда маяка: рівно 3×3 залізних блоків просто під ним, інакше промінь не
        // засвітиться. Розтягти їх на халяву не вийде — блоки святині під захистом.
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                cells.put(pack(x, 1, z), pedestal);
            }
        }
        cells.put(pack(2, 2, 2), altar);
        // Над маяком лишається чисте повітря аж до верху структури: промінь інакше не
        // піде. У клітинку (2,3,2) плагін ставить кольорове скло церкви — воно промінь
        // не гасить, а фарбує.

        List<Object> blocks = new ArrayList<>();
        for (Map.Entry<Long, Integer> cell : cells.entrySet()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("pos", new ArrayList<Object>(List.of(
                    unpackX(cell.getKey()), unpackY(cell.getKey()), unpackZ(cell.getKey()))));
            block.put("state", cell.getValue());
            blocks.add(block);
        }
        root.put("blocks", blocks);
        root.put("entities", new ArrayList<Object>());

        write(out, root);
        System.out.println("шрайн записано: " + out + " (" + sx + "×" + sy + "×" + sz
                + ", вівтар у центрі, без jigsaw і без мітки — їх ставить плагін)");
    }

    // ─────────────────────────────── dump ───────────────────────────────

    private static void dump(Path file) throws IOException {
        Map<String, Object> root = read(file);
        System.out.println("===== " + file.getFileName());
        System.out.println("  size         = " + root.get("size"));
        System.out.println("  DataVersion  = " + root.get("DataVersion"));
        List<Object> blocks = list(root.get("blocks"));
        List<Object> palette = list(root.get("palette"));
        System.out.println("  blocks       = " + blocks.size() + " / palette " + palette.size());
        reportCoverage(root, blocks, palette);
        for (Object b : blocks) {
            Map<String, Object> block = compound(b);
            Map<String, Object> state = compound(palette.get((Integer) block.get("state")));
            String name = String.valueOf(state.get("Name"));
            if (name.contains("jigsaw")) {
                System.out.println("  jigsaw at " + block.get("pos")
                        + " " + state.get("Properties") + " " + block.get("nbt"));
            }
        }
        for (Object e : list(root.get("entities"))) {
            Map<String, Object> nbt = compound(compound(e).get("nbt"));
            System.out.println("  entity " + nbt.get("id") + " at " + compound(e).get("blockPos")
                    + " Tags=" + nbt.get("Tags") + " data=" + nbt.get("data")
                    + " Rotation=" + nbt.get("Rotation"));
        }
    }

    /**
     * Скільки клітинок структура реально описує, а скільки лишає порожніми (structure
     * void). Різниця вирішальна для того, хто ставить структуру в порожній світ: клітинку,
     * якої в списку немає, паста НЕ ЧІПАЄ — тобто якщо перед пастою залити основу каменем,
     * такі клітинки лишаться каменем, і кімнати виявляться замурованими.
     */
    private static void reportCoverage(Map<String, Object> root, List<Object> blocks,
                                       List<Object> palette) {
        List<Integer> size = intList(root.get("size"));
        long volume = (long) size.get(0) * size.get(1) * size.get(2);
        long air = 0;
        for (Object b : blocks) {
            Map<String, Object> state = compound(palette.get((Integer) compound(b).get("state")));
            if (isAir(String.valueOf(state.get("Name")))) {
                air++;
            }
        }
        long missing = volume - blocks.size();
        System.out.println("  volume       = " + volume + " (air listed " + air
                + ", structure void " + missing + " = " + (100 * missing / volume) + "%)");
    }

    // ─────────────────────────────── patch ──────────────────────────────

    /**
     * @param part допоміжний шматок (як підвал Вічної Ночі): йому потрібні лише ремап
     *             блоків і чистка дропів — власної мітки й вхідного jigsaw він не має,
     *             бо в'їжджає у світ причепленим до головної будівлі.
     */
    private static void patch(Path file, boolean part) throws IOException {
        Map<String, Object> root = read(file);
        String churchId = file.getFileName().toString()
                .replaceFirst("\\.nbt$", "").replaceFirst("^church_", "");
        System.out.println("===== " + file.getFileName() + (part ? " (шматок)" : " (" + churchId + ")"));

        System.out.println("  blocks remapped : " + remapBlocks(root));
        System.out.println("  item entities   : -" + stripItemEntities(root));
        if (!part) {
            System.out.println("  marker          : " + normalizeMarkers(root, churchId));
            System.out.println("  entrance jigsaw : " + removeEntranceJigsaw(root));
        }

        write(file, root);
    }

    /** 1.21.2+ → 1.21.1. Набори властивостей у цих пар збігаються, тож Properties лишаємо. */
    private static int remapBlocks(Map<String, Object> root) {
        int count = 0;
        for (Object entry : list(root.get("palette"))) {
            Map<String, Object> state = compound(entry);
            String replacement = BLOCK_REMAP.get(String.valueOf(state.get("Name")));
            if (replacement != null) {
                state.put("Name", replacement);
                count++;
            }
        }
        return count;
    }

    /** Дропи, що випадково потрапили у виділення структурного блоку. */
    private static int stripItemEntities(Map<String, Object> root) {
        List<Object> entities = list(root.get("entities"));
        int before = entities.size();
        entities.removeIf(e -> "minecraft:item"
                .equals(compound(compound(e).get("nbt")).get("id")));
        return before - entities.size();
    }

    /**
     * Bukkit читає в сутності лише scoreboard-теги, тож усе, що плагіну треба знати,
     * мусить лежати в `Tags` (і в `Rotation`), а не в `data{}`. `data{}` лишається
     * як самодокументація — його ніхто не читає.
     */
    private static String normalizeMarkers(Map<String, Object> root, String churchId) {
        List<Object> entities = list(root.get("entities"));
        List<Map<String, Object>> markers = new ArrayList<>();
        for (Object e : entities) {
            Map<String, Object> nbt = compound(compound(e).get("nbt"));
            if ("minecraft:marker".equals(nbt.get("id"))) {
                markers.add(compound(e));
            }
        }
        if (markers.isEmpty()) {
            int[] spot = findFloorSpotNearCentre(root);
            if (spot == null) {
                return "ВІДСУТНЯ, і місця на підлозі не знайшлось — постав вручну";
            }
            entities.add(newMarker(spot, churchId));
            markers.add(compound(entities.get(entities.size() - 1)));
        }
        for (Map<String, Object> marker : markers) {
            Map<String, Object> nbt = compound(marker.get("nbt"));
            Map<String, Object> data = nbt.get("data") instanceof Map
                    ? compound(nbt.get("data")) : new LinkedHashMap<>();
            String role = String.valueOf(data.getOrDefault("role", "priest"));
            String church = String.valueOf(data.getOrDefault("church", churchId));
            List<Object> tags = nbt.get("Tags") instanceof List
                    ? list(nbt.get("Tags")) : new ArrayList<>();
            addTag(tags, "lotm_anchor");
            addTag(tags, "lotm_role_" + role);
            addTag(tags, "lotm_church_" + church);
            replaceTag(tags, "lotm_box_", boxTag(root, marker));
            nbt.put("Tags", tags);
            nbt.put("Rotation", new ArrayList<Object>(List.of(markerYaw(data, nbt), 0f)));
        }
        return markers.size() + " шт.: теги, Rotation і межі будівлі";
    }

    /**
     * Габарити будівлі відносно самої мітки — щоб плагін знав, що боронити від поламки,
     * не вгадуючи. Півширина береться КВАДРАТНОЮ (максимум по обох осях), бо worldgen
     * повертає структуру випадково на 0/90/180/270°, і після повороту X та Z міняються
     * місцями: прямокутник довелося б розвертати, квадрат — ні. Ціна — коробка трохи
     * більша за саму будівлю; це радше плюс, бо захищає й ґанок.
     */
    private static String boxTag(Map<String, Object> root, Map<String, Object> marker) {
        List<Integer> size = intList(root.get("size"));
        List<Integer> at = intList(marker.get("blockPos"));
        int halfX = Math.max(at.get(0), size.get(0) - 1 - at.get(0));
        int halfZ = Math.max(at.get(2), size.get(2) - 1 - at.get(2));
        int half = Math.max(halfX, halfZ);
        int down = at.get(1);
        int up = size.get(1) - 1 - at.get(1);
        return "lotm_box_" + half + "_" + down + "_" + up;
    }

    /**
     * Кут мітки: спершу `data.yaw` (самодокументація в NBT), потім УЖЕ НАЯВНИЙ `Rotation`,
     * і лише як останній варіант 0.
     *
     * <p>Другий крок не косметичний. Мітку, перезбережену структурним блоком у грі,
     * `data{}` не супроводжує (так сталося з `church_evernight.nbt`), і без цього фолбеку
     * повторний `patch` скидав би її поворот у нуль. Кут — не декор: плагін виводить із
     * нього БІК ВХОДУ в храм (жрець дивиться на двері), тож обнулення тихо розвертало б
     * будівлю й висаджувало гравця з іншого боку.
     */
    private static float markerYaw(Map<String, Object> data, Map<String, Object> nbt) {
        if (data.get("yaw") instanceof Number n) {
            return n.floatValue();
        }
        if (nbt.get("Rotation") instanceof List<?> rotation
                && !rotation.isEmpty() && rotation.get(0) instanceof Number n) {
            return n.floatValue();
        }
        return 0f;
    }

    private static void replaceTag(List<Object> tags, String prefix, String tag) {
        tags.removeIf(t -> String.valueOf(t).startsWith(prefix));
        tags.add(tag);
    }

    private static void addTag(List<Object> tags, String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }

    private static Map<String, Object> newMarker(int[] pos, String churchId) {
        Map<String, Object> nbt = new LinkedHashMap<>();
        nbt.put("id", "minecraft:marker");
        nbt.put("data", new LinkedHashMap<>(Map.of(
                "type", "church", "church", churchId, "role", "priest")));
        nbt.put("Tags", new ArrayList<Object>(List.of(
                "lotm_anchor", "lotm_role_priest", "lotm_church_" + churchId)));
        nbt.put("Rotation", new ArrayList<Object>(List.of(0f, 0f)));
        nbt.put("Pos", new ArrayList<Object>(List.of(
                pos[0] + 0.5, (double) pos[1], pos[2] + 0.5)));
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("blockPos", new ArrayList<Object>(List.of(pos[0], pos[1], pos[2])));
        entity.put("pos", new ArrayList<Object>(List.of(
                pos[0] + 0.5, (double) pos[1], pos[2] + 0.5)));
        entity.put("nbt", nbt);
        return entity;
    }

    /** Стояча позиція (твердий блок під ногами + 2 повітря) найближче до центру будівлі. */
    private static int[] findFloorSpotNearCentre(Map<String, Object> root) {
        Map<Long, String> world = blockIndex(root);
        List<Integer> size = intList(root.get("size"));
        double cx = size.get(0) / 2.0;
        double cz = size.get(2) / 2.0;
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (Map.Entry<Long, String> entry : world.entrySet()) {
            if (isAir(entry.getValue())) {
                continue;
            }
            int x = unpackX(entry.getKey());
            int y = unpackY(entry.getKey());
            int z = unpackZ(entry.getKey());
            if (!isAir(world.get(pack(x, y + 1, z))) || !isAir(world.get(pack(x, y + 2, z)))) {
                continue;
            }
            double score = Math.hypot(x + 0.5 - cx, z + 0.5 - cz) + y * 0.75;
            if (score < bestScore) {
                bestScore = score;
                best = new int[] {x, y + 1, z};
            }
        }
        return best;
    }

    private static boolean isAir(String name) {
        return "minecraft:air".equals(name) || "minecraft:cave_air".equals(name);
    }

    /**
     * Прибирає вхідний jigsaw, що лишився від worldgen-підходу, повертаючи на його місце
     * той блок, який jigsaw ніс у `final_state`. Потрібне саме прибирання, а не вставка:
     * `Structure#place` (яким плагін кладе храм у кишеньковий світ) `final_state` не
     * застосовує, тож jigsaw лишився б у стіні видимим блоком.
     */
    private static String removeEntranceJigsaw(Map<String, Object> root) {
        List<Object> palette = list(root.get("palette"));
        for (Object b : list(root.get("blocks"))) {
            Map<String, Object> block = compound(b);
            Map<String, Object> state = compound(palette.get((Integer) block.get("state")));
            Map<String, Object> nbt = compound(block.get("nbt"));
            if (!"minecraft:jigsaw".equals(state.get("Name")) || nbt == null
                    || !String.valueOf(nbt.get("name")).startsWith(CHURCH_ENTRANCE_PREFIX)) {
                continue;
            }
            String finalState = String.valueOf(nbt.getOrDefault("final_state", "minecraft:air"));
            block.put("state", stateIndexOf(palette, finalState));
            block.remove("nbt");
            return "прибрано з " + block.get("pos") + ", відновлено " + finalState;
        }
        return "немає (нічого прибирати)";
    }

    /** `minecraft:stone_bricks[axis=y]` → індекс у палітрі, з додаванням за потреби. */
    private static int stateIndexOf(List<Object> palette, String blockState) {
        String name = blockState;
        Map<String, Object> properties = new LinkedHashMap<>();
        int bracket = blockState.indexOf('[');
        if (bracket > 0) {
            name = blockState.substring(0, bracket);
            for (String pair : blockState.substring(bracket + 1, blockState.length() - 1).split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    properties.put(kv[0].trim(), kv[1].trim());
                }
            }
        }
        for (int i = 0; i < palette.size(); i++) {
            Map<String, Object> state = compound(palette.get(i));
            Object existing = state.get("Properties");
            if (name.equals(state.get("Name"))
                    && (properties.isEmpty() ? existing == null : properties.equals(existing))) {
                return i;
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("Name", name);
        if (!properties.isEmpty()) {
            state.put("Properties", properties);
        }
        palette.add(state);
        return palette.size() - 1;
    }

    // ─────────────────────────── індекс блоків ──────────────────────────

    /** pos → ім'я блоку. Відсутня позиція = structure void (worldgen її не чіпає). */
    private static Map<Long, String> blockIndex(Map<String, Object> root) {
        List<Object> palette = list(root.get("palette"));
        Map<Long, String> index = new TreeMap<>();
        for (Object b : list(root.get("blocks"))) {
            List<Integer> pos = intList(compound(b).get("pos"));
            Map<String, Object> state = compound(palette.get((Integer) compound(b).get("state")));
            index.put(pack(pos.get(0), pos.get(1), pos.get(2)), String.valueOf(state.get("Name")));
        }
        return index;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
    }

    private static int unpackX(long key) { return (int) ((key >> 32) & 0xFFFF); }

    private static int unpackY(long key) { return (int) ((key >> 16) & 0xFFFF); }

    private static int unpackZ(long key) { return (int) (key & 0xFFFF); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) { return (List<Object>) o; }

    @SuppressWarnings("unchecked")
    private static List<Integer> intList(Object o) { return (List<Integer>) o; }

    // ──────────────────────────── NBT I/O ───────────────────────────────

    private static Map<String, Object> read(Path file) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        InputStream is = new ByteArrayInputStream(raw);
        if ((raw[0] & 0xFF) == 0x1F) {
            is = new GZIPInputStream(is);
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(is))) {
            byte type = in.readByte();
            in.readUTF();
            if (type != 10) {
                throw new IOException("root is not a compound: " + type);
            }
            return compound(readTag(in, type));
        }
    }

    private static void write(Path file, Map<String, Object> root) throws IOException {
        OutputStream os = new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)));
        try (DataOutputStream out = new DataOutputStream(os)) {
            out.writeByte(10);
            out.writeUTF("");
            writeTag(out, root);
        }
    }

    private static Object readTag(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                return a;
            }
            case 8: return in.readUTF();
            case 9: {
                byte element = in.readByte();
                int length = in.readInt();
                List<Object> l = new ArrayList<>(Math.max(length, 0));
                for (int i = 0; i < length; i++) {
                    l.add(readTag(in, element));
                }
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    byte t = in.readByte();
                    if (t == 0) {
                        return m;
                    }
                    m.put(in.readUTF(), readTag(in, t));
                }
            }
            case 11: {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case 12: {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            default: throw new IOException("unsupported tag " + type);
        }
    }

    private static byte tagType(Object value) throws IOException {
        if (value instanceof Byte) return 1;
        if (value instanceof Short) return 2;
        if (value instanceof Integer) return 3;
        if (value instanceof Long) return 4;
        if (value instanceof Float) return 5;
        if (value instanceof Double) return 6;
        if (value instanceof byte[]) return 7;
        if (value instanceof String) return 8;
        if (value instanceof List) return 9;
        if (value instanceof Map) return 10;
        if (value instanceof int[]) return 11;
        if (value instanceof long[]) return 12;
        throw new IOException("cannot write " + value.getClass());
    }

    private static void writeTag(DataOutputStream out, Object value) throws IOException {
        switch (tagType(value)) {
            case 1 -> out.writeByte((Byte) value);
            case 2 -> out.writeShort((Short) value);
            case 3 -> out.writeInt((Integer) value);
            case 4 -> out.writeLong((Long) value);
            case 5 -> out.writeFloat((Float) value);
            case 6 -> out.writeDouble((Double) value);
            case 7 -> {
                byte[] a = (byte[]) value;
                out.writeInt(a.length);
                out.write(a);
            }
            case 8 -> out.writeUTF((String) value);
            case 9 -> {
                List<Object> l = list(value);
                byte element = l.isEmpty() ? 0 : tagType(l.get(0));
                out.writeByte(element);
                out.writeInt(l.size());
                for (Object o : l) {
                    writeTag(out, o);
                }
            }
            case 10 -> {
                for (Map.Entry<String, Object> e : compound(value).entrySet()) {
                    out.writeByte(tagType(e.getValue()));
                    out.writeUTF(e.getKey());
                    writeTag(out, e.getValue());
                }
                out.writeByte(0);
            }
            case 11 -> {
                int[] a = (int[]) value;
                out.writeInt(a.length);
                for (int i : a) {
                    out.writeInt(i);
                }
            }
            case 12 -> {
                long[] a = (long[]) value;
                out.writeInt(a.length);
                for (long v : a) {
                    out.writeLong(v);
                }
            }
            default -> throw new IOException("unreachable");
        }
    }
}
