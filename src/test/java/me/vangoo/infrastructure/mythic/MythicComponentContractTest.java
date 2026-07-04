package me.vangoo.infrastructure.mythic;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MythicMobs' CustomComponentRegistry instantiates custom components reflectively:
 * it requires a public constructor taking exactly the load event
 * (MythicMechanicLoadEvent / MythicConditionLoadEvent), optionally preceded by
 * JavaPlugin. A component without such a constructor fails registration at server
 * start with NoSuchMethodException and every skill using it silently breaks.
 */
class MythicComponentContractTest {

    private static final String COMPONENTS_PACKAGE = "me.vangoo.infrastructure.mythic.components";

    @Test
    void everyMechanicHasLoadEventConstructor() {
        assertComponentsHaveLoadEventConstructor(MythicMechanicLoadEvent.class, MythicMechanic.class);
    }

    @Test
    void everyConditionHasLoadEventConstructor() {
        assertComponentsHaveLoadEventConstructor(MythicConditionLoadEvent.class, MythicCondition.class);
    }

    /**
     * MythicMobs' skill clock runs on async executor threads and SkillMechanic's
     * async flag defaults to true (ThreadSafetyLevel.EITHER), so a mechanic touching
     * the Bukkit API trips Paper's AsyncCatcher at runtime. Every mechanic must
     * therefore declare its own getThreadSafetyLevel() (returning SYNC_ONLY) instead
     * of inheriting the default.
     */
    @Test
    void everyMechanicDeclaresThreadSafetyLevel() {
        for (Class<?> mechanic : componentsAnnotatedWith(MythicMechanic.class)) {
            assertTrue(declaresThreadSafetyLevel(mechanic),
                    mechanic.getSimpleName() + " must override getThreadSafetyLevel() (return SYNC_ONLY"
                            + " when it touches Bukkit) — MythicMobs' skill clock is async");
        }
    }

    private boolean declaresThreadSafetyLevel(Class<?> mechanic) {
        try {
            mechanic.getDeclaredMethod("getThreadSafetyLevel");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private List<Class<?>> componentsAnnotatedWith(Class<? extends Annotation> annotation) {
        JavaClasses imported = new ClassFileImporter().importPackages(COMPONENTS_PACKAGE);
        List<Class<?>> components = imported.stream()
                .filter(c -> c.isAnnotatedWith(annotation))
                .map(JavaClass::reflect)
                .toList();
        assertFalse(components.isEmpty(),
                "No @" + annotation.getSimpleName() + " classes found in " + COMPONENTS_PACKAGE
                        + " — package scan is broken");
        return components;
    }

    private void assertComponentsHaveLoadEventConstructor(Class<?> loadEvent,
                                                          Class<? extends Annotation> annotation) {
        for (Class<?> component : componentsAnnotatedWith(annotation)) {
            assertTrue(hasLoadEventConstructor(component, loadEvent),
                    component.getSimpleName() + " needs a public constructor (" + loadEvent.getSimpleName()
                            + ") — MythicMobs' CustomComponentRegistry cannot register it otherwise");
        }
    }

    private boolean hasLoadEventConstructor(Class<?> component, Class<?> loadEvent) {
        for (Constructor<?> ctor : component.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 1 && params[0] == loadEvent) return true;
            if (params.length == 2 && params[1] == loadEvent
                    && params[0] == org.bukkit.plugin.java.JavaPlugin.class) return true;
        }
        return false;
    }
}
