package edu.ftcphoenix.fw.drive.guidance;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw.drive.DriveOverlay;
import edu.ftcphoenix.fw.drive.DriveOverlayMask;
import edu.ftcphoenix.fw.drive.DriveSource;
import edu.ftcphoenix.fw.field.TagLayout;
import edu.ftcphoenix.fw.localization.PoseEstimator;
import edu.ftcphoenix.fw.sensing.observation.ObservationSource2d;

/**
 * Builder + helpers for creating {@link DriveGuidancePlan}s.
 *
 * <p>This replaces older tag-specific drive assist helpers (e.g. TagAim) with a single,
 * composable “guidance overlay” abstraction:</p>
 * <ul>
 *   <li>You describe a target (field point or tag-relative point).</li>
 *   <li>You describe what to do (translate, aim, or both).</li>
 *   <li>You pick feedback sources (observation, field pose, or both).</li>
 *   <li>You apply the resulting overlay to a base {@link DriveSource} using
 *       {@link DriveSource#overlayWhen}.</li>
 * </ul>
 */
public final class DriveGuidance {

    private DriveGuidance() {
        // static utility
    }

    /**
     * Start building a {@link DriveGuidancePlan}.
     */
    public static PlanBuilder0 plan() {
        return new Builder0(new State());
    }

    /**
     * Convenience helper to apply a guidance plan as an overlay on top of a base drive source.
     *
     * <p>This is equivalent to:
     * <pre>{@code
     * base.overlayWhen(enabledWhen, plan.overlay(), requestedMask)
     * }</pre>
     */
    public static DriveSource overlayOn(DriveSource base,
                                        BooleanSupplier enabledWhen,
                                        DriveGuidancePlan plan,
                                        DriveOverlayMask requestedMask) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(enabledWhen, "enabledWhen");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(requestedMask, "requestedMask");
        return base.overlayWhen(enabledWhen, plan.overlay(), requestedMask);
    }

    /**
     * Convenience helper that uses {@link DriveGuidancePlan#suggestedMask()}.
     */
    public static DriveSource overlayOn(DriveSource base,
                                        BooleanSupplier enabledWhen,
                                        DriveGuidancePlan plan) {
        return overlayOn(base, enabledWhen, plan, plan.suggestedMask());
    }

    /**
     * Create a pose-lock overlay that holds the current field pose.
     */
    public static DriveOverlay poseLock(PoseEstimator poseEstimator) {
        return poseLock(poseEstimator, DriveGuidancePlan.Tuning.defaults());
    }

    /**
     * Create a pose-lock overlay with custom tuning.
     */
    public static DriveOverlay poseLock(PoseEstimator poseEstimator, DriveGuidancePlan.Tuning tuning) {
        return new PoseLockOverlay(poseEstimator, tuning);
    }

    // ------------------------------------------------------------------------
    // Plan builder staging
    // ------------------------------------------------------------------------

    /**
     * Common methods shared by all plan builder stages.
     *
     * <p><b>Student mental model:</b> you first tell DriveGuidance what you want to do
     * (move and/or aim). Then you wire up feedback (vision and/or odometry). Finally you
     * call {@link #build()} to get an immutable {@link DriveGuidancePlan}.</p>
     *
     * <p>Most plans follow this shape:</p>
     * <pre>{@code
     * DriveGuidancePlan plan = DriveGuidance.plan()
     *     .aimTo()...doneAimTo()
     *     .translateTo()...doneTranslateTo()   // optional
     *     .feedback()...doneFeedback()
     *     .build();
     * }</pre>
     */
    public interface PlanBuilderCommon<SELF> {

        /**
         * Configure how DriveGuidance knows where the robot and/or the target is.
         *
         * <p>You must configure at least one feedback source:</p>
         * <ul>
         *   <li><b>Vision / observations</b> via {@link FeedbackBuilder#observation(ObservationSource2d)}.
         *       This works even with no odometry.</li>
         *   <li><b>Field pose / odometry</b> via {@link FeedbackBuilder#fieldPose(PoseEstimator)}.</li>
         *   <li><b>Both</b> for adaptive “smart” behavior (use vision when available, fall back to odometry).</li>
         * </ul>
         *
         * <p>When you finish configuring feedback, call {@link FeedbackBuilder#doneFeedback()} to return
         * to the main plan builder.</p>
         *
         * <h3>Example: AprilTag vision feedback</h3>
         * <pre>{@code
         * ObservationSource2d obs = ObservationSources.aprilTag(tagTarget, cameraMount);
         *
         * DriveGuidancePlan plan = DriveGuidance.plan()
         *     .aimTo().tagCenter().doneAimTo()
         *     .feedback()
         *         .observation(obs, 0.25, 0.0)   // ignore obs older than 0.25s
         *         .lossPolicy(DriveGuidancePlan.LossPolicy.PASS_THROUGH)
         *         .doneFeedback()
         *     .build();
         * }</pre>
         *
         * @return a nested builder for selecting feedback sources
         */
        FeedbackBuilder<SELF> feedback();

        /**
         * Choose which point(s) on the robot DriveGuidance should control.
         *
         * <p>By default, DriveGuidance controls the <b>robot center</b> for both moving and aiming.
         * In many games you actually care about an off-center mechanism (shooter, intake tip,
         * bucket, etc.).</p>
         *
         * <p>Use {@link ControlFrames#robotCenter()} for the common case.
         * Use {@link ControlFrames#withAimFrame(edu.ftcphoenix.fw.core.geometry.Pose2d)} / {@link ControlFrames#withTranslationFrame(edu.ftcphoenix.fw.core.geometry.Pose2d)}
         * when you need an offset.</p>
         *
         * @param frames robot → controlled-frame transforms
         * @return this builder stage for chaining
         */
        SELF controlFrames(ControlFrames frames);

        /**
         * Adjust how strongly DriveGuidance corrects errors.
         *
         * <p>This is the method students most often tweak when an assist feels “off”.</p>
         *
         * <ul>
         *   <li>If the assist is <b>too weak</b>: increase {@code kPTranslate} and/or {@code kPAim}.</li>
         *   <li>If it is <b>too twitchy</b>: decrease the gains.</li>
         *   <li>If it moves/turns <b>too fast</b>: lower {@code maxTranslateCmd} / {@code maxOmegaCmd}.</li>
         *   <li>If it keeps “hunting” near the target: increase {@code aimDeadbandRad}.</li>
         * </ul>
         *
         * <p><b>Tip:</b> start with {@link DriveGuidancePlan.Tuning#defaults()} and change <em>one</em>
         * value at a time.</p>
         *
         * <h3>Example: make aiming gentler</h3>
         * <pre>{@code
         * DriveGuidancePlan.Tuning t = DriveGuidancePlan.Tuning.defaults()
         *     .withAimKp(1.5);          // lower kPAim = gentler turning
         *
         * DriveGuidancePlan plan = DriveGuidance.plan()
         *     .aimTo().tagCenter().doneAimTo()
         *     .tuning(t)
         *     .feedback()...doneFeedback()
         *     .build();
         * }</pre>
         *
         * @param tuning controller tuning values
         * @return this builder stage for chaining
         */
        SELF tuning(DriveGuidancePlan.Tuning tuning);

        /**
         * Finish the builder and create an immutable {@link DriveGuidancePlan}.
         *
         * <p>After you have a plan, you can apply it as an overlay in TeleOp. For example,
         * to override only turning (omega) while a button is held:</p>
         *
         * <pre>{@code
         * DriveSource drive = DriveGuidance.overlayOn(
         *     baseDrive,
         *     gamepads.p2().leftBumper()::isHeld,
         *     plan,
         *     DriveOverlayMask.OMEGA_ONLY);
         * }</pre>
         *
         * @return an immutable plan (safe to reuse across loops)
         */
        DriveGuidancePlan build();
    }

    /**
     * Initial stage: you may configure translation (move) and/or aim (turn).
     */
    public interface PlanBuilder0 extends PlanBuilderCommon<PlanBuilder0> {

        /**
         * Configure a translation target: “move the robot to a point”.
         *
         * <p>You can call this at most once per plan. After choosing a target, call
         * {@link TranslateToBuilder#doneTranslateTo()} to return to the main plan builder.</p>
         */
        TranslateToBuilder<PlanBuilder1> translateTo();

        /**
         * Configure an aim target: “turn the robot to point toward something”.
         *
         * <p>You can call this at most once per plan. After choosing a target, call
         * {@link AimToBuilder#doneAimTo()} to return to the main plan builder.</p>
         */
        AimToBuilder<PlanBuilder2> aimTo();
    }

    /**
     * Stage after {@code translateTo()} has been configured (you can still add aim).
     */
    public interface PlanBuilder1 extends PlanBuilderCommon<PlanBuilder1> {

        /**
         * Configure an aim target (turn).
         */
        AimToBuilder<PlanBuilder3> aimTo();
    }

    /**
     * Stage after {@code aimTo()} has been configured (you can still add translation).
     */
    public interface PlanBuilder2 extends PlanBuilderCommon<PlanBuilder2> {

        /**
         * Configure a translation target (move).
         */
        TranslateToBuilder<PlanBuilder3> translateTo();
    }

    /**
     * Stage after both translation and aim targets have been configured.
     */
    public interface PlanBuilder3 extends PlanBuilderCommon<PlanBuilder3> {
        // No additional target methods.
    }

    // ------------------------------------------------------------------------
    // Nested builders
    // ------------------------------------------------------------------------

    /**
     * Nested builder for choosing a translation target.
     *
     * <p><b>Units:</b> all distances are in inches.</p>
     */
    public interface TranslateToBuilder<RETURN> {

        /**
         * Move toward a point in <b>field coordinates</b>.
         *
         * <p>Use this when you have a good {@link PoseEstimator} (odometry) and want to drive
         * to a known spot on the field.</p>
         *
         * @param xInches field X (forward) in inches
         * @param yInches field Y (left) in inches
         */
        TranslateToBuilder<RETURN> fieldPointInches(double xInches, double yInches);

        /**
         * Move toward a point that is defined relative to a specific AprilTag.
         *
         * <p>Coordinates are in the tag frame: +X is forward away from the tag, +Y is left.</p>
         *
         * <p>If you use field-pose feedback, you must also provide a {@link TagLayout} so the
         * tag can be located on the field.</p>
         *
         * @param tagId         the fixed tag ID
         * @param forwardInches forward offset from the tag (inches)
         * @param leftInches    left offset from the tag (inches)
         */
        TranslateToBuilder<RETURN> tagRelativePointInches(int tagId, double forwardInches, double leftInches);

        /**
         * Move relative to “whichever tag is currently observed”.
         *
         * <p>This is useful for vision-first TeleOp assist when you don’t care which scoring tag
         * you see — you just want to align to the one in view.</p>
         *
         * @param forwardInches forward offset from the observed tag (inches)
         * @param leftInches    left offset from the observed tag (inches)
         */
        TranslateToBuilder<RETURN> tagRelativePointInches(double forwardInches, double leftInches);

        /**
         * Finish translation configuration and return to the main plan builder.
         */
        RETURN doneTranslateTo();
    }

    /**
     * Nested builder for choosing an aim target.
     *
     * <p><b>Units:</b> all distances are in inches, angles are in radians.</p>
     */
    public interface AimToBuilder<RETURN> {

        /**
         * Turn so the controlled frame “looks at” a point in <b>field coordinates</b>.
         *
         * @param xInches field X (forward) in inches
         * @param yInches field Y (left) in inches
         */
        AimToBuilder<RETURN> lookAtFieldPointInches(double xInches, double yInches);

        /**
         * Turn so the robot “looks at” a point defined in a specific tag's coordinate frame.
         *
         * <p>This is how you do things like “aim 4 inches left of tag 5”.</p>
         */
        AimToBuilder<RETURN> lookAtTagPointInches(int tagId, double forwardInches, double leftInches);

        /**
         * Turn so the robot “looks at” a point in the currently observed tag's frame.
         */
        AimToBuilder<RETURN> lookAtTagPointInches(double forwardInches, double leftInches);

        /**
         * Convenience: aim at the center of a specific tag.
         */
        AimToBuilder<RETURN> tagCenter(int tagId);

        /**
         * Convenience: aim at the center of whichever tag is currently observed.
         */
        AimToBuilder<RETURN> tagCenter();

        /**
         * Finish aim configuration and return to the main plan builder.
         */
        RETURN doneAimTo();
    }

    /**
     * Nested builder for feedback configuration.
     *
     * <p>This is where you choose whether guidance uses vision, odometry, or both.</p>
     */
    public interface FeedbackBuilder<RETURN> {

        /**
         * Use observation-only feedback (vision, object detection, etc.) with default gating.
         *
         * <p>Use this when you do <b>not</b> have reliable odometry, or you want the assist to work
         * purely off what the camera sees.</p>
         *
         * @param observation a source of robot-relative observations (called once per loop)
         */
        FeedbackBuilder<RETURN> observation(ObservationSource2d observation);

        /**
         * Use observation-only feedback with explicit gating.
         *
         * @param observation observation source
         * @param maxAgeSec   ignore observations older than this many seconds (typical: 0.2–0.5)
         * @param minQuality  ignore observations with quality below this (0 accepts all)
         */
        FeedbackBuilder<RETURN> observation(ObservationSource2d observation, double maxAgeSec, double minQuality);

        /**
         * Use field-pose feedback (odometry / localization) with default gating.
         *
         * <p>This is the right choice when you have odometry wheels or a fused pose estimator.</p>
         */
        FeedbackBuilder<RETURN> fieldPose(PoseEstimator poseEstimator);

        /**
         * Use field-pose feedback and provide a {@link TagLayout}.
         *
         * <p>You need a {@link TagLayout} if you want to use any tag-relative targets
         * (because the system must know where the tag is on the field).</p>
         */
        FeedbackBuilder<RETURN> fieldPose(PoseEstimator poseEstimator, TagLayout tagLayout);

        /**
         * Use field-pose feedback with explicit gating.
         */
        FeedbackBuilder<RETURN> fieldPose(PoseEstimator poseEstimator, TagLayout tagLayout, double maxAgeSec, double minQuality);

        /**
         * Set (or replace) the tag layout used for tag-relative targets in field-pose mode.
         */
        FeedbackBuilder<RETURN> tagLayout(TagLayout tagLayout);

        /**
         * Configure adaptive “smart” selection gates.
         *
         * <p>This only matters when <em>both</em> observation and field pose are configured.
         * If you never call this, reasonable defaults are used.</p>
         *
         * @param enterRangeInches switch to observation when observed target range is <= this
         * @param exitRangeInches  switch back to field pose when observed target range is >= this
         * @param takeoverBlendSec blend time when switching sources (0 = instant)
         */
        FeedbackBuilder<RETURN> gates(double enterRangeInches, double exitRangeInches, double takeoverBlendSec);

        /**
         * Prefer observation for turning (omega) whenever it is valid.
         *
         * <p>This is usually what you want for AprilTag aiming: if the tag is visible, use the
         * camera bearing for crisp turning. Translation may still rely on field pose.</p>
         */
        FeedbackBuilder<RETURN> preferObservationForOmegaWhenVisible(boolean prefer);

        /**
         * Set behavior when guidance cannot produce a valid command.
         *
         * <p><b>Recommendation for TeleOp:</b> use {@link DriveGuidancePlan.LossPolicy#PASS_THROUGH}
         * so the driver keeps control if vision/pose drops out.</p>
         */
        FeedbackBuilder<RETURN> lossPolicy(DriveGuidancePlan.LossPolicy lossPolicy);

        /**
         * Optional readability helper.
         *
         * <p>Calling this does not change behavior by itself. Adaptive selection automatically
         * activates whenever both observation and field pose feedback are configured.</p>
         */
        FeedbackBuilder<RETURN> autoSelect();

        /**
         * Finish feedback configuration and return to the main plan builder.
         */
        RETURN doneFeedback();
    }

    // ------------------------------------------------------------------------
    // Implementation
    // ------------------------------------------------------------------------

    private static final class State {
        DriveGuidancePlan.TranslationTarget translationTarget;
        DriveGuidancePlan.AimTarget aimTarget;

        ControlFrames controlFrames = ControlFrames.robotCenter();
        DriveGuidancePlan.Tuning tuning = DriveGuidancePlan.Tuning.defaults();

        // Feedback
        ObservationSource2d observationSource;
        double obsMaxAgeSec = DriveGuidancePlan.Observation.DEFAULT_MAX_AGE_SEC;
        double obsMinQuality = DriveGuidancePlan.Observation.DEFAULT_MIN_QUALITY;

        PoseEstimator poseEstimator;
        TagLayout tagLayout;
        double poseMaxAgeSec = DriveGuidancePlan.FieldPose.DEFAULT_MAX_AGE_SEC;
        double poseMinQuality = DriveGuidancePlan.FieldPose.DEFAULT_MIN_QUALITY;

        DriveGuidancePlan.Gates gates;
        boolean preferObsOmega = true;
        DriveGuidancePlan.LossPolicy lossPolicy = DriveGuidancePlan.LossPolicy.PASS_THROUGH;

        boolean autoSelectIntent = false;
    }

    private static DriveGuidancePlan buildPlan(State s) {
        if (s.translationTarget == null && s.aimTarget == null) {
            throw new IllegalStateException("DriveGuidance plan needs translateTo() and/or aimTo() configured");
        }

        // Build feedback config.
        DriveGuidancePlan.Observation obs = null;
        if (s.observationSource != null) {
            obs = new DriveGuidancePlan.Observation(s.observationSource, s.obsMaxAgeSec, s.obsMinQuality);
        }

        DriveGuidancePlan.FieldPose fp = null;
        if (s.poseEstimator != null) {
            fp = new DriveGuidancePlan.FieldPose(s.poseEstimator, s.tagLayout, s.poseMaxAgeSec, s.poseMinQuality);
        }

        if (obs == null && fp == null) {
            throw new IllegalStateException("DriveGuidance plan needs feedback(): observation(...) and/or fieldPose(...)");
        }

        DriveGuidancePlan.Feedback fb = DriveGuidancePlan.Feedback.create(
                obs,
                fp,
                s.gates,
                s.preferObsOmega,
                s.lossPolicy
        );

        return new DriveGuidancePlan(
                s.translationTarget,
                s.aimTarget,
                s.controlFrames,
                s.tuning,
                fb
        );
    }

    /**
     * Base builder that provides shared methods across plan stages.
     */
    private static abstract class BaseBuilder<SELF> {
        final State s;

        BaseBuilder(State s) {
            this.s = s;
        }

        @SuppressWarnings("unchecked")
        final SELF self() {
            return (SELF) this;
        }

        public final SELF controlFrames(ControlFrames frames) {
            s.controlFrames = Objects.requireNonNull(frames, "frames");
            return self();
        }

        public final SELF tuning(DriveGuidancePlan.Tuning tuning) {
            s.tuning = Objects.requireNonNull(tuning, "tuning");
            return self();
        }

        public final FeedbackBuilder<SELF> feedback() {
            return new FeedbackStep<>(s, self());
        }

        public final DriveGuidancePlan build() {
            return buildPlan(s);
        }
    }

    private static final class Builder0 extends BaseBuilder<PlanBuilder0> implements PlanBuilder0 {
        Builder0(State s) {
            super(s);
        }

        @Override
        public TranslateToBuilder<PlanBuilder1> translateTo() {
            return new TranslateToStep<>(s, new Builder1(s));
        }

        @Override
        public AimToBuilder<PlanBuilder2> aimTo() {
            return new AimToStep<>(s, new Builder2(s));
        }
    }

    private static final class Builder1 extends BaseBuilder<PlanBuilder1> implements PlanBuilder1 {
        Builder1(State s) {
            super(s);
        }

        @Override
        public AimToBuilder<PlanBuilder3> aimTo() {
            return new AimToStep<>(s, new Builder3(s));
        }
    }

    private static final class Builder2 extends BaseBuilder<PlanBuilder2> implements PlanBuilder2 {
        Builder2(State s) {
            super(s);
        }

        @Override
        public TranslateToBuilder<PlanBuilder3> translateTo() {
            return new TranslateToStep<>(s, new Builder3(s));
        }
    }

    private static final class Builder3 extends BaseBuilder<PlanBuilder3> implements PlanBuilder3 {
        Builder3(State s) {
            super(s);
        }
    }

    private static final class TranslateToStep<RETURN> implements TranslateToBuilder<RETURN> {
        private final State s;
        private final RETURN ret;

        TranslateToStep(State s, RETURN ret) {
            this.s = s;
            this.ret = ret;
        }

        @Override
        public TranslateToBuilder<RETURN> fieldPointInches(double xInches, double yInches) {
            s.translationTarget = new DriveGuidancePlan.FieldPoint(xInches, yInches);
            return this;
        }

        @Override
        public TranslateToBuilder<RETURN> tagRelativePointInches(int tagId, double forwardInches, double leftInches) {
            s.translationTarget = new DriveGuidancePlan.TagPoint(tagId, forwardInches, leftInches);
            return this;
        }

        @Override
        public TranslateToBuilder<RETURN> tagRelativePointInches(double forwardInches, double leftInches) {
            return tagRelativePointInches(-1, forwardInches, leftInches);
        }

        @Override
        public RETURN doneTranslateTo() {
            if (s.translationTarget == null) {
                throw new IllegalStateException("translateTo() requires a target before doneTranslateTo()");
            }
            return ret;
        }
    }

    private static final class AimToStep<RETURN> implements AimToBuilder<RETURN> {
        private final State s;
        private final RETURN ret;

        AimToStep(State s, RETURN ret) {
            this.s = s;
            this.ret = ret;
        }

        @Override
        public AimToBuilder<RETURN> lookAtFieldPointInches(double xInches, double yInches) {
            s.aimTarget = new DriveGuidancePlan.FieldPoint(xInches, yInches);
            return this;
        }

        @Override
        public AimToBuilder<RETURN> lookAtTagPointInches(int tagId, double forwardInches, double leftInches) {
            s.aimTarget = new DriveGuidancePlan.TagPoint(tagId, forwardInches, leftInches);
            return this;
        }

        @Override
        public AimToBuilder<RETURN> lookAtTagPointInches(double forwardInches, double leftInches) {
            return lookAtTagPointInches(-1, forwardInches, leftInches);
        }

        @Override
        public AimToBuilder<RETURN> tagCenter(int tagId) {
            return lookAtTagPointInches(tagId, 0.0, 0.0);
        }

        @Override
        public AimToBuilder<RETURN> tagCenter() {
            return lookAtTagPointInches(-1, 0.0, 0.0);
        }

        @Override
        public RETURN doneAimTo() {
            if (s.aimTarget == null) {
                throw new IllegalStateException("aimTo() requires a target before doneAimTo()");
            }
            return ret;
        }
    }

    private static final class FeedbackStep<RETURN> implements FeedbackBuilder<RETURN> {
        private final State s;
        private final RETURN ret;

        FeedbackStep(State s, RETURN ret) {
            this.s = s;
            this.ret = ret;
        }

        @Override
        public FeedbackBuilder<RETURN> observation(ObservationSource2d observation) {
            s.observationSource = Objects.requireNonNull(observation, "observation");
            s.obsMaxAgeSec = DriveGuidancePlan.Observation.DEFAULT_MAX_AGE_SEC;
            s.obsMinQuality = DriveGuidancePlan.Observation.DEFAULT_MIN_QUALITY;
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> observation(ObservationSource2d observation, double maxAgeSec, double minQuality) {
            s.observationSource = Objects.requireNonNull(observation, "observation");
            s.obsMaxAgeSec = maxAgeSec;
            s.obsMinQuality = minQuality;
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> fieldPose(PoseEstimator poseEstimator) {
            s.poseEstimator = Objects.requireNonNull(poseEstimator, "poseEstimator");
            s.poseMaxAgeSec = DriveGuidancePlan.FieldPose.DEFAULT_MAX_AGE_SEC;
            s.poseMinQuality = DriveGuidancePlan.FieldPose.DEFAULT_MIN_QUALITY;
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> fieldPose(PoseEstimator poseEstimator, TagLayout tagLayout) {
            s.poseEstimator = Objects.requireNonNull(poseEstimator, "poseEstimator");
            s.tagLayout = tagLayout;
            s.poseMaxAgeSec = DriveGuidancePlan.FieldPose.DEFAULT_MAX_AGE_SEC;
            s.poseMinQuality = DriveGuidancePlan.FieldPose.DEFAULT_MIN_QUALITY;
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> fieldPose(PoseEstimator poseEstimator,
                                                 TagLayout tagLayout,
                                                 double maxAgeSec,
                                                 double minQuality) {
            s.poseEstimator = Objects.requireNonNull(poseEstimator, "poseEstimator");
            s.tagLayout = tagLayout;
            s.poseMaxAgeSec = maxAgeSec;
            s.poseMinQuality = minQuality;
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> tagLayout(TagLayout tagLayout) {
            s.tagLayout = tagLayout;
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> gates(double enterRangeInches, double exitRangeInches, double takeoverBlendSec) {
            s.gates = new DriveGuidancePlan.Gates(enterRangeInches, exitRangeInches, takeoverBlendSec);
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> preferObservationForOmegaWhenVisible(boolean prefer) {
            s.preferObsOmega = prefer;
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> lossPolicy(DriveGuidancePlan.LossPolicy lossPolicy) {
            s.lossPolicy = Objects.requireNonNull(lossPolicy, "lossPolicy");
            return this;
        }

        @Override
        public FeedbackBuilder<RETURN> autoSelect() {
            s.autoSelectIntent = true;
            return this;
        }

        @Override
        public RETURN doneFeedback() {
            return ret;
        }
    }
}
