package me.vangoo.domain.valueobjects;

/**
 * Один слуга почту нежиті (Death, Посл. 6), достатньо описаний, щоб відновити його ефект-шаром
 * ({@code UndeadRetinue.spawnFromRecord}) після relog/рестарту чи показу з тіні.
 *
 * <p>{@code typeOrTag} — ванільний {@code EntityType} (RESURRECTED/SUBJUGATED) або internal id
 * MythicMobs-істоти Світу Духів (PACT). {@code SUBJUGATED} живе в цьому переліку заради
 * приховання/показу в межах ОДНОЇ сесії — на диск (переживання relog/рестарту) такі записи
 * свідомо не пишуться, бо підкорена нежить — довільний ванільний моб без власної ідентичності.
 */
public record RetinueServant(Kind kind, String typeOrTag, String customName, RetinuePoint at) {

    public enum Kind {RESURRECTED, SUBJUGATED, PACT}
}
