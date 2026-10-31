package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.track.TrackPath;

/** One section as the trolley sees it, from the server graph or the client copy. */
public record Rail(long id, TrackPath path, double y, boolean live) {
    public static final double TOP = 1 / 3.0 + 0.24;

    public double length() {
        return path.length;
    }

    public double top(double s) {
        return y + path.height(s) + TOP;
    }
}
