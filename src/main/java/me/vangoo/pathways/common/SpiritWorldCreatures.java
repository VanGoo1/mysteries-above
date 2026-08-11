package me.vangoo.pathways.common;

import org.bukkit.entity.Entity;

/**
 * Що вважається істотою Світу Духів. Як і {@link Spirits}, істота без шляху (MythicMobs-пак
 * {@code Mobs/spirit-world.yml}), тож упізнання спільне: зараз нею живе Смерть (Посл. 6 —
 * Домовленість з духами), а вікі описує тих самих істот поза прив'язкою до шляху.
 *
 * <p>Тег ставить сам пак ({@code addtag{t=ma_spirit_world} @self ~onSpawn}): шар здібностей не
 * має права торкатись MythicMobs API ({@code ArchitectureTest.mythicMobsApiIsConfinedToBridgePackage}).
 */
public final class SpiritWorldCreatures {

    /** Тег із пака; мусить збігатися з {@code addtag} у {@code Mobs/spirit-world.yml}. */
    public static final String TAG = "ma_spirit_world";

    private SpiritWorldCreatures() {
    }

    public static boolean isSpiritWorldCreature(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }
}
