package me.vangoo.domain.valueobjects;

import java.util.List;

/**
 * Персистентний знімок почту нежиті одного власника (Death, Посл. 6): пишеться при виході з
 * серверу, читається при вході. {@code guardPost} ненульовий лише якщо останній наказ був
 * «стерегти це місце» — {@code FOLLOW}/{@code ATTACK} до диску не доїжджають, бо ціль наказу все
 * одно вже невалідна після relog/рестарту.
 */
public record RetinueSnapshot(List<RetinueServant> servants, boolean hidden, RetinuePoint guardPost) {
}
