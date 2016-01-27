/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.util.concurrent.TimeUnit;
import com.google.common.base.Throwables;
import org.apache.log4j.Logger;
import net.juniper.contrail.watchdog.TaskWatchDog;

class VCenterMonitorTask implements Runnable {
    private static Logger s_logger = Logger.getLogger(VCenterMonitorTask.class);
    private static VCenterDB vcenterDB;
    VCenterNotify eventTask;
    private boolean VcenterDBInitComplete = false;
    
    public VCenterMonitorTask(VCenterNotify eventTask,
            String vcenterUrl, String vcenterUsername,
            String vcenterPassword, String dcName,
            String dvsName, String ipFabricPgName) {
        this.eventTask = eventTask;        
        vcenterDB = new VCenterDB(vcenterUrl, vcenterUsername, vcenterPassword,
                dcName, dvsName, ipFabricPgName, VCenterMonitor.mode);
    }

    private void connect2vcenter() {       
        TaskWatchDog.startMonitoring(this, "Init VCenter", 
                300000, TimeUnit.MILLISECONDS);
        try {
            if (vcenterDB.connect() == true) {
                vcenterDB.setReadTimeout(VCenterDB.VCENTER_READ_TIMEOUT);
                VcenterDBInitComplete = true;
            }
        } catch (Exception e) {
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error("Error while initializing VCenter connection: " + e); 
            s_logger.error(stackTrace); 
            e.printStackTrace();
        }
        TaskWatchDog.stopMonitoring(this);
    }


    @Override
    public void run() {
        
        //check if you are the master from time to time
        //sometimes things dont go as planned
        if (VCenterMonitor.isZookeeperLeader() == false) {
            s_logger.debug("Lost zookeeper leadership. Restarting myself\n");
            System.exit(0);
        }
        
        checkVroutersConnection();
        
        // When syncVirtualNetworks is run the first time, it also does
        // addPort to vrouter agent for existing VMIs.
        // Clear the flag  on first run of syncVirtualNetworks.

        //check aliveness for Vcenter
        if (VcenterDBInitComplete == false) {
            connect2vcenter();
        }
        if (vcenterDB.isAlive() == false) {
            VcenterDBInitComplete = false;
            eventTask.setVCenterNotifyForceRefresh(true);
            VCenterNotify.stopUpdates();
        }
    }

    private void checkVroutersConnection() {
        TaskWatchDog.startMonitoring(this, "VRouter Keep alive check",
                60000, TimeUnit.MILLISECONDS);
        // run KeepAlive with vRouter Agent.

        try {
            VRouterNotifier.vrouterAgentPeriodicConnectionCheck();
        } catch (Exception e) {
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error("Error while vrouterAgentPeriodicConnectionCheck: " + e); 
            s_logger.error(stackTrace); 
            e.printStackTrace();
        }

        TaskWatchDog.stopMonitoring(this);
    }
}
