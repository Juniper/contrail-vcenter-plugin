/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
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

class VCenterAsComputeMonitorTask implements VCenterMonitorTask {
    private static Logger s_logger = Logger.getLogger(VCenterAsComputeMonitorTask.class);
    private VCenterDB vcenterDB;
    private VncDB vncDB;
    private boolean AddPortSyncAtPluginStart = true;
    private boolean VncDBInitCompelete = false;
    private boolean VcenterDBInitCompelete = false;
    private static short iteration = 0;
    public boolean VCenterNotifyForceRefresh = false;
    
    public VCenterAsComputeMonitorTask(String vcenterUrl, String vcenterUsername,
                              String vcenterPassword, String vcenterDcName,
                              String vcenterDvsName, String apiServerAddress,
                              int apiServerPort, String vcenterIpFabricPg) throws Exception {
        vcenterDB = new VCenterAsComputeDB(vcenterUrl, vcenterUsername, vcenterPassword,
                                  vcenterDcName, vcenterDvsName, vcenterIpFabricPg);
        vncDB     = new VCenterAsComputeVncDB(apiServerAddress, apiServerPort);
    }

    public void Initialize() {
        // Initialize the databases
        if (vncDB.Initialize() == true) {
            VncDBInitCompelete = true;
        }
        if (vcenterDB.Initialize() == true && vcenterDB.Initialize_data() == true) {
            VcenterDBInitCompelete = true;
        }
    }

    public VCenterDB getVCenterDB() {
        return vcenterDB;
    }

    public void setVCenterDB(VCenterDB _vcenterDB) {
        vcenterDB = _vcenterDB;
    }

    public VncDB getVncDB() {
        return vncDB;
    }

    public boolean getVCenterNotifyForceRefresh() {
        return VCenterNotifyForceRefresh;
    }

    public void setVCenterNotifyForceRefresh(boolean _VCenterNotifyForceRefresh) {
        VCenterNotifyForceRefresh = _VCenterNotifyForceRefresh;
    }

    public void setAddPortSyncAtPluginStart(boolean _AddPortSyncAtPluginStart)
    {
        AddPortSyncAtPluginStart = _AddPortSyncAtPluginStart;
    }

    public boolean getAddPortSyncAtPluginStart()
    {
        return AddPortSyncAtPluginStart;
    }

    public void syncVirtualMachines(String vnUuid, 
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
                VmwareVirtualMachineInfo vmwareVmInfo = vmwareItem.getValue();
                // Match found, advance Vmware and Vnc iters
                if (AddPortSyncAtPluginStart == true && vmwareVmInfo.isPoweredOnState()) {
                    vncDB.VifPlug(vnUuid, vmwareVmUuid,
                            vmwareVmInfo.getMacAddress(),
                            vmwareVmInfo.getName(),
                            vmwareVmInfo.getVrouterIpAddress(),
                            vmwareVmInfo.getHostName(),
                            vmwareNetworkInfo.getIsolatedVlanId(),
                            vmwareNetworkInfo.getPrimaryVlanId(), vmwareVmInfo);
                }
                vncItem = vncIter.hasNext() ? vncIter.next() : null;
                vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
            } else if (cmp > 0){
                // Delete Vnc virtual machine
                vncItem = vncIter.hasNext() ? vncIter.next() : null;
            } else if (cmp < 0){
                // create VMWare virtual machine in VNC
                vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
            }
        }

        while (vmwareItem != null) {
            // Error condition.
            // VM exists on vmware but not on vnc
            vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
        }

        while (vncItem != null) {
            // Delete
            vncItem = vncIter.hasNext() ? vncIter.next() : null;
        }
    }
    
    public void syncVirtualNetworks() throws Exception {
        s_logger.info("Syncing Vnc and VCenter DBs");
        SortedMap<String, VmwareVirtualNetworkInfo> vmwareVirtualNetworkInfos =
                vcenterDB.populateVirtualNetworkInfoOptimized();
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
                vncItem = vncIter.hasNext() ? vncIter.next() : null;
            } else if (cmp < 0){
                vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
            }
        }
        while (vmwareItem != null) {
            vmwareItem = vmwareIter.hasNext() ? vmwareIter.next() : null;
        }
        while (vncItem != null) {
            vncItem = vncIter.hasNext() ? vncIter.next() : null;
        }
        vcenterDB.setPrevVmwareVNInfos(vmwareVirtualNetworkInfos);
        s_logger.info("Syncing Vnc and VCenter DBs : Done");
    }

    public void syncVmwareVirtualMachines(String vnUuid,
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
            VmwareVirtualMachineInfo prevVmwareVmInfo = prevVmwareItem.getValue();
            String prev_vrouter = prevVmwareVmInfo.getVrouterIpAddress();
            if (cmp == 0) {
                //If VM has migrated from one host to other, uuid is same, so handle it
                VmwareVirtualMachineInfo curVmwareVmInfo = curVmwareItem.getValue();
                curVmwareVmInfo.setInterfaceUuid(prevVmwareVmInfo.getInterfaceUuid());
                String cur_vrouter  = curVmwareVmInfo.getVrouterIpAddress();
                Integer cmp_vrouter = prev_vrouter.compareTo(cur_vrouter);
                if (cmp_vrouter != 0) {
                    s_logger.info("\nuuids are same, but the vrouters are different. "
                                  + "old vrouter: " + prev_vrouter+" new vrouter:"
                                  + cur_vrouter + " Taking care of migration");
                    if (prevVmwareVmInfo.isPoweredOnState()) {
                        vncDB.VifUnplug(prevVmwareVmInfo.getInterfaceUuid(),
                                         prevVmwareVmInfo.getVrouterIpAddress());
                    }
                    if (curVmwareVmInfo.isPoweredOnState()) {
                        vncDB.VifPlug(vnUuid, curVmwareVmUuid,
                                curVmwareVmInfo.getMacAddress(),
                                curVmwareVmInfo.getName(),
                                curVmwareVmInfo.getVrouterIpAddress(),
                                curVmwareVmInfo.getHostName(),
                                curVmwareVNInfo.getIsolatedVlanId(),
                                curVmwareVNInfo.getPrimaryVlanId(), curVmwareVmInfo);
                    }
                } else {
                    if (!curVmwareVmInfo.isPowerStateEqual(prevVmwareVmInfo.getPowerState())) {
                        if (curVmwareVmInfo.isPoweredOnState()) {
                            vncDB.VifPlug(vnUuid, curVmwareVmUuid,
                                    curVmwareVmInfo.getMacAddress(),
                                    curVmwareVmInfo.getName(),
                                    curVmwareVmInfo.getVrouterIpAddress(),
                                    curVmwareVmInfo.getHostName(),
                                    curVmwareVNInfo.getIsolatedVlanId(),
                                    curVmwareVNInfo.getPrimaryVlanId(), curVmwareVmInfo);
                        } else {
                            vncDB.VifUnplug(curVmwareVmInfo.getInterfaceUuid(),
                                            curVmwareVmInfo.getVrouterIpAddress());
                        }
                    }
                }

                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
                curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null; 
            } else if (cmp > 0){
                // Delete Vnc virtual machine
                vncDB.VifUnplug(prevVmwareVmInfo.getInterfaceUuid(),
                                prevVmwareVmInfo.getVrouterIpAddress());
                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
            } else if (cmp < 0){
                // create VMWare virtual machine in VNC
                VmwareVirtualMachineInfo curVmwareVmInfo = curVmwareItem.getValue();
                if (curVmwareVmInfo.isPoweredOnState()) {
                    vncDB.VifPlug(vnUuid, curVmwareVmUuid,
                            curVmwareVmInfo.getMacAddress(),
                            curVmwareVmInfo.getName(),
                            curVmwareVmInfo.getVrouterIpAddress(),
                            curVmwareVmInfo.getHostName(),
                            curVmwareVNInfo.getIsolatedVlanId(),
                            curVmwareVNInfo.getPrimaryVlanId(), curVmwareVmInfo);
                }
                curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
            }
        }       
        while (curVmwareItem != null) {
            // Create
            String curVmwareVmUuid = curVmwareItem.getKey();
            VmwareVirtualMachineInfo curVmwareVmInfo = curVmwareItem.getValue();
            if (curVmwareVmInfo.isPoweredOnState()) {
                vncDB.VifPlug(vnUuid, curVmwareVmUuid,
                        curVmwareVmInfo.getMacAddress(),
                        curVmwareVmInfo.getName(),
                        curVmwareVmInfo.getVrouterIpAddress(),
                        curVmwareVmInfo.getHostName(),
                        curVmwareVNInfo.getIsolatedVlanId(),
                        curVmwareVNInfo.getPrimaryVlanId(), curVmwareVmInfo);
            }
            curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
        }
        while (prevVmwareItem != null) {
            // Delete
            VmwareVirtualMachineInfo prevVmwareVmInfo = prevVmwareItem.getValue();
            vncDB.VifUnplug(prevVmwareVmInfo.getInterfaceUuid(),
                            prevVmwareVmInfo.getVrouterIpAddress());
            prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
        }
    }

    public void syncVmwareVirtualNetworks() throws Exception {
        s_logger.debug("Syncing VCenter Currrent and Previous DBs");
        SortedMap<String, VmwareVirtualNetworkInfo> curVmwareVNInfos =
                vcenterDB.populateVirtualNetworkInfoOptimized();
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
                // Vmware Virtual Network is deleted. Delete VMs under it and unplug vifs
                VmwareVirtualNetworkInfo prevVNInfo = prevVmwareItem.getValue();
                s_logger.info("VN (" + prevVNInfo.getName() + ") is deleted. Check for vif unplugs ..");
                VmwareVirtualNetworkInfo curVmwareVNInfo = 
                                    new VmwareVirtualNetworkInfo(prevVNInfo.getName(),
                                        prevVNInfo.getIsolatedVlanId(),
                                        prevVNInfo.getPrimaryVlanId(),
                                        null, 
                                        prevVNInfo.getSubnetAddress(),
                                        prevVNInfo.getSubnetMask(),
                                        prevVNInfo.getGatewayAddress(),
                                        false, null, false);
                syncVmwareVirtualMachines(curVmwareVNInfo.getName(), curVmwareVNInfo, prevVNInfo);
                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
            } else if (cmp < 0) {
                // Vmware Virtual Network is added. create VMs under it and plug vifs
                VmwareVirtualNetworkInfo curVNInfo = curVmwareItem.getValue();
                s_logger.info("VN (" + curVNInfo.getName() + ") is added. Check for vif plugs ..");
                VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                    new VmwareVirtualNetworkInfo(curVNInfo.getName(),
                                        curVNInfo.getIsolatedVlanId(),
                                        curVNInfo.getPrimaryVlanId(),
                                        null, 
                                        curVNInfo.getSubnetAddress(),
                                        curVNInfo.getSubnetMask(),
                                        curVNInfo.getGatewayAddress(),
                                        false, null, false);
                syncVmwareVirtualMachines(curVNInfo.getName(), curVmwareItem.getValue(),
                                          prevVmwareVNInfo);
                curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
            }
        }
        while (curVmwareItem != null) {
            // Create
            String vmwareVnUuid = curVmwareItem.getKey();
            // Vmware Virtual Network is added. create VMs under it and plug vifs
            VmwareVirtualNetworkInfo curVNInfo = curVmwareItem.getValue();
            s_logger.info("VN (" + curVNInfo.getName() + ") is added. Check for vif plugs ..");
            VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                new VmwareVirtualNetworkInfo(curVNInfo.getName(),
                                    curVNInfo.getIsolatedVlanId(),
                                    curVNInfo.getPrimaryVlanId(),
                                    null, 
                                    curVNInfo.getSubnetAddress(),
                                    curVNInfo.getSubnetMask(),
                                    curVNInfo.getGatewayAddress(),
                                    false, null, false);
            syncVmwareVirtualMachines(vmwareVnUuid, curVmwareItem.getValue(),
                                      prevVmwareVNInfo);
            curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
        }
        while (prevVmwareItem != null) {
            // Vmware Virtual Network is deleted. Delete VMs under it and unplug vifs
            String prevVmwareVnUuid = prevVmwareItem.getKey();
            VmwareVirtualNetworkInfo prevVNInfo = prevVmwareItem.getValue();
            s_logger.info("VN (" + prevVNInfo.getName() + ") is deleted. Check for vif unplugs ..");
            VmwareVirtualNetworkInfo curVmwareVNInfo = 
                                new VmwareVirtualNetworkInfo(prevVNInfo.getName(),
                                    prevVNInfo.getIsolatedVlanId(),
                                    prevVNInfo.getPrimaryVlanId(),
                                    null, 
                                    prevVNInfo.getSubnetAddress(),
                                    prevVNInfo.getSubnetMask(),
                                    prevVNInfo.getGatewayAddress(),
                                    false, null, false);
            syncVmwareVirtualMachines(prevVmwareVnUuid, curVmwareVNInfo, prevVNInfo);
            prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
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
                if (vcenterDB.Initialize() == true && vcenterDB.Initialize_data() == true) {
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
                if (stackTrace.contains("java.net.ConnectException: Connection refused"))       {
                     //Remote Exception. Some issue with connection to vcenter-server
                     // Exception on accessing remote objects.
                     // Try to reinitialize the VCenter connection.
                     //For some reasom RemoteException not thrown
                     s_logger.error("Problem with connection to vCenter-Server");
                     s_logger.error("Restart connection and reSync");
                     vcenterDB.connectRetry();
                     this.VCenterNotifyForceRefresh = true;
                }
                return;
            }
            setAddPortSyncAtPluginStart(false);
            return;
        }

        // 8 second timeout. run KeepAlive with vRouer Agent.
        if (iteration == 0) {
            try {
                vncDB.vrouterAgentPeriodicConnectionCheck(vcenterDB.vRouterActiveMap);
            } catch (Exception e) {

                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error("Error while vrouterAgentPeriodicConnectionCheck: " + e); 
                s_logger.error(stackTrace); 
                e.printStackTrace();
            }
        } 

        // 4 sec timeout. Compare current and prev VCenterDB.
        try {
            syncVmwareVirtualNetworks();
        } catch (Exception e) {
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
            s_logger.error(stackTrace); 
            e.printStackTrace();
            if (stackTrace.contains("java.net.ConnectException: Connection refused")) 	{
                //Remote Exception. Some issue with connection to vcenter-server
                // Exception on accessing remote objects.
                // Try to reinitialize the VCenter connection.
                //For some reasom RemoteException not thrown
                s_logger.error("Problem with connection to vCenter-Server");
                s_logger.error("Restart connection and reSync");
                vcenterDB.connectRetry();
            }
        }

        // Increment
        iteration++;
        if (iteration == 2) // 4 sec for poll
            iteration = 0;
    }
}
