package me.vangoo.infrastructure.organizations;

import me.vangoo.application.services.ChurchService;
import me.vangoo.infrastructure.citizens.ChurchPriestService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Сайти храмів: ручний bind, заявка на храм, знайдений у світі (кожна церква — раз на
 * світ), NPC-священики. Будівлі ставить датапак, не плагін — див.
 * `.claude/rules/church-structures.md`.
 */
public class ChurchSiteService {

    private final ChurchSiteRepository repository;
    private final ChurchPriestService priests;
    private final ChurchService churchService;
    private List<ChurchSiteRepository.Site> sites = new ArrayList<>();
    private java.util.function.Predicate<String> priestClosurePredicate = id -> false;

    public ChurchSiteService(ChurchSiteRepository repository, ChurchPriestService priests,
                             ChurchService churchService) {
        this.repository = repository;
        this.priests = priests;
        this.churchService = churchService;
        repository.load().ifPresent(m -> sites = new ArrayList<>(m.sites()));
    }

    /** Ручний bind (`/church bind`) габаритів будівлі не знає — блоки такий сайт не боронить. */
    public boolean bind(String institutionId, Location loc) {
        return bind(institutionId, loc, null);
    }

    public boolean bind(String institutionId, Location loc, ChurchSiteRepository.Box box) {
        sites.add(toSite(institutionId, loc, box));
        churchService.seedVaultIfAbsent(institutionId);
        priests.spawn(institutionId, loc);
        persist();
        return true;
    }

    /**
     * Храм знайдено у світі — плагін побачив його якір-мітку. Церква унікальна на світ:
     * перший заявлений храм свого типу отримує сайт, сховище й священика, повторна
     * заявка тієї ж церкви нічого не робить.
     *
     * @return чи саме цей храм став «тим самим»
     */
    public boolean claim(String institutionId, Location priestSpot, ChurchSiteRepository.Box box) {
        if (siteOf(institutionId).isPresent()) {
            return false; // ця церква вже має свій храм десь у світі
        }
        if (!churchService.registry().isSpawnEnabled(institutionId)) {
            return false; // шлях ще не реалізований — священика не спавнимо
        }
        return bind(institutionId, priestSpot, box);
    }

    /**
     * Заявити всі храми, чиї якорі-мітки трапились серед {@code entities}, і прибрати ті
     * мітки (сайт уже пам'ятає точку й кут). Спільна реалізація для двох входів:
     * `ChurchSpawnListener` (мітка приїхала з чанком) і `ChurchWorldProvider` (мітку щойно
     * наплодила паста — подій завантаження чанка там не буде взагалі).
     */
    public void claimAnchors(Iterable<org.bukkit.entity.Entity> entities) {
        for (org.bukkit.entity.Entity entity : entities) {
            ChurchAnchor.of(entity)
                    .filter(ChurchAnchor::isPriest)
                    .ifPresent(anchor -> {
                        claim(anchor.institutionId(), anchor.marker().getLocation(), anchor.box());
                        anchor.marker().remove();
                    });
        }
    }

    /**
     * Забути сайт церкви разом із її священиком — щоб храм можна було заявити наново.
     *
     * <p>Потрібно рівно тоді, коли храм ПЕРЕКЛАДАЮТЬ (змінилась розкладка кишенькового
     * світу): `claim` навмисно відмовляє другій заявці тієї ж церкви, тож без цього сайт
     * лишався б із координатами старої розкладки, і священик спавнився б у камені.
     * Сховище церкви не чіпаємо — воно до розташування будівлі не має стосунку.
     */
    public void forget(String institutionId) {
        if (sites.removeIf(s -> s.institutionId().equals(institutionId))) {
            priests.despawn(institutionId);
            persist();
        }
    }

    public boolean unbindNearest(Location loc) {
        for (int i = 0; i < sites.size(); i++) {
            ChurchSiteRepository.Site s = sites.get(i);
            World w = Bukkit.getWorld(s.world());
            if (w != null && w.equals(loc.getWorld())
                    && loc.distance(new Location(w, s.x(), s.y(), s.z())) <= 16) {
                priests.despawnAt(s.institutionId(), loc);
                sites.remove(i);
                persist();
                return true;
            }
        }
        return false;
    }

    public void spawnAllNpcs() {
        for (ChurchSiteRepository.Site s : sites) {
            if (priestClosurePredicate.test(s.institutionId())) {
                continue; // храм закритий після замаху — священика відродить SecretOrderService
            }
            if (!churchService.registry().isSpawnEnabled(s.institutionId())) {
                continue; // шлях ще не реалізований — священика не спавнимо навіть якщо сайт лишився зі старих даних
            }
            World w = Bukkit.getWorld(s.world());
            if (w != null) {
                priests.spawn(s.institutionId(),
                        new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
            }
        }
    }

    public void setPriestClosurePredicate(java.util.function.Predicate<String> predicate) {
        this.priestClosurePredicate = predicate;
    }

    public List<ChurchSiteRepository.Site> sites() {
        return List.copyOf(sites);
    }

    public java.util.Optional<ChurchSiteRepository.Site> siteOf(String institutionId) {
        return sites.stream()
                .filter(s -> s.institutionId().equals(institutionId))
                .findFirst();
    }

    private static ChurchSiteRepository.Site toSite(String institutionId, Location l,
                                                    ChurchSiteRepository.Box box) {
        return new ChurchSiteRepository.Site(institutionId, l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), box);
    }

    /**
     * Чи належить блок якомусь храмові. Сайтів щонайбільше 10, тож лінійний прохід
     * дешевший за будь-який індекс — а кличеться це з `BlockBreakEvent`.
     */
    public boolean isProtected(Location loc) {
        if (loc.getWorld() == null) {
            return false;
        }
        for (ChurchSiteRepository.Site s : sites) {
            ChurchSiteRepository.Box box = s.box();
            if (box == null || !s.world().equals(loc.getWorld().getName())) {
                continue;
            }
            if (Math.abs(loc.getBlockX() - Math.floor(s.x())) <= box.half()
                    && Math.abs(loc.getBlockZ() - Math.floor(s.z())) <= box.half()
                    && loc.getBlockY() >= Math.floor(s.y()) - box.down()
                    && loc.getBlockY() <= Math.floor(s.y()) + box.up()) {
                return true;
            }
        }
        return false;
    }

    private void persist() {
        repository.save(new ChurchSiteRepository.Model(sites, List.of()));
    }
}