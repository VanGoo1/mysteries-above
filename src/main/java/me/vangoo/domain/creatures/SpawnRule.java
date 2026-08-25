package me.vangoo.domain.creatures;

import java.util.List;

/**
 * Правила появи істоти. Природний спавн — заміна ванільного моба ({@code naturalReplaces}) у
 * своєму біомі; структурний — вага в лотереї «скриню відкрито» ({@code structureChance} &gt; 0
 * означає «трапляється біля структур»). Ключів лут-таблиць структурний спавн більше не має:
 * працює БУДЬ-ЯКА структура, а кого саме приведе — вирішує послідовність гравця
 * ({@link CreatureSelector#pickForStructure}).
 */
public record SpawnRule(
        List<String> naturalBiomes,
        List<String> naturalReplaces,
        double naturalChance,
        double structureChance) {}
