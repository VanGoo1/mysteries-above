package me.vangoo.domain.entities;

public enum PathwayGroup {
    LordOfMysteries("Володар Таємниць"),
    DemonOfKnowledge("Демон Знання"),
    GodAlmighty("Бог Всемогутній"),
    TheAnarchy("Анархія"),
    EternalDarkness("Вічна Темрява"),
    GoddessOfOrigin("Богиня Витоків"),
    CalamityOfDestruction("Лихо Руйнування"),
    FatherOfDevils("Батько Дияволів"),
    KeyOfLight("Ключ Світла");

    private final String displayName;

    PathwayGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}