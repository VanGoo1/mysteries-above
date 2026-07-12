package me.vangoo.domain.organizations;

import java.util.List;
import java.util.Optional;

/** Канонічна інституція: церква або таємна організація. Незмінний VO. */
public record Institution(
        String id,
        InstitutionType type,
        String displayName,
        String lore,
        List<PathwayAccess> accesses) {

    public Institution {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (type == InstitutionType.CHURCH && accesses.isEmpty()) {
            throw new IllegalArgumentException("church must declare pathway accesses: " + id);
        }
        accesses = List.copyOf(accesses);
    }

    public Optional<PathwayAccess> accessFor(String pathwayName) {
        return accesses.stream()
                .filter(a -> a.pathwayName().equalsIgnoreCase(pathwayName))
                .findFirst();
    }

    /** Порожні доступи в SECRET_ORDER = «шляхи залежать від учасників» (приймає будь-кого). */
    public boolean acceptsAnyPathway() {
        return type == InstitutionType.SECRET_ORDER && accesses.isEmpty();
    }

    /** Правило вступу: церква приймає гравця без шляху або зі шляхом зі своїх доступів. */
    public boolean acceptsPathway(String pathwayNameOrNull) {
        if (acceptsAnyPathway()) {
            return true;
        }
        if (pathwayNameOrNull == null) {
            return type == InstitutionType.CHURCH;
        }
        return accessFor(pathwayNameOrNull).isPresent();
    }
}
