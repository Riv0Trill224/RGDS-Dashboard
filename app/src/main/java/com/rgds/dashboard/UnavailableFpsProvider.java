package com.rgds.dashboard;

public final class UnavailableFpsProvider implements FpsProvider {
    @Override public Reading sample() {
        return new Reading(null, "FPS del juego no disponible");
    }
    @Override public void close() { }
}
