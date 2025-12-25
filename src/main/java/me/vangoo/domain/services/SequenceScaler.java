package me.vangoo.domain.services;

public class SequenceScaler {

    public enum ScalingStrategy {

        WEAK(5),      // +5% за рівень (Seq 0: +45%)
        MODERATE(15),  // +15% за рівень (Seq 0: +135%)
        STRONG(30),    // +30% за рівень (Seq 0: +270%)
        DIVINE(60),    // +60% за рівень (Seq 0: +540% - справжня потужність)

        // Штрафи (High Risk): Штраф стає набагато жорсткішим для низьких послідовностей
        PENALTY_LINEAR(20),   // +20% до штрафу за кожен рівень сили
        PENALTY_SEVERE(50),   // +50% до штрафу за кожен рівень сили
        PENALTY_EXTREME(100); // Штраф подвоюється за кожен рівень сили (екстремальний ризик)

        private final double percentPerLevel;

        ScalingStrategy(double percentPerLevel) {
            this.percentPerLevel = percentPerLevel;
        }

        public double getPercentPerLevel() {
            return percentPerLevel;
        }
    }

    public static double calculateMultiplier(int sequence, ScalingStrategy strategy) {
        int powerLevel = 9 - sequence; // Seq 9 = 0, Seq 0 = 9
        return 1.0 + (powerLevel * strategy.getPercentPerLevel() / 100.0);
    }

    public static int scaleSpiritualityLoss(int maxSpirituality, int sequence) {
        int power = 9 - sequence;

        // Seq 9: втрачає ~5% від максимуму
        // Seq 0: втрачає ~50% від максимуму (катастрофічно)
        double basePercent = 0.05;
        double growthPerLevel = 0.05; // +5% до загального обсягу за кожну послідовність

        double totalPercent = basePercent + (power * growthPerLevel);
        int loss = (int) (maxSpirituality * totalPercent);

        return Math.max(10, loss);
    }

    public static int scaleDamagePenalty(int sequence) {
        int power = 9 - sequence;

        // Базова шкода: 2 (1 серце)
        // Додаємо прогресію: Seq 9 = 2, Seq 5 = 10, Seq 0 = 20 (10 сердець!)
        int baseDamage = 2;
        int escalation = power * 2;

        // Для напівбогів (Seq 4-0) додаємо додатковий "божественний відкат"
        if (sequence <= 4) {
            escalation += (5 - sequence) * 3;
        }

        return baseDamage + escalation;
    }

    public static int getSequencePower(int sequence) {
        return 9 - sequence;
    }
}