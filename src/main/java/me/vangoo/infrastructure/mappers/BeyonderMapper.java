package me.vangoo.infrastructure.mappers;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.OneTimeUseAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.SanityLoss;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.dto.BeyonderDTO;
import me.vangoo.application.services.PathwayManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Infrastructure: Маппінг між Domain (Beyonder) та DTO
 *
 * Відповідальність: серіалізація/десеріалізація Beyonder
 * та делегування серіалізації складних здібностей до відповідних Serializer'ів
 */
public class BeyonderMapper {

    private static final String ONE_TIME_PREFIX = "onetime:";

    private final PathwayManager pathwayManager;

    public BeyonderMapper(PathwayManager pathwayManager) {
        this.pathwayManager = pathwayManager;
    }

    /**
     * Convert domain model to DTO for persistence
     */
    public BeyonderDTO toDTO(Beyonder beyonder) {
        List<String> offPathwayAbilityIds = beyonder.getOffPathwayActiveAbilities().stream()
                .map(a -> a.getIdentity().id())
                .toList();
        return new BeyonderDTO(
                beyonder.getPlayerId(),
                beyonder.getPathway().getName(),
                beyonder.getSequenceLevel(),
                beyonder.getMasteryValue(),
                beyonder.getSpiritualityValue(),
                beyonder.getSanityLossScale(),
                offPathwayAbilityIds
        );
    }

    /**
     * Convert DTO to domain model
     */
    public Beyonder toDomain(BeyonderDTO dto) {
        Pathway pathway = pathwayManager.getPathway(dto.getPathwayName());
        if (pathway == null) {
            throw new IllegalArgumentException("Unknown pathway: " + dto.getPathwayName());
        }

        Set<Ability> offPathwayAbilities = new HashSet<>();
        if (dto.getOffPathwayAbilities() != null) {
            offPathwayAbilities = dto.getOffPathwayAbilities().stream()
                    .map(this::resolveAbility)
                    .filter(ability -> ability != null) // Фільтруємо невалідні
                    .collect(Collectors.toSet());
        }

        Beyonder beyonder = Beyonder.restore(
                dto.getPlayerId(),
                pathway,
                Sequence.of(dto.getSequence()),
                Mastery.of(dto.getMastery()),
                dto.getSpirituality(),
                SanityLoss.of(dto.getSanityLossScale()),
                offPathwayAbilities
        );

        beyonder.initializeTransientFields();

        return beyonder;
    }

    /**
     * Відновлює здібність з серіалізованого ID
     * Підтримує: звичайні здібності, OneTimeUse wrapper, GeneratedSpell
     */
    private Ability resolveAbility(String abilityId) {
        // 1. Перевіряємо чи це GeneratedSpell (найвищий пріоритет)
        if (GeneratedSpellSerializer.isSerializedSpell(abilityId)) {
            try {
                return GeneratedSpellSerializer.deserialize(abilityId);
            } catch (Exception e) {
                System.err.println("Warning: Failed to restore generated spell: " + abilityId);
                System.err.println("Error: " + e.getMessage());
                return null;
            }
        }

        // 2. Перевіряємо чи це OneTimeUse wrapper
        boolean isOneTime = abilityId.startsWith(ONE_TIME_PREFIX);
        String searchId = abilityId;

        if (isOneTime) {
            searchId = abilityId.substring(ONE_TIME_PREFIX.length());

            // Підтримка OneTimeUse(GeneratedSpell)
            if (GeneratedSpellSerializer.isSerializedSpell(searchId)) {
                try {
                    Ability spell = GeneratedSpellSerializer.deserialize(searchId);
                    if (spell instanceof ActiveAbility) {
                        return new OneTimeUseAbility((ActiveAbility) spell);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to restore one-time generated spell: " + searchId);
                    return null;
                }
            }
        }

        // 3. Шукаємо звичайну здібність у pathway
        Ability baseAbility = pathwayManager.findAbilityInAllPathways(AbilityIdentity.of(searchId));
        if (baseAbility == null) {
            System.err.println("Warning: Could not restore off-pathway ability with ID: " + searchId);
            return null;
        }

        // 4. Обгортаємо в OneTimeUse якщо потрібно
        if (isOneTime) {
            if (baseAbility instanceof ActiveAbility) {
                return new OneTimeUseAbility((ActiveAbility) baseAbility);
            } else {
                System.err.println("Error: OneTimeUse wrapper cannot apply to non-active ability: " + searchId);
                return null;
            }
        }

        return baseAbility;
    }
}