package me.vangoo.domain;

public enum PathwayGroup {
    LordOfMysteries("Lord of Mysteries"),
    DemonOfKnowledge("Demon of Knowledge"),
    GodAlmighty("God Almighty");

    private final String displayName;

    PathwayGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
