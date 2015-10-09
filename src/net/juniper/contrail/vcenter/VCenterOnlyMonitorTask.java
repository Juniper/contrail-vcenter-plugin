/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
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
import net.juniper.contrail.watchdog.TaskWatchDog;

class VCenterOnlyMonitorTask implements Runnable, VCenterMonitorTask {
    private static Logger s_logger = Logger.getLogger(VCenterMonitorTask.class);
    private VCenterDB vcenterDB;
    private VncDB vncDB;
    private boolean AddPortSyncAtPluginStart = true;
    private boolean VncDBInitCompelete = false;
    private boolean VcenterDBInitCompelete = false;
    public boolean VCenterNotifyForceRefresh = false;
    private static short iteration = 0;

    public VCenterOnlyMonitorTask(String vcenterUrl, String vcenterUsername,
                              String vcenterPassword, String vcenterDcName,
                              String vcenterDvsName, String apiServerAddress,
                              int apiServerPort, String vcenterIpFabricPg) throws Exception {
        vcenterDB = new VCenterDB(vcenterUrl, vcenterUsername, vcenterPassword,
                                  vcenterDcName, vcenterDvsName, vcenterIpFabricPg);
        vncDB     = new VncDB(apiServerAddress, apiServerPort);
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
                if ((vmwareVmInfo.isPoweredOnState() == true)
                    && (vmwareNetworkInfo.getExternalIpam() == true)
                    && (vmwareVmInfo.getIpAddress() != null) ) {
                    vncDB.CreateVMInterfaceInstanceIp(vnUuid, vmwareVmUuid, vmwareVmInfo);
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
                        vmwareNetworkInfo.getPrimaryVlanId(),
                        vmwareNetworkInfo.getExternalIpam(), vmwareVmInfo);
                if (vmwareVmInfo.isPoweredOnState()
                    && (vmwareNetworkInfo.getExternalIpam() == true)
                    && (vmwareVmInfo.getIpAddress() != null)) {
                    vncDB.CreateVMInterfaceInstanceIp(vnUuid, vmwareVmUuid, vmwareVmInfo);
                }
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
                    vmwareNetworkInfo.getPrimaryVlanId(),
                    vmwareNetworkInfo.getExternalIpam(), vmwareVmInfo);
            if (vmwareVmInfo.isPoweredOnState()
                && (vmwareNetworkInfo.getExternalIpam() == true)
                && vmwareVmInfo.getIpAddress() != null ) {
                vncDB.CreateVMInterfaceInstanceIp(vnUuid, vmwareVmUuid, vmwareVmInfo);
            }
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
                boolean ipPoolEnabld = vnInfo.getIpPoolEnabled();
                String range = vnInfo.getRange();
                boolean externalIpam = vnInfo.getExternalIpam();
                vncDB.CreateVirtualNetwork(vmwareVnUuid, vmwareVnName, subnetAddr,
                        subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId, 
                        ipPoolEnabld, range, externalIpam, vmInfos);
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
            boolean ipPoolEnabld = vnInfo.getIpPoolEnabled();
            String range = vnInfo.getRange();
            boolean externalIpam = vnInfo.getExternalIpam();
            vncDB.CreateVirtualNetwork(vmwareVnUuid, vmwareVnName, subnetAddr,
                    subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId,
                    ipPoolEnabld, range, externalIpam, vmInfos);
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

                if (curVmwareVmInfo.isPoweredOnState() 
                    && (curVmwareVNInfo.getExternalIpam() == true)
                    && (curVmwareVmInfo.getIpAddress() != null)
                    && !curVmwareVmInfo.getIpAddress().equals(prevVmwareVmInfo.getIpAddress())) {
                    vncDB.CreateVMInterfaceInstanceIp(vnUuid, curVmwareVmUuid, curVmwareVmInfo);
                }

                prevVmwareItem = prevVmwareIter.hasNext() ? prevVmwareIter.next() : null;
                curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null; 
            } else if (cmp > 0){
                // Delete Vnc virtual machine
                vncDB.DeleteVirtualMachine(prevVmwareItem.getKey(), vnUuid, prev_vrouter);
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
                        curVmwareVNInfo.getPrimaryVlanId(),
                        curVmwareVNInfo.getExternalIpam(), curVmwareVmInfo);
                if (curVmwareVmInfo.isPoweredOnState() 
                    && (curVmwareVNInfo.getExternalIpam() == true)
                    && curVmwareVmInfo.getIpAddress() != null ) {
                    vncDB.CreateVMInterfaceInstanceIp(vnUuid, curVmwareVmUuid, curVmwareVmInfo);
                }
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
                    curVmwareVNInfo.getPrimaryVlanId(),
                    curVmwareVNInfo.getExternalIpam(), curVmwareVmInfo);
            if (curVmwareVmInfo.isPoweredOnState() 
                && curVmwareVNInfo.getExternalIpam() 
                && curVmwareVmInfo.getIpAddress() != null ) {
                vncDB.CreateVMInterfaceInstanceIp(vnUuid, curVmwareVmUuid, curVmwareVmInfo);
            }
            curVmwareItem = curVmwareIter.hasNext() ? curVmwareIter.next() : null;
        }
        while (prevVmwareItem != null) {
            // Delete
            VmwareVirtualMachineInfo prevVmwareVmInfo = prevVmwareItem.getValue();
            String prev_vrouter = prevVmwareVmInfo.getVrouterIpAddress();
            vncDB.DeleteVirtualMachine(prevVmwareItem.getKey(), vnUuid, prev_vrouter);
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
                boolean ipPoolEnabld = vnInfo.getIpPoolEnabled();
                String range = vnInfo.getRange();
                boolean externalIpam = vnInfo.getExternalIpam();
                vncDB.CreateVirtualNetwork(curVmwareVnUuid, vmwareVnName, subnetAddr,
                        subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId,
                        ipPoolEnabld, range, externalIpam, vmInfos);
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
            boolean ipPoolEnabld = vnInfo.getIpPoolEnabled();
            String range = vnInfo.getRange();
            boolean externalIpam = vnInfo.getExternalIpam();
            vncDB.CreateVirtualNetwork(vmwareVnUuid, vmwareVnName, subnetAddr,
                    subnetMask, gatewayAddr, isolatedVlanId, primaryVlanId,
                        ipPoolEnabld, range, externalIpam, vmInfos);
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
        
        //check if you are the master from time to time
        //sometimes things dont go as planned
        if (VCenterMonitor.isZookeeperLeader() == false) {
            s_logger.debug("Lost zookeeper leadership. Restarting myself\n");
            System.exit(0);
        }

        // Don't perform one time or periodic sync if
        // Vnc AND Vcenter DB init aren't complete or successful.

        if ( (VncDBInitCompelete == false) || (VcenterDBInitCompelete == false)) {
            TaskWatchDog.startMonitoring(this, "Init", 
                    300000, TimeUnit.MILLISECONDS);
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
            TaskWatchDog.stopMonitoring(this);
            return;
        }

        // Perform one time sync between VNC and VCenter DBs.
        if (getAddPortSyncAtPluginStart() == true) {
            TaskWatchDog.startMonitoring(this, "One time sync",
                    300000, TimeUnit.MILLISECONDS);

            // When syncVirtualNetworks is run the first time, it also does
            // addPort to vrouter agent for existing VMIs.
            // Clear the flag  on first run of syncVirtualNetworks.
            try {
                syncVirtualNetworks();
            } catch (Exception e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error("Error while syncVirtualNetworks: " + e); 
                s_logger.error(stackTrace); 
                e.printStackTrace();
                if (stackTrace.contains("java.net.ConnectException: Connection refused") ||
                    stackTrace.contains("java.rmi.RemoteException: VI SDK invoke"))   {
                        //Remote Exception. Some issue with connection to vcenter-server
                        // Exception on accessing remote objects.
                        // Try to reinitialize the VCenter connection.
                        //For some reasom RemoteException not thrown
                        s_logger.error("Problem with connection to vCenter-Server");
                        s_logger.error("Restart connection and reSync");
                        vcenterDB.connectRetry();
                        this.VCenterNotifyForceRefresh = true;
                }
            }
            setAddPortSyncAtPluginStart(false);
            TaskWatchDog.stopMonitoring(this);
            return;
        }

        TaskWatchDog.startMonitoring(this, "Periodic sync",
                60000, TimeUnit.MILLISECONDS);
        // 8 second timeout. run KeepAlive with vRouter Agent.
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
            TaskWatchDog.stopMonitoring(this);
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error("Error while syncVmwareVirtualNetworks: " + e);
            s_logger.error(stackTrace);
            e.printStackTrace();
            if (stackTrace.contains("java.net.ConnectException: Connection refused") ||
                stackTrace.contains("java.rmi.RemoteException: VI SDK invoke"))   {
                //Remote Exception. Some issue with connection to vcenter-server
                // Exception on accessing remote objects.
                // Try to reinitialize the VCenter connection.
                //For some reason RemoteException not thrown
                s_logger.error("Problem with connection to vCenter-Server");
                s_logger.error("Restart connection and reSync");
                vcenterDB.connectRetry();
                s_logger.info("Restart connection and reSync Complete..");
                s_logger.info("Inform Notify thread about it....");
                this.VCenterNotifyForceRefresh = true;
            }
        } 

        // Increment
        iteration++;
        if (iteration == 2) { // 4 sec for poll
            iteration = 0;
        }
        TaskWatchDog.stopMonitoring(this);
    }
}
