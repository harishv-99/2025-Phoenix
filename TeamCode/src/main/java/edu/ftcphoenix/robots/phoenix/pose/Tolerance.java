package edu.ftcphoenix.robots.phoenix.pose;


/** Shared tolerances for arm pose controllers (if you implement one). */
public final class Tolerance {
    private Tolerance(){}
    public static final double ANG_RAD = 0.06; // ~3.4°
    public static final double LIFT_M  = 0.005;
}