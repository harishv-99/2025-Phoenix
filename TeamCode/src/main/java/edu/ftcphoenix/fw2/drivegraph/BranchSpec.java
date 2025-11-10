package edu.ftcphoenix.fw2.drivegraph;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;
import edu.ftcphoenix.fw2.filters.Filter;

/**
 * BranchSpec — declarative description of an "assist" branch in the drive graph.
 *
 * <h3>What is a branch?</h3>
 * A branch is any {@link DriveSource} other than the human driver that contributes to the final
 * drive command (e.g., AprilTag aiming, snap-to-angle, path follower, "pull" to a point).
 *
 * <h3>What does BranchSpec configure?</h3>
 * <ul>
 *   <li><b>source</b> — the producing {@link DriveSource} (compute unit).</li>
 *   <li><b>enabled</b> — a BooleanSupplier gate. When false, the graph will <i>not</i> evaluate
 *       the inner source (saves CPU, prevents stale outputs).</li>
 *   <li><b>weight</b> — live gain sent to the mixer. For {@code WEIGHTED_SUM} you’ll typically normalize;
 *       for {@code PRIORITY_SOFT_SAT} this is a “how strong is this intent” knob.</li>
 *   <li><b>role</b> — which axes the branch is allowed to touch (FULL / OMEGA_ONLY / TRANSLATION_ONLY).
 *       The graph enforces this via an axis mask; your branch doesn’t need to zero axes itself.</li>
 *   <li><b>extraFilter</b> — optional per-branch {@link Filter} for {@link DriveSignal} (e.g., a dedicated slew,
 *       clamp, or low-pass for just this branch), applied <i>before</i> axis masking.</li>
 * </ul>
 *
 * <h3>When should I use BranchSpec?</h3>
 * <ul>
 *   <li>Whenever you add an autonomous "assist" alongside manual driving.</li>
 *   <li>When you want to gate an expensive computation (vision, path follower) until a button is held.</li>
 *   <li>When you want to constrain a branch to specific axes (e.g., omega-only).</li>
 *   <li>When you want per-branch shaping (e.g., smoother turn rate from TagAim than driver stick).</li>
 * </ul>
 *
 * <h3>Best practices</h3>
 * <ul>
 *   <li>Choose <b>PRIORITY_SOFT_SAT</b> in the {@code DriveArbiter} for "driver first; assists fill what's left".</li>
 *   <li>Make branches <b>axis-focused</b>: omega-only for aiming, translation-only for snap-to-point.</li>
 *   <li>Gate compute with {@code enabled} so disabled branches don't waste cycles or emit stale values.</li>
 *   <li>If two branches conflict (e.g., two omega controllers), resolve mutual exclusivity in your TeleOp
 *       (only one enabled at a time) or use priority order + weights to bias the mixer.</li>
 * </ul>
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // 1) An AprilTag aim (omega-only) enabled while LB is held
 * BranchSpec tagAim = BranchSpec.of(
 *     "TagAim",
 *     tagAimSource,                // produces omega from bearing error
 *     () -> gp1.left_bumper,       // enabled while held
 *     () -> 1.0,                   // full strength
 *     AxisRole.OMEGA_ONLY          // only rotate, no translation
 * );
 *
 * // 2) Snap to 0/90/180/270 (omega-only), stronger than tag aim
 * BranchSpec snapHeading = BranchSpec.of(
 *     "SnapHeading",
 *     snapAngleSource,
 *     () -> gp1.right_bumper,
 *     () -> 1.0,                   // weight; mixer will prioritize by order for PRIORITY_SOFT_SAT
 *     AxisRole.OMEGA_ONLY
 * );
 *
 * // 3) Snap to point (translation-only) with modest weight
 * BranchSpec snapPoint = BranchSpec.of(
 *     "SnapPoint",
 *     snapPointSource,             // produces vx, vy toward target
 *     () -> gp1.a,
 *     () -> 0.6,                   // gentler "pull"
 *     AxisRole.TRANSLATION_ONLY
 * );
 * }</pre>
 *
 * <p>These specs are passed to {@code DriveGraph.build(driver, options, Arrays.asList(...))} in priority order
 * after the driver. The graph will gate, (optionally) filter, mask axes, and add them to the {@code DriveArbiter}.</p>
 */
public final class BranchSpec {
    /**
     * Human-readable name for telemetry/debug.
     */
    public final String name;

    /**
     * The producing source (compute unit).
     */
    public final DriveSource source;

    /**
     * Compute gate: when false, the graph does NOT evaluate {@link #source} and the branch contributes zero.
     * Prefer a fast predicate (e.g., button/switch). This keeps vision/followers cold when not needed.
     */
    public final BooleanSupplier enabled;

    /**
     * Live weight sent to the mixer. For {@code WEIGHTED_SUM}, turn on normalization in the arbiter.
     * For {@code PRIORITY_SOFT_SAT}, this is a strength knob per branch when capacity remains.
     */
    public final DoubleSupplier weight;

    /**
     * Which axes this branch may affect. The graph applies an axis mask so the branch doesn't have to.
     */
    public final AxisRole role;

    /**
     * Optional branch-local filter (DriveSignal → DriveSignal), applied before axis mask.
     * Use for per-branch smoothing or rate limits. May be null.
     */
    public final Filter<DriveSignal> extraFilter;

    private BranchSpec(String name,
                       DriveSource source,
                       BooleanSupplier enabled,
                       DoubleSupplier weight,
                       AxisRole role,
                       Filter<DriveSignal> extraFilter) {
        this.name = name;
        this.source = source;
        this.enabled = enabled;
        this.weight = weight;
        this.role = role;
        this.extraFilter = extraFilter;
    }

    /**
     * Builder: no extra per-branch filter.
     */
    public static BranchSpec of(String name,
                                DriveSource src,
                                BooleanSupplier enabled,
                                DoubleSupplier weight,
                                AxisRole role) {
        return new BranchSpec(name, src, enabled, weight, role, null);
    }

    /**
     * Optional: attach a per-branch DriveSignal filter (e.g., branch-specific slew/clamp).
     */
    public BranchSpec withFilter(Filter<DriveSignal> f) {
        return new BranchSpec(name, source, enabled, weight, role, f);
    }
}
