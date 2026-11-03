package com.workshopcraft.railworks.api;

/** Voltage tiers for carriage power, with the most a single fixture may move each tick at that tier. */
public enum PowerTier {
    LV(32),
    MV(128),
    HV(512);

    private final int maxRate;

    PowerTier(int maxRate) {
        this.maxRate = maxRate;
    }

    public int maxRate() {
        return maxRate;
    }
}
