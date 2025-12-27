# Drive Guidance

Phoenix `DriveGuidance` is a small “driver assist” API built around two ideas:

1. **Describe what you want** (a *plan*): go to a point, look at a point, or both.
2. **Apply it without rewriting TeleOp** by turning that plan into a `DriveOverlay` and enabling it
   with `DriveSource.overlayWhen(...)`.

It replaces the older `drive.assist` utilities (`TagAim`, `BearingSource`, etc.). The new API:

* uses a single mental model for “go-to” and “aim-at” behaviors
* supports multiple feedback styles (vision-only, localization-only, or adaptive)
* makes the “controlled point” on the robot explicit via `ControlFrames`

---

## Quick start

### 1) Start with a normal TeleOp drive source

```java
DriveSource base = GamepadDriveSource.teleOpMecanum(gamepads);
```

### 2) Build a guidance plan

This plan **aims** at the currently observed tag center (it does not translate).

```java
ObservationSource2d obs = ObservationSources.aprilTag(scoringTarget, cameraMount);

DriveGuidancePlan aimPlan = DriveGuidance.plan()
        .aimTo()
            .lookAtTagPointInches(0.0, 0.0)   // center of the observed tag
            .doneAimTo()
        .feedback()
            .observation(obs)                 // vision-only feedback
            .doneFeedback()
        .build();
```

### 3) Enable the overlay while a button is held

```java
DriveSource drive = DriveGuidance.overlayOn(
        base,
        () -> gamepads.p1().leftBumper().isHeld(),
        aimPlan,
        DriveOverlayMask.OMEGA_ONLY
);
```

That’s it: your driver keeps full stick translation, and the overlay “owns” only omega.

---

## What a plan can do

`DriveGuidancePlan` can control up to two degrees of freedom:

* **Translation**: move a point on the robot to a target point
* **Omega**: rotate the robot so a point on the robot “looks at” a target point

You can use either one independently, or both together.

### Translation target

* `.translateTo().fieldPointInches(x, y)` — a fixed point on the field
* `.translateTo().tagRelativePointInches(tagId, forward, left)` — a point in a tag’s coordinate frame
* `.translateTo().tagRelativePointInches(forward, left)` — a point in the **currently observed** tag’s frame

### Aim target

* `.aimTo().lookAtFieldPointInches(x, y)`
* `.aimTo().lookAtTagPointInches(tagId, forward, left)`
* `.aimTo().lookAtTagPointInches(forward, left)` — **currently observed** tag

---

## ControlFrames

By default, plans control the robot’s center.

If you want to aim using an off-center mechanism (like a shooter), you can set an aim control frame:

```java
ControlFrames frames = ControlFrames.robotCenter()
        .withAimFrame(new Pose2d(6.0, 0.0, 0.0));   // 6" forward of robot center

DriveGuidancePlan plan = DriveGuidance.plan()
        .aimTo().tagCenter(1).doneAimTo()
        .feedback().fieldPose(poseEstimator, tagLayout).doneFeedback()
        .controlFrames(frames)
        .build();
```

Now the robot rotates so that the shooter point is what “faces” the target.

---

## Feedback modes

Guidance needs a way to know where the robot is (or where the target is).

### Observation-only feedback

Use when you only care about **relative** movement and the target is visible.

```java
ObservationSource2d obs = ObservationSources.aprilTag(tagTarget, cameraMount);

DriveGuidancePlan plan = DriveGuidance.plan()
        .aimTo().lookAtTagPointInches(0.0, 0.0).doneAimTo()
        .feedback().observation(obs).doneFeedback()
        .build();
```

Pros:

* simple
* no odometry required
* very accurate up close

Cons:

* if the target drops out of view, guidance can’t “see” anymore

### Field-pose feedback

Use when you have a localization estimate (odometry, fused vision, etc.) and want to aim/drive to a
known field point.

```java
DriveGuidancePlan plan = DriveGuidance.plan()
        .translateTo().fieldPointInches(12, 48).doneTranslateTo()
        .aimTo().lookAtFieldPointInches(0, 0).doneAimTo()
        .feedback().fieldPose(poseEstimator, tagLayout).doneFeedback()
        .build();
```

Pros:

* works even when you can’t see the target
* supports field points and tag-relative points (via `TagLayout`)

Cons:

* depends on localization quality

### Adaptive feedback

If you configure **both** observation and field pose feedback, DriveGuidance becomes adaptive:

* far away: prefer field pose (stable global behavior)
* close: prefer observation (better local accuracy)
* for omega: can prefer observation whenever the target is visible

```java
DriveGuidancePlan plan = DriveGuidance.plan()
        .translateTo().tagRelativePointInches(1, 6, 0).doneTranslateTo()
        .aimTo().tagCenter(1).doneAimTo()
        .feedback()
            .autoSelect()
            .fieldPose(poseEstimator, tagLayout)
            .observation(obs)
            .gates(10.0, 14.0, 0.20)   // enter, exit, blend (inches, inches, seconds)
            .doneFeedback()
        .build();
```

---

## Loss policy

When guidance can’t compute an output (no pose, no target, too old, too low quality), it needs to
decide what to do.

* `PASS_THROUGH` (default): output mask is reduced to the DOFs we can solve; other DOFs remain under
  driver control.
* `ZERO_OUTPUT`: if any requested DOF can’t be solved, output **zeros** for all requested DOFs.

`ZERO_OUTPUT` is useful for “hold still” behaviors.

---

## Pose lock

If you want the robot to “brace” in TeleOp (resist bumps), use `poseLock`.

```java
DriveOverlay lock = DriveGuidance.poseLock(poseEstimator);

DriveSource drive = base.overlayWhen(
        () -> gamepads.p2().x().isHeld(),
        lock,
        DriveOverlayMask.ALL
);
```

Tip: pose lock only works as well as your localization.

---

## Debugging tips

* Start with `feedback().fieldPose(...).doneFeedback()` and verify your pose estimate looks sane.
* Then add `observation(...)` and switch to adaptive.
* Add `debugDump(...)` calls to your telemetry if you want to see which feedback source is active.
