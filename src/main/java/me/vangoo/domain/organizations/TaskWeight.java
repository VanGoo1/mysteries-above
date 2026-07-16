package me.vangoo.domain.organizations;

/** Вага завдання ордену — визначає цінність фавора, який ним заробляється. */
public enum TaskWeight {
    LIGHT, STANDARD, MAJOR;

    public boolean atLeast(TaskWeight other) {
        return ordinal() >= other.ordinal();
    }
}
