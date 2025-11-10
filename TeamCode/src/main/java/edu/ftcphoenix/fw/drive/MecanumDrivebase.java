package edu.ftcphoenix.fw.drive;

import edu.ftcphoenix.fw.hal.Motor;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * Non-blocking mecanum mixer with simple normalization.
 *
 * <h3>Best practices</h3>
 * <ul>
 *   <li>Keep all math here; upstream sources supply shaped signals.</li>
 *   <li>Configuration carries motor inversions; no sign flips sprinkled around the robot code.</li>
 *   <li>No timing here; this class just maps signals to powers.</li>
 * </ul>
 */
public final class MecanumDrivebase implements Drivebase {
    private final Motor fl, fr, bl, br;
    private final MecanumConfig cfg;
    private DriveSignal last = new DriveSignal(0, 0, 0);

    public MecanumDrivebase(Motor fl, Motor fr, Motor bl, Motor br, MecanumConfig cfg) {
        this.fl = fl;
        this.fr = fr;
        this.bl = bl;
        this.br = br;
        this.cfg = (cfg == null) ? MecanumConfig.defaults() : cfg;
    }

    @Override
    public void drive(DriveSignal s) {
        last = s == null ? new DriveSignal(0, 0, 0) : s;

        final double ax = last.axial;
        final double lt = last.lateral;
        final double om = last.omega * cfg.turnGain;

        // Standard mecanum mix (robot-centric)
        double pFL = ax + lt + om;
        double pFR = ax - lt - om;
        double pBL = ax - lt + om;
        double pBR = ax + lt - om;

        // Normalize if any magnitude exceeds 1
        double max = Math.max(1.0, Math.max(Math.abs(pFL), Math.max(Math.abs(pFR), Math.max(Math.abs(pBL), Math.abs(pBR)))));
        pFL /= max;
        pFR /= max;
        pBL /= max;
        pBR /= max;

        // Apply inversions
        if (cfg.invertFL) pFL = -pFL;
        if (cfg.invertFR) pFR = -pFR;
        if (cfg.invertBL) pBL = -pBL;
        if (cfg.invertBR) pBR = -pBR;

        fl.setPower(pFL);
        fr.setPower(pFR);
        bl.setPower(pBL);
        br.setPower(pBR);
    }

    @Override
    public void stop() {
        fl.setPower(0);
        fr.setPower(0);
        bl.setPower(0);
        br.setPower(0);
    }

    @Override
    public void update(LoopClock clock) { /* stateless */ }
}
