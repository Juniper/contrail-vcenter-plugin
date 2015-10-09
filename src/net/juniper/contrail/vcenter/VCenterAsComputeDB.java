/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.UUID;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;

import org.apache.log4j.Logger;

import com.vmware.vim25.DVPortSetting;
import com.vmware.vim25.DVPortgroupConfigInfo;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.IpPool;
import com.vmware.vim25.IpPoolIpPoolConfigInfo;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.NetIpConfigInfo;
import com.vmware.vim25.NetIpConfigInfoIpAddress;
import com.vmware.vim25.NetworkSummary;
import com.vmware.vim25.VMwareDVSPortSetting;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.VmwareDistributedVirtualSwitchPvlanSpec;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanIdSpec;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanSpec;
import com.vmware.vim25.DistributedVirtualSwitchKeyedOpaqueBlob;
import com.vmware.vim25.VMwareDVSConfigInfo;
import com.vmware.vim25.VMwareDVSPvlanMapEntry;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.IpPoolManager;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.util.PropertyCollectorUtil;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;

public class VCenterAsComputeDB extends VCenterDB {
    private static final Logger s_logger =
            Logger.getLogger(VCenterDB.class);

    public VCenterAsComputeDB (String vcenterUrl, String vcenterUsername,
                     String vcenterPassword, String contrailDcName,
                     String contrailDvsName, String ipFabricPgName) {
      super(vcenterUrl, vcenterUsername, vcenterPassword, contrailDcName,
            contrailDvsName, ipFabricPgName);
    }

    VmwareVirtualMachineInfo fillVmwareVirtualMachineInfo(
                                       VirtualMachine vcenterVm,
                                       VirtualMachineConfigInfo vmConfigInfo,
                                       DistributedVirtualPortgroup portGroup)
                                       throws Exception {
        // Name
        String vmName = vcenterVm.getName();
        String dvPgName = portGroup.getName();

        // Ignore virtual machine?
        if (doIgnoreVirtualMachine(vmName)) {
            s_logger.debug("dvPg: " + dvPgName +
                    " Ignoring vm: " + vmName);
            return null;
        }

        // Is it powered on?
        VirtualMachineRuntimeInfo vmRuntimeInfo = vcenterVm.getRuntime();
        VirtualMachinePowerState powerState =
                vmRuntimeInfo.getPowerState();
            s_logger.debug("dvPg: " + dvPgName + " VM: " +
                    vmName + " Power State: " + powerState);

        // Extract MAC address
        String vmMac = getVirtualMachineMacAddress(vmConfigInfo,
                portGroup);
        if (vmMac == null) {
            s_logger.error("dvPg: " + dvPgName + " vm: " + 
                    vmName + " MAC Address NOT found");
            return null;
        }

        // Get host information
        ManagedObjectReference hmor = vmRuntimeInfo.getHost();
        HostSystem host = new HostSystem(
            vcenterVm.getServerConnection(), hmor);
        String hostName = host.getName();

        // Get Contrail VRouter virtual machine information from the host
        String vrouterIpAddress = getVRouterVMIpFabricAddress(dvPgName,
                hostName, host, contrailVRouterVmNamePrefix);
        if (vrouterIpAddress == null) {
            s_logger.error("ContrailVM not found on ESXi host: " 
                    + hostName + ", skipping VM (" + vmName + ") creation"
                    + " on dvPg: " + dvPgName);
            return null;
        }

        // found valid vm instance.
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmName, hostName, hmor,
                        vrouterIpAddress, vmMac, powerState);

        return vmInfo;
    }

    private SortedMap<String, VmwareVirtualMachineInfo> 
        populateVirtualMachineInfo(
                DistributedVirtualPortgroup portGroup,
                boolean externalIpam) throws Exception {
        String dvPgName = portGroup.getName();
        // Get list of virtual machines connected to the port group
        VirtualMachine[] vms = portGroup.getVms();
        if (vms == null || vms.length == 0) {
            s_logger.debug("dvPg: " + dvPgName + 
                    " NO virtual machines connected");
            return null;
        }
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos = 
                new TreeMap<String, VmwareVirtualMachineInfo>();
        for (VirtualMachine vm : vms) {
            // Extract configuration info and get instance UUID
            VirtualMachineConfigInfo vmConfigInfo = vm.getConfig();
            String instanceUuid = vmConfigInfo.getInstanceUuid();

            VmwareVirtualMachineInfo vmInfo = fillVmwareVirtualMachineInfo(vm, vmConfigInfo,
                                                                           portGroup);
            if (vmInfo == null) {
                continue;
            }
            vmInfos.put(instanceUuid, vmInfo);
        }
        if (vmInfos.size() == 0) {
            return null;
        }
        return vmInfos;
    }



    public SortedMap<String, VmwareVirtualNetworkInfo> 
        populateVirtualNetworkInfo() throws Exception {

        // Search contrailDvSwitch
        //VmwareDistributedVirtualSwitch contrailDvs =
        //        (VmwareDistributedVirtualSwitch)
        //        inventoryNavigator.searchManagedEntity(
        //                "VmwareDistributedVirtualSwitch",
        //                contrailDvSwitchName);
        if (contrailDVS == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName + 
                    " NOT configured");
            return null;
        }
        // Extract distributed virtual port groups 
        DistributedVirtualPortgroup[] dvPgs = contrailDVS.getPortgroup();
        if (dvPgs == null || dvPgs.length == 0) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName + 
                    " Distributed portgroups NOT configured");
            return null;
        }

        // Extract private vlan entries for the virtual switch
        VMwareDVSConfigInfo dvsConfigInfo = (VMwareDVSConfigInfo) contrailDVS.getConfig();
        if (dvsConfigInfo == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " ConfigInfo " +
                    "is empty");
            return null;
        }

        if (!(dvsConfigInfo instanceof VMwareDVSConfigInfo)) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " ConfigInfo " +
                    "isn't instanceof VMwareDVSConfigInfo");
            return null;
        }

        VMwareDVSPvlanMapEntry[] pvlanMapArray = dvsConfigInfo.getPvlanConfig();
        if (pvlanMapArray == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " Private VLAN NOT" +
                    "configured");
            return null;
        }

        // Populate VMware Virtual Network Info
        SortedMap<String, VmwareVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VmwareVirtualNetworkInfo>();
        for (DistributedVirtualPortgroup dvPg : dvPgs) {
            s_logger.debug("dvPg: " + dvPg.getName());
            // Extract dvPg configuration info and port setting
            DVPortgroupConfigInfo configInfo = dvPg.getConfig();
            DVPortSetting portSetting = configInfo.getDefaultPortConfig();
            // Ignore network?
            if (doIgnoreVirtualNetwork(portSetting)) {
                continue;
            }
            String vnName = dvPg.getName();
            String vnUuid = vnName;
            s_logger.debug("VN name: " + vnName);

            // get pvlan/vlan info for the portgroup.
            HashMap<String, Short> vlan = getVlanInfo(dvPg, portSetting,
                                                      pvlanMapArray);
            if (vlan == null) {
                s_logger.debug("no pvlan/vlan is associated to dvPg: " + dvPg.getName());
                return null;
            }
            short primaryVlanId   = vlan.get("primary-vlan");
            short isolatedVlanId  = vlan.get("secondary-vlan");

            // Populate associated VMs
            SortedMap<String, VmwareVirtualMachineInfo> vmInfo = 
                    populateVirtualMachineInfo(dvPg, false /*externalIpam*/);
            VmwareVirtualNetworkInfo vnInfo = new
                    VmwareVirtualNetworkInfo(vnName, isolatedVlanId, 
                            primaryVlanId, vmInfo,
                            "11.22.33.0",
                            "255.255.255.0",
                            "11.22.33.1",
                            false,
                            null,
                            false);
            vnInfos.put(vnUuid, vnInfo);
        }
        return vnInfos;
    }

    VmwareVirtualMachineInfo fillVmwareVirtualMachineInfo(
                                       VirtualMachine vcenterVm,
                                       VmwareVirtualMachineInfo prevVmwareVmInfo,
                                       Hashtable pTable,
                                       DistributedVirtualPortgroup portGroup,
                                       String dvPgName,
                                       boolean externalIpam) throws Exception {
        // Name
        String vmName = (String) pTable.get("name");

        // Ignore virtual machine?
        if (doIgnoreVirtualMachine(vmName)) {
            s_logger.debug("dvPg: " + dvPgName +
                    " Ignoring vm: " + vmName);
            return null;
        }

        // Is it powered on?
        VirtualMachinePowerState powerState =(VirtualMachinePowerState)  pTable.get("runtime.powerState");

        // Extract MAC address & host/vrouter information
        String vmMac = null;
        ManagedObjectReference host_mor = null;
        String vrouterIpAddress = null;
        String hostName = null;
        ManagedObjectReference prev_hmor = null;

        if (prevVmwareVmInfo != null) {
            vmMac = prevVmwareVmInfo.getMacAddress();
            prev_hmor = prevVmwareVmInfo.getHmor();
        } else {
            vmMac = getVirtualMachineMacAddress(vcenterVm.getConfig(), portGroup);
            if (vmMac == null) {
                s_logger.error("dvPg: " + dvPgName + " vm: " + 
                        vmName + " MAC Address NOT found");
                return null;
            }
        }

        // Compare saved and current host reference
        ManagedObjectReference hmor = (ManagedObjectReference) pTable.get("runtime.host");
        if ((prev_hmor != null) && prev_hmor.getVal().equals(hmor.getVal())) {
            vrouterIpAddress = prevVmwareVmInfo.getVrouterIpAddress();
            hostName = prevVmwareVmInfo.getHostName();
        } else {
            HostSystem host = new HostSystem(
                vcenterVm.getServerConnection(), hmor);
            hostName = host.getName();

            // Get Contrail VRouter virtual machine information from the host
            vrouterIpAddress = getVRouterVMIpFabricAddress(dvPgName,
                    hostName, host, contrailVRouterVmNamePrefix);
            if (vrouterIpAddress == null) {
                s_logger.error("ContrailVM not found on ESXi host: " 
                        + hostName + ", skipping VM (" + vmName + ") creation"
                        + " on dvPg: " + dvPgName);
                return null;
            }
        }

        // found valid vm instance.
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmName, hostName, hmor,
                        vrouterIpAddress, vmMac, powerState);

        // everything done.
        return vmInfo;
    }

    private SortedMap<String, VmwareVirtualMachineInfo> 
        populateVirtualMachineInfoOptimized(
                DistributedVirtualPortgroup portGroup,
                String dvPgName,
                SortedMap<String, VmwareVirtualMachineInfo> prevVmwareVmInfos,
                boolean externalIpam) throws Exception {

        // Get list of virtual machines connected to the port group
        VirtualMachine[] vms = portGroup.getVms();

        if (vms == null || vms.length == 0) {
            s_logger.debug("dvPg: " + dvPgName + 
                    " NO virtual machines connected");
            return null;
        }

        // Read Virtual Machine Managed Object.
        Hashtable[] pTables = PropertyCollectorUtil.retrieveProperties(vms, 
                                    "VirtualMachine",
				    new String[] {"name",
				    "config.instanceUuid",
				    "runtime.powerState",
				    "runtime.host",
				    });

        SortedMap<String, VmwareVirtualMachineInfo> vmInfos =
                new TreeMap<String, VmwareVirtualMachineInfo>();
        for (int i=0; i < vms.length; i++) {

            // Check if previous vmware database has vm with this uuid
            String instanceUuid             = (String)  pTables[i].get("config.instanceUuid");
            VmwareVirtualMachineInfo prevVmwareVmInfo = null;
            if (prevVmwareVmInfos != null) {
                prevVmwareVmInfo = prevVmwareVmInfos.get(instanceUuid);
            }
            VmwareVirtualMachineInfo vmInfo = fillVmwareVirtualMachineInfo(vms[i],
                                                                           prevVmwareVmInfo,
                                                                           pTables[i],
                                                                           portGroup,
                                                                           dvPgName,
                                                                           externalIpam);
            if (vmInfo == null) {
                continue;
            }
            vmInfos.put(instanceUuid, vmInfo);
        }
        if (vmInfos.size() == 0) {
            return null;
        }
        return vmInfos;
    }

    public SortedMap<String, VmwareVirtualNetworkInfo>
        populateVirtualNetworkInfoOptimized() throws Exception {

        if (contrailDVS == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " NOT configured");
            return null;
        }
        // Extract distributed virtual port groups
        DistributedVirtualPortgroup[] dvPgs = contrailDVS.getPortgroup();
        if (dvPgs == null || dvPgs.length == 0) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Distributed portgroups NOT configured");
            return null;
        }

        // Get stored vcenter database from previous run
        SortedMap<String, VmwareVirtualNetworkInfo>
                    prevVmwareVNInfos = getPrevVmwareVNInfos();

        // Extract private vlan entries for the virtual switch
        VMwareDVSConfigInfo dvsConfigInfo = (VMwareDVSConfigInfo) contrailDVS.getConfig();
        if (dvsConfigInfo == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " ConfigInfo " +
                    "is empty");
            return null;
        }

        if (!(dvsConfigInfo instanceof VMwareDVSConfigInfo)) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " ConfigInfo " +
                    "isn't instanceof VMwareDVSConfigInfo");
            return null;
        }

        VMwareDVSPvlanMapEntry[] pvlanMapArray = dvsConfigInfo.getPvlanConfig();
        if (pvlanMapArray == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " Private VLAN NOT" +
                    "configured");
            return null;
        }

        Hashtable[] pTables = PropertyCollectorUtil.retrieveProperties(dvPgs,
                                "DistributedVirtualPortgroup",
				new String[] {"name",
				"config.defaultPortConfig",
				});

        // Populate VMware Virtual Network Info
        SortedMap<String, VmwareVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VmwareVirtualNetworkInfo>();
        for (int i=0; i < dvPgs.length; i++) {
            short primaryVlanId;
            short isolatedVlanId;
            String subnetAddress;
            String subnetMask;
            String gatewayAddress;
            boolean ipPoolEnabled;
            String range;
            boolean externalIpam;
            VmwareVirtualNetworkInfo prevVmwareVNInfo =null;
            SortedMap<String, VmwareVirtualMachineInfo> prevVmwareVmInfos = null;

            String vnName = (String) pTables[i].get("name");
            s_logger.debug("dvPg: " + vnName);

            String vnUuid = vnName;

            // Extract dvPg configuration info and port setting
            DVPortSetting portSetting = (DVPortSetting) pTables[i].get("config.defaultPortConfig");

            // Ignore network?
            if (doIgnoreVirtualNetwork(portSetting)) {
                continue;
            }

            // Check if the network was created in the previous run of periodic
            if (prevVmwareVNInfos != null) {
                prevVmwareVNInfo = prevVmwareVNInfos.get(vnUuid);
            }

            if (prevVmwareVNInfo != null) {
                prevVmwareVmInfos = prevVmwareVNInfo.getVmInfo();
            }

            // get pvlan/vlan info for the portgroup.
            HashMap<String, Short> vlan = getVlanInfo(dvPgs[i], portSetting, pvlanMapArray);
            if (vlan == null) {
                s_logger.debug("no pvlan/vlan is associated to dvPg: " + vnName);
                return null;
            }
            s_logger.debug("VN name: " + vnName);

            primaryVlanId   = vlan.get("primary-vlan");
            isolatedVlanId  = vlan.get("secondary-vlan");

            // Populate associated VMs
            SortedMap<String, VmwareVirtualMachineInfo> vmInfo =
                    populateVirtualMachineInfoOptimized(dvPgs[i], vnName, prevVmwareVmInfos, false);

            VmwareVirtualNetworkInfo vnInfo = new
                    VmwareVirtualNetworkInfo(vnName, isolatedVlanId, 
                            primaryVlanId, vmInfo,
                            "11.22.33.0",
                            "255.255.255.0",
                            "11.22.33.1",
                            false,
                            null,
                            false);
            vnInfos.put(vnUuid, vnInfo);
        }
        return vnInfos;
    }
}
