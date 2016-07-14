/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.util.concurrent.TimeUnit;
import com.google.common.base.Throwables;
import org.apache.log4j.Logger;
import net.juniper.contrail.watchdog.TaskWatchDog;

class VRouterMonitorTask implements Runnable {
    private static Logger s_logger = Logger.getLogger(VRouterMonitorTask.class);

    @Override
    public void run() {

        //check if you are the master from time to time
        //sometimes things don't go as planned
        if (VCenterMonitor.isZookeeperLeader() == false) {
            s_logger.warn("Lost zookeeper leadership. Restarting myself\n");
            System.exit(0);
        }

        checkVroutersConnection();
    }

    private void checkVroutersConnection() {
        TaskWatchDog.startMonitoring(this, "VRouter Keep alive check",
                60000, TimeUnit.MILLISECONDS);

        try {
            // run KeepAlive with vRouter Agent.
            VRouterNotifier.vrouterAgentPeriodicConnectionCheck();
        } catch (Exception e) {
            s_logger.error("Error while vrouterAgentPeriodicConnectionCheck: " + e);
            s_logger.error(Throwables.getStackTraceAsString(e));
        }

        TaskWatchDog.stopMonitoring(this);
    }
}
