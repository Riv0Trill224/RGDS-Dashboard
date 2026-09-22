package com.rgds.dashboard;

public final class UnavailableFpsProvider implements FpsProvider {
    @Override public Reading sample() {
        return new Reading(null, "FPS: fuente aún no implementada");
    }
    @Override public void close() { }
}
