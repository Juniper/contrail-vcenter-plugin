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
    private static String _username          = "admin";
    private static String _password          = "contrail123";
    private static String _tenant            = "admin";
    private static String _authtype          = "keystone";
    private static String _authurl           = "http://10.84.24.54:35357/v2.0";

    private static String _zookeeperAddrPort  = "127.0.0.1:2181";
    private static String _zookeeperLatchPath = "/vcenter-plugin";
    private static String _zookeeperId        = "node-vcenter-plugin";

    private static String _mode  = "vcenter-only";
    
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

                String ipFabricPg = configProps.getProperty("vcenter.ipfabricpg");
                if (ipFabricPg != null)
                    _vcenterIpFabricPg = ipFabricPg;

                _vcenterURL = configProps.getProperty("vcenter.url");
                _vcenterUsername = configProps.getProperty("vcenter.username");
                _vcenterPassword = configProps.getProperty("vcenter.password");
                _vcenterDcName = configProps.getProperty("vcenter.datacenter");
                _vcenterDvsName = configProps.getProperty("vcenter.dvswitch");

                _zookeeperAddrPort = configProps.getProperty("zookeeper.serverlist");
                _vcenterIpFabricPg = configProps.getProperty("vcenter.ipfabricpg");

                _apiServerAddress = configProps.getProperty("api.hostname");
                String portStr = configProps.getProperty("api.port");
                if (portStr != null && portStr.length() > 0) {
                    _apiServerPort = Integer.parseInt(portStr);
                }

                _mode = configProps.getProperty("mode");
                if (_mode == null)
                    _mode = "vcenter-only";

                if (_mode.equals("vcenter-as-compute")) {
                    String authurl  = configProps.getProperty("auth_url");
                    if (authurl != null && authurl.length() > 0)
                        _authurl = authurl;

                    String username = configProps.getProperty("admin_user");
                    if (username != null && username.length() > 0)
                        _username = username;

                    String password = configProps.getProperty("admin_password");
                    if (password != null && password.length() > 0)
                        _password = password;

                    String tenant   = configProps.getProperty("admin_tenant_name");
                    if (tenant != null && tenant.length() > 0)
                        _tenant = tenant;

                }
            } finally {
                fileStream.close();
            }
        } catch (IOException ex) {
            s_logger.warn("Unable to read " + _configurationFile, ex);
        } catch (Exception ex) {
            s_logger.error("Exception in readVcenterPluginConfigFile: " + ex);
            ex.printStackTrace();
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
        VCenterMonitorTask _monitorTask = null;
        if (_mode == "vcenter-only") {
            s_logger.info("vcenter-only mode of operation.. ");
            _monitorTask = new VCenterOnlyMonitorTask(_vcenterURL, 
                                  _vcenterUsername, _vcenterPassword, 
                                  _vcenterDcName, _vcenterDvsName,
                                  _apiServerAddress, _apiServerPort, _vcenterIpFabricPg);
        } else {
            s_logger.info("vcenter-as-compute mode of operation.. ");
            _monitorTask = new VCenterAsComputeMonitorTask(_vcenterURL, 
                                  _vcenterUsername, _vcenterPassword, 
                                  _vcenterDcName, _vcenterDvsName,
                                  _vcenterIpFabricPg,
                                  _apiServerAddress, _apiServerPort,
                                  _username, _password, _tenant,
                                  _authtype, _authurl);
        }

        _vncDB = _monitorTask.getVncDB();
        _vcenterDB = _monitorTask.getVCenterDB();

        scheduledTaskExecutor.scheduleWithFixedDelay(_monitorTask, 0, 4, //4 second periodic
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
        _eventMonitor = new VCenterNotify(_monitorTask, _vcenterURL,
                                          _vcenterUsername, _vcenterPassword,
                                          _vcenterDcName);
        _eventMonitor.start();
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
