package edu.ftcphoenix.fw2.vision.ftc;

import android.util.Size;

import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class TagVision {
    AprilTagProcessor tagProcessor;
    VisionPortal visionPortal;
    Telemetry telemetry;
    HardwareMap hardwareMap;
    public TagVision(HardwareMap hardwareMap, Telemetry telemetry, String strName) {
        tagProcessor = new AprilTagProcessor.Builder()
                .setDrawAxes(true)
                .setDrawCubeProjection(true)
                .setDrawTagID(true)
                .setDrawTagOutline(true)
                .build();

        VisionPortal.Builder b = new VisionPortal.Builder()
                .addProcessor(tagProcessor);
        if (strName != null) {
            b.setCamera(hardwareMap.get(WebcamName.class, strName))
                    .setCameraResolution(new Size(640, 480));
        }
        visionPortal = b.build();

        // this.hardwareMap is referring to the one in the class
        this.hardwareMap = hardwareMap;
        this.telemetry = telemetry;
    }
    public List<Integer> getAprilTagIds() {
        List<AprilTagDetection> tags;
        ArrayList<Integer> ids = new ArrayList<Integer>();
        tags = tagProcessor.getDetections();
        if (!tags.isEmpty()) {
            for (AprilTagDetection d : tags) {
                if (d.metadata != null) {
                    ids.add(d.metadata.id);
                }
            }
        }
        return ids;
    }

    public AprilTagDetection getAprilTagDetection(int id) {
        List<AprilTagDetection> tags;
        tags = tagProcessor.getDetections();
        if (tags.isEmpty()) {
            return null;
        }

        for(AprilTagDetection d : tags) {
            if(d.metadata != null && d.metadata.id == id) {
                return d;
            }
        }

        return null;
    }

    /** Return the detection whose absolute bearing is closest to 0 (i.e., closest to straight ahead). */
    public Optional<AprilTagDetection> getClosestBearingTag() {
        List<AprilTagDetection> ds = tagProcessor.getDetections();
        return ds.stream().min(Comparator.comparingDouble(d -> Math.abs(d.ftcPose.bearing)));
    }

    /** Return the detection with the given id whose bearing is closest to 0. */
    public Optional<AprilTagDetection> getClosestBearingTagWithId(int id) {
        List<AprilTagDetection> ds = tagProcessor.getDetections();
        return ds.stream()
                .filter(d -> d.id == id)
                .min(Comparator.comparingDouble(d -> Math.abs(d.ftcPose.bearing)));
    }

    public void close() {
        visionPortal.close();
    }
}
