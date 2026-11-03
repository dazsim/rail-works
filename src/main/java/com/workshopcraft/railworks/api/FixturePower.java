package com.workshopcraft.railworks.api;

/**
 * Power a fixture uses or makes, in FE a tick at a tier, and the buffer it adds to its carriage's store.
 * The rate is the most the fixture can move through {@link CarriagePowerNetwork} each tick. A storage fixture
 * has rate 0 and only adds its buffer.
 */
public record FixturePower(Role role, PowerTier tier, int rate, long buffer) {
    public enum Role {
        CONSUMER,
        PRODUCER,
        STORAGE
    }

    public FixturePower {
        if (rate < 0 || rate > tier.maxRate()) throw new IllegalArgumentException("rate must be 0 to " + tier.maxRate() + " at " + tier);
        if (buffer < 0) throw new IllegalArgumentException("buffer must not be negative");
        if (role != Role.STORAGE && rate == 0) throw new IllegalArgumentException("a consumer or producer needs a rate");
        if (role == Role.STORAGE && (rate != 0 || buffer == 0)) throw new IllegalArgumentException("storage has a buffer and no rate");
    }

    public FixturePower(Role role, PowerTier tier, int rate) {
        this(role, tier, rate, 0);
    }

    public static FixturePower consumer(PowerTier tier, int rate) {
        return new FixturePower(Role.CONSUMER, tier, rate, 0);
    }

    public static FixturePower consumer(PowerTier tier, int rate, long buffer) {
        return new FixturePower(Role.CONSUMER, tier, rate, buffer);
    }

    public static FixturePower producer(PowerTier tier, int rate) {
        return new FixturePower(Role.PRODUCER, tier, rate, 0);
    }

    public static FixturePower producer(PowerTier tier, int rate, long buffer) {
        return new FixturePower(Role.PRODUCER, tier, rate, buffer);
    }

    public static FixturePower storage(long buffer) {
        return new FixturePower(Role.STORAGE, PowerTier.LV, 0, buffer);
    }
}
