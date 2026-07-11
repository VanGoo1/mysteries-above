package me.vangoo.domain.market;

/**
 * Ціна на ринку: предметна вимога (обмін) та/або гроші (буст). Мінімум одне з двох
 * присутнє. Чисте правило — жодного Bukkit. Комісія організатора береться лише з
 * ЧИСТО грошових угод; будь-яка предметна складова робить угоду бартером без комісії.
 */
public record Consideration(ItemDemand item, PoundMoney money) {

    /** Скільки й чого треба віддати як предметну частину ціни (amount > 0). */
    public record ItemDemand(String itemKey, int amount) {
        public ItemDemand {
            if (itemKey == null || itemKey.isEmpty()) {
                throw new IllegalArgumentException("itemKey не може бути порожнім");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Кількість має бути додатною: " + amount);
            }
        }
    }

    public Consideration {
        if (money == null) {
            throw new IllegalArgumentException("money не може бути null (використай PoundMoney.ofCoppets(0))");
        }
        if (item == null && money.isZero()) {
            throw new IllegalArgumentException("Ціна не може бути порожньою: потрібні гроші або предмет");
        }
    }

    /** Чисто грошова ціна (як було до бартера). */
    public static Consideration money(PoundMoney money) {
        return new Consideration(null, money);
    }

    /** Предметна ціна з опційним грошовим бустом (boot може бути 0). */
    public static Consideration of(ItemDemand item, PoundMoney boot) {
        if (item == null) {
            throw new IllegalArgumentException("item не може бути null для бартерної ціни");
        }
        return new Consideration(item, boot);
    }

    public boolean isBarter() {
        return item != null;
    }

    public boolean hasMoney() {
        return !money.isZero();
    }

    /** Комісія: нуль для бартеру, ceil(гроші×rate) для чисто грошової угоди. */
    public PoundMoney commission(double rate) {
        return isBarter() ? PoundMoney.ofCoppets(0) : money.commission(rate);
    }
}
