package me.vangoo.domain.brewing;

/** Кристалічна есенція сили, ключована шляхом+послідовністю. */
public record Characteristic(String pathwayName, int sequence) {
    /** Канонічний ключ-інгредієнт Характеристики. */
    public String itemKey() {
        return "characteristic:" + pathwayName + ":" + sequence;
    }
}
