package edu.ftcphoenix.fw.control;

public final class Pid implements PidController {
    private double kP, kI, kD;
    private double iState = 0.0, prevError = 0.0;
    private boolean first = true;
    private double iMin = -1e9, iMax = 1e9;
    private double outMin = -1.0, outMax = 1.0;

    public Pid(double kP, double kI, double kD) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
    }

    public Pid withIntegralClamp(double iMin, double iMax) {
        this.iMin = iMin;
        this.iMax = iMax;
        return this;
    }

    public Pid withOutputClamp(double outMin, double outMax) {
        this.outMin = outMin;
        this.outMax = outMax;
        return this;
    }

    @Override
    public double update(double error, double dt) {
        double p = kP * error;
        iState += kI * error * Math.max(0.0, dt);
        if (iState < iMin) iState = iMin;
        if (iState > iMax) iState = iMax;
        double d = (!first && dt > 0) ? kD * (error - prevError) / dt : 0.0;
        first = false;
        prevError = error;
        double out = p + iState + d;
        if (out < outMin) out = outMin;
        if (out > outMax) out = outMax;
        return out;
    }

    @Override
    public void reset() {
        iState = 0.0;
        prevError = 0.0;
        first = true;
    }
}
