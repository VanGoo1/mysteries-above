---
trigger: always_on
---

### Project Overview
mysteries-above is a Spigot-based Minecraft plugin inspired by the "Lord of the Mysteries" series. It implements a complex system of Pathways and Abilities that players can progress through (Sequences 9 to 0).

### Build & Configuration
- Requirements: JDK 21, Maven.
- Build: Run mvn clean package to generate the plugin JAR.
- Dependencies:
    - spigot-api: 1.21.1
    - EffectLib: For visual effects.
    - glowingentities: For entity highlights.
    - triumph-gui: For inventory menus.
    - CoreProtect: For block interaction handling.

### Project Structure
The project follows a Clean/Hexagonal Architecture:
1.  Domain Layer (`me.vangoo.domain`):
    - Contains core business logic and entities.
    - abilities: Base classes and interfaces for all abilities.
    - entities: Core entities like Beyonder and Pathway.
    - pathways: Implementation of specific pathways (Error, Door, etc.) and their unique abilities.
    - valueobjects: Immutable objects like Sequence, AbilityResult.
2.  Application Layer (`me.vangoo.application`):
    - Orchestrates domain objects to perform tasks.
    - services: Managers like PathwayManager, BeyonderService.
3.  Infrastructure Layer (`me.vangoo.infrastructure`):
    - External system integrations (e.g., Bukkit implementations of domain interfaces).
    - persistence: Data storage.
4.  Presentation Layer (`me.vangoo.presentation`):
    - User interfaces and entry points.
    - commands: Plugin commands.
    - listeners: Event handlers.
    - gui: Menu implementations.

### Development Workflow: Adding New Pathways & Abilities

#### 1. Create an Ability
Create a class in me.vangoo.domain.pathways.<pathway>.abilities.
Inherit from one of the base classes in me.vangoo.domain.abilities.core:
- ActiveAbility: For abilities triggered by the player.
- PermanentPassiveAbility: For constant buffs.
- ToggleableAbility: For abilities that can be turned on/off.

Example implementation:
public class MyAbility extends ActiveAbility {
    @Override
    public String getName() { return "My Ability"; }

    @Override
    public String getDescription(Sequence userSequence) { return "Description..."; }

    @Override
    public int getSpiritualityCost() { return 10; }

    @Override
    public int getCooldown(Sequence userSequence) { return 5; }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // Logic here
        return AbilityResult.success();
    }
}

#### 2. Create/Update a Pathway
Create a class in me.vangoo.domain.pathways.<pathway> extending Pathway.
Register abilities in initializeAbilities():

public class MyPathway extends Pathway {
    public MyPathway(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        sequenceAbilities.put(9, List.of(new MyAbility()));
    }
}

#### 3. Register the Pathway
Add the pathway to me.vangoo.application.services.PathwayManager.initializePathways():
pathways.put("MyPathway", new MyPathway(PathwayGroup.LordOfMysteries, 
    List.of("Seq0", "Seq1", ..., "Seq9")));

### General Development Info
- Code Style: Follow the existing naming conventions. Use meaningful names for abilities.
- Resource Management: Spirituality and Cooldowns are handled by the execute method in Ability. For active abilities, AbilityResourceConsumer (in infrastructure) typically handles the consumption after successful execution.
- Scaling: Use scaleValue in Ability to make effects stronger as the player's sequence level decreases.
- Success Checks: Override getSequenceCheckTarget to enable automatic success chance calculations based on sequence difference between caster and target.
