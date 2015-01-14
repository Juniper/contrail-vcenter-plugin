/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.SortedMap;
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
import net.juniper.contrail.api.types.SubnetType;
import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.VnSubnetsType;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.IdPermsType;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

public class VncDB {
    private static final Logger s_logger = 
            Logger.getLogger(VncDB.class);
    private static final int vrouterApiPort = 9090;
    private final String apiServerAddress;
    private final int apiServerPort;
    private HashMap<String, ContrailVRouterApi> vrouterApiMap;
    
    private ApiConnector apiConnector;
    private Project vCenterProject;
    private NetworkIpam vCenterIpam;
    private IdPermsType vCenterIdPerms;

    public static final String VNC_ROOT_DOMAIN     = "default-domain";
    public static final String VNC_VCENTER_PROJECT = "vCenter";
    public static final String VNC_VCENTER_IPAM    = "vCenter-ipam";
    public static final String VNC_VCENTER_PLUGIN  = "vcenter-plugin";
    
    public VncDB(String apiServerAddress, int apiServerPort) {
        this.apiServerAddress = apiServerAddress;
        this.apiServerPort = apiServerPort;

        // Create vrouter api map
        vrouterApiMap = new HashMap<String, ContrailVRouterApi>();

        // Create global id-perms object.
        vCenterIdPerms = new IdPermsType();
        vCenterIdPerms.setCreator("vcenter-plugin");
        vCenterIdPerms.setEnable(true);

    }
    
    public boolean isVncApiServerAlive() {
        if (apiConnector == null) {
            apiConnector = ApiConnectorFactory.build(apiServerAddress,
                                                     apiServerPort);
            if (apiConnector == null) {
                s_logger.error(" failed to create ApiConnector.. retry later");
                return false;
            }
        }

        // Read project list as a life check
        s_logger.info(" Checking if api-server is alive and kicking..");
        Short count =10; // 10 sec total

        try {
            List<Project> projects = (List<Project>) apiConnector.list(Project.class, null);
            if (projects == null) {
                s_logger.error(" ApiServer not fully awake yet.. retry again..");
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        s_logger.info(" Api-server alive. Got the pulse..");
        return true;

    }

    public boolean Initialize() {

        // Check if api-server is alive
        if (isVncApiServerAlive() == false)
            return false;

        // Check if Vmware Project exists on VNC. If not, create one.
        try {
            vCenterProject = (Project) apiConnector.findByFQN(Project.class, 
                                        VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT);
        } catch (IOException e) {
            return false;
        }
        if (vCenterProject == null) {
            s_logger.info(" vCenter project not present, creating ");
            vCenterProject = new Project();
            vCenterProject.setName("vCenter");
            vCenterProject.setIdPerms(vCenterIdPerms);
            try {
                if (!apiConnector.create(vCenterProject)) {
                    s_logger.error("Unable to create project: " + vCenterProject.getName());
                    return false;
                }
            } catch (IOException e) { 
                s_logger.error("Exception : " + e);
                e.printStackTrace();
                return false;
            }
        } else {
            s_logger.info(" vCenter project present, continue ");
        }

        // Check if VMWare vCenter-ipam exists on VNC. If not, create one.
        try {
            vCenterIpam = (NetworkIpam) apiConnector.findByFQN(NetworkIpam.class,
                       VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT + ":" + VNC_VCENTER_IPAM);
        } catch (IOException e) {
            return false;
        }

        if (vCenterIpam == null) {
            s_logger.info(" vCenter Ipam not present, creating ...");
            vCenterIpam = new NetworkIpam();
            vCenterIpam.setParent(vCenterProject);
            vCenterIpam.setName("vCenter-ipam");
            vCenterIpam.setIdPerms(vCenterIdPerms);
            try {
                if (!apiConnector.create(vCenterIpam)) {
                    s_logger.error("Unable to create Ipam: " + vCenterIpam.getName());
                }
            } catch (IOException e) { 
                s_logger.error("Exception : " + e);
                e.printStackTrace();
                return false;
            }
        } else {
            s_logger.info(" vCenter Ipam present, continue ");
        }

        return true;
    }
    
    private void DeleteVirtualMachineInternal(
            VirtualMachineInterface vmInterface) throws IOException {

        s_logger.debug("Delete Virtual Machine given VMI (uuid = " + vmInterface.getUuid() + ")");

        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                vmInterface.getInstanceIpBackRefs();
        for (ObjectReference<ApiPropertyBase> instanceIpRef : 
            Utils.safe(instanceIpRefs)) {
            s_logger.info("Delete instance IP: " + instanceIpRef.getReferredName());
            apiConnector.delete(InstanceIp.class, instanceIpRef.getUuid());
        }
        // There should only be one virtual machine hanging off the virtual
        // machine interface
        //String vmUuid = vmInterface.getParentUuid();   
        List<ObjectReference<ApiPropertyBase>> vmRefs = vmInterface.getVirtualMachine();
        if (vmRefs == null || vmRefs.size() == 0) {
            s_logger.error("Virtual Machine Interface : " + vmInterface.getDisplayName() + 
                    " NO associated virtual machine ");
        }
        if (vmRefs.size() > 1) {
            s_logger.error("Virtual Machine Interface : " + vmInterface.getDisplayName() + 
                           "is associated with" + "(" + vmRefs.size() + ")" + " virtual machines ");
        }

        ObjectReference<ApiPropertyBase> vmRef = vmRefs.get(0);
        VirtualMachine vm = (VirtualMachine) apiConnector.findById(
                VirtualMachine.class, vmRef.getUuid());
        if (vm == null) {
            s_logger.warn("Virtual machine with uuid: " + vmRef.getUuid()
                          + " doesn't exist on api-server. Nothing to delete");
            return;
        }

        if ((vm.getVirtualMachineInterfaces() == null) || 
             vm.getVirtualMachineInterfaces().isEmpty()) {
            s_logger.debug("Delete virtual machine: " + vm.getName());
            apiConnector.delete(VirtualMachine.class, vmRef.getUuid());      
            return;
        }

        boolean deleteVm = false;
        if (vm.getVirtualMachineInterfaces().size() == 1) {
            deleteVm = true;
        }
        // Extract VRouter IP address from display name
        String vrouterIpAddress = vm.getDisplayName();
        String vmInterfaceName = vmInterface.getName();
        String vmInterfaceDisplayName = vmInterface.getDisplayName();
        s_logger.debug("Delete virtual machine interface: " + vmInterfaceName);
        String vmInterfaceUuid = vmInterface.getUuid();
        apiConnector.delete(VirtualMachineInterface.class,
                vmInterfaceUuid);
        if (deleteVm) {
            s_logger.debug("Delete virtual machine: " + vm.getName());
            apiConnector.delete(VirtualMachine.class, vmRef.getUuid());      
        }

        // Unplug notification to vrouter
        if (vrouterIpAddress == null) {
            s_logger.debug("Virtual machine interface: " + vmInterfaceUuid + 
                    " delete notification NOT sent");
            return;
        }
        ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
        if (vrouterApi == null) {
            vrouterApi = new ContrailVRouterApi(
                    InetAddress.getByName(vrouterIpAddress), 
                    vrouterApiPort, false);
            vrouterApiMap.put(vrouterIpAddress, vrouterApi);
        }
        boolean ret = vrouterApi.DeletePort(UUID.fromString(vmInterfaceUuid));
        if ( ret == true) {
            s_logger.debug("VRouterAPi Delete Port success - port name: "
                          + vmInterfaceName + "(" + vmInterfaceDisplayName + ")");
        } else {
            // log failure but don't worry. Periodic KeepAlive task will
            // attempt to connect to vRouter Agent and ports that are not
            // replayed by client(plugin) will be deleted by vRouter Agent.
            s_logger.warn("VRouterAPi Delete Port failure - port name: "
                          + vmInterfaceName + "(" + vmInterfaceDisplayName + ")");
        }
    }

    public void DeleteVirtualMachine(VncVirtualMachineInfo vmInfo) 
            throws IOException {
        DeleteVirtualMachineInternal(vmInfo.getVmInterfaceInfo());
    }
    
    public void DeleteVirtualMachine(String vmUuid, String vnUuid) throws IOException {

        s_logger.info("Delete Virtual Machine (vmUuid = " + vmUuid
                       + " vnUuid = " + vnUuid + ")");

        VirtualMachine vm = (VirtualMachine) apiConnector.findById(
                VirtualMachine.class, vmUuid);
        
        if (vm == null) {
            s_logger.warn("Virtual Machine (uuid = " + vmUuid + ") doesn't exist on VNC");
            return;
        }

        // Extract VRouter IP address from display name
        String vrouterIpAddress = vm.getDisplayName();

        // Delete InstanceIp, VMInterface & VM
        boolean deleteVm = false;
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        s_logger.info("Virtual Machine has " + vmInterfaceRefs.size() 
                      + " interfaces");
        for (ObjectReference<ApiPropertyBase> vmInterfaceRef :
             Utils.safe(vmInterfaceRefs)) {
            String vmInterfaceUuid = vmInterfaceRef.getUuid();
            VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                    apiConnector.findById(VirtualMachineInterface.class, 
                            vmInterfaceUuid);
            List<ObjectReference<ApiPropertyBase>> vnRefs =
                                            vmInterface.getVirtualNetwork();
            if (!(vnRefs.get(0).getUuid().equals(vnUuid))) {
                continue;
            }

            // Found vmInterface matching vnUuuid
            s_logger.info("Found VMInterface matching" + " vnUuid = " + vnUuid);

            // If this is the only interface on this VM,
            // delete Virtual Machine as well after deleting last VMI
            if (vmInterfaceRefs.size() == 1) {
              deleteVm = true;
            }

            // Clear flloating-ip associations if it exists on VMInterface
            List<ObjectReference<ApiPropertyBase>> floatingIpRefs = 
                    vmInterface.getFloatingIpBackRefs();
            if ((floatingIpRefs != null) && !floatingIpRefs.isEmpty()) {
                s_logger.info("floatingIp association exists for VMInterface:" + vmInterface.getUuid());
                // there can be one floating-ip per VMI.
                FloatingIp floatingIp = (FloatingIp)
                    apiConnector.findById(FloatingIp.class, 
                                          floatingIpRefs.get(0).getUuid());
                // clear VMInterface back reference.
                FloatingIp fip = new FloatingIp();
                fip.setParent(floatingIp.getParent());
                fip.setName(floatingIp.getName());
                fip.setUuid(floatingIp.getUuid());
                fip.setVirtualMachineInterface(vmInterface);
                fip.clearVirtualMachineInterface();

                floatingIp.clearVirtualMachineInterface();
                apiConnector.update(fip);
                s_logger.info("Removed floatingIp association for VMInterface:" + vmInterface.getUuid());
            }
           
            // delete instancIp
            List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                    vmInterface.getInstanceIpBackRefs();
            for (ObjectReference<ApiPropertyBase> instanceIpRef : 
                Utils.safe(instanceIpRefs)) {
                s_logger.info("Delete instance IP: " + 
                        instanceIpRef.getReferredName());
                apiConnector.delete(InstanceIp.class, 
                        instanceIpRef.getUuid());
            }

            // delete VMInterface
            s_logger.info("Delete virtual machine interface: " + 
                    vmInterface.getName());
            apiConnector.delete(VirtualMachineInterface.class,
                    vmInterfaceUuid);

            // Send Unplug notification to vrouter
            if (vrouterIpAddress == null) {
                s_logger.warn("Virtual machine interace: " + vmInterfaceUuid + 
                        " delete notification NOT sent");
                continue;
            }
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            if (vrouterApi == null) {
                vrouterApi = new ContrailVRouterApi(
                        InetAddress.getByName(vrouterIpAddress), 
                        vrouterApiPort, false);
                vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }
            vrouterApi.DeletePort(UUID.fromString(vmInterfaceUuid));
        }

        // delete VirtualMachine or may-be-not 
        if (deleteVm == true) {
            apiConnector.delete(VirtualMachine.class, vmUuid);
            s_logger.info("Delete Virtual Machine (uuid = " + vmUuid + ") Done.");
        } else {
            s_logger.info("Virtual Machine (uuid = " + vmUuid + ") not deleted"
                          + " yet as more interfaces to be deleted.");
        }
    }
    
    public void CreateVirtualMachine(String vnUuid, String vmUuid,
            String macAddress, String vmName, String vrouterIpAddress,
            String hostName, short isolatedVlanId, short primaryVlanId) throws IOException {
        s_logger.info("Create Virtual Machine : " 
                       + ", VM: " + vmName + " (" + vmUuid + ")" 
                       + ", VN: " + vnUuid
                       + ", vrouterIpAddress: " + vrouterIpAddress 
                       + ", vlan: " + isolatedVlanId + "/" + primaryVlanId);
        
        // Virtual Network
        VirtualNetwork network = (VirtualNetwork) apiConnector.findById(
                VirtualNetwork.class, vnUuid);
        if (network == null) {
            s_logger.warn("Create Virtual Machine requested with invalid VN: " + vmUuid
                       + ", VM: " + vmName + " (" + vmUuid + ")" 
                       + ", vrouterIpAddress: " + vrouterIpAddress 
                       + ", vlan: " + isolatedVlanId + "/" + primaryVlanId);
            return;
        }

        // Virtual Machine
        VirtualMachine vm = (VirtualMachine) apiConnector.findById(
                VirtualMachine.class, vmUuid);
        if (vm == null) {
            // Create Virtual machine
            vm = new VirtualMachine();
            vm.setName(vmUuid);
            vm.setUuid(vmUuid);

            // Encode VRouter IP address in display name
            if (vrouterIpAddress != null) {
                vm.setDisplayName(vrouterIpAddress);
            }
            vm.setIdPerms(vCenterIdPerms);
            apiConnector.create(vm);
            apiConnector.read(vm);
        }

        // find VMI matching vmUuid & vnUuid
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        for (ObjectReference<ApiPropertyBase> vmInterfaceRef :
            Utils.safe(vmInterfaceRefs)) {
            String vmInterfaceUuid = vmInterfaceRef.getUuid();
            VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                    apiConnector.findById(VirtualMachineInterface.class, 
                            vmInterfaceUuid);
            List<ObjectReference<ApiPropertyBase>> vnRefs =
                                            vmInterface.getVirtualNetwork();
            for (ObjectReference<ApiPropertyBase> vnRef : vnRefs) {
                if (vnRef.getUuid().equals(vnUuid)) {
                    s_logger.debug("VMI exits with vnUuid =" + vnUuid 
                                 + " vmUuid = " + vmUuid + " no need to create new ");
                    return;
                }
            }
        }

        // create Virtual machine interface
        String vmInterfaceName = "vmi-" + network.getName() + "-" + vmName;
        String vmiUuid = UUID.randomUUID().toString();
        VirtualMachineInterface vmInterface = new VirtualMachineInterface();
        vmInterface.setDisplayName(vmInterfaceName);
        vmInterface.setUuid(vmiUuid);
        vmInterface.setParent(vCenterProject);
        vmInterface.setName(vmiUuid);
        vmInterface.setVirtualNetwork(network);
        vmInterface.addVirtualMachine(vm);
        MacAddressesType macAddrType = new MacAddressesType();
        macAddrType.addMacAddress(macAddress);
        vmInterface.setMacAddresses(macAddrType);
        vmInterface.setIdPerms(vCenterIdPerms);
        apiConnector.create(vmInterface);
        s_logger.debug("Created virtual machine interface:" + vmInterfaceName + 
                ", vmiUuid :" + vmiUuid);

        // Instance Ip
        String instanceIpName = "ip-" + network.getName() + "-" + vmName;
        String instIpUuid = UUID.randomUUID().toString();
        InstanceIp instanceIp = new InstanceIp();
        instanceIp.setDisplayName(instanceIpName);
        instanceIp.setUuid(instIpUuid);
        instanceIp.setName(instIpUuid);
        instanceIp.setVirtualNetwork(network);
        instanceIp.setVirtualMachineInterface(vmInterface);
        instanceIp.setIdPerms(vCenterIdPerms);
        apiConnector.create(instanceIp);

        // Read back to get assigned IP address
        apiConnector.read(instanceIp);
        String vmIpAddress = instanceIp.getAddress();
        s_logger.debug("Created instance IP:" + instanceIp.getName() + ": " + 
                vmIpAddress);

        // Plug notification to vrouter
        if (vrouterIpAddress == null) {
            s_logger.warn("Virtual machine: " + vmName + " host: " + hostName
                + " addPort notification NOT sent");
            return;
        }
        try {
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            if (vrouterApi == null) {
                   vrouterApi = new ContrailVRouterApi(
                         InetAddress.getByName(vrouterIpAddress), 
                         vrouterApiPort, false);
                   vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }
            boolean ret = vrouterApi.AddPort(UUID.fromString(vmiUuid),
                                         UUID.fromString(vmUuid), vmInterface.getName(),
                                         InetAddress.getByName(vmIpAddress),
                                         Utils.parseMacAddress(macAddress),
                                         UUID.fromString(vnUuid), isolatedVlanId, 
                                         primaryVlanId, vmName);
            if ( ret == true) {
                s_logger.info("VRouterAPi Add Port success - interface name: "
                              +  vmInterface.getDisplayName() 
                              + "(" + vmInterface.getName() + ")");
            } else {
                // log failure but don't worry. Periodic KeepAlive task will
                // attempt to connect to vRouter Agent and replay AddPorts.
                s_logger.error("VRouterAPi Add Port failed - interface name: "
                              +  vmInterface.getDisplayName() 
                              + "(" + vmInterface.getName() + ")");
            }

        }catch(Throwable e) {
            s_logger.error("Exception : " + e);
            e.printStackTrace();
        }
        s_logger.info("Create Virtual Machine : Done");
    }

    public void syncAddPortPerVirtualMachineInterface(String vnUuid, String vmUuid,
            String macAddress, String vmName, String vrouterIpAddress,
            String hostName, short isolatedVlanId, short primaryVlanId) throws IOException {
        s_logger.debug("syncAddPortPerVirtualMachineInterface : " 
                      + ", VN: " + vnUuid
                      + ", VM: " + vmName + " (" + vmUuid + ")"
                      + ", vrouterIpAddress: " + vrouterIpAddress
                      + ", hostName: " + hostName 
                      + ", vlan: " + isolatedVlanId + "/" + primaryVlanId);

        // Virtual network
        VirtualNetwork network = (VirtualNetwork) apiConnector.findById(
                VirtualNetwork.class, vnUuid);

        // Virtual machine
        VirtualMachine vm = (VirtualMachine) apiConnector.findById(
                VirtualMachine.class, vmUuid);

        // Virtual machine interface
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        VirtualMachineInterface vmInterface = null;
        for (ObjectReference<ApiPropertyBase> vmInterfaceRef :
                Utils.safe(vmInterfaceRefs)) {
            vmInterface = (VirtualMachineInterface)
                    apiConnector.findById(VirtualMachineInterface.class,
                            vmInterfaceRef.getUuid());
        }

        if (vmInterface == null) {
            s_logger.warn("Virtual machine: " + vmName
                          + " has no network interface");
            return;
        }

        // Instance Ip
        // Read back to get assigned IP address
        List<ObjectReference<ApiPropertyBase>> instanceIpBackRefs =
                vmInterface.getInstanceIpBackRefs();
        InstanceIp instanceIp = null;
        for (ObjectReference<ApiPropertyBase> instanceIpRef :
                Utils.safe(instanceIpBackRefs)) {
            instanceIp = (InstanceIp)
                    apiConnector.findById(InstanceIp.class,
                            instanceIpRef.getUuid());
        }

        if (instanceIp == null) {
            s_logger.warn("Virtual machine interface: " + vmInterface.getName()
                          + " has no ip address");
            return;
        }

        String vmIpAddress = instanceIp.getAddress();

        // Plug notification to vrouter
        if (vrouterIpAddress == null) {
            s_logger.warn("Virtual machine: " + vmName + " host: " + hostName
                + " AddPort notification NOT sent since vrouter-ip address missing");
            return;
        }
        try {
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            if (vrouterApi == null) {
                   vrouterApi = new ContrailVRouterApi(
                         InetAddress.getByName(vrouterIpAddress),
                         vrouterApiPort, false);
                   vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }
            boolean ret = vrouterApi.AddPort(UUID.fromString(vmInterface.getUuid()),
                               UUID.fromString(vmUuid), vmInterface.getName(),
                               InetAddress.getByName(vmIpAddress),
                               Utils.parseMacAddress(macAddress),
                               UUID.fromString(vnUuid), isolatedVlanId, 
                               primaryVlanId, vmName);
            if ( ret == true) {
                s_logger.info("VRouterAPi Add Port success - interface name: "
                              +  vmInterface.getDisplayName() 
                              + "(" + vmInterface.getName() + ")");
            } else {
                // log failure but don't worry. Periodic KeepAlive task will
                // attempt to connect to vRouter Agent and replay AddPorts.
                s_logger.error("VRouterAPi Add Port failed - interface name: "
                              +  vmInterface.getDisplayName() 
                              + "(" + vmInterface.getName() + ")");
            }
        }catch(Throwable e) {
            s_logger.error("Exception : " + e);
            e.printStackTrace();
        }
    }

    public void CreateVirtualNetwork(String vnUuid, String vnName,
            String subnetAddr, String subnetMask, String gatewayAddr, 
            short isolatedVlanId, short primaryVlanId,
            SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos) throws
            IOException {
        s_logger.info("Create Virtual Network: " 
                        + vnName + " (" + vnUuid + ")"
                        + ", Subnet/Mask/GW: " 
                        + subnetAddr + "/" + subnetMask + "/" + gatewayAddr);
        VirtualNetwork vn = new VirtualNetwork();
        vn.setName(vnName);
        vn.setDisplayName(vnName);
        vn.setUuid(vnUuid);
        vn.setIdPerms(vCenterIdPerms);
        vn.setParent(vCenterProject);
        SubnetUtils subnetUtils = new SubnetUtils(subnetAddr, subnetMask);  
        String cidr = subnetUtils.getInfo().getCidrSignature();
        VnSubnetsType subnet = new VnSubnetsType();
        String[] addr_pair = cidr.split("\\/");
        subnet.addIpamSubnets(new VnSubnetsType.IpamSubnetType(
                                   new SubnetType(addr_pair[0],
                                       Integer.parseInt(addr_pair[1])), gatewayAddr,
                                       null, UUID.randomUUID().toString(), true, null,
                                       null, false, null, null, vn.getName() + "-subnet"));

        vn.setNetworkIpam(vCenterIpam, subnet);
        apiConnector.create(vn); 
        if (vmMapInfos == null) {
            s_logger.info("Create Virtual Network: Done");
            return;
        }

        for (Map.Entry<String, VmwareVirtualMachineInfo> vmMapInfo :
            vmMapInfos.entrySet()) {
            String vmUuid = vmMapInfo.getKey();
            VmwareVirtualMachineInfo vmInfo = vmMapInfo.getValue();
            String macAddress = vmInfo.getMacAddress();
            String vmName = vmInfo.getName();
            String vrouterIpAddr = vmInfo.getVrouterIpAddress();
            String hostName = vmInfo.getHostName();
            CreateVirtualMachine(vnUuid, vmUuid, macAddress, vmName,
                    vrouterIpAddr, hostName, isolatedVlanId, primaryVlanId);
        }
        s_logger.info("Create Virtual Network: Done");
    }
    
    public void DeleteVirtualNetwork(String uuid) 
            throws IOException {
        s_logger.info("Delete virtual network: " + uuid);
        VirtualNetwork network = (VirtualNetwork) apiConnector.findById(
                VirtualNetwork.class, uuid);
        apiConnector.read(network);
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs = 
                network.getVirtualMachineInterfaceBackRefs();
        for (ObjectReference<ApiPropertyBase> vmInterfaceRef : 
                Utils.safe(vmInterfaceRefs)) {
            VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                    apiConnector.findById(VirtualMachineInterface.class,
                            vmInterfaceRef.getUuid());
            DeleteVirtualMachineInternal(vmInterface);
        }
        apiConnector.delete(VirtualNetwork.class, network.getUuid());     
        s_logger.info("Delete virtual network: Done");
    }
    
    private static boolean doIgnoreVirtualNetwork(String name) {
        // Ignore default, fabric, and link-local networks
        if (name.equals("__link_local__") || 
                name.equals("default-virtual-network") || 
                name.equals("ip-fabric")) {
            return true;
        }
        return false;
    }
    
    @SuppressWarnings("unchecked")
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
            // Ignore Vnc VNs where creator isn't "vcenter-plugin"
            if ((network.getIdPerms().getCreator() == null)  ||
                !(network.getIdPerms().getCreator().equals(VNC_VCENTER_PLUGIN))) {
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
                VirtualMachineInterface vmInterface =
                        (VirtualMachineInterface) apiConnector.findById(
                                VirtualMachineInterface.class,
                                vmInterfaceRef.getUuid());
                apiConnector.read(vmInterface);
                // Ignore Vnc VMInterfaces where "creator" isn't "vcenter-plugin"
                if (!vmInterface.getIdPerms().getCreator().equals(VNC_VCENTER_PLUGIN)) {
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
                // Ignore Vnc VMs where creator isn't "vcenter-plugin"
                if (!vm.getIdPerms().getCreator().equals(VNC_VCENTER_PLUGIN)) {
                    continue;
                }

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

    // KeepAlive with all active vRouter Agent Connections.
    public void vrouterAgentPeriodicConnectionCheck() {
        for (String vrouterIpAddress : vrouterApiMap.keySet()) {
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            // run Keep Alive with vRouter Agent.
            if (vrouterApi != null) {
              vrouterApi.PeriodicConnectionCheck();
            }
        }
    }
}
