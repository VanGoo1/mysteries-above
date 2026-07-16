package me.vangoo.domain.organizations;

/** Виконане завдання = борг вдячності куратора. Персистується до витрати. */
public record Favor(TaskWeight weight, long earnedAtEpochMillis) {}
