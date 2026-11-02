package com.workshopcraft.railworks.api;

/**
 * The shared power a carriage holds for its fixtures, joined later across a train through couplings and
 * gangways. Producers offer energy in, consumers draw it out, each at most their declared rate a tick.
 */
public interface CarriagePowerNetwork {
    /** No rail fitted: nothing is connected and nothing moves. */
    CarriagePowerNetwork NONE = new CarriagePowerNetwork() {
        @Override
        public boolean connected() {
            return false;
        }

        @Override
        public PowerTier tier() {
            return PowerTier.LV;
        }

        @Override
        public long offer(long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long draw(long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long stored() {
            return 0;
        }

        @Override
        public long capacity() {
            return 0;
        }
    };

    boolean connected();

    PowerTier tier();

    /** Puts up to amount in; returns how much was taken. */
    long offer(long amount, boolean simulate);

    /** Takes up to amount out; returns how much was given. */
    long draw(long amount, boolean simulate);

    long stored();

    long capacity();
}
