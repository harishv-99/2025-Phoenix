package edu.ftcphoenix.fw.drive.guidance;

import edu.ftcphoenix.fw.core.debug.DebugSink;
import edu.ftcphoenix.fw.core.geometry.Pose2d;
import edu.ftcphoenix.fw.core.math.MathUtil;
import edu.ftcphoenix.fw.core.time.LoopClock;
import edu.ftcphoenix.fw.drive.DriveOverlay;
import edu.ftcphoenix.fw.drive.DriveOverlayMask;
import edu.ftcphoenix.fw.drive.DriveOverlayOutput;
import edu.ftcphoenix.fw.drive.DriveSignal;

/**
 * Implementation of {@link DriveOverlay} for {@link DriveGuidancePlan}.
 */
final class DriveGuidanceOverlay implements DriveOverlay {

    private final DriveGuidancePlan plan;
    private final DriveGuidanceEvaluator evaluator;

    // Adaptive selection state.
    private boolean obsInRangeForTranslation = false;
    private double blendTTranslate = 0.0;
    private double blendTOmega = 0.0;

    // Note: per-plan spatial evaluation state is held by {@link DriveGuidanceEvaluator}.

    // Debug last values.
    private DriveOverlayOutput lastOut = DriveOverlayOutput.zero();
    private String lastMode = "";

    DriveGuidanceOverlay(DriveGuidancePlan plan) {
        this.plan = plan;
        this.evaluator = new DriveGuidanceEvaluator(plan);
    }

    @Override
    public void onEnable(LoopClock clock) {
        // Reset adaptive state so first output after enable is predictable.
        obsInRangeForTranslation = false;
        blendTTranslate = 0.0;
        blendTOmega = 0.0;
        evaluator.onEnable();
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
        dbg.addData(p + ".adaptive.lastObservedTagId", evaluator.lastObservedTagId());
        Pose2d anchor = evaluator.fieldToTranslationFrameAnchor();
        dbg.addData(p + ".robotRelative.translationAnchor", anchor != null ? anchor.toString() : "null");
    }

    // ------------------------------------------------------------------------
    // Solvers
    // ------------------------------------------------------------------------

    private Solution solveWithObservation(LoopClock clock) {
        DriveGuidanceEvaluator.Solution sol = evaluator.solveWithObservation(clock);
        return toCommandSolution(sol);
    }

    private Solution solveWithFieldPose(LoopClock clock) {
        DriveGuidanceEvaluator.Solution sol = evaluator.solveWithFieldPose();
        return toCommandSolution(sol);
    }

    private Solution toCommandSolution(DriveGuidanceEvaluator.Solution sol) {
        if (sol == null || !sol.valid) {
            return Solution.invalid();
        }

        double axial = 0.0;
        double lateral = 0.0;
        double omega = 0.0;

        if (sol.canTranslate) {
            DriveSignal t = DriveGuidanceControllers.translationCmd(sol.forwardErrorIn, sol.leftErrorIn, plan.tuning);
            axial = t.axial;
            lateral = t.lateral;
        }
        if (sol.canOmega) {
            omega = DriveGuidanceControllers.omegaCmd(sol.omegaErrorRad, plan.tuning);
        }

        return new Solution(true,
                new DriveSignal(axial, lateral, omega),
                sol.canTranslate,
                sol.canOmega,
                sol.hasRangeInches,
                sol.rangeInches);
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
