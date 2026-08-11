package me.vangoo.pathways.common;

import org.bukkit.entity.Entity;

/**
 * Що вважається духом. Дух — істота БЕЗ шляху (MythicMobs-пак {@code Mobs/spirits.yml}), тож
 * і це знання спільне: зараз ним живе Смерть (Посл. 8 — Спілкування з духами, Око Смерті,
 * Духовний зір, Похмура присутність), згодом — Darkness (Spirit Warlock).
 *
 * <p>Упізнання йде через ванільний scoreboard-тег, який пак ставить сам
 * ({@code addtag{t=ma_spirit} @self ~onSpawn}): шар здібностей не має права торкатись MythicMobs
 * API ({@code ArchitectureTest.mythicMobsApiIsConfinedToBridgePackage}). Тег живе тут в одному
 * місці, бо рядок мусить збігатися з паком — п'ять копій літерала рано чи пізно розійшлися б.
 */
public final class Spirits {

    /** Тег із пака духів; мусить збігатися з {@code addtag} у {@code Mobs/spirits.yml}. */
    public static final String TAG = "ma_spirit";

    private Spirits() {
    }

    public static boolean isSpirit(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }
}
