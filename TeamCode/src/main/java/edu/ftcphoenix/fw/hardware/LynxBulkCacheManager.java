package edu.ftcphoenix.fw.hardware;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.List;

import edu.ftcphoenix.fw.robotbase.periodicrunner.PeriodicRunnable;
import edu.ftcphoenix.fw.robotbase.periodicrunner.PeriodicRunner;

/**
 * This class will help manage the caching of the Lynx Modules.  Rather than keeping this as a
 * method to set the caching mode, the benefit of having it as a class allows this to register
 * itself with the PeriodicRunner to guarantee that the cleanup happens for MANUAL caching mode.
 */
public class LynxBulkCacheManager implements PeriodicRunnable {
    private final List<LynxModule> allHubs;
    private final LynxModule.BulkCachingMode cachingMode;

    public LynxBulkCacheManager(HardwareMap hardwareMap, LynxModule.BulkCachingMode cachingMode,
                                PeriodicRunner periodicRunner) {

        allHubs = hardwareMap.getAll(LynxModule.class);
        this.cachingMode = cachingMode;

        // Establish manual caching mode.
        for (LynxModule hub : allHubs) {
            hub.setBulkCachingMode(cachingMode);
        }

        // Register this object to be updated every cycle for manual caching mode.
        if (cachingMode == LynxModule.BulkCachingMode.MANUAL)
            periodicRunner.addPeriodicRunnable(this);
    }

    @Override
    public void onPeriodic() {
        // If the manual caching mode is chosen, then clear the cache each loop.
        if (cachingMode == LynxModule.BulkCachingMode.MANUAL) {
            for (LynxModule hub : allHubs) {
                hub.clearBulkCache();
            }
        }
    }

    @Override
    public Priority getPeriodicRunnablePriority() {
        return Priority.PREPARE_TO_READ_INPUT;
    }
}
