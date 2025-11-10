package edu.ftcphoenix.fw2.sensing.impl;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import edu.ftcphoenix.fw2.platform.VoltageProvider;

public final class HubVoltageProvider implements VoltageProvider {
    private final Iterable<VoltageSensor> sensors;
    public HubVoltageProvider(HardwareMap hw){ this.sensors = hw.getAll(VoltageSensor.class); }
    @Override public double getVoltage(){
        double sum=0, n=0;
        for (VoltageSensor s: sensors){ double v=s.getVoltage(); if (v>0){sum+=v;n++;}}
        return (n>0)? sum/n : 12.0;
    }
}
