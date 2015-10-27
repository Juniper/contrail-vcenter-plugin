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

        String dcName = event.getDatacenter().getName();
        dc = vcenterDB.getVmwareDatacenter(dcName);

        String dvsName = event.getDvs().getName();
        dvs = vcenterDB.getVmwareDvs(dcName, dc, dcName);

        String nwName = event.getNet().getName();
        nw = vcenterDB.getVmwareNetwork(nwName, dvs, dvsName, dcName);

        String dpgName = event.getNet().getName();
        dpg = vcenterDB.getVmwareDpg(dpgName, dvs, dvsName, dcName);

        String hostName = event.getHost().getName();
        host = vcenterDB.getVmwareHost(hostName, dc, dcName);

        String vmName = event.getVm().getName();
        vm = vcenterDB.getVmwareVirtualMachine(vmName, host, hostName, dcName);

        vrouterIpAddress = vcenterDB.getVRouterVMIpFabricAddress(dpgName,
                hostName, host, VCenterDB.contrailVRouterVmNamePrefix);

        // finished retrieving all info from Vcenter

        //populate vnInfo to our own cached objects
        vnInfo = new VmwareVirtualNetworkInfo();
        vnInfo.setName(dpg.getName());
        String vnUuid = UUID.nameUUIDFromBytes(dpg.getKey().getBytes()).toString();
        vnInfo.setUuid(vnUuid);
        //TODO populate all fields

        vmInfo = vcenterDB.fillVmwareVirtualMachineInfo(vm, vm.getConfig(), dpg);
    }
}
