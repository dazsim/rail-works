package com.workshopcraft.railworks.api;

/** Carriage power tiers, with the most a single fixture may move each tick. LV is the only one. */
public enum PowerTier {
    LV(1000);

    private final int maxRate;

    PowerTier(int maxRate) {
        this.maxRate = maxRate;
    }

    public int maxRate() {
        return maxRate;
    }
}
