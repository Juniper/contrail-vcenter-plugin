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

import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;

import net.juniper.contrail.zklibrary.MasterSelection;


class VCenterMonitorTask implements Runnable {
    private static Logger s_logger = Logger.getLogger(VCenterMonitorTask.class);
    private VCenterDB vcenterDB;
    private VncDB vncDB;
    private boolean AddPortSyncAtPluginStart = true;
    private boolean VncDBInitCompelete = false;
    private boolean VcenterDBInitCompelete = false;
    private static short iteration = 0;
    
    public VCenterMonitorTask(String vcenterUrl, String vcenterUsername,
                              String vcenterPassword, String vcenterDcName,
                              String vcenterDvsName, String apiServerAddress, 
                              int apiServerPort) throws Exception {
        vcenterDB = new VCenterDB(vcenterUrl, vcenterUsername, vcenterPassword,
                                  vcenterDcName, vcenterDvsName);
        vncDB     = new VncDB(apiServerAddress, apiServerPort);

        // Initialize the databases
        if (vncDB.Initialize() == true) {
            VncDBInitCompelete = true;
        }
        if (vcenterDB.Initialize() == true) {
            VcenterDBInitCompelete = true;
        }
    }

    public void setAddPortSyncAtPluginStart(boolean _AddPortSyncAtPluginStart)
    {
        AddPortSyncAtPluginStart = _AddPortSyncAtPluginStart;
    }

    public boolean getAddPortSyncAtPluginStart()
    {
        return AddPortSyncAtPluginStart;
    }

    private void syncVirtualMachines(String vnUuid, 
            VmwareVirtualNetworkInfo vmwareNetworkInfo,
            VncVirtualNetworkInfo vncNetworkInfo) throws IOException {
        String vncVnName = vncNetworkInfo.getName();
        String vmwareVnName = vmwareNetworkInfo.getName();
        s_logger.info("Syncing virtual machines in network " + vncVnName + " (" + vnUuid + ")"
                + " across Vnc and VCenter DBs");
        SortedMap<String, VmwareVirtualMachineInfo> vmwareVmInfos =
                vmwareNetworkInfo.getVmInfo();
        SortedMap<String, VncVirtualMachineInfo> vncVmInfos =
                vncNetworkInfo.getVmInfo();
        Iterator<Entry<String, VmwareVirtualMachineInfo>> vmwareIter = 
                (vmwareVmInfos != null ? vmwareVmInfos.entrySet().iterator() : null);
        Iterator<Entry<String, VncVirtualMachineInfo>> vncIter =
                (vncVmInfos != null ? vncVmInfos.entrySet().iterator() : null);
        s_logger.info("VMs: Vmware size: " + ((vmwareVmInfos != null) ? vmwareVmInfos.size():0) 
                      + ", Vnc size: " + ((vncVmInfos != null) ? vncVmInfos.size():0));
        Map.Entry<String, VmwareVirtualMachineInfo> vmwareItem = null;
        if (vmwareIter != null && vmwareIter.hasNext()) {
                vmwareItem = (Entry<String, VmwareVirtualMachineInfo>)vmwareIter.next();
        } 
        Map.Entry<String, VncVirtualMachineInfo> vncItem = null;
        if (vncIter != null && vncIter.hasNext()) {
                vncItem = (Entry<String, VncVirtualMachineInfo>)vncIter.next();
        }
        
        while (vmwareItem != null && vncItem != null) {
            // Do Vmware and Vnc virtual machines match?
            String vmwareVmUuid = vmwareItem.getKey();
            String vncVmUuid = vncItem.getKey();
            Integer cmp = vmwareVmUuid.compareTo(vncVmUuid);

            if (cmp == 0) {
                // Match found, advance Vmware and Vnc iters
                if (AddPortSyncAtPluginStart == true) {
                    VmwareVirtualMachineInfo vmwareVmInfo = vmwareItem.getValue();
                    vncDB.syncAddPortPerVirtualMachineInterface(
                            vnUuid, vmwareVmUuid,
                            vmwareVmInfo.getMacAddress(),
                            vmwareVmInfo.getName(),
                            vmwareVmInfo.getVrouterIpAddress(),
                            vmwareVmInfo.getHostName(),
                            vmwareNetworkInfo.getIsolatedVlanId(),
                            vmwareNetworkInfo.getPrimaryVlanId());
                }
                vncItem = vncIter.hasNext() ? vncIter.next() : null;
                vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
            } else if (cmp > 0){
                // Delete Vnc virtual machine
                vncDB.DeleteVirtualMachine(vncItem.getValue());
                vncItem = vncIter.hasNext() ? vncIter.next() : null;
            } else if (cmp < 0){
                // create VMWare virtual machine in VNC
                VmwareVirtualMachineInfo vmwareVmInfo = vmwareItem.getValue();
                vncDB.CreateVirtualMachine(vnUuid, vmwareVmUuid,
                        vmwareVmInfo.getMacAddress(),
                        vmwareVmInfo.getName(),
                        vmwareVmInfo.getVrouterIpAddress(),
                        vmwareVmInfo.getHostName(),
                        vmwareNetworkInfo.getIsolatedVlanId(),
                        vmwareNetworkInfo.getPrimaryVlanId());
                vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
            }
        }       
        while (vmwareItem != null) {
            // Create
            String vmwareVmUuid = vmwareItem.getKey();
            VmwareVirtualMachineInfo vmwareVmInfo = vmwareItem.getValue();
            vncDB.CreateVirtualMachine(vnUuid, vmwareVmUuid,
                    vmwareVmInfo.getMacAddress(),
                    vmwareVmInfo.getName(),
                    vmwareVmInfo.getVrouterIpAddress(),
                    vmwareVmInfo.getHostName(), 
                    vmwareNetworkInfo.getIsolatedVlanId(),
                    vmwareNetworkInfo.getPrimaryVlanId());
            vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
        }
        while (vncItem != null) {
            // Delete
            vncDB.DeleteVirtualMachine(vncItem.getValue());
            vncItem = vncIter.hasNext() ? vncIter.next() : null;
        }
    }
    
    public void syncVirtualNetworks() throws Exception {
        s_logger.info("Syncing Vnc and VCenter DBs");
        SortedMap<String, VmwareVirtualNetworkInfo> vmwareVirtualNetworkInfos =
                vcenterDB.populateVirtualNetworkInfo();
        SortedMap<String, VncVirtualNetworkInfo> vncVirtualNetworkInfos =
                vncDB.populateVirtualNetworkInfo();
        s_logger.debug("VNs vmware size: "
                + ((vmwareVirtualNetworkInfos != null) ? vmwareVirtualNetworkInfos.size() : 0)
                + ", vnc size: "
                + ((vncVirtualNetworkInfos != null) ? vncVirtualNetworkInfos.size() : 0) );

        Iterator<Entry<String, VmwareVirtualNetworkInfo>> vmwareIter = null;
        Map.Entry<String, VmwareVirtualNetworkInfo> vmwareItem = null;
        if (vmwareVirtualNetworkInfos != null && vmwareVirtualNetworkInfos.size() > 0 && vmwareVirtualNetworkInfos.entrySet() != null) {
            vmwareIter = vmwareVirtualNetworkInfos.entrySet().iterator();
            if (vmwareIter != null) { 
                vmwareItem = (Entry<String, VmwareVirtualNetworkInfo>) 
                (vmwareIter.hasNext() ? vmwareIter.next() : null);
            }
        }

        Iterator<Entry<String, VncVirtualNetworkInfo>> vncIter = null;
        Map.Entry<String, VncVirtualNetworkInfo> vncItem = null;
        if (vncVirtualNetworkInfos != null && vncVirtualNetworkInfos.size() > 0 && vncVirtualNetworkInfos.entrySet() != null) {
                vncIter = vncVirtualNetworkInfos.entrySet().iterator();
            if (vncIter != null) { 
                vncItem = (Entry<String, VncVirtualNetworkInfo>) 
                (vncIter.hasNext() ? vncIter.next() : null);
            }
        }

        while (vmwareItem != null && vncItem != null) {
            // Do Vmware and Vnc networks match?
            String vmwareVnUuid = vmwareItem.getKey();
            String vncVnUuid = vncItem.getKey();
            Integer cmp = vmwareVnUuid.compareTo(vncVnUuid);
            if (cmp == 0) {
                // Sync
                syncVirtualMachines(vncVnUuid, vmwareItem.getValue(),
                        vncItem.getValue());
                // Advance
                vncItem = vncIter.hasNext() ? vncIter.next() : null;
                vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
            } else if (cmp > 0){
                // Delete Vnc virtual network
                vncDB.DeleteVirtualNetwork(vncVnUuid);
                vncItem = vncIter.hasNext() ? vncIter.next() : null;
            } else if (cmp < 0){
                // Create VMWare virtual network in VNC
                VmwareVirtualNetworkInfo vnInfo = vmwareItem.getValue();
                SortedMap<String, VmwareVirtualMachineInfo> vmInfos = vnInfo.getVmInfo();
                String subnetAddr = vnInfo.getSubnetAddress();
                String subnetMask = vnInfo.getSubnetMask();
                String gatewayAddr = vnInfo.getGatewayAddress();
                String vmwareVnName = vnInfo.getName();
                short isolatedVlanId = vnInfo.getIsolatedVlanId();
                short primaryVlanId = vnInfo.getPrimaryVlanId();
                vncDB.CreateVirtualNetwork(vmwareVnUuid, vmwareVnName, subnetAddr,
                        subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId, vmInfos);
                vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
            }
        }
        while (vmwareItem != null) {
            // Create
            String vmwareVnUuid = vmwareItem.getKey();
            VmwareVirtualNetworkInfo vnInfo = vmwareItem.getValue();
            SortedMap<String, VmwareVirtualMachineInfo> vmInfos = vnInfo.getVmInfo();
            String subnetAddr = vnInfo.getSubnetAddress();
            String subnetMask = vnInfo.getSubnetMask();
            String gatewayAddr = vnInfo.getGatewayAddress();
            String vmwareVnName = vnInfo.getName();
            short isolatedVlanId = vnInfo.getIsolatedVlanId();
            short primaryVlanId = vnInfo.getPrimaryVlanId();
            vncDB.CreateVirtualNetwork(vmwareVnUuid, vmwareVnName, subnetAddr,
                    subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId, vmInfos);
            vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
        }
        while (vncItem != null) {
            // Delete
            vncDB.DeleteVirtualNetwork(vncItem.getKey());
            vncItem = vncIter.hasNext() ? vncIter.next() : null;
        }
        vcenterDB.setPrevVmwareVNInfos(vmwareVirtualNetworkInfos);
        s_logger.info("Syncing Vnc and VCenter DBs : Done");
    }

    private void syncVmwareVirtualMachines(String vnUuid,
            VmwareVirtualNetworkInfo curVmwareVNInfo,
            VmwareVirtualNetworkInfo prevVmwareVNInfo) throws IOException {
        String prevVmwareVnName = prevVmwareVNInfo.getName();
        String curVmwareVnName = curVmwareVNInfo.getName();
        s_logger.debug("Syncing virtual machines in network: " + vnUuid + 
                " across vCenter DBs (" + prevVmwareVnName + ") ");
        SortedMap<String, VmwareVirtualMachineInfo> curVmwareVmInfos =
                curVmwareVNInfo.getVmInfo();
        SortedMap<String, VmwareVirtualMachineInfo> prevVmwareVmInfos =
                prevVmwareVNInfo.getVmInfo();
        Iterator<Entry<String, VmwareVirtualMachineInfo>> curVmwareIter = 
                (curVmwareVmInfos != null ? curVmwareVmInfos.entrySet().iterator() : null);
        Iterator<Entry<String, VmwareVirtualMachineInfo>> prevVmwareIter =
                (prevVmwareVmInfos != null ? prevVmwareVmInfos.entrySet().iterator() : null);
        s_logger.debug("VMs: curVmware size: " + ((curVmwareVmInfos != null) ? curVmwareVmInfos.size():0) 
                      + ", prevVmware size: " + ((prevVmwareVmInfos != null) ? prevVmwareVmInfos.size():0));

        Map.Entry<String, VmwareVirtualMachineInfo> curVmwareItem = null;
        if (curVmwareIter != null && curVmwareIter.hasNext()) {
                curVmwareItem = (Entry<String, VmwareVirtualMachineInfo>)curVmwareIter.next();
        } 

        Map.Entry<String, VmwareVirtualMachineInfo> prevVmwareItem = null;
        if (prevVmwareIter != null && prevVmwareIter.hasNext()) {
                prevVmwareItem = (Entry<String, VmwareVirtualMachineInfo>)prevVmwareIter.next();
        }
        
        while (curVmwareItem != null && prevVmwareItem != null) {
            // Do Vmware and Vnc virtual machines match?
            String curVmwareVmUuid = curVmwareItem.getKey();
            String prevVmwareVmUuid = prevVmwareItem.getKey();
            Integer cmp = curVmwareVmUuid.compareTo(prevVmwareVmUuid);

            if (cmp == 0) {
                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
                curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
            } else if (cmp > 0){
                // Delete Vnc virtual machine
                vncDB.DeleteVirtualMachine(prevVmwareItem.getKey(), vnUuid);
                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
            } else if (cmp < 0){
                // create VMWare virtual machine in VNC
                VmwareVirtualMachineInfo curVmwareVmInfo = curVmwareItem.getValue();
                vncDB.CreateVirtualMachine(vnUuid, curVmwareVmUuid,
                        curVmwareVmInfo.getMacAddress(),
                        curVmwareVmInfo.getName(),
                        curVmwareVmInfo.getVrouterIpAddress(),
                        curVmwareVmInfo.getHostName(),
                        curVmwareVNInfo.getIsolatedVlanId(),
                        curVmwareVNInfo.getPrimaryVlanId());
                curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
            }
        }       
        while (curVmwareItem != null) {
            // Create
            String curVmwareVmUuid = curVmwareItem.getKey();
            VmwareVirtualMachineInfo curVmwareVmInfo = curVmwareItem.getValue();
            vncDB.CreateVirtualMachine(vnUuid, curVmwareVmUuid,
                    curVmwareVmInfo.getMacAddress(),
                    curVmwareVmInfo.getName(),
                    curVmwareVmInfo.getVrouterIpAddress(),
                    curVmwareVmInfo.getHostName(), 
                    curVmwareVNInfo.getIsolatedVlanId(),
                    curVmwareVNInfo.getPrimaryVlanId());
            curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
        }
        while (prevVmwareItem != null) {
            // Delete
            vncDB.DeleteVirtualMachine(prevVmwareItem.getKey(), vnUuid);
            prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
        }
    }

    public void syncVmwareVirtualNetworks() throws Exception {
        s_logger.debug("Syncing VCenter Currrent and Previous DBs");
        SortedMap<String, VmwareVirtualNetworkInfo> curVmwareVNInfos =
                vcenterDB.populateVirtualNetworkInfo();
        SortedMap<String, VmwareVirtualNetworkInfo> prevVmwareVNInfos =
                vcenterDB.getPrevVmwareVNInfos();
        s_logger.debug("VNs cur-vmware size: "
                + ((curVmwareVNInfos != null) ? curVmwareVNInfos.size() : 0)
                + ", prev-vmware size: "
                + ((prevVmwareVNInfos != null) ? prevVmwareVNInfos.size() : 0) );

        Iterator<Entry<String, VmwareVirtualNetworkInfo>> curVmwareIter = null;
        Map.Entry<String, VmwareVirtualNetworkInfo> curVmwareItem = null;
        if (curVmwareVNInfos != null && curVmwareVNInfos.size() > 0 && curVmwareVNInfos.entrySet() != null) {
            curVmwareIter = curVmwareVNInfos.entrySet().iterator();
            if (curVmwareIter != null) { 
                curVmwareItem = (Entry<String, VmwareVirtualNetworkInfo>) 
                (curVmwareIter.hasNext() ? curVmwareIter.next() : null);
            }
        }

        Iterator<Entry<String, VmwareVirtualNetworkInfo>> prevVmwareIter = null;
        Map.Entry<String, VmwareVirtualNetworkInfo> prevVmwareItem = null;
        if (prevVmwareVNInfos != null && prevVmwareVNInfos.size() > 0 && prevVmwareVNInfos.entrySet() != null) {
            prevVmwareIter = prevVmwareVNInfos.entrySet().iterator();
            if (prevVmwareIter != null) { 
                prevVmwareItem = (Entry<String, VmwareVirtualNetworkInfo>) 
                (prevVmwareIter.hasNext() ? prevVmwareIter.next() : null);
            }
        }

        while (curVmwareItem != null && prevVmwareItem != null) {
            // Do Vmware and Vnc networks match?
            String curVmwareVnUuid  = curVmwareItem.getKey();
            String prevVmwareVnUuid = prevVmwareItem.getKey();
            Integer cmp = curVmwareVnUuid.compareTo(prevVmwareVnUuid);
            if (cmp == 0) {
                // Sync
                syncVmwareVirtualMachines(curVmwareVnUuid, curVmwareItem.getValue(),
                        prevVmwareItem.getValue());
                // Advance
                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
                curVmwareItem  = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
            } else if (cmp > 0) {
                // Delete Vnc virtual network
                vncDB.DeleteVirtualNetwork(prevVmwareVnUuid);
                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
            } else if (cmp < 0) {
                // Create VMWare virtual network in VNC
                VmwareVirtualNetworkInfo vnInfo = curVmwareItem.getValue();
                SortedMap<String, VmwareVirtualMachineInfo> vmInfos = vnInfo.getVmInfo();
                String subnetAddr = vnInfo.getSubnetAddress();
                String subnetMask = vnInfo.getSubnetMask();
                String gatewayAddr = vnInfo.getGatewayAddress();
                String vmwareVnName = vnInfo.getName();
                short isolatedVlanId = vnInfo.getIsolatedVlanId();
                short primaryVlanId = vnInfo.getPrimaryVlanId();
                vncDB.CreateVirtualNetwork(curVmwareVnUuid, vmwareVnName, subnetAddr,
                        subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId, vmInfos);
                curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
            }
        }
        while (curVmwareItem != null) {
            // Create
            String vmwareVnUuid = curVmwareItem.getKey();
            VmwareVirtualNetworkInfo vnInfo = curVmwareItem.getValue();
            SortedMap<String, VmwareVirtualMachineInfo> vmInfos = vnInfo.getVmInfo();
            String subnetAddr = vnInfo.getSubnetAddress();
            String subnetMask = vnInfo.getSubnetMask();
            String gatewayAddr = vnInfo.getGatewayAddress();
            String vmwareVnName = vnInfo.getName();
            short isolatedVlanId = vnInfo.getIsolatedVlanId();
            short primaryVlanId = vnInfo.getPrimaryVlanId();
            vncDB.CreateVirtualNetwork(vmwareVnUuid, vmwareVnName, subnetAddr,
                    subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId, vmInfos);
            curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
        }
        while (prevVmwareItem != null) {
            // Delete
            vncDB.DeleteVirtualNetwork(prevVmwareItem.getKey());
            prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
        }

        vcenterDB.setPrevVmwareVNInfos(curVmwareVNInfos);
        s_logger.debug("Syncing VCenter Currrent and Previous DBs : Done \n");
    }
    
    @Override
    public void run() {

        // Don't perform one time or periodic sync if
        // Vnc AND Vcenter DB init aren't complete or successful.
        if ( (VncDBInitCompelete == false) || (VcenterDBInitCompelete == false)) {
            if (VncDBInitCompelete == false) {
                if (vncDB.Initialize() == true) {
                    VncDBInitCompelete = true;
                }
            }

            if (VcenterDBInitCompelete == false) {
                if (vcenterDB.Initialize() == true) {
                    VcenterDBInitCompelete = true;
                }
            }

            return;
        }

        // Perform one time sync between VNC and VCenter DBs.
        if (getAddPortSyncAtPluginStart() == true) {

            // When syncVirtualNetwrorks is run the first time, it also does
            // addPort to vrouter agent for existing VMIs.
            // Clear the flag  on first run of syncVirtualNetworks.
            try {
                syncVirtualNetworks();
            } catch (Exception e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error("Error while syncVirtualNetworks: " + e); 
                s_logger.error(stackTrace); 
                e.printStackTrace();
            }
            setAddPortSyncAtPluginStart(false);
            return;
        }

        // 2 second timeout. run KeepAlive with vRouer Agent.
        try {
            vncDB.vrouterAgentPeriodicConnectionCheck();
        } catch (Exception e) {
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error("Error while vrouterAgentPeriodicConnectionCheck: " + e); 
            s_logger.error(stackTrace); 
            e.printStackTrace();
        }

        // 4 sec timeout. Compare current and prev VCenterDB.
        if (iteration == 0) {
            try {
                syncVmwareVirtualNetworks();
            } catch (Exception e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
                s_logger.error(stackTrace); 
                e.printStackTrace();
            }
        } 

        // Increment
        iteration++;
        if (iteration == 2) // 4 sec for poll
            iteration = 0;
    }
}

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

        // Start event notify thread and wait for events.
        //_eventMonitor = new VCenterNotify(_vncDB, _vcenterDB, _monitorTask);
        //_eventMonitor.start();

    }
}
