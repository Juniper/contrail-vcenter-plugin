/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;



import java.util.UUID;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.SortedMap;
import java.util.UUID;

import org.apache.log4j.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.MethodSorters;

import org.apache.log4j.Logger;
import org.apache.commons.net.util.SubnetUtils;

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorMock;
import net.juniper.contrail.api.ApiConnectorFactory;
import net.juniper.contrail.api.ApiPropertyBase;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.FloatingIp;
import net.juniper.contrail.api.types.MacAddressesType;
import net.juniper.contrail.api.types.NetworkIpam;
import net.juniper.contrail.api.types.SecurityGroup;
import net.juniper.contrail.api.types.SubnetType;
import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.VnSubnetsType;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.IdPermsType;

import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.ManagedObjectReference;

@RunWith(JUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VncDBTest extends TestCase {
    private static final Logger s_logger =
        Logger.getLogger(VncDBTest.class);
    private static VncDB vncDB;
    private static ApiConnector _api;

    @Before
    public void globalSetUp() throws IOException {
        _api   = new ApiConnectorMock(null, 0);
        assertNotNull(_api);

        // Create default-domain,default-project
        Project vProject = new Project();
        vProject.setName("default-project");
        try {
            if (!_api.create(vProject)) {
                s_logger.error("Unable to create project: " + vProject.getName());
                fail("default-project creation failed");
                return;
            }
        } catch (IOException e) { 
            s_logger.error("Exception : " + e);
            e.printStackTrace();
            fail("default-project creation failed");
            return;
        }

        // Setup vnc object
        vncDB = new VncDB(null,0, Mode.VCENTER_ONLY);
        vncDB.setApiConnector(_api);
        assertNotNull(vncDB.getApiConnector());
        assertTrue(vncDB.isVncApiServerAlive());
        assertTrue(vncDB.Initialize());
    }

    @Test
    public void testVirtualNetworkAddDelete() throws IOException {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-A";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2#230";
        boolean externalIpam  = false;

        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(vnUuid);
        vnInfo.setName(vnName);
        vnInfo.setSubnetAddress(subnetAddr);
        vnInfo.setSubnetMask(subnetMask);
        vnInfo.setGatewayAddress(gatewayAddr);
        vnInfo.setPrimaryVlanId(primaryVlanId);
        vnInfo.setIsolatedVlanId(isolatedVlanId);
        vnInfo.setIpPoolEnabled(true);
        vnInfo.setRange(range);
        vnInfo.setExternalIpam(externalIpam);
        
        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);
        
        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);
        assertEquals(vn1.getUuid(), vnUuid);
        assertEquals(vn1.getName(), vnName);

        vnInfo = new VirtualNetworkInfo(vn1);
        // Delete virtual-network from api-server
        vncDB.deleteVirtualNetwork(vnInfo);

        // Verify virtual-network is deleted
        VirtualNetwork vn2 = (VirtualNetwork)  _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn2);
    }

    @Test
    public void testAddDeleteOneByOne() throws IOException {
        String vnUuid            = UUID.randomUUID().toString();
        String vnName            = "TestVN-C";
        String subnetAddr        = "192.168.3.0";
        String subnetMask        = "255.255.255.0";
        String gatewayAddr       = "192.168.3.1";
        short isolatedVlanId     = 300;
        short primaryVlanId      = 301;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2#230";
        boolean externalIpam  = false;

        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(vnUuid);
        vnInfo.setName(vnName);
        vnInfo.setSubnetAddress(subnetAddr);
        vnInfo.setSubnetMask(subnetMask);
        vnInfo.setGatewayAddress(gatewayAddr);
        vnInfo.setPrimaryVlanId(primaryVlanId);
        vnInfo.setIsolatedVlanId(isolatedVlanId);
        vnInfo.setIpPoolEnabled(true);
        vnInfo.setRange(range);
        vnInfo.setExternalIpam(externalIpam);
        
        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);
        
        // Verify virtual-network creation
        VirtualNetwork vn = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn);

        // Create Virtual Machine
        String vmUuid            = UUID.randomUUID().toString();
        String vmName            = "VMC";
        String vrouterIpAddress  = null; //"10.84.24.45";
        String hostName          = "10.20.30.40";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        
        VirtualMachineInfo vmInfo = new VirtualMachineInfo(vmUuid);
        vmInfo.setName(vmName);
        vmInfo.setHostName(hostName);
        vmInfo.setHmor(hmor);
        vmInfo.setVrouterIpAddress(vrouterIpAddress);
        vmInfo.setPowerState(VirtualMachinePowerState.poweredOff);
        vncDB.createVirtualMachine(vmInfo);

        // Verify virtual-machine is created on api-server
        VirtualMachine vm = (VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNotNull(vm);
        assertEquals(vm.getUuid(), vmUuid);
        assertEquals(vm.getName(), vmUuid);
        assertEquals(vm.getDisplayName(), vrouterIpAddress);
        assertEquals(vm.getIdPerms(), vncDB.getVCenterIdPerms());

        VirtualMachineInterfaceInfo vmiInfo = 
                new VirtualMachineInterfaceInfo(vmInfo, vnInfo);
        String macAddress        = "00:11:22:33:44:55";
        vmiInfo.setMacAddress(macAddress);
        vncDB.createVirtualMachineInterface(vmiInfo);
        vncDB.createInstanceIp(vmiInfo);
        
        // Read again the VM to read the back refs to VMI
        _api.read(vm);
        
        //find vmInterface corresponding to vmUUID, VnUUID
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertEquals(vmInterfaceRefs.size(), 1);
        ObjectReference<ApiPropertyBase> vmInterfaceRef = vmInterfaceRefs.get(0);
        assertNotNull(vmInterfaceRef);

        // Verify virtual-machine-interface is created on api-server
        String vmInterfaceUuid = vmInterfaceRef.getUuid();
        VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                _api.findById(VirtualMachineInterface.class, vmInterfaceUuid);
        assertNotNull(vmInterface);
        assertEquals(vmInterface.getUuid(), vmiInfo.getUuid());
        assertEquals(vmInterface.getName(), vmiInfo.getUuid());
        assertEquals(vmInterface.getIdPerms(), vncDB.getVCenterIdPerms());
        assertEquals(vmInterface.getParent(), vncDB.getVCenterProject());
        List<String> macAddresses = vmInterface.getMacAddresses().getMacAddress();
        assertTrue(macAddresses.size() > 0);
        assertEquals(macAddresses.get(0), macAddress);
       
        String vmInterfaceName = "vmi-" + vn.getName() + "-" + vmName;
        assertEquals(vmInterface.getDisplayName(), vmInterfaceName);

        List<ObjectReference<ApiPropertyBase>> vmRefs = vmInterface.getVirtualMachine();
        assertNotNull(vmRefs);
        assertEquals(1, vmRefs.size());
        assertEquals(vm.getUuid(), vmRefs.get(0).getUuid());
       
        List<ObjectReference<ApiPropertyBase>> vmiVnRefs = vmInterface.getVirtualNetwork();
        assertNotNull(vmiVnRefs);
        assertEquals(1, vmiVnRefs.size());
        assertEquals(vn.getUuid(), vmiVnRefs.get(0).getUuid());
       
        // find instance-ip corresponding to virtual-machine-interface
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                vmInterface.getInstanceIpBackRefs();
        assertNotNull(instanceIpRefs);
        assertEquals(1, instanceIpRefs.size());
        ObjectReference<ApiPropertyBase> instanceIpRef = instanceIpRefs.get(0);
        assertNotNull(instanceIpRef);

        InstanceIp instanceIp = (InstanceIp)
                _api.findById(InstanceIp.class, instanceIpRef.getUuid());
        assertNotNull(instanceIp);
        String instanceIpName = "ip-" + vn.getName() + "-" + vmName;
        assertEquals(instanceIp.getDisplayName(), instanceIpName);
        assertEquals(instanceIp.getUuid(), instanceIpRef.getUuid());
        assertEquals(instanceIp.getName(), instanceIpRef.getUuid());
        assertEquals(instanceIp.getIdPerms(), vncDB.getVCenterIdPerms());
        
        List<ObjectReference<ApiPropertyBase>> instIpVnRefs = instanceIp.getVirtualNetwork();
        assertNotNull(instIpVnRefs);
        assertEquals(instIpVnRefs.size(), 1);
        assertEquals(instIpVnRefs.get(0).getUuid(), vn.getUuid());

        List<ObjectReference<ApiPropertyBase>> vmiRefs = instanceIp.getVirtualMachineInterface();
        assertNotNull(vmiRefs);
        assertEquals(1, vmiRefs.size());
        assertEquals(vmInterface.getUuid(), vmiRefs.get(0).getUuid());

        // Delete
        vncDB.deleteInstanceIp(vmiInfo);

        // Verify instance-ip is deleted from  api-server
        InstanceIp ip1 =(InstanceIp) _api.findById(InstanceIp.class, instanceIp.getUuid());
        assertNull(ip1);

        vncDB.deleteVirtualMachineInterface(vmiInfo);

        // Verify virtual-machine-inteeface is deleted from  api-server
        VirtualMachineInterface vmi1 =(VirtualMachineInterface) 
                                    _api.findById(VirtualMachineInterface.class, vmInterfaceUuid);
        assertNull(vmi1);

        vncDB.deleteVirtualMachine(vmInfo);
        
        // Verify virtual-machine is deleted from  api-server
        VirtualMachine vm1 =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNull(vm1);

        // Delete virtual-network from api-server
        vncDB.deleteVirtualNetwork(vnInfo);

        // Verify virtual-network is deleted
        VirtualNetwork vn1 = (VirtualNetwork)  _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn1);
    }


    @Test
    public void testAddHierarchicalDelete() throws IOException {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-B";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2#230";
        boolean externalIpam  = false;

        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(vnUuid);
        vnInfo.setName(vnName);
        vnInfo.setSubnetAddress(subnetAddr);
        vnInfo.setSubnetMask(subnetMask);
        vnInfo.setGatewayAddress(gatewayAddr);
        vnInfo.setPrimaryVlanId(primaryVlanId);
        vnInfo.setIsolatedVlanId(isolatedVlanId);
        vnInfo.setIpPoolEnabled(true);
        vnInfo.setRange(range);
        vnInfo.setExternalIpam(externalIpam);
        
        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);
        
        // Verify virtual-network creation
        VirtualNetwork vn = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn);
        assertEquals(vn.getUuid(), vnUuid);
        assertEquals(vn.getName(), vnName);

        // Create Virtual Machine
        String vmUuid            = UUID.randomUUID().toString();
        String vmName            = "VMC";
        String vrouterIpAddress  = null; //"10.84.24.45";
        String hostName          = "10.20.30.40";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        
        VirtualMachineInfo vmInfo = new VirtualMachineInfo(vmUuid);
        vmInfo.setName(vmName);
        vmInfo.setHostName(hostName);
        vmInfo.setHmor(hmor);
        vmInfo.setVrouterIpAddress(vrouterIpAddress);
        vmInfo.setPowerState(VirtualMachinePowerState.poweredOff);
        vncDB.createVirtualMachine(vmInfo);

        // Verify virtual-machine is created on api-server
        VirtualMachine vm = (VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNotNull(vm);
        assertEquals(vm.getUuid(), vmUuid);
        assertEquals(vm.getName(), vmUuid);
        assertEquals(vm.getDisplayName(), vrouterIpAddress);
        assertEquals(vm.getIdPerms(), vncDB.getVCenterIdPerms());

        VirtualMachineInterfaceInfo vmiInfo = 
                new VirtualMachineInterfaceInfo(vmInfo, vnInfo);
        String macAddress        = "00:11:22:33:44:55";
        vmiInfo.setMacAddress(macAddress);
        vncDB.createVirtualMachineInterface(vmiInfo);
        vncDB.createInstanceIp(vmiInfo);
        
        // Read again the VM to read the back refs to VMI
        _api.read(vm);
        
        //find vmInterface corresponding to vmUUID, VnUUID
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertEquals(vmInterfaceRefs.size(), 1);
        ObjectReference<ApiPropertyBase> vmInterfaceRef = vmInterfaceRefs.get(0);
        assertNotNull(vmInterfaceRef);

        // Verify virtual-machine-interface is created on api-server
        String vmInterfaceUuid = vmInterfaceRef.getUuid();
        VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                _api.findById(VirtualMachineInterface.class, vmInterfaceUuid);
        assertNotNull(vmInterface);
        assertEquals(vmInterface.getUuid(), vmiInfo.getUuid());
        assertEquals(vmInterface.getName(), vmiInfo.getUuid());
        assertEquals(vmInterface.getIdPerms(), vncDB.getVCenterIdPerms());
        assertEquals(vmInterface.getParent(), vncDB.getVCenterProject());
        List<String> macAddresses = vmInterface.getMacAddresses().getMacAddress();
        assertTrue(macAddresses.size() > 0);
        assertEquals(macAddresses.get(0), macAddress);
       
        String vmInterfaceName = "vmi-" + vn.getName() + "-" + vmName;
        assertEquals(vmInterface.getDisplayName(), vmInterfaceName);

        List<ObjectReference<ApiPropertyBase>> vmRefs = vmInterface.getVirtualMachine();
        assertNotNull(vmRefs);
        assertEquals(1, vmRefs.size());
        assertEquals(vm.getUuid(), vmRefs.get(0).getUuid());
       
        List<ObjectReference<ApiPropertyBase>> vmiVnRefs = vmInterface.getVirtualNetwork();
        assertNotNull(vmiVnRefs);
        assertEquals(1, vmiVnRefs.size());
        assertEquals(vn.getUuid(), vmiVnRefs.get(0).getUuid());
       
        // find instance-ip corresponding to virtual-machine-interface
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                vmInterface.getInstanceIpBackRefs();
        assertNotNull(instanceIpRefs);
        assertEquals(1, instanceIpRefs.size());
        ObjectReference<ApiPropertyBase> instanceIpRef = instanceIpRefs.get(0);
        assertNotNull(instanceIpRef);

        InstanceIp instanceIp = (InstanceIp)
                _api.findById(InstanceIp.class, instanceIpRef.getUuid());
        assertNotNull(instanceIp);
        String instanceIpName = "ip-" + vn.getName() + "-" + vmName;
        assertEquals(instanceIp.getDisplayName(), instanceIpName);
        assertEquals(instanceIp.getUuid(), instanceIpRef.getUuid());
        assertEquals(instanceIp.getName(), instanceIpRef.getUuid());
        assertEquals(instanceIp.getIdPerms(), vncDB.getVCenterIdPerms());
        
        List<ObjectReference<ApiPropertyBase>> instIpVnRefs = instanceIp.getVirtualNetwork();
        assertNotNull(instIpVnRefs);
        assertEquals(instIpVnRefs.size(), 1);
        assertEquals(instIpVnRefs.get(0).getUuid(), vn.getUuid());

        List<ObjectReference<ApiPropertyBase>> vmiRefs = instanceIp.getVirtualMachineInterface();
        assertNotNull(vmiRefs);
        assertEquals(1, vmiRefs.size());
        assertEquals(vmInterface.getUuid(), vmiRefs.get(0).getUuid());


        // Delete virtual-network from api-server
        // This should in turn delete thr virtual-machine,
        // virtual-machine-interfce, instance-ip etc
        vncDB.deleteVirtualNetwork(vnInfo);
 
        // Verify instance-ip is deleted from  api-server
        InstanceIp ip1 =(InstanceIp) _api.findById(InstanceIp.class, instanceIp.getUuid());
        assertNull(ip1);

        // Verify virtual-machine-inteeface is deleted from  api-server
        VirtualMachineInterface vmi1 =(VirtualMachineInterface) 
                                    _api.findById(VirtualMachineInterface.class, vmInterfaceUuid);
        assertNull(vmi1);
       
        // Verify virtual-network is deleted
        VirtualNetwork vn2 = (VirtualNetwork)  _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn2);
        
        vncDB.deleteVirtualMachine(vmInfo);
        // Verify virtual-machine is deleted from  api-server
        VirtualMachine vm1 =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNull(vm1); 
    }

    @Test
    public void testAddHierarchicalDeleteVM() throws IOException {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-C";
        String subnetAddr     = "192.168.3.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.3.1";
        short primaryVlanId   = 300;
        short isolatedVlanId  = 301;
        boolean ipPoolEnabled = true;
        String range          = "192.18.3.2#230";
        boolean externalIpam  = false;

        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(vnUuid);
        vnInfo.setName(vnName);
        vnInfo.setSubnetAddress(subnetAddr);
        vnInfo.setSubnetMask(subnetMask);
        vnInfo.setGatewayAddress(gatewayAddr);
        vnInfo.setPrimaryVlanId(primaryVlanId);
        vnInfo.setIsolatedVlanId(isolatedVlanId);
        vnInfo.setIpPoolEnabled(true);
        vnInfo.setRange(range);
        vnInfo.setExternalIpam(externalIpam);
        
        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);
        
        // Verify virtual-network creation
        VirtualNetwork vn = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn);
        assertEquals(vn.getUuid(), vnUuid);
        assertEquals(vn.getName(), vnName);

        // Create Virtual Machine
        String vmUuid            = UUID.randomUUID().toString();
        String vmName            = "VMC-1";
        String vrouterIpAddress  = null; //"10.84.24.45";
        String hostName          = "10.20.30.40";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        
        VirtualMachineInfo vmInfo = new VirtualMachineInfo(vmUuid);
        vmInfo.setName(vmName);
        vmInfo.setHostName(hostName);
        vmInfo.setHmor(hmor);
        vmInfo.setVrouterIpAddress(vrouterIpAddress);
        vmInfo.setPowerState(VirtualMachinePowerState.poweredOff);
        vncDB.createVirtualMachine(vmInfo);

        // Verify virtual-machine is created on api-server
        VirtualMachine vm = (VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNotNull(vm);
        assertEquals(vm.getUuid(), vmUuid);
        assertEquals(vm.getName(), vmUuid);
        assertEquals(vm.getDisplayName(), vrouterIpAddress);
        assertEquals(vm.getIdPerms(), vncDB.getVCenterIdPerms());

        VirtualMachineInterfaceInfo vmiInfo = 
                new VirtualMachineInterfaceInfo(vmInfo, vnInfo);
        String macAddress        = "00:11:28:34:44:55";
        vmiInfo.setMacAddress(macAddress);
        vncDB.createVirtualMachineInterface(vmiInfo);
        vncDB.createInstanceIp(vmiInfo);
        
        // Read again the VM to read the back refs to VMI
        _api.read(vm);
        
        //find vmInterface corresponding to vmUUID, VnUUID
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertEquals(vmInterfaceRefs.size(), 1);
        ObjectReference<ApiPropertyBase> vmInterfaceRef = vmInterfaceRefs.get(0);
        assertNotNull(vmInterfaceRef);

        // Verify virtual-machine-interface is created on api-server
        String vmInterfaceUuid = vmInterfaceRef.getUuid();
        VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                _api.findById(VirtualMachineInterface.class, vmInterfaceUuid);
        assertNotNull(vmInterface);
        assertEquals(vmInterface.getUuid(), vmiInfo.getUuid());
        assertEquals(vmInterface.getName(), vmiInfo.getUuid());
        assertEquals(vmInterface.getIdPerms(), vncDB.getVCenterIdPerms());
        assertEquals(vmInterface.getParent(), vncDB.getVCenterProject());
        List<String> macAddresses = vmInterface.getMacAddresses().getMacAddress();
        assertTrue(macAddresses.size() > 0);
        assertEquals(macAddresses.get(0), macAddress);
       
        String vmInterfaceName = "vmi-" + vn.getName() + "-" + vmName;
        assertEquals(vmInterface.getDisplayName(), vmInterfaceName);

        List<ObjectReference<ApiPropertyBase>> vmRefs = vmInterface.getVirtualMachine();
        assertNotNull(vmRefs);
        assertEquals(1, vmRefs.size());
        assertEquals(vm.getUuid(), vmRefs.get(0).getUuid());
       
        List<ObjectReference<ApiPropertyBase>> vmiVnRefs = vmInterface.getVirtualNetwork();
        assertNotNull(vmiVnRefs);
        assertEquals(1, vmiVnRefs.size());
        assertEquals(vn.getUuid(), vmiVnRefs.get(0).getUuid());
       
        // find instance-ip corresponding to virtual-machine-interface
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                vmInterface.getInstanceIpBackRefs();
        assertNotNull(instanceIpRefs);
        assertEquals(1, instanceIpRefs.size());
        ObjectReference<ApiPropertyBase> instanceIpRef = instanceIpRefs.get(0);
        assertNotNull(instanceIpRef);

        InstanceIp instanceIp = (InstanceIp)
                _api.findById(InstanceIp.class, instanceIpRef.getUuid());
        assertNotNull(instanceIp);
        String instanceIpName = "ip-" + vn.getName() + "-" + vmName;
        assertEquals(instanceIp.getDisplayName(), instanceIpName);
        assertEquals(instanceIp.getUuid(), instanceIpRef.getUuid());
        assertEquals(instanceIp.getName(), instanceIpRef.getUuid());
        assertEquals(instanceIp.getIdPerms(), vncDB.getVCenterIdPerms());
        
        List<ObjectReference<ApiPropertyBase>> instIpVnRefs = instanceIp.getVirtualNetwork();
        assertNotNull(instIpVnRefs);
        assertEquals(instIpVnRefs.size(), 1);
        assertEquals(instIpVnRefs.get(0).getUuid(), vn.getUuid());

        List<ObjectReference<ApiPropertyBase>> vmiRefs = instanceIp.getVirtualMachineInterface();
        assertNotNull(vmiRefs);
        assertEquals(1, vmiRefs.size());
        assertEquals(vmInterface.getUuid(), vmiRefs.get(0).getUuid());

        vncDB.deleteVirtualMachine(vmInfo);
        // Verify virtual-machine-inteeface is deleted from  api-server
        VirtualMachineInterface vmi1 =(VirtualMachineInterface) 
                                    _api.findById(VirtualMachineInterface.class, vmInterfaceUuid);
        assertNull(vmi1);
 
        // Verify instance-ip is deleted from  api-server
        InstanceIp ip1 =(InstanceIp) _api.findById(InstanceIp.class, instanceIp.getUuid());
        assertNull(ip1);

        // Verify virtual-machine is deleted from  api-server
        VirtualMachine vm1 =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNull(vm1);

        // Delete virtual-network from api-server
        // This should in turn delete thr virtual-machine,
        // virtual-machine-interfce, instance-ip etc
        vncDB.deleteVirtualNetwork(vnInfo);
        
        // Verify virtual-network is deleted
        VirtualNetwork vn2 = (VirtualNetwork)  _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn2);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDeleteVirtualNetworkNullInput() throws IOException {
        vncDB.deleteVirtualNetwork(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDeleteVirtualMachineNullInput() throws IOException {
        vncDB.deleteVirtualMachine(null);
    }
}
