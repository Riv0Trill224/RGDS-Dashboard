package com.rgds.dashboard;

/** A future provider must identify the game's display and SurfaceFlinger layer.
 * Calls run on the sampling worker, never on the UI thread. */
public interface FpsProvider extends AutoCloseable {
    final class Reading {
        public final Float fps;
        public final String status;
        public Reading(Float fps, String status) {
            this.fps = fps;
            this.status = status;
        }
    }
    Reading sample();
    @Override void close();
}
