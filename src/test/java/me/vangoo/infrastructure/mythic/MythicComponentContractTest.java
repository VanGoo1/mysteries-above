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
        assertComponentsHaveLoadEventConstructor(MythicMechanic.class, MythicMechanicLoadEvent.class);
    }

    @Test
    void everyConditionHasLoadEventConstructor() {
        assertComponentsHaveLoadEventConstructor(MythicCondition.class, MythicConditionLoadEvent.class);
    }

    private void assertComponentsHaveLoadEventConstructor(Class<? extends Annotation> annotation,
                                                          Class<?> loadEvent) {
        JavaClasses imported = new ClassFileImporter().importPackages(COMPONENTS_PACKAGE);
        List<Class<?>> components = imported.stream()
                .filter(c -> c.isAnnotatedWith(annotation))
                .map(JavaClass::reflect)
                .toList();
        assertFalse(components.isEmpty(),
                "No @" + annotation.getSimpleName() + " classes found in " + COMPONENTS_PACKAGE
                        + " — package scan is broken");
        for (Class<?> component : components) {
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
