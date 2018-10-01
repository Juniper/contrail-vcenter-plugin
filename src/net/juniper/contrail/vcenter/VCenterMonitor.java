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
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

import com.google.common.base.Throwables;

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
    private static String _vcenterClusterName = null;
    private static String _vcenterDvsName    = "dvSwitch";
    private static String _vcenterIpFabricPg = "contrail-fab-pg";

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

    static volatile Mode mode  = Mode.VCENTER_ONLY;

    private static volatile MasterSelection zk_ms;
    public static boolean isZookeeperLeader() {
        if (mode.equals(Mode.VCENTER_AS_COMPUTE))
            return true;

        return zk_ms.isLeader();
    }

    public static void zookeeperClose() {
        try {
            zk_ms.close();
        } catch (IOException e) {
            s_logger.error("zookeeper client close failed " + e);
        }
    }

    private static Properties readVcenterPluginConfigFile() {
        final Properties configProps = new Properties();
        File configFile = new File(_configurationFile);
        if (!configFile.isFile()) {
            return configProps;
        }
        try {
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

                String _mode = configProps.getProperty("mode");

                if (_mode != null && _mode.equals("vcenter-as-compute")) {
                    mode = Mode.VCENTER_AS_COMPUTE;
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

                    _vcenterClusterName = configProps.getProperty("vcenter.cluster_name");
                } else { // vcenter-only mode
                    mode = Mode.VCENTER_ONLY;
                }
            } finally {
                fileStream.close();
            }
        } catch (IOException e) {
            s_logger.error("Unable to read " + _configurationFile, e);
        } catch (Exception e) {
            s_logger.error("Exception in readVcenterPluginConfigFile: " + e);
            s_logger.error(Throwables.getStackTraceAsString(e));
        }

        return configProps;
    }

    public static void main(String[] args) throws Exception {

        //Read contrail-vcenter-plugin.conf file
        Properties configProps = readVcenterPluginConfigFile();
        s_logger.info("Config params vcenter url: " + _vcenterURL + ", _vcenterUsername: "
                       + _vcenterUsername + ", api server: " + _apiServerAddress);

        launchWatchDogs();

        VCenterHttpServices.init(configProps);

        // Connect to zookeeper only if VCenterOnly mode of operation.
        // Since in vCenterOnly mode, we can run vcenter-plugin in active-standby mode.
        // For vcenter-as-compute mode, there is only active instance of vcenter-plugin.
        if (mode.equals(Mode.VCENTER_ONLY)) {
            // Zookeeper mastership logic
            zk_ms = new MasterSelection(_zookeeperAddrPort, _zookeeperLatchPath, _zookeeperId);
            s_logger.info("Waiting for zookeeper Mastership .. ");
            zk_ms.waitForLeadership();
            s_logger.info("Acquired zookeeper Mastership .. ");
        }

        _eventMonitor = new VCenterNotify(_vcenterURL, _vcenterUsername, _vcenterPassword,
                _vcenterDcName, _vcenterClusterName, _vcenterDvsName, _vcenterIpFabricPg,
                _apiServerAddress, _apiServerPort, _username, _password,
                _tenant,
                _authtype, _authurl, mode);

        s_logger.info("Starting the notify task.. ");
        _eventMonitor.start();
        s_logger.info("Notify task started.");

        Runtime.getRuntime().addShutdownHook(
                new ExecutorServiceShutdownThread(scheduledTaskExecutor));
        s_logger.info("All aboard.");
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
