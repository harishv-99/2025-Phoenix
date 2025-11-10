package edu.ftcphoenix.fw2.drivegraph;

/**
 * What axes an assist may influence.
 */
public enum AxisRole {
    FULL,               // lat + ax + omega
    OMEGA_ONLY,         // rotation only
    TRANSLATION_ONLY    // lat + ax, no omega
}
