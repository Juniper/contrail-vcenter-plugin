/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import com.google.common.base.Throwables;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import net.juniper.contrail.zklibrary.MasterSelection;

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
    private static String _apiServerAddress  = "10.84.13.23";
    private static int _apiServerPort        = 8082;
    private static String _zookeeperAddrPort  = "127.0.0.1:2181";
    private static String _zookeeperLatchPath  = "/vcenter-plugin";
    private static String _zookeeperId  = "node-vcenter-plugin";
    
    private static VCenterDB     _vcenterDB;
    private static VncDB         _vncDB;
    private static VCenterNotify _eventMonitor;

    private static boolean readVcenterPluginConfigFile() {

        File configFile = new File(_configurationFile);
        FileInputStream fileStream = null;
        try {
            String hostname = null;
            int port = 0;
            if (configFile == null) {
                return false;
            } else {
                final Properties configProps = new Properties();
                fileStream = new FileInputStream(configFile);
                configProps.load(fileStream);

                _vcenterURL = configProps.getProperty("vcenter.url");
                _vcenterUsername = configProps.getProperty("vcenter.username");
                _vcenterPassword = configProps.getProperty("vcenter.password");
                _vcenterDcName = configProps.getProperty("vcenter.datacenter");
                _vcenterDvsName = configProps.getProperty("vcenter.dvswitch");
                _apiServerAddress = configProps.getProperty("api.hostname");
                _zookeeperAddrPort = configProps.getProperty("zookeeper.serverlist");
		String portStr = configProps.getProperty("api.port");
                if (portStr != null && portStr.length() > 0) {
                    _apiServerPort = Integer.parseInt(portStr);
                }
            }
        } catch (IOException ex) {
            s_logger.warn("Unable to read " + _configurationFile, ex);
        } catch (Exception ex) {
            s_logger.error("Exception in readVcenterPluginConfigFile: " + ex);
            ex.printStackTrace();
        } finally {
            //IOUtils.closeQuietly(fileStream);
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

        // Zookeeper mastership logic
        MasterSelection zk_ms = null;
	zk_ms = new MasterSelection(_zookeeperAddrPort, _zookeeperLatchPath, _zookeeperId);
        s_logger.info("Waiting for zookeeper Mastership .. ");
	zk_ms.waitForLeadership();
        s_logger.info("Acquired zookeeper Mastership .. ");

        // Launch the periodic VCenterMonitorTask
        VCenterMonitorTask _monitorTask = new VCenterMonitorTask(_vcenterURL, 
                              _vcenterUsername, _vcenterPassword, 
                              _vcenterDcName, _vcenterDvsName, 
                              _apiServerAddress, _apiServerPort);
       
        scheduledTaskExecutor.scheduleWithFixedDelay(_monitorTask, 0, 2,
                TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(
                new ExecutorServiceShutdownThread(scheduledTaskExecutor));

        //Start event notify thread if VNC & VCenter one time resync is complete.
        s_logger.info("Waiting for one time resync to complete.. ");
        while (_monitorTask.getAddPortSyncAtPluginStart() == true) {
            // wait for sync to complete.
            try {
                Thread.sleep(2);
            } catch (java.lang.InterruptedException e) {
              System.out.println(e);
            }
        }
        s_logger.info("Starting event monitor Task.. ");
        _eventMonitor = new VCenterNotify(_monitorTask);
        _eventMonitor.start();
    }
}
