package me.vangoo.domain.forage;

/** Один кандидат фореджу: id допоміжного інгредієнта + вага у таблиці біому. */
public record ForageEntry(String ingredientId, int weight) {}
