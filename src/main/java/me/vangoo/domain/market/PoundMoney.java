package me.vangoo.domain.market;

/**
 * Грошова сума підпільного ринку. Внутрішнє представлення — коппети.
 * 1 золотий фунт = {@value #COPPETS_PER_POUND} коппетів.
 */
public record PoundMoney(int coppets) {

    public static final int COPPETS_PER_POUND = 20;

    public PoundMoney {
        if (coppets < 0) {
            throw new IllegalArgumentException("Сума не може бути від'ємною: " + coppets);
        }
    }

    public static PoundMoney ofCoppets(int coppets) {
        return new PoundMoney(coppets);
    }

    public static PoundMoney of(int pounds, int coppets) {
        if (pounds < 0 || coppets < 0) {
            throw new IllegalArgumentException("Сума не може бути від'ємною: " + pounds + " ф " + coppets + " к");
        }
        return new PoundMoney(pounds * COPPETS_PER_POUND + coppets);
    }

    public int wholePounds() {
        return coppets / COPPETS_PER_POUND;
    }

    public int remainderCoppets() {
        return coppets % COPPETS_PER_POUND;
    }

    public boolean isZero() {
        return coppets == 0;
    }

    public PoundMoney plus(PoundMoney other) {
        return new PoundMoney(coppets + other.coppets);
    }

    /** Кидає IllegalArgumentException, якщо результат від'ємний (через компактний конструктор). */
    public PoundMoney minus(PoundMoney other) {
        return new PoundMoney(coppets - other.coppets);
    }

    /** Множить суму на кількість одиниць (напр. ціна за одиницю × стак); factor не може бути від'ємним. */
    public PoundMoney times(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Множник не може бути від'ємним: " + factor);
        }
        return new PoundMoney(coppets * factor);
    }

    /** Комісія організатора: ceil(coppets × rate). */
    public PoundMoney commission(double rate) {
        return new PoundMoney((int) Math.ceil(coppets * rate));
    }

    /** «2 ф 15 к» / «2 ф» / «15 к» / «0 к». */
    public String format() {
        int p = wholePounds();
        int c = remainderCoppets();
        if (p > 0 && c > 0) return p + " ф " + c + " к";
        if (p > 0) return p + " ф";
        return c + " к";
    }
}
