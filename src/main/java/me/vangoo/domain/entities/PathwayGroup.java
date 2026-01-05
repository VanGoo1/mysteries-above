package me.vangoo.domain.entities;

public enum PathwayGroup {
    LordOfMysteries("Володар Таємниць"),
    DemonOfKnowledge("Демон Знання"),
    GodAlmighty("Бог Всемогутній"),
    TheAnarchy("Анархія");

    private final String displayName;

    PathwayGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}