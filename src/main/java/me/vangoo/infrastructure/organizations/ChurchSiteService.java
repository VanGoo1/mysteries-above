package me.vangoo.infrastructure.organizations;

import me.vangoo.application.services.ChurchService;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.infrastructure.citizens.ChurchPriestService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/** Сайти храмів: ручний bind, автоспавн біля сіл (кожна церква — раз на світ), NPC. */
public class ChurchSiteService {

    private final ChurchSiteRepository repository;
    private final ChurchPriestService priests;
    private final ChurchStructurePlacer placer;
    private final ChurchService churchService;
    private List<ChurchSiteRepository.Site> sites = new ArrayList<>();
    private List<String> processedVillages = new ArrayList<>();
    private java.util.function.Predicate<String> priestClosurePredicate = id -> false;

    public ChurchSiteService(ChurchSiteRepository repository, ChurchPriestService priests,
                             ChurchStructurePlacer placer, ChurchService churchService) {
        this.repository = repository;
        this.priests = priests;
        this.placer = placer;
        this.churchService = churchService;
        repository.load().ifPresent(m -> {
            sites = new ArrayList<>(m.sites());
            processedVillages = new ArrayList<>(m.processedVillageKeys());
        });
    }

    public boolean bind(String institutionId, Location loc) {
        sites.add(toSite(institutionId, loc));
        churchService.seedVaultIfAbsent(institutionId);
        priests.spawn(institutionId, loc);
        persist();
        return true;
    }

    public boolean autoPlace(String villageKey, String institutionId, Location loc) {
        placer.place(institutionId, loc); // false = без будівлі, сайт усе одно живе
        markVillageProcessed(villageKey);
        return bind(institutionId, loc);
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

    public boolean isVillageProcessed(String key) {
        return processedVillages.contains(key);
    }

    public void markVillageProcessed(String key) {
        if (!processedVillages.contains(key)) {
            processedVillages.add(key);
            persist();
        }
    }

    /** Церкви без жодного сайту — кандидати автоспавну (кожна — щонайбільше раз на світ). */
    public List<String> unplacedChurchIds() {
        List<String> placed = sites.stream()
                .map(ChurchSiteRepository.Site::institutionId).toList();
        return churchService.registry().churches().stream()
                .map(Institution::id)
                .filter(id -> !placed.contains(id))
                .toList();
    }

    public void spawnAllNpcs() {
        for (ChurchSiteRepository.Site s : sites) {
            if (priestClosurePredicate.test(s.institutionId())) {
                continue; // храм закритий після замаху — священика відродить SecretOrderService
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

    private static ChurchSiteRepository.Site toSite(String institutionId, Location l) {
        return new ChurchSiteRepository.Site(institutionId, l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    private void persist() {
        repository.save(new ChurchSiteRepository.Model(sites, processedVillages));
    }
}
