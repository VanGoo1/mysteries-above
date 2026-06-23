package me.vangoo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Архітектурні вартові шва "правила vs ефекти" (див. CLAUDE.md).
 * <p>
 * Поведінка здібностей живе в окремому шарі {@code me.vangoo.pathways} (Bukkit дозволено),
 * а {@code me.vangoo.domain} лишається ядром правил. Ці тести фіксують обидва боки межі.
 */
class ArchitectureTest {

    /**
     * Зелений стартовий скоуп: пакети domain, що ВЖЕ чисті від Bukkit.
     * Розширюй у міру чистки решти (valueobjects, abilities.core/context...).
     */
    private static final String[] PURE_DOMAIN = {
            "me.vangoo.domain.entities",
            "me.vangoo.domain.services",
            "me.vangoo.domain.spells"
    };

    @Test
    void pureDomainCoreHasNoBukkitOrGuiDependencies() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(PURE_DOMAIN);

        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.bukkit..", "dev.triumphteam..", "net.kyori..")
                .because("ядро domain (правила прогресу/балансу) має лишатися незалежним від Bukkit/GUI")
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnBehaviorLayer() {
        JavaClasses domain = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("me.vangoo.domain");

        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage("me.vangoo.pathways..")
                .because("поведінка здібностей винесена з domain — domain не повинен знати про шар ефектів")
                .check(domain);
    }
}
