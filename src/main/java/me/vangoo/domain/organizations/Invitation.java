package me.vangoo.domain.organizations;

/** Персистентне запрошення ордену за вчинок: чекає, поки гравець прийме (не згорає). */
public record Invitation(String institutionId, String reason, long createdAtEpochMillis) {}
