package me.vangoo.infrastructure.organizations;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.InstitutionRegistry;
import me.vangoo.domain.organizations.ShrineAssignment;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Шрайни в селах: маленькі святині, що переносять гравця в кишеньковий світ храмів
 * ({@link ChurchWorldProvider}) і назад.
 *
 * <p><b>Шрайн не має власного сховища.</b> Його персистентність — сама мітка-якір, яка
 * (на відміну від мітки священика) НЕ видаляється після першого дотику й живе у світі
 * разом із чанком. Церкву шрайнові призначає {@link ShrineAssignment} за координатами, і
 * результат штампується на мітку тегом `lotm_church_*` — тож повторний дотик читає вже
 * готову відповідь, і зміна набору увімкнених церков не переграє видані шрайни.
 */
public class ShrineService {

    private static final String PREFIX = ChatColor.GOLD + "[Святиня] " + ChatColor.RESET;
    /** Радіус пошуку мітки навколо блоку. Шрайн маленький, тож ширше шукати нема сенсу. */
    private static final double SCAN_RADIUS = 8.0;
    /**
     * Скільки блоків над коробкою шрайна теж недоторканні. Сам `lotm_box_*` закінчується
     * рівно на даху святині (його рахує інструмент із розміру NBT), тож без запасу її
     * можна було б просто накрити зверху — формально не зачепивши жодного блоку шрайна.
     */
    private static final int HEADROOM = 2;
    /**
     * Габарити шрайна, поставленого командою (у структурних мітка несе свій `lotm_box_*`).
     * `down = 3` не випадкове: мітка стоїть на два блоки вище за маяк, а під маяком ще шар
     * заліза — з меншим числом піраміду можна було б розібрати попід святинею.
     */
    private static final ChurchSiteRepository.Box MANUAL_BOX =
            new ChurchSiteRepository.Box(3, 3, 1);

    private final InstitutionRegistry registry;
    private final ChurchWorldProvider worlds;
    private final ReturnPointRepository repository;
    private final ShrineRegistry shrines;
    private final double interactRadius;
    private final Material focusBlock;

    private final Map<UUID, ReturnPointRepository.Point> returnPoints = new HashMap<>();

    public ShrineService(InstitutionRegistry registry, ChurchWorldProvider worlds,
                         ReturnPointRepository repository, ShrineRegistry shrines,
                         double interactRadius, Material focusBlock) {
        this.registry = registry;
        this.worlds = worlds;
        this.repository = repository;
        this.shrines = shrines;
        this.interactRadius = interactRadius;
        this.focusBlock = focusBlock;
        repository.load().ifPresent(model -> {
            for (ReturnPointRepository.Point point : model.points()) {
                returnPoints.put(UUID.fromString(point.playerId()), point);
            }
        });
    }

    /**
     * Церква цього шрайна.
     *
     * <p>Зазвичай відповідь уже на мітці: і {@link VillageShrinePlacer}, і
     * {@code /church shrine} ставлять тег одразу. Гілка з вибором лишається для міток без
     * тега (старі світи, де церкву обирав хеш координат) — і для міток, чия церква храму
     * НЕ МАЄ. Такій мітці віддаємо передусім церкву, що ще не має святині, щоб не завести
     * другу.
     *
     * @return id церкви або {@code null}, якщо жодної придатної церкви немає
     */
    public String assign(ChurchAnchor shrine) {
        if (shrine.institutionId() != null && hasTemple(shrine.institutionId())) {
            return shrine.institutionId();
        }
        List<String> candidates = usableChurchIds().stream()
                .filter(id -> !shrines.has(id))
                .toList();
        if (candidates.isEmpty()) {
            candidates = usableChurchIds();
        }
        Location loc = shrine.marker().getLocation();
        Optional<String> picked = ShrineAssignment.pick(
                candidates, loc.getBlockX(), loc.getBlockZ());
        picked.ifPresent(id -> {
            // Мітка з мертвою церквою переклеюється на живу: інакше святиня, яку колись
            // видали церкві без храму, лишалась би декорацією назавжди.
            if (shrine.institutionId() != null) {
                shrine.marker().removeScoreboardTag(
                        ChurchAnchor.churchTag(shrine.institutionId()));
            }
            shrine.marker().addScoreboardTag(ChurchAnchor.churchTag(id));
            shrines.add(id, loc);
        });
        return picked.orElse(null);
    }

    /**
     * Чи стоїть по той бік справжній храм. Церква без храму (шлях ще не реалізований або
     * NBT не заявився) святині не отримує ВЗАГАЛІ — ні від worldgen'у, ні від команди:
     * мовчазний шрайн, що нікуди не веде, гірший за його відсутність.
     */
    public boolean hasTemple(String institutionId) {
        return institutionId != null
                && registry.isSpawnEnabled(institutionId)
                && worlds.arrivalOf(institutionId).isPresent();
    }

    /**
     * Шрайн, до якого належить клікнутий блок.
     *
     * <p>Спершу — матеріал вівтаря, і лише потім пошук мітки: ПКМ по блоку прилітає на
     * кожен скринь, двері й поставлений блок, а сканування сутностей на кожен такий клік
     * було б відчутно дорожчим за одне порівняння енама.
     */
    public Optional<ChurchAnchor> shrineAt(Block clicked) {
        if (clicked == null || clicked.getType() != focusBlock) {
            return Optional.empty();
        }
        return shrinesNear(clicked.getLocation(), interactRadius).stream().findFirst();
    }

    public List<ChurchAnchor> shrinesNear(Location center, double radius) {
        List<ChurchAnchor> found = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return found;
        }
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            ChurchAnchor.of(entity)
                    .filter(ChurchAnchor::isShrine)
                    .ifPresent(found::add);
        }
        return found;
    }

    /**
     * Блоки шрайна теж недоторканні — межі несе `lotm_box_*` на його мітці.
     *
     * <p>Це найдорожча з перевірок захисту (пошук сутностей навколо блоку), тому
     * `ChurchProtectionListener` кличе її ОСТАННЬОЮ, після назви світу й лінійного проходу
     * по сайтах.
     */
    public boolean isProtected(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        for (ChurchAnchor shrine : shrinesNear(loc, SCAN_RADIUS)) {
            ChurchSiteRepository.Box box = shrine.box();
            if (box == null) {
                continue;
            }
            Location anchor = shrine.marker().getLocation();
            if (Math.abs(loc.getBlockX() - anchor.getBlockX()) <= box.half()
                    && Math.abs(loc.getBlockZ() - anchor.getBlockZ()) <= box.half()
                    && loc.getBlockY() >= anchor.getBlockY() - box.down()
                    && loc.getBlockY() <= anchor.getBlockY() + box.up() + HEADROOM) {
                return true;
            }
        }
        return false;
    }

    public boolean isExitPad(Location loc) {
        return worlds.isExitPad(loc);
    }

    /** Камені-виходи в кишеньковому світі (institutionId → блок) — для партиклів над ними. */
    public Map<String, Location> exitPads() {
        return worlds.exitPads();
    }

    public boolean isChurchWorld(World world) {
        return worlds.isChurchWorld(world);
    }

    /** Блок-вівтар: за ним пізнається шрайн при кліку й шукається мітка при постановці. */
    public Material focusBlock() {
        return focusBlock;
    }

    /**
     * У храмі сили Потойбічного мовчать. Підключається як {@code AbilityGuard} в
     * `AbilityExecutor` — тим самим швом, що й тиша на підпільних зборах, тож жодних
     * гардів по місцях виклику здібностей не додається.
     *
     * <p>На відміну від зборів, порушення тут не фіксується: спроба касту в храмі — не
     * проступок перед кимось, просто нічого не стається.
     */
    public boolean blocksAbilities(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && worlds.isChurchWorld(player.getWorld());
    }

    public void enter(Player player, ChurchAnchor shrine) {
        String institutionId = assign(shrine);
        if (institutionId == null) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Святиня мовчить — жодна церква не відгукується.");
            // Гравець бачить лише «мовчить», і без цього рядка адмін не має ЖОДНОГО сліду:
            // саме так святині Вічної Темряви й Блазня виглядали зламаними без причини.
            Bukkit.getLogger().warning("[Mysteries-Above] Shrine at "
                    + describe(shrine.marker().getLocation())
                    + " leads nowhere: no church with a standing temple is available.");
            return;
        }
        Optional<Location> arrival = worlds.arrivalOf(institutionId);
        if (arrival.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Храм по той бік не постав. Схоже, датапак структур не встановлено.");
            Bukkit.getLogger().warning("[Mysteries-Above] Shrine at "
                    + describe(shrine.marker().getLocation()) + " points at " + institutionId
                    + ", which has no arrival point — its temple was not built or not claimed.");
            return;
        }
        remember(player);
        player.teleport(arrival.get());
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
        player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Ви ступаєте в " + displayName(institutionId) + ".");
        player.sendMessage(PREFIX + ChatColor.GRAY + "Щоб повернутись — клацніть по каменю біля виходу.");
    }

    public void leave(Player player) {
        ReturnPointRepository.Point point = returnPoints.remove(player.getUniqueId());
        persist();
        Location back = toLocation(point);
        if (back == null) {
            back = Bukkit.getWorlds().get(0).getSpawnLocation();
            player.sendMessage(PREFIX + ChatColor.GRAY
                    + "Дорогу назад загублено — вас винесло до світового шпиля.");
        }
        player.teleport(back);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
    }

    /**
     * Гравець зайшов у гру всередині світу храмів, а точки повернення на нього немає:
     * файл загубився або його закинуло сюди командою. Той, у кого точка є, лишається —
     * він прийшов сам і вийде каменем.
     */
    public void restoreIfStranded(Player player) {
        if (!worlds.isChurchWorld(player.getWorld())
                || returnPoints.containsKey(player.getUniqueId())) {
            return;
        }
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        player.sendMessage(PREFIX + ChatColor.GRAY
                + "Храм відпустив вас: дороги назад він не пам'ятав.");
    }

    /**
     * `/church shrine` — шрайн у вже згенерованому селі, куди worldgen більше не зазирне.
     * Ставить і вівтарний блок: адмінська команда — свідома разова дія, а не автоматична
     * забудова світу, якою займається датапак.
     */
    public boolean placeMarker(Location loc, String institutionId) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        // Вівтар + піраміда під ним + кольорове скло над ним — той самий набір, що дає
        // структура, інакше маяк на порожньому місці не засвітився б.
        world.getBlockAt(loc).setType(focusBlock);
        if (focusBlock == Material.BEACON) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getBlockAt(loc.getBlockX() + dx, loc.getBlockY() - 1,
                            loc.getBlockZ() + dz).setType(Material.IRON_BLOCK);
                }
            }
            world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ())
                    .setType(beamGlassOf(institutionId));
        }
        Marker marker = world.spawn(loc.clone().add(0, 2, 0), Marker.class);
        marker.addScoreboardTag(ChurchAnchor.TAG_ANCHOR);
        marker.addScoreboardTag("lotm_role_" + ChurchAnchor.ROLE_SHRINE);
        marker.addScoreboardTag(ChurchAnchor.churchTag(institutionId));
        marker.addScoreboardTag("lotm_box_" + MANUAL_BOX.half()
                + "_" + MANUAL_BOX.down() + "_" + MANUAL_BOX.up());
        // Реєструємо нарівні з автоматичними: інакше ця церква вважалась би «ще без
        // святині», і біля найближчого нового села їй поставили б другу.
        shrines.add(institutionId, loc);
        return true;
    }

    public String displayName(String institutionId) {
        return registry.byId(institutionId).map(Institution::displayName).orElse(institutionId);
    }

    /**
     * Колір святині — колір шляху, чиє ім'я носить церква
     * ({@code InstitutionRegistry.brandingPathwayOf} → {@code PathwayBranding}).
     *
     * <p>Раніше тут брався перший ПОВНИЙ доступ церкви, і це давало хибу: Церква Блазня
     * повністю веде Двері, тож її святиня світилась аквамарином замість фіолетового.
     * Шлях, який церква ДАЄ, і шлях, чиїм іменем вона зветься, — різні речі.
     */
    public Color colorOf(String institutionId) {
        return PathwayBranding.liquidOf(registry.brandingPathwayOf(institutionId));
    }

    /**
     * Кольорове скло, яким фарбується промінь маяка святині — найближчий із 16 ванільних
     * барвників до кольору церкви.
     *
     * <p>Підбір за відстанню в RGB, а не таблиця «церква → скло»: кольори шляхів живуть у
     * {@code PathwayBranding}, і другий список довелось би тримати з ним у синхроні
     * вручну. Так новий шлях дістає скло сам собою.
     */
    public Material beamGlassOf(String institutionId) {
        Color color = colorOf(institutionId);
        DyeColor best = DyeColor.WHITE;
        int bestDistance = Integer.MAX_VALUE;
        for (DyeColor dye : DyeColor.values()) {
            Color other = dye.getColor();
            int dr = other.getRed() - color.getRed();
            int dg = other.getGreen() - color.getGreen();
            int db = other.getBlue() - color.getBlue();
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = dye;
            }
        }
        return Material.valueOf(best.name() + "_STAINED_GLASS");
    }

    /** Короткі координати для лога — повний {@code Location.toString()} нечитний. */
    private static String describe(Location loc) {
        return (loc.getWorld() == null ? "?" : loc.getWorld().getName())
                + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void remember(Player player) {
        Location loc = player.getLocation();
        returnPoints.put(player.getUniqueId(), new ReturnPointRepository.Point(
                player.getUniqueId().toString(), loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        persist();
    }

    private static Location toLocation(ReturnPointRepository.Point point) {
        if (point == null) {
            return null;
        }
        World world = Bukkit.getWorld(point.world());
        return world == null ? null
                : new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    /** Церкви, яким узагалі можна віддати святиню: увімкнені й із живим храмом. */
    public List<String> usableChurchIds() {
        return registry.churches().stream()
                .map(Institution::id)
                .filter(this::hasTemple)
                .toList();
    }

    private void persist() {
        repository.save(new ReturnPointRepository.Model(List.copyOf(returnPoints.values())));
    }
}
