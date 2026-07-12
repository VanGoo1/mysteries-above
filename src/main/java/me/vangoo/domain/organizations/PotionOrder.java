package me.vangoo.domain.organizations;

/** Активне замовлення зілля: що вариться, коли буде готове, скільки очок сплачено. */
public record PotionOrder(String pathwayName, int sequence,
                          long readyAtEpochMillis, int pointsPaid) {

    public boolean isReady(long nowMillis) {
        return nowMillis >= readyAtEpochMillis;
    }
}
