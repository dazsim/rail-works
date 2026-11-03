package com.workshopcraft.railworks.api;

/**
 * The power a fixture's carriage holds, in FE, shared with the carriages it is linked to by gangways and
 * jumper cables. Producers offer energy in, consumers draw it out, each at most its declared rate a tick and
 * all of a carriage's fixtures together at most {@link #NETWORK_CAP} a tick. Server side, call from
 * {@link Fixture#tick} or {@link Fixture#use}.
 */
public interface CarriagePowerNetwork {
    long NETWORK_CAP = 1000;

    enum State {
        /** No LV rail in the carriage: nothing moves. */
        NONE,
        /** Under 10% stored, or a draw was refused this tick or the last. */
        LOW,
        OK
    }

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
        public long space() {
            return 0;
        }

        @Override
        public long capacity() {
            return 0;
        }

        @Override
        public State state() {
            return State.NONE;
        }
    };

    boolean connected();

    PowerTier tier();

    /** Puts up to amount in, own carriage first then along the train; returns how much was taken. */
    long offer(long amount, boolean simulate);

    /** Takes exactly amount out or nothing; returns amount or 0. A refused real draw makes the state LOW. */
    long draw(long amount, boolean simulate);

    /** FE held across the linked carriages. */
    long stored();

    /** FE that would still fit across the linked carriages. */
    long space();

    /** stored() plus space(). */
    long capacity();

    State state();
}
