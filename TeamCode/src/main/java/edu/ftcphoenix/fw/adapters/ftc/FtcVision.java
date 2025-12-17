package edu.ftcphoenix.fw.adapters.ftc;

import android.util.Size;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagGameDatabase;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import edu.ftcphoenix.fw.debug.DebugSink;
import edu.ftcphoenix.fw.geom.Pose3d;
import edu.ftcphoenix.fw.sensing.AprilTagObservation;
import edu.ftcphoenix.fw.sensing.AprilTagSensor;
import edu.ftcphoenix.fw.sensing.CameraMountConfig;

/**
 * FTC-specific vision adapter for AprilTag sensing.
 *
 * <p>This class wires up a {@link VisionPortal} and {@link AprilTagProcessor}
 * (defaulting to the current game's tag library), and exposes a framework-level
 * {@link AprilTagSensor} API so robot code can read tag observations without
 * depending on FTC vision classes.</p>
 *
 * <h2>Frames &amp; conversions</h2>
 *
 * <p>FTC AprilTag detection pose values are reported in the FTC detection pose reference frame
 * (documented in {@link FtcFrames}). Phoenix core code uses the Phoenix framing convention, so
 * this adapter converts FTC poses into Phoenix poses using {@link FtcFrames} before constructing
 * {@link AprilTagObservation}.</p>
 *
 * <h2>Range / bearing</h2>
 *
 * <p>Phoenix does not store redundant scalar bearing/range in {@link AprilTagObservation}.
 * Instead, robot code uses derived helpers:</p>
 *
 * <ul>
 *   <li>{@link AprilTagObservation#cameraBearingRad()}</li>
 *   <li>{@link AprilTagObservation#cameraRangeInches()}</li>
 * </ul>
 *
 * <h2>Camera mount / SDK robotPose</h2>
 *
 * <p>If you provide {@link Config#cameraMount}, this adapter applies the camera extrinsics to the FTC
 * {@link AprilTagProcessor.Builder} via {@link AprilTagProcessor.Builder#setCameraPose(Position, YawPitchRollAngles)}.
 * This enables FTC to compute per-detection robot pose when the SDK has sufficient metadata.</p>
 *
 * <p><b>Important:</b> FTC uses a separate camera-axes convention for {@code setCameraPose} (see
 * {@link FtcFrames} “Localization camera axes”). This adapter converts the Phoenix camera mount pose
 * into that convention before passing it to the SDK.</p>
 */
public final class FtcVision {

    /**
     * Nano-seconds per second, used when converting FTC's
     * {@link AprilTagDetection#frameAcquisitionNanoTime} into seconds.
     */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /**
     * Default camera resolution if none is provided.
     */
    private static final Size DEFAULT_RESOLUTION = new Size(640, 480);

    /**
     * Configuration for {@link #aprilTags(HardwareMap, String, Config)}.
     */
    public static final class Config {

        /**
         * Camera streaming resolution. If {@code null}, defaults to 640x480.
         */
        public Size cameraResolution = DEFAULT_RESOLUTION;

        /**
         * Camera mount extrinsics (robot-frame camera pose).
         *
         * <p>If set, it is converted and applied to the FTC {@link AprilTagProcessor.Builder} via
         * {@link AprilTagProcessor.Builder#setCameraPose(Position, YawPitchRollAngles)} to enable
         * FTC SDK robot-pose estimation when supported.</p>
         */
        public CameraMountConfig cameraMount = null;

        /**
         * Optional pitch offset (radians) applied when converting {@link #cameraMount} into
         * the FTC {@link YawPitchRollAngles} used by {@code setCameraPose}.
         *
         * <p>Default is {@code 0}. Keep this at 0 unless you have a specific FTC sample or
         * measurement that indicates an offset is required for your configuration.</p>
         */
        public double sdkPitchRadOffset = 0.0;

        /**
         * Convenience helper to attach a camera mount without call-site boilerplate.
         *
         * @param mount camera mount (may be {@code null} to clear)
         * @return this config for chaining
         */
        public Config useCameraMount(CameraMountConfig mount) {
            this.cameraMount = mount;
            return this;
        }

        /**
         * Convenience helper to set camera resolution.
         *
         * @param resolution requested resolution (may be {@code null} to use default)
         * @return this config for chaining
         */
        public Config useCameraResolution(Size resolution) {
            this.cameraResolution = resolution;
            return this;
        }
    }

    /**
     * Create a basic AprilTag sensor using a webcam and the official game tag
     * layout from {@link AprilTagGameDatabase#getCurrentGameTagLibrary()}.
     *
     * <p>This overload uses default configuration. See
     * {@link #aprilTags(HardwareMap, String, Config)} for customization.</p>
     *
     * @param hw         robot {@link HardwareMap}
     * @param cameraName hardware configuration name of the webcam
     * @return a ready-to-use {@link AprilTagSensor}
     */
    public static AprilTagSensor aprilTags(HardwareMap hw, String cameraName) {
        return aprilTags(hw, cameraName, new Config());
    }

    /**
     * Create an AprilTag sensor using a webcam and the official game tag layout.
     *
     * <p>The returned {@link AprilTagSensor} instance performs ID filtering
     * and age checks, and converts FTC's pose information into Phoenix framing
     * using {@link FtcFrames}.</p>
     *
     * @param hw         robot {@link HardwareMap}
     * @param cameraName hardware configuration name of the webcam
     * @param cfg        configuration options (must not be {@code null})
     * @return a ready-to-use {@link AprilTagSensor}
     */
    public static AprilTagSensor aprilTags(HardwareMap hw, String cameraName, Config cfg) {
        Objects.requireNonNull(hw, "hardwareMap is required");
        Objects.requireNonNull(cameraName, "cameraName is required");
        Objects.requireNonNull(cfg, "cfg is required");

        WebcamName webcam = hw.get(WebcamName.class, cameraName);

        // Configure the AprilTag processor: current-game library, inches + radians for pose.
        AprilTagProcessor.Builder tagBuilder = new AprilTagProcessor.Builder()
                .setTagLibrary(AprilTagGameDatabase.getCurrentGameTagLibrary())
                .setOutputUnits(DistanceUnit.INCH, AngleUnit.RADIANS);

        // Optional: apply Phoenix camera extrinsics so FTC can compute robotPose.
        if (cfg.cameraMount != null) {
            applyCameraMountToAprilTagProcessor(tagBuilder, cfg.cameraMount, cfg.sdkPitchRadOffset);
        }

        AprilTagProcessor processor = tagBuilder.build();

        // Wire the processor into a VisionPortal using the webcam.
        Size resolution = (cfg.cameraResolution != null) ? cfg.cameraResolution : DEFAULT_RESOLUTION;
        VisionPortal.Builder portalBuilder = new VisionPortal.Builder()
                .setCamera(webcam)
                .addProcessor(processor)
                .setCameraResolution(resolution);

        VisionPortal portal = portalBuilder.build();

        return new PortalAprilTagSensor(portal, processor);
    }

    /**
     * Apply a Phoenix {@link CameraMountConfig} to an FTC {@link AprilTagProcessor.Builder}.
     *
     * <p>This overload uses {@code sdkPitchRadOffset = 0}.</p>
     */
    public static void applyCameraMountToAprilTagProcessor(
            AprilTagProcessor.Builder builder,
            CameraMountConfig mount
    ) {
        applyCameraMountToAprilTagProcessor(builder, mount, 0.0);
    }

    /**
     * Apply a Phoenix {@link CameraMountConfig} to an FTC {@link AprilTagProcessor.Builder}.
     *
     * <p>This converts the mount pose from Phoenix framing into the FTC AprilTag Localization
     * “camera axes” convention (see {@link FtcFrames}) before passing it to
     * {@link AprilTagProcessor.Builder#setCameraPose(Position, YawPitchRollAngles)}.</p>
     *
     * @param builder           FTC AprilTag processor builder (non-null)
     * @param mount             Phoenix camera mount (robot frame) (non-null)
     * @param sdkPitchRadOffset optional pitch offset (radians) to apply after conversion
     */
    public static void applyCameraMountToAprilTagProcessor(
            AprilTagProcessor.Builder builder,
            CameraMountConfig mount,
            double sdkPitchRadOffset
    ) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(mount, "mount");

        // Convert robot->camera pose from Phoenix framing to FTC localization camera axes.
        Pose3d robotToCameraPose = mount.robotToCameraPose();
        Pose3d ftcLocCamPose = FtcFrames.toFtcLocalizationCameraAxesFromPhoenix(robotToCameraPose);

        Position pos = new Position(
                DistanceUnit.INCH,
                ftcLocCamPose.xInches,
                ftcLocCamPose.yInches,
                ftcLocCamPose.zInches,
                0
        );

        YawPitchRollAngles ypr = new YawPitchRollAngles(
                AngleUnit.RADIANS,
                ftcLocCamPose.yawRad,
                ftcLocCamPose.pitchRad + sdkPitchRadOffset,
                ftcLocCamPose.rollRad,
                0
        );

        builder.setCameraPose(pos, ypr);
    }

    /**
     * Internal implementation of {@link AprilTagSensor} backed by a
     * {@link VisionPortal} and {@link AprilTagProcessor}.
     */
    static final class PortalAprilTagSensor implements AprilTagSensor {

        @SuppressWarnings("unused")
        private final VisionPortal portal;   // kept for lifecycle; not directly used yet

        private final AprilTagProcessor processor;

        PortalAprilTagSensor(VisionPortal portal, AprilTagProcessor processor) {
            this.portal = Objects.requireNonNull(portal, "portal");
            this.processor = Objects.requireNonNull(processor, "processor");
        }

        @Override
        public AprilTagObservation bestAny(double maxAgeSec) {
            return selectBest(null, maxAgeSec);
        }

        @Override
        public AprilTagObservation best(Set<Integer> idsOfInterest, double maxAgeSec) {
            Objects.requireNonNull(idsOfInterest, "idsOfInterest");
            if (idsOfInterest.isEmpty()) {
                return AprilTagObservation.noTarget(Double.POSITIVE_INFINITY);
            }
            return selectBest(idsOfInterest, maxAgeSec);
        }

        @Override
        public AprilTagObservation best(int id, double maxAgeSec) {
            return best(Collections.singleton(id), maxAgeSec);
        }

        @Override
        public boolean hasFreshAny(double maxAgeSec) {
            AprilTagObservation obs = bestAny(maxAgeSec);
            return obs.isFresh(maxAgeSec);
        }

        @Override
        public boolean hasFresh(Set<Integer> idsOfInterest, double maxAgeSec) {
            AprilTagObservation obs = best(idsOfInterest, maxAgeSec);
            return obs.isFresh(maxAgeSec);
        }

        @Override
        public boolean hasFresh(int id, double maxAgeSec) {
            AprilTagObservation obs = best(id, maxAgeSec);
            return obs.isFresh(maxAgeSec);
        }

        /**
         * Framework-style debug dump (optional helper; not part of {@link AprilTagSensor}).
         *
         * @param dbg       debug sink (may be {@code null})
         * @param prefix    key prefix (may be {@code null} or empty)
         * @param maxAgeSec freshness window used for the shown "bestAny"
         */
        public void debugDump(DebugSink dbg, String prefix, double maxAgeSec) {
            if (dbg == null) {
                return;
            }
            String p = (prefix == null || prefix.isEmpty()) ? "ftcVision.tags" : prefix;

            List<AprilTagDetection> detections = processor.getDetections();
            int n = (detections == null) ? 0 : detections.size();

            dbg.addLine(p + ": PortalAprilTagSensor");
            dbg.addData(p + ".detections.count", n);
            dbg.addData(p + ".maxAgeSec", maxAgeSec);

            AprilTagObservation obs = bestAny(maxAgeSec);
            dbg.addData(p + ".bestAny.hasTarget", obs.hasTarget);
            dbg.addData(p + ".bestAny.id", obs.id);
            dbg.addData(p + ".bestAny.ageSec", obs.ageSec);
            dbg.addData(p + ".bestAny.cameraBearingRad", obs.cameraBearingRad());
            dbg.addData(p + ".bestAny.cameraRangeInches", obs.cameraRangeInches());
        }

        /**
         * Core selection logic shared by the {@code best*} methods.
         *
         * <p>This method:</p>
         * <ol>
         *   <li>Fetches the current list of detections from the processor.</li>
         *   <li>If there are no detections, returns {@link AprilTagObservation#noTarget(double)}.</li>
         *   <li>Computes the age of each detection based on {@link System#nanoTime()} and
         *       {@link AprilTagDetection#frameAcquisitionNanoTime}.</li>
         *   <li>Rejects any detection older than {@code maxAgeSec}.</li>
         *   <li>Optionally filters by {@code idsOrNull}.</li>
         *   <li>Ignores detections without pose data.</li>
         *   <li>Among remaining detections, chooses the one with the smallest range
         *       (closest tag in 3D line-of-sight distance).</li>
         * </ol>
         *
         * @param idsOrNull set of IDs to accept, or {@code null} for "any"
         * @param maxAgeSec maximum acceptable age (seconds)
         */
        private AprilTagObservation selectBest(Set<Integer> idsOrNull, double maxAgeSec) {
            if (maxAgeSec < 0.0) {
                return AprilTagObservation.noTarget(Double.POSITIVE_INFINITY);
            }

            List<AprilTagDetection> detections = processor.getDetections();
            if (detections == null || detections.isEmpty()) {
                return AprilTagObservation.noTarget(Double.POSITIVE_INFINITY);
            }

            long nowNanos = System.nanoTime();

            AprilTagDetection bestDet = null;
            Pose3d bestCameraToTagPose = null;
            double bestRangeInches = Double.POSITIVE_INFINITY;
            double bestAgeSec = Double.POSITIVE_INFINITY;

            for (AprilTagDetection det : detections) {
                if (det == null) {
                    continue;
                }

                if (idsOrNull != null && !idsOrNull.contains(det.id)) {
                    continue;
                }

                // We need pose values to build cameraToTagPose.
                if (det.ftcPose == null) {
                    continue;
                }

                long frameTime = det.frameAcquisitionNanoTime;
                double ageSec = (frameTime == 0L)
                        ? 0.0
                        : (nowNanos - frameTime) / NANOS_PER_SECOND;

                if (ageSec > maxAgeSec) {
                    continue;
                }

                // Convert FTC detection pose -> Phoenix cameraToTagPose.
                Pose3d ftcCamToTag = new Pose3d(
                        det.ftcPose.x,
                        det.ftcPose.y,
                        det.ftcPose.z,
                        det.ftcPose.yaw,
                        det.ftcPose.pitch,
                        det.ftcPose.roll
                );

                Pose3d cameraToTagPose = FtcFrames.toPhoenixFromFtcDetectionFrame(ftcCamToTag);

                // Choose the closest (3D range). We compute from cameraToTagPose to avoid relying on
                // any additional FTC convenience fields.
                double r = Math.sqrt(
                        cameraToTagPose.xInches * cameraToTagPose.xInches
                                + cameraToTagPose.yInches * cameraToTagPose.yInches
                                + cameraToTagPose.zInches * cameraToTagPose.zInches
                );

                if (r < bestRangeInches) {
                    bestRangeInches = r;
                    bestDet = det;
                    bestAgeSec = ageSec;
                    bestCameraToTagPose = cameraToTagPose;
                }
            }

            if (bestDet == null || bestCameraToTagPose == null) {
                return AprilTagObservation.noTarget(Double.POSITIVE_INFINITY);
            }

            // If the FTC SDK produced a global robot pose (requires a configured camera mount),
            // surface it as an optional fieldToRobotPose measurement.
            //
            // FTC's Field Coordinate System matches Phoenix field framing (+X forward, +Y left, +Z up).
            // (See FTC Docs April Tags Guide Fig. 40.)
            if (bestDet.robotPose != null) {
                Position pos = bestDet.robotPose.getPosition();
                YawPitchRollAngles ypr = bestDet.robotPose.getOrientation();

                Pose3d fieldToRobotPose = new Pose3d(
                        pos.x,
                        pos.y,
                        pos.z,
                        ypr.getYaw(AngleUnit.RADIANS),
                        ypr.getPitch(AngleUnit.RADIANS),
                        ypr.getRoll(AngleUnit.RADIANS)
                );

                return AprilTagObservation.target(bestDet.id, bestCameraToTagPose, fieldToRobotPose, bestAgeSec);
            }

            return AprilTagObservation.target(bestDet.id, bestCameraToTagPose, bestAgeSec);
        }
    }
}
