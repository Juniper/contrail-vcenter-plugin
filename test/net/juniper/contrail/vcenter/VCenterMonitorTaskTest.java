/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.List;

import com.google.common.base.Throwables;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import net.juniper.contrail.api.types.Project;

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

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorMock;
import net.juniper.contrail.api.ApiConnectorFactory;
import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualNetwork;

import com.vmware.vim25.VirtualMachinePowerState;

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
                                   "unittest_dc", "unittest_dvs", null, 0);

        // Create mock for VCenterDB.
        _vcenterDB = mock(VCenterDB.class);
        _vcenterMonitorTask.setVCenterDB(_vcenterDB);

        _vncDB     = _vcenterMonitorTask.getVncDB();
        _vncDB.setApiConnector(_api);
        assertTrue(_vncDB.isVncApiServerAlive());
        assertTrue(_vncDB.Initialize());
    }

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

        // Fill vmMapInfos such that 2 VMs will be created
        // as part of CreateVirtualNetwork() call
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        // Virtual Machine #1 info
        String vmUuid            = UUID.randomUUID().toString();
        String macAddress        = "00:11:22:33:44:55";
        String vmName            = "VM-C";
        String vrouterIpAddress  = null;
        String hostName          = "10.20.30.40";
        
        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmName, hostName,
                                                    vrouterIpAddress, macAddress,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuid, vmwareVmInfo);

        // Create virtual-network on api-server
        // This call should also result in VM creation on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, vmMapInfos);

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
                                                  gatewayAddr, ipPoolEnabled, range);

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

        // Fill vmMapInfos as null such that no VM will be created
        // as part of CreateVirtualNetwork() call
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos =  null;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, vmMapInfos);

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
        String vrouterIpAddress  = null; //"10.84.24.45";
        String macAddress        = "00:11:22:33:44:55";
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmName, hostName,
                                         vrouterIpAddress, macAddress, 
                                         VirtualMachinePowerState.poweredOff);
        vmInfos.put(vmUuid, vmInfo);

        // Populate VmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo vmwareNetworkInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);

        //Sync virtual-machines between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualMachines(vncItem.getKey(),
                                             vmwareNetworkInfo, vncItem.getValue());

        // Virtual-machine should be created on pi-server since it's present on VCenter
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

        String vmUuidA            = UUID.randomUUID().toString();
        String macAddressA        = "00:11:22:33:44:55";
        String vmNameA            = "VM-A";
        String vrouterIpAddressA  = null;
        String hostNameA          = "10.20.30.40";
        
        // Fill vmMapInfos such that 2 VMs will be created
        // as part of CreateVirtualNetwork() call
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameA, hostNameA,
                                                    vrouterIpAddressA, macAddressA,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuidA, vmwareVmInfo);

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, vmMapInfos);

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
        String vrouterIpAddressB  = null; //"10.84.24.45";
        String macAddressB        = "00:11:22:33:44:56";
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB,
                                         vrouterIpAddressB, macAddressB, 
                                         VirtualMachinePowerState.poweredOff);
        vmInfos.put(vmUuidB, vmInfo);

        // Populate VmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo vmwareNetworkInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);

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

        // Set vmMapInfos to null
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = null;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, gatewayAddr, 
                                    isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(null);

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

        SortedMap<String, VmwareVirtualNetworkInfo> vnInfos =
                new TreeMap<String, VmwareVirtualNetworkInfo>();

        VmwareVirtualNetworkInfo vnInfo = new
                VmwareVirtualNetworkInfo(vnName, isolatedVlanId, primaryVlanId, 
                                    null, subnetAddr, subnetMask, gatewayAddr, 
                                    ipPoolEnabled, range);
        vnInfos.put(vnUuid, vnInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnInfos);

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

        // Set vmMapInfos to null
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = null;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuidA, vnNameA, subnetAddrA, subnetMaskA, gatewayAddrA,
                                    isolatedVlanIdA, primaryVlanIdA,
                                    ipPoolEnabledA, rangeA, vmMapInfos);

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
                                    ipPoolEnabledB, rangeB);
        vnInfos.put(vnUuidB, vnInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnInfos);

        //Sync virtual-networks between Vnc & VCenter
        _vcenterMonitorTask.syncVirtualNetworks();

        // TestVN-B should be created on api-server since it's present on VCenter
        VirtualNetwork vn =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidA);
        assertNull(vn);

        // TestVN-A should be deleted from api-server since it's not present on VCenter
        VirtualNetwork vnB =(VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidB);
        assertNotNull(vnB);
    }

    //  prevVCenterINfo : 1 VN & 1 VM (VM-A)
    //  currVCenterINfo : 1 VN 
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

        String vmUuidA            = UUID.randomUUID().toString();
        String vmNameA            = "VM-A";
        String hostNameA          = "10.20.30.40";
        String vrouterIpAddressA  = null; //"10.84.24.45";
        String macAddressA        = "00:11:22:33:44:55";

        // Populate prevVmwareVirtualMachineInfo 
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameA, hostNameA,
                                                    vrouterIpAddressA, macAddressA,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuidA, vmwareVmInfo);

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate prevVmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmMapInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);

        // Populate currVmwareVirtualNetworkInfo , no VMs
        VmwareVirtualNetworkInfo currVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);

        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-A should be deleted from api-server since it wasn't present on
        // curr  vmware database read
        VirtualMachine vmA =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidA);
        assertNull(vmA);
    }

    //  prevVCenterINfo : 1 VN 
    //  currVCenterINfo : 1 VN & 1 VM (VM-B) 
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

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, null);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

       // Populate prevVmwareVirtualNetworkInfo . No VMs
        VmwareVirtualNetworkInfo prevVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);

        // Populate currVmwareVirtualMachineInfo with VM-B info
        SortedMap<String, VmwareVirtualMachineInfo> vmInfos = 
                                    new TreeMap<String, VmwareVirtualMachineInfo>();

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B";
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = null; //"10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";
        VmwareVirtualMachineInfo vmInfo = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB,
                                         vrouterIpAddressB, macAddressB, 
                                         VirtualMachinePowerState.poweredOff);
        vmInfos.put(vmUuidB, vmInfo);

        // Populate currVmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo currVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);
 
        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-B should be present on api-server since it's present on
        // latest vmware database read
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNotNull(vmB);
    }

    //  prevVCenterINfo : 1 VN & 1 VM (VM-A)
    //  currVCenterINfo : 1 VN & 1 VM (VM-B) 
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

        String vmUuidA            = UUID.randomUUID().toString();
        String vmNameA            = "VM-A";
        String hostNameA          = "10.20.30.40";
        String vrouterIpAddressA  = null; //"10.84.24.45";
        String macAddressA        = "00:11:22:33:44:55";

        // Populate prevVmwareVirtualMachineInfo 
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameA, hostNameA,
                                                    vrouterIpAddressA, macAddressA,
                                                    VirtualMachinePowerState.poweredOff);
        vmMapInfos.put(vmUuidA, vmwareVmInfo);

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, vmMapInfos);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate prevVmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo prevVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmMapInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);


        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = "VM-B";
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = null; //"10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";

        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfosB = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();
        VmwareVirtualMachineInfo vmInfoB = new
                VmwareVirtualMachineInfo(vmNameB, hostNameB,
                                         vrouterIpAddressB, macAddressB, 
                                         VirtualMachinePowerState.poweredOff);
        vmMapInfosB.put(vmUuidB, vmInfoB);

        // Populate currVmwareVirtualNetworkInfo 
        VmwareVirtualNetworkInfo currVmwareVNInfo = 
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmMapInfosB, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range);
 
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

    //  prevVCenterINfo : 1 VN (TestVn-A)
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

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask, 
                                    gatewayAddr, isolatedVlanId, primaryVlanId, 
                                    ipPoolEnabled, range, null);

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
                                                  gatewayAddr, ipPoolEnabled, range);
        vnMapInfos.put(vnUuid, prevVmwareVNInfo);
        when(_vcenterDB.getPrevVmwareVNInfos()).thenReturn(vnMapInfos);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(null);

        _vcenterMonitorTask.syncVmwareVirtualNetworks();

        // Verify virtual-network deletion
        VirtualNetwork vn2 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNull(vn2);
    }

    //  prevVCenterINfo : 0 VN 
    //  currVCenterINfo : 1 VN (TestVn-B)
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
                                                  gatewayAddr, ipPoolEnabled, range);
        vnMapInfos.put(vnUuid, currVmwareVNInfo);
        when(_vcenterDB.getPrevVmwareVNInfos()).thenReturn(null);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnMapInfos);

        _vcenterMonitorTask.syncVmwareVirtualNetworks();

        // Verify virtual-network creation
        VirtualNetwork vn2 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn2);
    }

    //  prevVCenterINfo : 1 VN (TestVN-A)
    //  currVCenterINfo : 1 VN (TestVN-B)
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

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuidA, vnNameA, subnetAddrA, subnetMaskA, 
                                    gatewayAddrA, isolatedVlanIdA, primaryVlanIdA, 
                                    ipPoolEnabledA, rangeA, null);

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
                                                  gatewayAddrA, ipPoolEnabledA, rangeA);
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
                                                  gatewayAddrB, ipPoolEnabledB, rangeB);
        vnMapInfosB.put(vnUuidB, currVmwareVNInfo);
        when(_vcenterDB.populateVirtualNetworkInfo()).thenReturn(vnMapInfosB);

        _vcenterMonitorTask.syncVmwareVirtualNetworks();

        // Verify virtual-network creation
        VirtualNetwork vnB = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidB);
        assertNotNull(vnB);

        // Verify virtual-network deletion
        VirtualNetwork vnA = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuidA);
        assertNull(vnA);
    }
}
