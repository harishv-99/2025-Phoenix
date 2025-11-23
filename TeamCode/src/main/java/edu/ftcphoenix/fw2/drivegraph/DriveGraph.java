package edu.ftcphoenix.fw2.drivegraph;

import java.util.Collections;
import java.util.List;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.drive.DriveArbiter;
import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;
import edu.ftcphoenix.fw2.drive.filters.DriveAxesFilter;
import edu.ftcphoenix.fw2.drive.filters.FieldCentric;
import edu.ftcphoenix.fw2.drive.source.ConditionalSource;
import edu.ftcphoenix.fw2.filters.Filter;
import edu.ftcphoenix.fw2.filters.scalar.SafeClamp;
import edu.ftcphoenix.fw2.filters.scalar.Scale;
import edu.ftcphoenix.fw2.filters.util.AxisChains;

/**
 * DriveGraph — central wiring of driver shaping, field-centric, assists, and mixing.
 *
 * <h2>What this class does</h2>
 * <ul>
 *   <li>Builds a single {@link DriveSource} you can feed to your drivetrain each loop.</li>
 *   <li>Standardizes the TeleOp “feel” with {@link AxisChains} <b>core</b> per-axis chains
 *       (no final clamp) so we can safely compose FC and assists before the mixer.</li>
 *   <li>Applies <b>field-centric</b> (optional) after shaping, rotating translation only; omega passes through.</li>
 *   <li>Combines driver and <b>assists</b> with {@link DriveArbiter} using uniform scaling to keep
 *       the mixed command within declared logical limits.</li>
 *   <li>Adds a <b>final sink-guard</b> via per-axis {@link SafeClamp} to coerce NaN/Inf and enforce
 *       absolute bounds once, in a single, predictable place.</li>
 * </ul>
 *
 * <h2>Why core chains?</h2>
 * <p>By omitting a clamp inside the per-axis chains we avoid “premature finalization.”
 * The graph can apply field-centric and mix in assists first, then the arbiter performs
 * vector-wise uniform scaling per declared limits. Finally, we add a single, explicit
 * {@link SafeClamp} at the very end as a sink guard.</p>
 *
 * <h2>Axis limits vs. mixer limits</h2>
 * <ul>
 *   <li><b>Mixer logical limits</b> — {@code mixLimLat/mixLimAx/mixLimOm}: guide the arbiter’s
 *       <i>uniform scaling</i> of the combined signal.</li>
 *   <li><b>Final per-axis SafeClamp</b> — catches NaN/Inf and enforces per-axis bounds once.</li>
 * </ul>
 * <p>In practice, set both sets of limits to the same values (often 1.0) for simple, predictable behavior.</p>
 *
 * <h2>Usage sketch</h2>
 * <pre>{@code
 * DriveSource driver = new GamepadSource(gp, "left_x", "left_y", "right_x");
 * DriveGraphOptions opt = new DriveGraphOptions();
 * opt.precisionScale = () -> gp1.right_bumper ? 0.35 : 1.0;
 * opt.omegaFineScale = () -> gp1.left_trigger > 0.5 ? 0.35 : 1.0;
 * opt.fieldCentric   = () -> gp1.y;
 * opt.headingRad     = io::getHeadingRad;
 *
 * List<BranchSpec> assists = Arrays.asList(
 *   BranchSpec.of("SnapAngle", snapAngle, () -> gp1.right_bumper, () -> 1.0, AxisRole.OMEGA_ONLY),
 *   BranchSpec.of("TagAim",    tagAim,    () -> gp1.left_bumper,  () -> 1.0, AxisRole.OMEGA_ONLY)
 * );
 *
 * DriveGraph.Result g = DriveGraph.build(driver, opt, assists);
 * // loop:
 * DriveSignal cmd = g.source.get(clock);
 * mecanumDrive.drive(cmd);
 * }</pre>
 */
public final class DriveGraph {

    private DriveGraph() {
    }

    /**
     * Result wrapper (future-friendly for adding taps/metrics without breaking signature).
     */
    public static final class Result {
        public final DriveSource source;

        public Result(DriveSource s) {
            this.source = s;
        }
    }

    /**
     * Build a drive graph with just the driver branch (no assists).
     *
     * @param driverRaw raw driver source (robot-centric stick mapping; unshaped).
     * @param opt       graph options (shaping, limits, field-centric, mixer config).
     * @return a single {@link DriveSource} ready to feed your drivetrain each loop.
     */
    public static Result build(DriveSource driverRaw, DriveGraphOptions opt) {
        return build(driverRaw, opt, Collections.<BranchSpec>emptyList());
    }

    /**
     * Build a complete drive graph.
     *
     * @param driverRaw              raw driver source (robot-centric stick mapping; unshaped).
     * @param opt                    graph options (shaping, limits, field-centric, mixer config).
     * @param assistsInPriorityOrder assists to add after the driver (may be null/empty). Order = priority.
     * @return a single {@link DriveSource} ready to feed your drivetrain each loop.
     */
    public static Result build(DriveSource driverRaw,
                               DriveGraphOptions opt,
                               List<BranchSpec> assistsInPriorityOrder) {

        // ---- Driver per-axis shaping via AxisChains (CORE: no final clamp) ----
        Filter<Double> lat = AxisChains.teleopAxisCore(
                opt.dbLat, opt.expoLat, opt.slewLat, opt.precisionScale);

        Filter<Double> ax = AxisChains.teleopAxisCore(
                opt.dbAx, opt.expoAx, opt.slewAx, opt.precisionScale);

        // omega uses precisionScale * omegaFineScale as the live scale
        Filter<Double> om = AxisChains.teleopAxisCore(
                opt.dbOm, opt.expoOm, opt.slewOm,
                () -> opt.precisionScale.getAsDouble() * opt.omegaFineScale.getAsDouble());

        // Merge scalar chains into a DriveSignal filter, then apply to the driver
        Filter<DriveSignal> axes = new DriveAxesFilter(lat, ax, om);
        DriveSource driverShaped = driverRaw.filtered(axes);

        // ---- Field-centric after shaping (rotate translation only; omega passthrough) ----
        Filter<DriveSignal> field = new FieldCentric(opt.headingRad);
        DriveSource driverFinal = driverShaped.transform((in, dt) ->
                opt.fieldCentric.getAsBoolean() ? field.apply(in, dt) : in);

        // ---- Mixer (Arbiter) with uniform scaling to limits ----
        DriveArbiter arb = new DriveArbiter()
                .strategy(opt.mix)
                .normalizeWeights(opt.normalizeWeights)
                .limits(opt.mixLimLat, opt.mixLimAx, opt.mixLimOm)
                .outputLimit(DriveArbiter.OutputLimitPolicy.UNIFORM_SCALE)
                .add(driverFinal, 1.0); // driver first

        // ---- Add assists in priority order (lazy-gated, axis-masked, live-weighted) ----
        if (assistsInPriorityOrder != null && !assistsInPriorityOrder.isEmpty()) {
            for (BranchSpec b : assistsInPriorityOrder) {
                DriveSource src = b.source;

                // Optional per-branch extra filter (before masking)
                if (b.extraFilter != null) {
                    src = src.filtered(b.extraFilter);
                }

                // Axis role → mask via DriveAxesFilter + Scale(0/1)
                Filter<Double> KEEP = new Scale(1.0);
                Filter<Double> ZERO = new Scale(0.0);
                Filter<DriveSignal> roleMask;
                switch (b.role) {
                    case OMEGA_ONLY:
                        roleMask = new DriveAxesFilter(ZERO, ZERO, KEEP);
                        break;
                    case TRANSLATION_ONLY:
                        roleMask = new DriveAxesFilter(KEEP, KEEP, ZERO);
                        break;
                    case FULL:
                    default:
                        roleMask = new DriveAxesFilter(KEEP, KEEP, KEEP);
                        break;
                }
                src = src.filtered(roleMask);

                // Lazy compute gate: don’t evaluate while disabled
                src = new ConditionalSource(src, b.enabled);

                // Add with live weight
                arb.add(src, b.weight);
            }
        }

        // ---- Final sink-guard: per-axis SafeClamp (coerce NaN/Inf + enforce bounds once) ----
        Filter<DriveSignal> finalClamp = new DriveAxesFilter(
                SafeClamp.symmetric(opt.mixLimLat.getAsDouble()),
                SafeClamp.symmetric(opt.mixLimAx.getAsDouble()),
                SafeClamp.symmetric(opt.mixLimOm.getAsDouble())
        );

        // Final source delegates to arbiter, then applies the sink-guard
        DriveSource finalSource = new DriveSource() {
            @Override
            public DriveSignal get(FrameClock clock) {
                DriveSignal mixed = arb.get(clock);            // includes uniform scaling
                return finalClamp.apply(mixed, clock.dtSec()); // sink-guard (finite, bounded)
            }
        };

        return new Result(finalSource);
    }
}
