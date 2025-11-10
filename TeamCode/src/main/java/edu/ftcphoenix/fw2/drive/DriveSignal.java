package edu.ftcphoenix.fw2.drive;

public final class DriveSignal {
    private final double lateral;
    private final double axial;
    private final double omega;

    public static final DriveSignal ZERO = new DriveSignal(0, 0, 0);

    public DriveSignal(double lateral, double axial, double omega) {
        this.lateral = lateral;
        this.axial = axial;
        this.omega = omega;
    }

    public double lateral() {
        return lateral;
    }

    public double axial() {
        return axial;
    }

    public double omega() {
        return omega;
    }

    public DriveSignal plus(DriveSignal other) {
        return new DriveSignal(lateral + other.lateral, axial + other.axial, omega + other.omega);
    }

    public DriveSignal scaled(double k) {
        return new DriveSignal(lateral * k, axial * k, omega * k);
    }

    public DriveSignal withLateral(double v) {
        return new DriveSignal(v, axial, omega);
    }

    public DriveSignal withAxial(double v) {
        return new DriveSignal(lateral, v, omega);
    }

    public DriveSignal withOmega(double v) {
        return new DriveSignal(lateral, axial, v);
    }
}
