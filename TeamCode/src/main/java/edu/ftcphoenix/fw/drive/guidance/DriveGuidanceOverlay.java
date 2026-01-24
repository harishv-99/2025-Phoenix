package edu.ftcphoenix.fw.drive.guidance;

import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.core.geometry.Pose2d;
import edu.ftcphoenix.fw.core.math.MathUtil;
import edu.ftcphoenix.fw.core.time.LoopClock;
import edu.ftcphoenix.fw.drive.DriveOverlay;
import edu.ftcphoenix.fw.drive.DriveOverlayMask;
import edu.ftcphoenix.fw.drive.DriveOverlayOutput;
import edu.ftcphoenix.fw.drive.DriveSignal;
import edu.ftcphoenix.fw.field.TagLayout;
import edu.ftcphoenix.fw.localization.PoseEstimate;
import edu.ftcphoenix.fw.sensing.observation.TargetObservation2d;

/**
 * Implementation of {@link DriveOverlay} for {@link DriveGuidancePlan}.
 */
final class DriveGuidanceOverlay implements DriveOverlay {

    private final DriveGuidancePlan plan;

    // Adaptive selection state.
    private boolean obsInRangeForTranslation = false;
    private double blendTTranslate = 0.0;
    private double blendTOmega = 0.0;

    // For “observed tag” targets (tagId = -1), remember the last seen tag ID so field-pose mode
    // can keep working even if the tag temporarily drops out of view.
    private int lastObservedTagId = -1;

    // For robot-relative translation targets, capture the translation-frame pose when the
    // overlay becomes enabled. This allows "move forward N inches" style plans.
    private Pose2d fieldToTranslationFrameAnchor = null;

    // Debug last values.
    private DriveOverlayOutput lastOut = DriveOverlayOutput.zero();
    private String lastMode = "";

    DriveGuidanceOverlay(DriveGuidancePlan plan) {
        this.plan = plan;
    }

    @Override
    public void onEnable(LoopClock clock) {
        // Reset adaptive state so first output after enable is predictable.
        obsInRangeForTranslation = false;
        blendTTranslate = 0.0;
        blendTOmega = 0.0;
        lastObservedTagId = -1;
        fieldToTranslationFrameAnchor = null;
        lastMode = "enabled";
    }

    @Override
    public DriveOverlayOutput get(LoopClock clock) {
        DriveGuidancePlan.Feedback fb = plan.feedback;

        DriveOverlayMask requested = plan.requestedMask();
        if (requested.isNone()) {
            lastOut = DriveOverlayOutput.zero();
            lastMode = "none";
            return lastOut;
        }

        // Compute candidate solutions.
        Solution field = fb.hasFieldPose() ? solveWithFieldPose(clock) : Solution.invalid();
        Solution obs = fb.hasObservation() ? solveWithObservation(clock) : Solution.invalid();

        if (!fb.isAdaptive()) {
            Solution chosen = fb.hasObservation() ? obs : field;
            lastMode = fb.hasObservation() ? "observation" : "fieldPose";
            lastOut = applyLossPolicy(chosen, requested, fb.lossPolicy);
            return lastOut;
        }

        // Adaptive: choose per DOF.
        DriveGuidancePlan.Gates gates = (fb.gates != null) ? fb.gates : DriveGuidancePlan.Gates.defaults();

        boolean wantTranslation = requested.overridesTranslation();
        boolean wantOmega = requested.overridesOmega();

        // --- Translation source selection
        boolean hasObsT = obs.valid && obs.canTranslate && obs.hasRangeInches;
        boolean hasFieldT = field.valid && field.canTranslate;

        if (!hasObsT) {
            obsInRangeForTranslation = false;
        } else {
            // Hysteresis on observed range.
            if (!obsInRangeForTranslation) {
                if (obs.rangeInches <= gates.enterRangeInches) {
                    obsInRangeForTranslation = true;
                }
            } else {
                if (obs.rangeInches >= gates.exitRangeInches) {
                    obsInRangeForTranslation = false;
                }
            }
        }

        boolean chooseObsForTranslation;
        if (!wantTranslation) {
            chooseObsForTranslation = false;
        } else if (!hasFieldT && hasObsT) {
            chooseObsForTranslation = true;
        } else if (hasFieldT && hasObsT) {
            chooseObsForTranslation = obsInRangeForTranslation;
        } else {
            chooseObsForTranslation = false;
        }

        // --- Omega source selection
        boolean hasObsO = obs.valid && obs.canOmega;
        boolean hasFieldO = field.valid && field.canOmega;

        boolean chooseObsForOmega;
        if (!wantOmega) {
            chooseObsForOmega = false;
        } else if (!hasFieldO && hasObsO) {
            chooseObsForOmega = true;
        } else if (fb.preferObservationForOmega) {
            chooseObsForOmega = hasObsO;
        } else if (hasFieldO && hasObsO) {
            // If not preferring observation, keep omega tied to translation choice when possible.
            chooseObsForOmega = chooseObsForTranslation;
        } else {
            chooseObsForOmega = false;
        }

        // --- Blend to smooth source switching
        double dt = clock.dtSec();
        double blendSec = Math.max(0.0, gates.takeoverBlendSec);
        double step = (blendSec <= 0.0) ? 1.0 : MathUtil.clamp(dt / blendSec, 0.0, 1.0);

        blendTTranslate = updateBlend(blendTTranslate, chooseObsForTranslation, step);
        blendTOmega = updateBlend(blendTOmega, chooseObsForOmega, step);

        // Compose final command.
        double axial = 0.0;
        double lateral = 0.0;
        double omega = 0.0;

        DriveOverlayMask mask = DriveOverlayMask.NONE;

        if (wantTranslation) {
            if (hasFieldT && hasObsT) {
                axial = MathUtil.lerp(field.signal.axial, obs.signal.axial, blendTTranslate);
                lateral = MathUtil.lerp(field.signal.lateral, obs.signal.lateral, blendTTranslate);
                mask = mask.withTranslation(true);
            } else if (hasObsT) {
                axial = obs.signal.axial;
                lateral = obs.signal.lateral;
                mask = mask.withTranslation(true);
            } else if (hasFieldT) {
                axial = field.signal.axial;
                lateral = field.signal.lateral;
                mask = mask.withTranslation(true);
            }
        }

        if (wantOmega) {
            if (hasFieldO && hasObsO) {
                omega = MathUtil.lerp(field.signal.omega, obs.signal.omega, blendTOmega);
                mask = mask.withOmega(true);
            } else if (hasObsO) {
                omega = obs.signal.omega;
                mask = mask.withOmega(true);
            } else if (hasFieldO) {
                omega = field.signal.omega;
                mask = mask.withOmega(true);
            }
        }

        DriveOverlayOutput out;
        if (mask.isNone()) {
            out = applyLossPolicy(Solution.invalid(), requested, fb.lossPolicy);
        } else {
            out = new DriveOverlayOutput(new DriveSignal(axial, lateral, omega), mask);
        }

        lastMode = "adaptive";
        lastOut = out;
        return out;
    }

    @Override
    public void debugDump(DebugSink dbg, String prefix) {
        if (dbg == null) {
            return;
        }
        String p = (prefix == null || prefix.isEmpty()) ? "guidance" : prefix;
        dbg.addData(p + ".class", getClass().getSimpleName());
        dbg.addData(p + ".mode", lastMode);
        dbg.addData(p + ".mask", lastOut.mask.toString());
        dbg.addData(p + ".signal", lastOut.signal.toString());
        dbg.addData(p + ".adaptive.obsInRange", obsInRangeForTranslation);
        dbg.addData(p + ".adaptive.blendTTranslate", blendTTranslate);
        dbg.addData(p + ".adaptive.blendTOmega", blendTOmega);
        dbg.addData(p + ".adaptive.lastObservedTagId", lastObservedTagId);
        dbg.addData(p + ".robotRelative.translationAnchor",
                fieldToTranslationFrameAnchor != null ? fieldToTranslationFrameAnchor.toString() : "null");
    }

    // ------------------------------------------------------------------------
    // Solvers
    // ------------------------------------------------------------------------

    private Solution solveWithObservation(LoopClock clock) {
        DriveGuidancePlan.Observation cfg = plan.feedback.observation;
        TargetObservation2d obs = cfg.source.sample(clock);

        if (obs == null) {
            return Solution.invalid();
        }

        boolean valid = obs.hasTarget
                && obs.ageSec <= cfg.maxAgeSec
                && obs.quality >= cfg.minQuality;

        if (!valid) {
            return Solution.invalid();
        }

        if (obs.hasTargetId()) {
            lastObservedTagId = obs.targetId;
        }

        // Observation feedback is robot-relative.
        boolean hasPos = obs.hasPosition();
        Pose2d robotToAnchorPose = hasPos
                ? new Pose2d(obs.forwardInches, obs.leftInches, obs.hasOrientation() ? obs.targetHeadingRad : 0.0)
                : null;

        double rangeIn = hasPos ? Math.hypot(obs.forwardInches, obs.leftInches) : Double.NaN;
        boolean hasRange = Double.isFinite(rangeIn);

        double axial = 0.0;
        double lateral = 0.0;
        double omega = 0.0;

        boolean canTranslate = false;
        boolean canOmega = false;

        // --- Translation ---
        if (plan.translationTarget instanceof DriveGuidancePlan.TagRelativePoint) {
            DriveGuidancePlan.TagRelativePoint tp = (DriveGuidancePlan.TagRelativePoint) plan.translationTarget;

            boolean idMatches = (tp.tagId < 0)
                    || (obs.hasTargetId() && obs.targetId == tp.tagId);

            boolean needsOrientation = !(Math.abs(tp.forwardInches) < 1e-9 && Math.abs(tp.leftInches) < 1e-9);
            boolean orientationOk = !needsOrientation || obs.hasOrientation();

            if (idMatches && hasPos && orientationOk && robotToAnchorPose != null) {
                Pose2d robotToTarget = robotToAnchorPose.then(new Pose2d(tp.forwardInches, tp.leftInches, 0.0));

                Pose2d robotToTFrame = plan.controlFrames.robotToTranslationFrame();
                double forwardErr = robotToTarget.xInches - robotToTFrame.xInches;
                double leftErr = robotToTarget.yInches - robotToTFrame.yInches;

                DriveSignal t = DriveGuidanceControllers.translationCmd(forwardErr, leftErr, plan.tuning);
                axial = t.axial;
                lateral = t.lateral;
                canTranslate = true;
            }
        }

        // --- Omega / Aim ---
        if (plan.aimTarget instanceof DriveGuidancePlan.TagRelativePoint) {
            DriveGuidancePlan.TagRelativePoint tp = (DriveGuidancePlan.TagRelativePoint) plan.aimTarget;

            boolean idMatches = (tp.tagId < 0)
                    || (obs.hasTargetId() && obs.targetId == tp.tagId);

            Pose2d robotToAimFrame = plan.controlFrames.robotToAimFrame();

            // If we have position, we can compute the true vector from the aim frame origin.
            if (idMatches && hasPos && robotToAnchorPose != null) {
                boolean needsOrientation = !(Math.abs(tp.forwardInches) < 1e-9 && Math.abs(tp.leftInches) < 1e-9);
                boolean orientationOk = !needsOrientation || obs.hasOrientation();

                if (orientationOk) {
                    Pose2d robotToAimPoint = robotToAnchorPose.then(new Pose2d(tp.forwardInches, tp.leftInches, 0.0));
                    Pose2d aimFrameToPoint = robotToAimFrame.inverse().then(robotToAimPoint);
                    double bearingErr = Math.atan2(aimFrameToPoint.yInches, aimFrameToPoint.xInches);
                    omega = DriveGuidanceControllers.omegaCmd(Pose2d.wrapToPi(bearingErr), plan.tuning);
                    canOmega = true;
                }
            } else {
                // Bearing-only fallback: only safe when the aim frame origin is the robot origin
                // and we are aiming at the anchor center (forward=0,left=0).
                boolean aimingAtCenter = Math.abs(tp.forwardInches) < 1e-9 && Math.abs(tp.leftInches) < 1e-9;
                boolean aimFrameAtOrigin = Math.abs(robotToAimFrame.xInches) < 1e-9 && Math.abs(robotToAimFrame.yInches) < 1e-9;

                if (idMatches && aimingAtCenter && aimFrameAtOrigin) {
                    double bearingErr = Pose2d.wrapToPi(obs.bearingRad - robotToAimFrame.headingRad);
                    omega = DriveGuidanceControllers.omegaCmd(bearingErr, plan.tuning);
                    canOmega = true;
                }
            }
        }

        return new Solution(true,
                new DriveSignal(axial, lateral, omega),
                canTranslate,
                canOmega,
                hasRange,
                rangeIn
        );
    }

    private Solution solveWithFieldPose(LoopClock clock) {
        DriveGuidancePlan.FieldPose cfg = plan.feedback.fieldPose;
        PoseEstimate est = cfg.poseEstimator.getEstimate();

        boolean valid = est != null
                && est.hasPose
                && est.ageSec <= cfg.maxAgeSec
                && est.quality >= cfg.minQuality;

        if (!valid) {
            return Solution.invalid();
        }

        Pose2d fieldToRobot = est.toPose2d();
        TagLayout layout = cfg.tagLayout;

        // Current controlled-frame poses.
        Pose2d fieldToTFrame = fieldToRobot.then(plan.controlFrames.robotToTranslationFrame());

        // Resolve translation target.
        Pose2d fieldToTranslatePoint;
        if (plan.translationTarget instanceof DriveGuidancePlan.RobotRelativePoint) {
            DriveGuidancePlan.RobotRelativePoint rr = (DriveGuidancePlan.RobotRelativePoint) plan.translationTarget;

            // Capture the "starting" translation-frame pose once per enable cycle.
            if (fieldToTranslationFrameAnchor == null) {
                fieldToTranslationFrameAnchor = fieldToTFrame;
            }

            fieldToTranslatePoint = fieldToTranslationFrameAnchor.then(new Pose2d(rr.forwardInches, rr.leftInches, 0.0));
        } else {
            fieldToTranslatePoint = resolveToFieldPoint(plan.translationTarget, layout);
        }

        // Resolve aim target (FieldHeading is handled as a non-point target below).
        Pose2d fieldToAimPoint = (plan.aimTarget instanceof DriveGuidancePlan.FieldHeading)
                ? null
                : resolveToFieldPoint(plan.aimTarget, layout);

        double axial = 0.0;
        double lateral = 0.0;
        double omega = 0.0;

        boolean canTranslate = false;
        boolean canOmega = false;

        // --- Translation ---
        if (fieldToTranslatePoint != null) {
            // Field error vector from translation-frame origin to target point.
            double dxField = fieldToTranslatePoint.xInches - fieldToTFrame.xInches;
            double dyField = fieldToTranslatePoint.yInches - fieldToTFrame.yInches;

            // Rotate into robot frame.
            double h = fieldToRobot.headingRad;
            double cos = Math.cos(h);
            double sin = Math.sin(h);
            double forwardErr = dxField * cos + dyField * sin;
            double leftErr = -dxField * sin + dyField * cos;

            DriveSignal t = DriveGuidanceControllers.translationCmd(forwardErr, leftErr, plan.tuning);
            axial = t.axial;
            lateral = t.lateral;
            canTranslate = true;
        }

        // --- Omega / Aim ---
        if (plan.aimTarget instanceof DriveGuidancePlan.FieldHeading) {
            DriveGuidancePlan.FieldHeading fh = (DriveGuidancePlan.FieldHeading) plan.aimTarget;
            Pose2d fieldToAimFrame = fieldToRobot.then(plan.controlFrames.robotToAimFrame());
            double headingErr = Pose2d.wrapToPi(fh.fieldHeadingRad - fieldToAimFrame.headingRad);
            omega = DriveGuidanceControllers.omegaCmd(headingErr, plan.tuning);
            canOmega = true;
        } else if (fieldToAimPoint != null) {
            Pose2d fieldToAimFrame = fieldToRobot.then(plan.controlFrames.robotToAimFrame());
            Pose2d aimFrameToPoint = fieldToAimFrame.inverse().then(fieldToAimPoint);
            double bearingErr = Math.atan2(aimFrameToPoint.yInches, aimFrameToPoint.xInches);
            omega = DriveGuidanceControllers.omegaCmd(Pose2d.wrapToPi(bearingErr), plan.tuning);
            canOmega = true;
        }

        return new Solution(true,
                new DriveSignal(axial, lateral, omega),
                canTranslate,
                canOmega,
                false,
                Double.NaN
        );
    }

    /**
     * Resolve a plan target into a field-coordinate point (x,y,heading=0) if possible.
     *
     * <p>Returns null when the target cannot be resolved in field space. Examples include:</p>
     * <ul>
     *   <li>tag-relative targets without a {@link TagLayout},</li>
     *   <li>“observed tag” targets when no tag has been seen yet,</li>
     *   <li>non-point targets such as {@link DriveGuidancePlan.FieldHeading}, and</li>
     *   <li>robot-relative translation targets ({@link DriveGuidancePlan.RobotRelativePoint}).</li>
     * </ul>
     */
    private Pose2d resolveToFieldPoint(Object target, TagLayout layout) {
        if (target == null) {
            return null;
        }

        if (target instanceof DriveGuidancePlan.FieldPoint) {
            DriveGuidancePlan.FieldPoint fp = (DriveGuidancePlan.FieldPoint) target;
            return new Pose2d(fp.xInches, fp.yInches, 0.0);
        }

        if (target instanceof DriveGuidancePlan.TagRelativePoint) {
            if (layout == null) {
                return null;
            }

            DriveGuidancePlan.TagRelativePoint tp = (DriveGuidancePlan.TagRelativePoint) target;
            int tagId = (tp.tagId >= 0) ? tp.tagId : lastObservedTagId;
            if (tagId < 0) {
                return null;
            }

            TagLayout.TagPose tagPose = layout.get(tagId);
            if (tagPose == null) {
                return null;
            }

            Pose2d fieldToTag = tagPose.fieldToTagPose().toPose2d();
            Pose2d tagToPoint = new Pose2d(tp.forwardInches, tp.leftInches, 0.0);
            return fieldToTag.then(tagToPoint);
        }

        return null;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static double updateBlend(double current, boolean chooseObs, double step) {
        if (chooseObs) {
            return Math.min(1.0, current + step);
        }
        return Math.max(0.0, current - step);
    }

    private static DriveOverlayOutput applyLossPolicy(Solution sol, DriveOverlayMask requested, DriveGuidancePlan.LossPolicy policy) {
        DriveOverlayMask mask = DriveOverlayMask.NONE;

        if (sol.valid) {
            if (requested.overridesTranslation() && sol.canTranslate) {
                mask = mask.withTranslation(true);
            }
            if (requested.overridesOmega() && sol.canOmega) {
                mask = mask.withOmega(true);
            }
            if (!mask.isNone()) {
                return new DriveOverlayOutput(sol.signal, mask);
            }
        }

        // If we couldn't produce any usable command this loop.
        if (policy == DriveGuidancePlan.LossPolicy.ZERO_OUTPUT) {
            return new DriveOverlayOutput(DriveSignal.zero(), requested);
        }
        return DriveOverlayOutput.zero();
    }

    /**
     * Small solver result bundle.
     */
    private static final class Solution {
        final boolean valid;
        final DriveSignal signal;
        final boolean canTranslate;
        final boolean canOmega;
        final boolean hasRangeInches;
        final double rangeInches;

        Solution(boolean valid,
                 DriveSignal signal,
                 boolean canTranslate,
                 boolean canOmega,
                 boolean hasRangeInches,
                 double rangeInches) {
            this.valid = valid;
            this.signal = signal;
            this.canTranslate = canTranslate;
            this.canOmega = canOmega;
            this.hasRangeInches = hasRangeInches;
            this.rangeInches = rangeInches;
        }

        static Solution invalid() {
            return new Solution(false, DriveSignal.zero(), false, false, false, Double.NaN);
        }
    }
}
