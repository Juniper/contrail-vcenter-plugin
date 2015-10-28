/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 */

package net.juniper.contrail.vcenter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.UUID;

import com.vmware.vim25.Event;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.NetIpConfigInfo;
import com.vmware.vim25.NetIpConfigInfoIpAddress;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VmDasBeingResetEventReasonCode;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.IpPoolManager;

public class EventData {
    VCenterDB vcenterDB;
    VncDB vncDB;
    boolean changed;
    boolean updateVrouterNeeded;

    //VCenter objects
    // using fully qualified name because of collisions
    com.vmware.vim25.Event event;
    com.vmware.vim25.mo.Datacenter dc;
    String dcName;
    com.vmware.vim25.mo.Datastore ds;
    String dsName;
    com.vmware.vim25.mo.VmwareDistributedVirtualSwitch dvs;
    String dvsName;
    com.vmware.vim25.mo.DistributedVirtualPortgroup dpg;
    String dpgName;
    com.vmware.vim25.mo.Network nw;
    String nwName;
    com.vmware.vim25.mo.HostSystem host;
    String hostName;
    com.vmware.vim25.mo.VirtualMachine vm; //this is the vmwareVM
    String vmName;
    com.vmware.vim25.mo.IpPoolManager ipPoolManager;

    // Cached objects
    VmwareVirtualNetworkInfo vnInfo; //this is our cached VN, names are messed up
    VmwareVirtualMachineInfo vmInfo; //this is our cached VM, names are messed up
    String vrouterIpAddress;

    //API server objects
    net.juniper.contrail.api.types.VirtualNetwork apiVn;
    net.juniper.contrail.api.types.VirtualMachine apiVm;
    net.juniper.contrail.api.types.VirtualMachineInterface apiVmi;
    net.juniper.contrail.api.types.InstanceIp apiInstanceIp;

    EventData(Event event,  VCenterDB vcenterDB, VncDB vncDB) throws Exception {
        this.event = event;
        this.vcenterDB = vcenterDB;
        this.vncDB = vncDB;

        ipPoolManager = vcenterDB.getIpPoolManager();

        if (event.getDatacenter() != null) {
            dcName = event.getDatacenter().getName();
            dc = vcenterDB.getVmwareDatacenter(dcName);
        }

        if (event.getDvs() != null) {
            dvsName = event.getDvs().getName();
            dvs = vcenterDB.getVmwareDvs(dvsName, dc, dcName);
        } else {
            dvsName = vcenterDB.contrailDvSwitchName;
            dvs = vcenterDB.getVmwareDvs(dvsName, dc, dcName);
        }

        if (event.getNet() != null) {
            nwName = event.getNet().getName();
            nw = vcenterDB.getVmwareNetwork(nwName, dvs, dvsName, dcName);
        
            dpgName = event.getNet().getName();
            dpg = vcenterDB.getVmwareDpg(dpgName, dvs, dvsName, dcName);
            
            vnInfo = new VmwareVirtualNetworkInfo();
            vnInfo.setName(dpg.getName());
            String vnUuid = UUID.nameUUIDFromBytes(dpg.getKey().getBytes()).toString();
            vnInfo.setUuid(vnUuid);
            //TODO populate all fields
            //vnInfo.setExternalIpam(externalIpam);
            //vnInfo.setGatewayAddress(gatewayAddress);
            //vnInfo.setIsolatedVlanId(vlanId);
        }

        if (event.getHost() != null) {
            hostName = event.getHost().getName();
            host = vcenterDB.getVmwareHost(hostName, dc, dcName);
            
            vrouterIpAddress = vcenterDB.getVRouterVMIpFabricAddress(
                    VCenterDB.contrailVRouterVmNamePrefix,
                    host, hostName);

            if (event.getVm() != null) {
                vmName = event.getVm().getName();
                vm = vcenterDB.getVmwareVirtualMachine(vmName, host, hostName, dcName);
                //dpg is not set
                //vmInfo = vcenterDB.fillVmwareVirtualMachineInfo(vm, vm.getConfig(), dpg);

                vmInfo = new VmwareVirtualMachineInfo();
                vmInfo.setName(vmName);
                vmInfo.setHostName(hostName);
                vmInfo.setVrouterIpAddress(vrouterIpAddress);
                vmInfo.setUuid(vm.getConfig().getInstanceUuid());
                //vmInfo.setPowerState();,
                //vmInfo.setVmMac();
                //vmInfo.setVnInfo();
            }
        }
    }
}
