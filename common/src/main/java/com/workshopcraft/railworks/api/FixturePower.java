package com.workshopcraft.railworks.api;

/**
 * Power a fixture uses or makes, in energy units a tick at a voltage tier. Declared now, carried later by the
 * power rail.
 */
public record FixturePower(Role role, PowerTier tier, int rate) {
    public enum Role {
        CONSUMER,
        PRODUCER
    }

    public FixturePower {
        if (rate <= 0 || rate > tier.maxRate()) throw new IllegalArgumentException("rate must be 1 to " + tier.maxRate() + " at " + tier);
    }

    public static FixturePower consumer(PowerTier tier, int rate) {
        return new FixturePower(Role.CONSUMER, tier, rate);
    }

    public static FixturePower producer(PowerTier tier, int rate) {
        return new FixturePower(Role.PRODUCER, tier, rate);
    }
}
