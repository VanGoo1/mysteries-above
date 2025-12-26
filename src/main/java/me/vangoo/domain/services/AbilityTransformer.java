package me.vangoo.domain.services;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.valueobjects.AbilityIdentity;

import java.util.*;
import java.util.logging.Logger;

/**
 * Domain Service: Handles transformation of abilities during sequence advancement
 * <p>
 * Responsibilities:
 * - Replace old ability versions with new ones
 * - Initialize new ability state
 * - Handle cases where multiple versions of same ability exist
 * - Deduplicate abilities by identity (keeps latest version)
 */
public class AbilityTransformer {
    /**
     * Transform abilities list by replacing old versions with new ones.
     * Preserves abilities that don't have replacements.
     * Deduplicates abilities with same identity (keeps latest from newAbilities).
     *
     * @param currentAbilities Current ability list (can be empty for initialization)
     * @param newAbilities     New abilities from advanced sequence
     * @return Transformed ability list with no duplicates
     */
    public List<Ability> transform(
            List<Ability> currentAbilities,
            List<Ability> newAbilities
    ) {
        // Step 1: Group new abilities by identity (last wins = latest version)
        Map<AbilityIdentity, Ability> newAbilitiesByIdentity = new LinkedHashMap<>();
        for (Ability ability : newAbilities) {
            AbilityIdentity identity = ability.getIdentity();

            // If duplicate identity, log and keep the last one (assumed to be from higher sequence)
            if (newAbilitiesByIdentity.containsKey(identity)) {
                Ability existing = newAbilitiesByIdentity.get(identity);
            }

            newAbilitiesByIdentity.put(identity, ability);
        }

        List<Ability> result = new ArrayList<>();
        Set<AbilityIdentity> processedIdentities = new HashSet<>();

        // Step 2: Process current abilities - replace or keep
        for (Ability current : currentAbilities) {
            AbilityIdentity currentIdentity = current.getIdentity();

            // Check if there's a new version with same identity
            if (newAbilitiesByIdentity.containsKey(currentIdentity)) {
                Ability replacement = newAbilitiesByIdentity.get(currentIdentity);

                // Only replace if it's actually different
                if (!replacement.equals(current)) {
                    result.add(replacement);
                } else {
                    // Same ability, keep it
                    result.add(current);
                }

                processedIdentities.add(currentIdentity);
            } else {
                // No replacement found, keep current
                result.add(current);
                processedIdentities.add(currentIdentity);
            }
        }

        // Step 3: Add truly new abilities (not already processed)
        for (Map.Entry<AbilityIdentity, Ability> entry : newAbilitiesByIdentity.entrySet()) {
            AbilityIdentity identity = entry.getKey();
            Ability newAbility = entry.getValue();

            if (!processedIdentities.contains(identity)) {
                result.add(newAbility);
                processedIdentities.add(identity);
            }
        }

        return result;
    }
}