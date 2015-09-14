/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.List;
import java.net.InetAddress;

import com.google.common.base.Throwables;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import net.juniper.contrail.api.types.Project;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.anyByte;
import static org.mockito.Mockito.anyObject;

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
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.ManagedObjectReference;

@RunWith(JUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VCenterMonitorTaskTest extends TestCase {
    private static Logger s_logger = Logger.getLogger(VCenterMonitorTaskTest.class);
    private static VncDB _vncDB;
    private static VCenterDB _vcenterDB;
    private static ApiConnector _api;
    private static VCenterMonitorTask _vcenterMonitorTask;

    @Before
    public void globalSetUp() throws Exception {
        _api   = new ApiConnectorMock(null, 0);

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


        // Setup VCenter object
        //ApiConnectorFactory.setImplementation(ApiConnectorMock.class);
        _vcenterMonitorTask = new VCenterMonitorTask("https://10.20.30.40/sdk", "admin", "admin123",
                                   "unittest_dc", "unittest_dvs", null, 0, "unittest_fabric_pg");

        // Create mock for VCenterDB.
        _vcenterDB = mock(VCenterDB.class);
        _vcenterMonitorTask.setVCenterDB(_vcenterDB);

        _vncDB     = _vcenterMonitorTask.getVncDB();
        _vncDB.setApiConnector(_api);
        assertTrue(_vncDB.isVncApiServerAlive());
        assertTrue(_vncDB.Initialize());

        // Setup mock ContrailVRouterApi connection for vrouterIp = 10.84.24.45
        HashMap<String, ContrailVRouterApi> vrouterApiMap = _vncDB.getVRouterApiMap();
        ContrailVRouterApi vrouterApi = mock(ContrailVRouterApi.class);
        when(vrouterApi.AddPort(any(UUID.class), any(UUID.class), anyString(), any(InetAddress.class),
                                any(byte[].class), any(UUID.class), anyShort(), anyShort(),
                                anyString())).thenReturn(true);
        when(vrouterApi.DeletePort(any(UUID.class))).thenReturn(true);
        vrouterApiMap.put("10.84.24.45", vrouterApi);
    }

    // +*********************************************************************+
    // + One Time Sync Tests                                                 +
    // **********************************************************************+

    //  VNC     : 1 VN & 1 VM
    //  VCENTER : 1 VN 
    //  Afert Virtual-machine sync, Vnc shouldn't have any VMs.
    @Test
    public void TestSyncVirtualMachineTC1() throws IOException {
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

        // Fill vmMapInfos such that 2 VMs will be created
        // as part of CreateVirtualNetwork() call
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        // Virtual Machine #1 info
        String vmUuid            = UUID.randomUUID().toString();
        String macAddress        = "00:11:22:33:44:55";
        String vmName            = "VM-C";
        String vrouterIpAddress  = "10.84.24.45";
        String hostName          = "10.20.30.40";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        
        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmName, hostName, hmor,
                                                    vrouterIpAddress, macAddress,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuid, vmwareVmInfo);

        // Create virtual-network on api-server
        // This call should also result in VM creation on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Verify virtual-machine is created on api-server
        VirtualMachine vm = (VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNotNull(vm);

        // Populate VncVirtualNetworkInfo.
        SortedMap<String, VncVirtualNetworkInfo> vncNetworkInfo = null;
        try {
           vncNetworkInfo = _vncDB.populateVirtualNetworkInfo();
        } catch (Exception e) {
            e.printStackTrace();
            fail("testSyncVirtualMachines failed due to populateVirtualNetworkInfo failure");
        }

        assertNotNull(vncNetworkInfo);
        assertEquals(1, vncNetworkInfo.size());
        Map.Entry<String, VncVirtualNetworkInfo> vncItem = null;
        Iterator<Entry<String, VncVirtualNetworkInfo>> vncIter = 
                                 vncIter = vncNetworkInfo.entrySet().iterator();
        assertNotNull(vncIter);
        vncItem = (Entry<String, VncVirtualNetworkInfo>) 
                                           (vncIter.hasNext() ? vncIter.next() : null);
        assertNotNull(vncItem);
        assertEquals(vnUuid, vncItem.getKey());
        assertNotNull(vncItem.getValue());

        VmwareVirtualNetworkInfo vmwareNetworkInfo = new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        //Sync virtual-machines between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualMachines(vncItem.getKey(),
                                             vmwareNetworkInfo, vncItem.getValue());

        // Virtual-machine should be deleted since it's no present on VCenter
        // Verify virtual-machine is deleted from  api-server
        VirtualMachine vm1 =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNull(vm1);
    }
    
    //  VNC     : 1 VN
    //  VCENTER : 1 VN  & 1 VM
    //  After Virtual-machine sync, Vnc should have 1 VM.
    @Test
    public void TestSyncVirtualMachineTC2() throws IOException {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-A";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2 # 230";
        boolean externalIpam  = false;

        // Fill vmMapInfos as null such that no VM will be created
        // as part of CreateVirtualNetwork() call
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos =  null;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate VncVirtualNetworkInfo.
        SortedMap<String, VncVirtualNetworkInfo> vncNetworkInfo = null;
        try {
           vncNetworkInfo = _vncDB.populateVirtualNetworkInfo();
        } catch (Exception e) {
            e.printStackTrace();
            fail("testSyncVirtualMachines failed due to populateVirtualNetworkInfo failure");
        }

        assertNotNull(vncNetworkInfo);
        assertEquals(1, vncNetworkInfo.size());
        Map.Entry<String, VncVirtualNetworkInfo> vncItem = null;
        Iterator<Entry<String, VncVirtualNetworkInfo>> vncIter = 
                                 vncIter = vncNetworkInfo.entrySet().iterator();
        assertNotNull(vncIter);
        vncItem = (Entry<String, VncVirtualNetworkInfo>) 
                                           (vncIter.hasNext() ? vncIter.next() : null);
        assertNotNull(vncItem);
        assertEquals(vnUuid, vncItem.getKey());
        assertNotNull(vncItem.getValue());

        // Populate VmwareVirtualMachineInfo 
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos = 
                                    new TreeMap<String, VmwareVirtualMachineInfo>();

        String vmUuid            = UUID.randomUUID().toString();
        String vmName            = "VMC";
        String hostName          = "10.20.30.40";
        String vrouterIpAddress  = "10.84.24.45";
        String macAddress        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmName, hostName, hmor,
                                         vrouterIpAddress, macAddress, 
                                         VirtualMachinePowerState.poweredOff);
        vmInfos.put(vmUuid, vmInfo);

        // Populate VmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo vmwareNetworkInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        //Sync virtual-machines between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualMachines(vncItem.getKey(),
                                             vmwareNetworkInfo, vncItem.getValue());

        // Virtual-machine should be created on api-server since it's present on VCenter
        VirtualMachine vm1 =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuid);
        assertNotNull(vm1);
    }

    //  VNC     : 1 VN (TestVN-A) & 1 VM (VM-A)
    //  VCENTER : 1 VN (TestVN-A) & 1 VM (VM-B)
    //  After Virtual-machine sync, Vnc should have 1 VM (VM-B).
    @Test
    public void TestSyncVirtualMachineTC3() throws IOException {
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

        String vmUuidA            = UUID.randomUUID().toString();
        String macAddressA        = "00:11:22:33:44:55";
        String vmNameA            = "VM-A";
        String vrouterIpAddressA  = "10.84.24.45";
        String hostNameA          = "10.20.30.40";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        
        // Fill vmMapInfos such that 2 VMs will be created
        // as part of CreateVirtualNetwork() call
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameA, hostNameA, hmor,
                                                    vrouterIpAddressA, macAddressA,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuidA, vmwareVmInfo);

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate VncVirtualNetworkInfo.
        SortedMap<String, VncVirtualNetworkInfo> vncNetworkInfo = null;
        try {
           vncNetworkInfo = _vncDB.populateVirtualNetworkInfo();
        } catch (Exception e) {
            e.printStackTrace();
            fail("testSyncVirtualMachines failed due to populateVirtualNetworkInfo failure");
        }

        assertNotNull(vncNetworkInfo);
        assertEquals(1, vncNetworkInfo.size());
        Map.Entry<String, VncVirtualNetworkInfo> vncItem = null;
        Iterator<Entry<String, VncVirtualNetworkInfo>> vncIter = 
                                 vncIter = vncNetworkInfo.entrySet().iterator();
        assertNotNull(vncIter);
        vncItem = (Entry<String, VncVirtualNetworkInfo>) 
                                           (vncIter.hasNext() ? vncIter.next() : null);
        assertNotNull(vncItem);
        assertEquals(vnUuid, vncItem.getKey());
        assertNotNull(vncItem.getValue());

        // Populate VmwareVirtualMachineInfo 
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos = 
                                    new TreeMap<String, VmwareVirtualMachineInfo>();

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B";
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:56";
        hmor.setVal("host-19208");
        hmor.setType("HostSystem");
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOff);
        vmInfos.put(vmUuidB, vmInfo);

        // Populate VmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo vmwareNetworkInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        //Sync virtual-machines between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualMachines(vncItem.getKey(),
                                             vmwareNetworkInfo, vncItem.getValue());

        // api-server should have 2 VMs (VM-A & VM-B)
        List<VirtualMachine> vncVmList = (List<VirtualMachine>)_api.list(VirtualMachine.class, null);
        assertNotNull(vncVmList);
        assertEquals(1, vncVmList.size());

        // VM-A should be deleted from api-server since itwasn't present on VCenter
        VirtualMachine vmA =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidA);
        assertNull(vmA);

        // VM-B should be created on api-server since it's present on VCenter
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);
    }

    //  VNC     : 1 VN (TestVN-A) 
    //  VCENTER : 0 VN 
    //  After virtual-network sync, Vnc should have 0 VNs.
    @Test
    public void TestSyncVirtualNetworksTC1() throws Exception {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-A";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2 # 230";
        boolean externalIpam  = false;

        // Set vmMapInfos to null
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = null;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(null);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(null);

        //Sync virtual-networks between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualNetworks();

        // TestVN-A should be deleted from api-server since itwasn't present on VCenter
        VirtualNetwork vn =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn);
    }

    //  VNC     : 0 VN
    //  VCENTER : 1 VN (TestVN-B) 
    //  After virtual-network sync, Vnc should have 1 VN (TestVN-B).
    @Test
    public void TestSyncVirtualNetworksTC2() throws Exception {
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

        SortedMap<String, VmwareVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VmwareVirtualNetworkInfo>();

        VmwareVirtualNetworkInfo vnInfo = new
                VmwareVirtualNetworkInfo(vnName, isolatedVlanId, primaryVlanId, 
                                    null, subnetAddr, subnetMask, gatewayAddr, 
                                    ipPoolEnabled, range, externalIpam);
        vnInfos.put(vnUuid, vnInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnInfos);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(vnInfos);

        //Sync virtual-networks between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualNetworks();

        // TestVN-B should be created on api-server since it's present on VCenter
        VirtualNetwork vn =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn);
    }

    //  VNC     : 1 VN (TestVN-A)
    //  VCENTER : 1 VN (TestVN-B) 
    //  After virtual-network sync, Vnc should have 1 VN (TestVN-B).
    @Test
    public void TestSyncVirtualNetworksTC3() throws Exception {
        String vnUuidA         = UUID.randomUUID().toString();
        String vnNameA         = "TestVN-A";
        String subnetAddrA     = "192.168.2.0";
        String subnetMaskA     = "255.255.255.0";
        String gatewayAddrA    = "192.168.2.1";
        short primaryVlanIdA   = 200;
        short isolatedVlanIdA  = 201;
        boolean ipPoolEnabledA = true;
        String rangeA          = "192.18.2.2 # 230";
        boolean externalIpam   = false;

        // Set vmMapInfos to null
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = null;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuidA, vnNameA, subnetAddrA, subnetMaskA, gatewayAddrA,
                                    isolatedVlanIdA, primaryVlanIdA,
                                    ipPoolEnabledA, rangeA, externalIpam, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidA);
        assertNotNull(vn1);

        String vnUuidB         = UUID.randomUUID().toString();
        String vnNameB         = "TestVN-B";
        String subnetAddrB     = "192.168.3.0";
        String subnetMaskB     = "255.255.255.0";
        String gatewayAddrB    = "192.168.3.1";
        short primaryVlanIdB   = 300;
        short isolatedVlanIdB  = 301;
        boolean ipPoolEnabledB = true;
        String rangeB          = "192.18.3.2#230";

        SortedMap<String, VmwareVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VmwareVirtualNetworkInfo>();

        VmwareVirtualNetworkInfo vnInfo = new
                VmwareVirtualNetworkInfo(vnNameB, isolatedVlanIdB, 
                                    primaryVlanIdB, null, subnetAddrB, 
                                    subnetMaskB, gatewayAddrB,
                                    ipPoolEnabledB, rangeB, externalIpam);
        vnInfos.put(vnUuidB, vnInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnInfos);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(vnInfos);

        //Sync virtual-networks between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualNetworks();

        // TestVN-B should be created on api-server since it's present on VCenter
        VirtualNetwork vn =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidA);
        assertNull(vn);

        // TestVN-A should be deleted from api-server since it's not present on VCenter
        VirtualNetwork vnB =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidB);
        assertNotNull(vnB);
    }

    //  VNC     : 0 VN
    //  VCENTER : 1 VN (TestVN-B)  & VM (VM-B) : VmPoweredOff
    //  After virtual-network sync, Vnc should have 1 VN (TestVN-B) & VM (VM-B).
    @Test
    public void TestSyncVirtualNetworksTC4() throws Exception {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-TC4";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2#230";
        boolean externalIpam  = true;

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B-TC4";
        String hostNameB          = "VM-B-HostName";
        String ipAddressB          = "192.168.2.100";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:56";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOff);
        vmInfo.setIpAddress(ipAddressB);
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos = 
                                    new TreeMap<String, VmwareVirtualMachineInfo>();
        vmInfos.put(vmUuidB, vmInfo);

        SortedMap<String, VmwareVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VmwareVirtualNetworkInfo>();

        VmwareVirtualNetworkInfo vnInfo = new
                VmwareVirtualNetworkInfo(vnName, isolatedVlanId, primaryVlanId, 
                                    vmInfos, subnetAddr, subnetMask, gatewayAddr, 
                                    ipPoolEnabled, range, externalIpam);
        vnInfos.put(vnUuid, vnInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnInfos);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(vnInfos);

        //Sync virtual-networks between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualNetworks();

        // TestVN-B should be created on api-server since it's present on VCenter
        VirtualNetwork vn =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn);

        // MN-B should be created on api-server since it's present on VCenter
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);

        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vmB.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertEquals(1, vmInterfaceRefs.size());

        String vmInterfaceUuid = vmInterfaceRefs.get(0).getUuid();
        VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                                  _api.findById(VirtualMachineInterface.class,
                                                vmInterfaceUuid);
        assertNotNull(vmInterface);

        // Check that instance-ip is null
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                vmInterface.getInstanceIpBackRefs();
        assertNull(instanceIpRefs);

    }

    //  VNC     : 0 VN
    //  VCENTER : 1 VN (TestVN-B)  & VM (VM-B) : VmPoweredOn
    //  After virtual-network sync, Vnc should have 1 VN (TestVN-B) & VM (VM-B).
    @Test
    public void TestSyncVirtualNetworksTC5() throws Exception {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-TC5";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2#230";
        boolean externalIpam  = true;

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B-TC5";
        String hostNameB          = "VM-B-HostName";
        String ipAddressB          = "192.168.2.100";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:56";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOn);
        vmInfo.setIpAddress(ipAddressB);
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos = 
                                    new TreeMap<String, VmwareVirtualMachineInfo>();
        vmInfos.put(vmUuidB, vmInfo);

        SortedMap<String, VmwareVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VmwareVirtualNetworkInfo>();

        VmwareVirtualNetworkInfo vnInfo = new
                VmwareVirtualNetworkInfo(vnName, isolatedVlanId, primaryVlanId, 
                                    vmInfos, subnetAddr, subnetMask, gatewayAddr, 
                                    ipPoolEnabled, range, externalIpam);
        vnInfos.put(vnUuid, vnInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnInfos);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(vnInfos);

        //Sync virtual-networks between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualNetworks();

        // TestVN-B should be created on api-server since it's present on VCenter
        VirtualNetwork vn =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn);

        // MN-B should be created on api-server since it's present on VCenter
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);

        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vmB.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertEquals(1, vmInterfaceRefs.size());

        String vmInterfaceUuid = vmInterfaceRefs.get(0).getUuid();
        VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                                  _api.findById(VirtualMachineInterface.class,
                                                vmInterfaceUuid);
        assertNotNull(vmInterface);

        // Check that instance-ip is null
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                vmInterface.getInstanceIpBackRefs();
        assertNotNull(instanceIpRefs);

    }

    // +*********************************************************************+
    // + Periodic Sync Tests (DHCP Networks)                                 +
    // **********************************************************************+

    //  prevVCenterINfo : 1 VN(DHCP) & 1 VM (VM-A-PoweredOff)
    //  currVCenterINfo : 1 VN(DHCP)
    //  Afert Virtual-machine sync, Vnc shouldn't have any VMs.
    @Test
    public void TestSyncVmwareVirtualMachinesTC1() throws Exception {
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

        String vmUuidA            = UUID.randomUUID().toString();
        String vmNameA            = "VM-A";
        String hostNameA          = "10.20.30.40";
        String vrouterIpAddressA  = "10.84.24.45";
        String macAddressA        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");

        // Populate prevVmwareVirtualMachineInfo 
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameA, hostNameA, hmor,
                                                    vrouterIpAddressA, macAddressA,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuidA, vmwareVmInfo);

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, externalIpam, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate prevVmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmMapInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        // Populate currVmwareVirtualNetworkInfo , no VMs
        VmwareVirtualNetworkInfo currVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-A should be deleted from api-server since it wasn't present on
        // curr  vmware database read
        VirtualMachine vmA =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidA);
        assertNull(vmA);
    }

    //  prevVCenterINfo : 1 VN(DHCP)
    //  currVCenterINfo : 1 VN(DHCP) & 1 VM (VM-B-PoweredOff)
    //  Afert Virtual-machine sync, Vnc should have 1 VM (VN-B).
    @Test
    public void TestSyncVmwareVirtualMachinesTC2() throws Exception {
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

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask,
                                    gatewayAddr, isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, null);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

       // Populate prevVmwareVirtualNetworkInfo . No VMs
        VmwareVirtualNetworkInfo prevVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        // Populate currVmwareVirtualMachineInfo with VM-B info
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos =
                                    new TreeMap<String, VmwareVirtualMachineInfo>();

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B";
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOff);
        vmInfos.put(vmUuidB, vmInfo);

        // Populate currVmwareVirtualNetworkInfo
        VmwareVirtualNetworkInfo currVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);
 
        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-B should be present on api-server since it's present on
        // latest vmware database read
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);
    }

    //  prevVCenterINfo : 1 VN & 1 VM (VM-A-PoweredOff)
    //  currVCenterINfo : 1 VN & 1 VM (VM-B-PoweredOff)
    //  Afert Virtual-machine sync, Vnc should have 1 VM (VN-B). VM-A is deleted.
    @Test
    public void TestSyncVmwareVirtualMachinesTC3() throws Exception {
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

        String vmUuidA            = UUID.randomUUID().toString();
        String vmNameA            = "VM-A";
        String hostNameA          = "10.20.30.40";
        String vrouterIpAddressA  = "10.84.24.45";
        String macAddressA        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");

        // Populate prevVmwareVirtualMachineInfo 
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameA, hostNameA, hmor,
                                                    vrouterIpAddressA, macAddressA,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuidA, vmwareVmInfo);

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, externalIpam, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate prevVmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmMapInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);


        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B";
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";
        hmor.setVal("host-19205");
        hmor.setType("HostSystem");

        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfosB = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();
        VmwareVirtualMachineInfo vmInfoB = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOff);
        vmMapInfosB.put(vmUuidB, vmInfoB);

        // Populate currVmwareVirtualNetworkInfo
        VmwareVirtualNetworkInfo currVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmMapInfosB, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);
 
        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-B should be created.
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);

        // VM-A should be deleted from api-server since it's not present on
        // latest vmware database read
        VirtualMachine vmA =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidA);
        assertNull(vmA);
    }


    // +*********************************************************************+
    // + Periodic Sync Tests (Static Ip Networks)                            +
    // **********************************************************************+

    //  prevVCenterINfo : 1 VN(Static-Ip)
    //  currVCenterINfo : 1 VN(Static-Ip) & 1 VM (VM-B-PoweredOn)
    //  Afert Virtual-machine sync, Vnc should have 1 VM (VN-B-PoweredOn).
    @Test
    public void TestSyncVmwareVirtualMachinesTC4() throws Exception {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestSyncVmwareVirtualMachinesTC4-VN-A";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2#230";
        boolean externalIpam  = true;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask,
                                    gatewayAddr, isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, null);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

       // Populate prevVmwareVirtualNetworkInfo . No VMs
        VmwareVirtualNetworkInfo prevVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        // Populate currVmwareVirtualMachineInfo with VM-B info
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos =
                                    new TreeMap<String, VmwareVirtualMachineInfo>();

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B";
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOn);
        vmInfos.put(vmUuidB, vmInfo);

        // Populate currVmwareVirtualNetworkInfo
        VmwareVirtualNetworkInfo currVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-B should be present on api-server since it's present on
        // latest vmware database read
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vmB.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertEquals(1, vmInterfaceRefs.size());

        String vmInterfaceUuid = vmInterfaceRefs.get(0).getUuid();
        VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                                  _api.findById(VirtualMachineInterface.class,
                                                vmInterfaceUuid);
        assertNotNull(vmInterface);

        // Check that instance-ip is null
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs =
                vmInterface.getInstanceIpBackRefs();
        assertNull(instanceIpRefs);

        // Update ip-address on VM and check that
        // instance-ip is created for the VM interface
        SortedMap<String, VmwareVirtualMachineInfo> vmInfosCurrPlus =
                                    new TreeMap<String, VmwareVirtualMachineInfo>();
        VmwareVirtualMachineInfo vmInfoCurrPlus = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOn);
        vmInfoCurrPlus.setIpAddress("192.168.2.10");
        vmInfosCurrPlus.put(vmUuidB, vmInfoCurrPlus);

        VmwareVirtualNetworkInfo currPlus1VmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfosCurrPlus, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        // curr become prev and currPlus1 becomes current.
        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currPlus1VmwareVNInfo, currVmwareVNInfo);

        // Now chedk that vmInterface has instanceIp
        VirtualMachineInterface vmInterfaceUpdated = (VirtualMachineInterface)
                                  _api.findById(VirtualMachineInterface.class,
                                                vmInterfaceUuid);
        assertNotNull(vmInterfaceUpdated);

        // Check that instance-ip is not null
        List<ObjectReference<ApiPropertyBase>> instanceIpRefsUpdated =
                vmInterfaceUpdated.getInstanceIpBackRefs();
        assertNotNull(instanceIpRefsUpdated);

    }

    //  prevVCenterINfo : 1 VN(Static-Ip)
    //  currVCenterINfo : 1 VN(Static-Ip) & 1 VM (VM-B-PoweredOff)
    //  Afert Virtual-machine sync, Vnc should have 1 VM (VN-B-PoweredOff).
    //  PowerOn VM and ensure that AddPort happens for the port.
    @Test
    public void TestSyncVmwareVirtualMachinesTC5() throws Exception {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestSyncVmwareVirtualMachinesTC5-VN-A";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.2.2#230";
        boolean externalIpam  = true;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, externalIpam, null);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

       // Populate prevVmwareVirtualNetworkInfo . No VMs
        VmwareVirtualNetworkInfo prevVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        // Populate currVmwareVirtualMachineInfo with VM-B info
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos = 
                                    new TreeMap<String, VmwareVirtualMachineInfo>();

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B";
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOff);
        vmInfos.put(vmUuidB, vmInfo);

        // Populate currVmwareVirtualNetworkInfo
        VmwareVirtualNetworkInfo currVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);
 
        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-B should be present on api-server since it's present on
        // latest vmware database read
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vmB.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertEquals(1, vmInterfaceRefs.size());

        String vmInterfaceUuid = vmInterfaceRefs.get(0).getUuid();
        VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                                  _api.findById(VirtualMachineInterface.class,
                                                vmInterfaceUuid);
        assertNotNull(vmInterface);

        // Check that instance-ip is null
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                vmInterface.getInstanceIpBackRefs();
        assertNull(instanceIpRefs);

        // Update ip-address on VM and check that 
        // instance-ip is created for the VM interface
        SortedMap<String, VmwareVirtualMachineInfo> vmInfosCurrPlus = 
                                    new TreeMap<String, VmwareVirtualMachineInfo>();
        VmwareVirtualMachineInfo vmInfoCurrPlus = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB, hmor,
                                         vrouterIpAddressB, macAddressB,
                                         VirtualMachinePowerState.poweredOn);
        vmInfoCurrPlus.setIpAddress("192.168.2.10");
        vmInfosCurrPlus.put(vmUuidB, vmInfoCurrPlus);

        VmwareVirtualNetworkInfo currPlus1VmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfosCurrPlus, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        // (prev = curr) and (cur = curr+1)
        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currPlus1VmwareVNInfo, currVmwareVNInfo);

        // Now chedk that vmInterface has instanceIp
        VirtualMachineInterface vmInterfaceUpdated = (VirtualMachineInterface)
                                  _api.findById(VirtualMachineInterface.class,
                                                vmInterfaceUuid);
        assertNotNull(vmInterfaceUpdated);

        // Check that instance-ip is not null
        List<ObjectReference<ApiPropertyBase>> instanceIpRefsUpdated = 
                vmInterfaceUpdated.getInstanceIpBackRefs();
        assertNotNull(instanceIpRefsUpdated);

    }


    //  prevVCenterINfo : 1 VN (TestVN-A)(Static-Ip)
    //  currVCenterINfo : 0 VN 
    //  Afert Virtual-network sync, Vnc shouldn't have any VNs.
    @Test
    public void TestSyncVmwareVirtualNetworksTC1() throws Exception {
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

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, externalIpam, null);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate prevVmwareVirtualNetworkInfo 
        SortedMap<String, VmwareVirtualNetworkInfo> vnMapInfos = 
                               new TreeMap<String, VmwareVirtualNetworkInfo>();
        VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);
        vnMapInfos.put(vnUuid, prevVmwareVNInfo);
        when(_vcenterDB.getPrevVmwareVNInfos()).thenReturn(vnMapInfos);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(null);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(null);

        _vcenterMonitorTask.syncVmwareVirtualNetworks();

        // Verify virtual-network deletion
        VirtualNetwork vn2 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn2);
    }

    //  prevVCenterINfo : 0 VN 
    //  currVCenterINfo : 1 VN (TestVn-B)(Static-Ip)
    //  Afert Virtual-network sync, Vnc shouldn't have any VNs.
    @Test
    public void TestSyncVmwareVirtualNetworksTC2() throws Exception {

        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-B";
        String subnetAddr     = "192.168.3.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.3.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = true;
        String range          = "192.18.3.2#230";
        boolean externalIpam  = false;

        // Verify virtual-network TestVN-B doesn't exist
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn1);

        // Populate currVmwareVirtualNetworkInfo
        SortedMap<String, VmwareVirtualNetworkInfo> vnMapInfos = 
                               new TreeMap<String, VmwareVirtualNetworkInfo>();
        VmwareVirtualNetworkInfo currVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);
        vnMapInfos.put(vnUuid, currVmwareVNInfo);
        when(_vcenterDB.getPrevVmwareVNInfos()).thenReturn(null);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnMapInfos);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(vnMapInfos);

        _vcenterMonitorTask.syncVmwareVirtualNetworks();

        // Verify virtual-network creation
        VirtualNetwork vn2 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn2);
    }

    //  prevVCenterINfo : 1 VN (TestVN-A)(Static-Ip)
    //  currVCenterINfo : 1 VN (TestVN-B)(Static-Ip)
    //  Afert Virtual-network sync, Vnc should have 1 VN (TestVN-B).
    @Test
    public void TestSyncVmwareVirtualNetworksTC3() throws Exception {
        String vnUuidA         = UUID.randomUUID().toString();
        String vnNameA         = "TestVN-A";
        String subnetAddrA     = "192.168.2.0";
        String subnetMaskA     = "255.255.255.0";
        String gatewayAddrA    = "192.168.2.1";
        short primaryVlanIdA   = 200;
        short isolatedVlanIdA  = 201;
        boolean ipPoolEnabledA= true;
        String rangeA         = "192.18.2.2#230";
        boolean externalIpam  = false;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuidA, vnNameA, subnetAddrA, subnetMaskA, 
                                    gatewayAddrA, isolatedVlanIdA, primaryVlanIdA, 
                                    ipPoolEnabledA, rangeA, externalIpam, null);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidA);
        assertNotNull(vn1);

        // Populate prevVmwareVirtualNetworkInfo 
        SortedMap<String, VmwareVirtualNetworkInfo> vnMapInfosA = 
                               new TreeMap<String, VmwareVirtualNetworkInfo>();
        VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnNameA, isolatedVlanIdA, primaryVlanIdA,
                                                  null, subnetAddrA, subnetMaskA,
                                                  gatewayAddrA, ipPoolEnabledA, rangeA, externalIpam);
        vnMapInfosA.put(vnUuidA, prevVmwareVNInfo);
        when(_vcenterDB.getPrevVmwareVNInfos()).thenReturn(vnMapInfosA);

        // Populate currVmwareVirtualNetworkInfo
        String vnUuidB         = UUID.randomUUID().toString();
        String vnNameB         = "TestVN-B";
        String subnetAddrB     = "192.168.4.0";
        String subnetMaskB     = "255.255.255.0";
        String gatewayAddrB    = "192.168.4.1";
        short primaryVlanIdB   = 200;
        short isolatedVlanIdB  = 201;
        boolean ipPoolEnabledB= true;
        String rangeB         = "192.18.4.2#230";
        SortedMap<String, VmwareVirtualNetworkInfo> vnMapInfosB = 
                               new TreeMap<String, VmwareVirtualNetworkInfo>();
        VmwareVirtualNetworkInfo currVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnNameB, isolatedVlanIdB, primaryVlanIdB,
                                                  null, subnetAddrB, subnetMaskB,
                                                  gatewayAddrB, ipPoolEnabledB, rangeB, externalIpam);
        vnMapInfosB.put(vnUuidB, currVmwareVNInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnMapInfosB);
        when(_vcenterDB.populateVirtualNetworkInfoOptimized()).thenReturn(vnMapInfosB);

        _vcenterMonitorTask.syncVmwareVirtualNetworks();

        // Verify virtual-network creation
        VirtualNetwork vnB = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidB);
        assertNotNull(vnB);

        // Verify virtual-network deletion
        VirtualNetwork vnA = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidA);
        assertNull(vnA);
    }
}
