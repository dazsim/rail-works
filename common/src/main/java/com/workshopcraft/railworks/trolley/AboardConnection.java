package com.workshopcraft.railworks.trolley;

/** What the mixin on the server's player connection lets Aboard see and reset. */
public interface AboardConnection {
    /** The player was moved by the server: take where it stands now as its last good spot. */
    void railworks$synced();

    boolean railworks$awaitingTeleport();
}
