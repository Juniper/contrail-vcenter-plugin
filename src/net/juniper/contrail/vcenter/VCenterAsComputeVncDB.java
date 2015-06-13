/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.commons.net.util.SubnetUtils;

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorFactory;
import net.juniper.contrail.api.ApiPropertyBase;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.FloatingIp;
import net.juniper.contrail.api.types.MacAddressesType;
import net.juniper.contrail.api.types.NetworkIpam;
import net.juniper.contrail.api.types.SecurityGroup;
import net.juniper.contrail.api.types.PolicyEntriesType;
import net.juniper.contrail.api.types.SubnetType;
import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.VnSubnetsType;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.IdPermsType;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

public class VCenterAsComputeVncDB extends VncDB {
    private static final Logger s_logger = 
            Logger.getLogger(VCenterAsComputeVncDB.class);

    public VCenterAsComputeVncDB(String apiServerAddress, int apiServerPort) {
        super(apiServerAddress, apiServerPort);
    }

    @Override
    public boolean Initialize() {

        // Check if api-server is alive
        if (isVncApiServerAlive() == false)
            return false;

        return true;
    }
 
    @SuppressWarnings("unchecked")
    @Override
    public SortedMap<String, VncVirtualNetworkInfo> populateVirtualNetworkInfo() 
        throws Exception {
        // Extract list of virtual networks
        List<VirtualNetwork> networks = null;
        try {
        networks = (List<VirtualNetwork>) 
                apiConnector.list(VirtualNetwork.class, null);
        } catch (Exception ex) {
            s_logger.error("Exception in api.list(VirtualNetorks): " + ex);
            ex.printStackTrace();
        }
        if (networks == null || networks.size() == 0) {
            s_logger.debug("NO virtual networks FOUND");
            return null;
        }
        SortedMap<String, VncVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VncVirtualNetworkInfo>();
        for (VirtualNetwork network : networks) {
            // Read in the virtual network
            apiConnector.read(network);
            String vnName = network.getName();
            String vnUuid = network.getUuid();
            // Ignore network ?
            if (doIgnoreVirtualNetwork(vnName)) {
                continue;
            }

            // Extract virtual machine interfaces
            List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs = 
                    network.getVirtualMachineInterfaceBackRefs();
            if (vmInterfaceRefs == null || vmInterfaceRefs.size() == 0) {
                s_logger.debug("Virtual network: " + network + 
                        " NO associated virtual machine interfaces");
            }
            SortedMap<String, VncVirtualMachineInfo> vmInfos = 
                    new TreeMap<String, VncVirtualMachineInfo>();
            for (ObjectReference<ApiPropertyBase> vmInterfaceRef :
                Utils.safe(vmInterfaceRefs)) {

                if (vmInterfaceRef == null) {
                    continue;
                }

                VirtualMachineInterface vmInterface =
                        (VirtualMachineInterface) apiConnector.findById(
                                VirtualMachineInterface.class,
                                vmInterfaceRef.getUuid());
                if (vmInterface == null) {
                    continue;
                }
                //String vmUuid = vmInterface.getParentUuid();
                List<ObjectReference<ApiPropertyBase>> vmRefs = vmInterface.getVirtualMachine();
                if (vmRefs == null || vmRefs.size() == 0) {
                    s_logger.error("Virtual Machine Interface : " + vmInterface.getDisplayName() + 
                            " NO associated virtual machine ");
                }
                if (vmRefs.size() > 1) {
                    s_logger.error("Virtual Machine Interface : " + vmInterface.getDisplayName() + 
                                   "(" + vmRefs.size() + ")" + " associated virtual machines ");
                }

                ObjectReference<ApiPropertyBase> vmRef = vmRefs.get(0);
                VirtualMachine vm = (VirtualMachine) apiConnector.findById(
                        VirtualMachine.class, vmRef.getUuid());
                apiConnector.read(vm);

                VncVirtualMachineInfo vmInfo = new VncVirtualMachineInfo(
                        vm, vmInterface);
                vmInfos.put(vm.getUuid(), vmInfo);
            }
            VncVirtualNetworkInfo vnInfo = 
                    new VncVirtualNetworkInfo(vnName, vmInfos);
            vnInfos.put(vnUuid, vnInfo);
        }
        if (vnInfos.size() == 0) {
            s_logger.debug("NO virtual networks found");
        }
        return vnInfos;
    }
}
