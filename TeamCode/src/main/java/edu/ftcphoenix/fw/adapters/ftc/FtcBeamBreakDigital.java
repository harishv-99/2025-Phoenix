package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.hardware.DigitalChannel;

import edu.ftcphoenix.fw.hal.BeamBreak;

/**
 * DigitalChannel → {@link BeamBreak}. Encodes active-low polarity.
 */
public final class FtcBeamBreakDigital implements BeamBreak {
    private final DigitalChannel ch;
    private final boolean activeLow;

    public FtcBeamBreakDigital(DigitalChannel ch, boolean activeLow) {
        this.ch = ch;
        this.activeLow = activeLow;
    }

    @Override
    public boolean blocked() {
        boolean raw = ch.getState();
        return activeLow ? !raw : raw;
    }
}
