/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import net.juniper.contrail.sandesh.VCenterHttpProvider;
import net.juniper.contrail.sandesh.VCenterHttpServices;
import net.juniper.contrail.watchdog.TaskWatchDog;
import net.juniper.contrail.zklibrary.MasterSelection;

import com.google.common.base.Throwables;

class ExecutorServiceShutdownThread extends Thread {
    private static final long timeoutValue = 60;
    private static final TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private static Logger s_logger = Logger.getLogger(ExecutorServiceShutdownThread.class);
    private ExecutorService es;
        
    public ExecutorServiceShutdownThread(ExecutorService es) {
        this.es = es;
    }
    
    @Override
    public void run() {
        es.shutdown();
        try {
            if (!es.awaitTermination(timeoutValue, timeoutUnit)) {
                es.shutdownNow();
                if (!es.awaitTermination(timeoutValue, timeoutUnit)) {
                    s_logger.error("ExecutorSevice: " + es + 
                            " did NOT terminate");
                }
            }
        } catch (InterruptedException e) {
            s_logger.error("ExecutorServiceShutdownThread: " + 
                Thread.currentThread() + " ExecutorService: " + e + 
                " interrupted : " + e);
        }
        
    }
}

public class VCenterMonitor {
    private static ScheduledExecutorService scheduledTaskExecutor = 
            Executors.newScheduledThreadPool(1);
    private static Logger s_logger = Logger.getLogger(VCenterMonitor.class);
    private static String _configurationFile = "/etc/contrail/contrail-vcenter-plugin.conf";
    private static String _vcenterURL        = "https://10.84.24.111/sdk";
    private static String _vcenterUsername   = "admin";
    private static String _vcenterPassword   = "Contrail123!";
    private static String _vcenterDcName     = "Datacenter";
    private static String _vcenterDvsName    = "dvSwitch";
    private static String _vcenterIpFabricPg = "contrail-fab-pg";
    
    private static volatile VCenterDB _vcenterDB;
    public static VCenterDB getVcenterDB() {
        return _vcenterDB;
    }
    
    private static volatile VncDB _vncDB;
    
    public static VncDB getVncDB() {
        return _vncDB;
    }
    
    static VCenterNotify _eventMonitor;    
    private static String _apiServerAddress  = "10.84.13.23";
    private static int _apiServerPort        = 8082;
    private static String _zookeeperAddrPort  = "127.0.0.1:2181";
    private static String _zookeeperLatchPath  = "/vcenter-plugin";
    private static String _zookeeperId  = "node-vcenter-plugin";
    
    private static volatile MasterSelection zk_ms;
    public static boolean isZookeeperLeader() {
        return zk_ms.isLeader();
    }
        
    private static boolean readVcenterPluginConfigFile() {

        File configFile = new File(_configurationFile);
        if (!configFile.isFile()) {
            return false;
        }
        try {       
            final Properties configProps = new Properties();
            FileInputStream fileStream = new FileInputStream(configFile);
            try {
                configProps.load(fileStream);

                _vcenterURL = configProps.getProperty("vcenter.url");
                _vcenterUsername = configProps.getProperty("vcenter.username");
                _vcenterPassword = configProps.getProperty("vcenter.password");
                _vcenterDcName = configProps.getProperty("vcenter.datacenter");
                _vcenterDvsName = configProps.getProperty("vcenter.dvswitch");
                _apiServerAddress = configProps.getProperty("api.hostname");
                _zookeeperAddrPort = configProps.getProperty("zookeeper.serverlist");
                _vcenterIpFabricPg = configProps.getProperty("vcenter.ipfabricpg");
                String portStr = configProps.getProperty("api.port");
                if (portStr != null && portStr.length() > 0) {
                    _apiServerPort = Integer.parseInt(portStr);
                }
            } finally {
                fileStream.close();
            }
        } catch (IOException ex) {
            s_logger.warn("Unable to read " + _configurationFile, ex);
            String stackTrace = Throwables.getStackTraceAsString(ex);
            s_logger.error(stackTrace);
        } catch (Exception ex) {
            s_logger.error("Exception in readVcenterPluginConfigFile: " + ex);
            String stackTrace = Throwables.getStackTraceAsString(ex);
            s_logger.error(stackTrace);
        }

        return true;
    }


    
    public static void main(String[] args) throws Exception {

       // log4j logger
        BasicConfigurator.configure();

        //Read contrail-vcenter-plugin.conf file
        readVcenterPluginConfigFile();
        s_logger.info("Config params vcenter url: " + _vcenterURL + ", _vcenterUsername: " 
                       + _vcenterUsername + ", api server: " + _apiServerAddress);

        launchWatchDogs();

        VCenterHttpServices.init();
                
        // Zookeeper mastership logic
        zk_ms = new MasterSelection(_zookeeperAddrPort, _zookeeperLatchPath, _zookeeperId);
        s_logger.info("Waiting for zookeeper Mastership .. ");
        zk_ms.waitForLeadership();
        s_logger.info("Acquired zookeeper Mastership .. ");

        // Launch the periodic VCenterMonitorTask
        VCenterMonitorTask _monitorTask = new VCenterMonitorTask(_vcenterURL, 
                              _vcenterUsername, _vcenterPassword, 
                              _vcenterDcName, _vcenterDvsName,
                              _apiServerAddress, _apiServerPort, _vcenterIpFabricPg);
        _vncDB = _monitorTask.getVncDB();
        _vcenterDB = _monitorTask.getVCenterDB();
        scheduledTaskExecutor.scheduleWithFixedDelay(_monitorTask,
                    0, 4, TimeUnit.SECONDS); //4 second periodic

        Runtime.getRuntime().addShutdownHook(
                new ExecutorServiceShutdownThread(scheduledTaskExecutor));

        //Start event notify thread if VNC & VCenter one time resync is complete.
        s_logger.info("Waiting for one time resync to complete.. ");
        while (_monitorTask.getAddPortSyncAtPluginStart() == true) {
            // wait for sync to complete.
            try {
                Thread.sleep(2000); // 2 sec
            } catch (java.lang.InterruptedException e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
            }
        }

        // Launch Event Monitoring Task
        _eventMonitor = new VCenterNotify(_monitorTask, _vcenterURL,
                                          _vcenterUsername, _vcenterPassword,
                                          _vcenterDcName, _vcenterDvsName);

        // Wait to initialize Vcenter serviceInstance connection for Notify handling
        // before starting Notify thread.
        _eventMonitor.initWithRetry();

        s_logger.info("Starting event monitor Task.. ");
        _eventMonitor.startThread();
    }

    private static void launchWatchDogs() {
        ScheduledExecutorService watchDogExecutor =
                Executors.newScheduledThreadPool(2);

        // launch watch dogs
        for (Runnable aker : TaskWatchDog.values()) {
            watchDogExecutor.scheduleWithFixedDelay(aker, 0, 60,
                    TimeUnit.SECONDS);
        }
    }
}
