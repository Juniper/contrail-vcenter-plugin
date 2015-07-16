/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
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
public class VCenterAsComputeMonitorTaskTest extends TestCase {
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
        _vcenterMonitorTask = new VCenterAsComputeMonitorTask("https://10.20.30.40/sdk", "admin", "admin123",
                                   "unittest_dc", "unittest_dvs", null, 0, "unittest_fabric_pg");

        // Ensure vncDB and vCenterDb are instances of appropriate class
        assertTrue(_vcenterMonitorTask.getVncDB() instanceof VCenterAsComputeVncDB);
        //assertTrue();

        // Create mock for VCenterDB.
        _vcenterDB = mock(VCenterAsComputeDB.class);
        _vcenterMonitorTask.setVCenterDB(_vcenterDB);

        _vncDB     = _vcenterMonitorTask.getVncDB();
        _vncDB.setApiConnector(_api);
        assertTrue(_vncDB.isVncApiServerAlive());
        assertTrue(_vncDB.Initialize());
        assertTrue(_vncDB.TestInitialize());

        // Setup mock ContrailVRouterApi connection for vrouterIp = 10.84.24.45
        HashMap<String, ContrailVRouterApi> vrouterApiMap = _vncDB.getVRouterApiMap();
        ContrailVRouterApi vrouterApi = mock(ContrailVRouterApi.class);
        when(vrouterApi.AddPort(any(UUID.class), any(UUID.class), anyString(), any(InetAddress.class),
                                any(byte[].class), any(UUID.class), anyShort(), anyShort(),
                                anyString())).thenReturn(true);
        when(vrouterApi.DeletePort(any(UUID.class))).thenReturn(true);
        vrouterApiMap.put("10.84.24.45", vrouterApi);
    }

    //  VNC     : 1 VN & 1 VM
    //  VCENTER : 1 VN & 1 VM
    //  Afert Virtual-machine sync, AddPort should get called
    @Test
    public void TestSyncVirtualMachineTC1() throws IOException {
    }

    //  VNC     : 1 VN & 1 VM
    //  VCENTER : 1 VN 
    //  Afert Virtual-machine sync, nothing should happen
    @Test
    public void TestSyncVirtualMachineTC2() throws IOException {
    }

    //  VNC     : 1 VN 
    //  VCENTER : 1 VN & 1 VM
    //  Afert Virtual-machine sync, Nothing shoud happen.
    //    Ideally, this is an error condition.
    @Test
    public void TestSyncVirtualMachineTC3() throws IOException {
    }

    //  VNC     : 1 VN & 1 VM
    //  VCENTER : 1 VN 
    //  Afert Virtual-machine sync, delPort should get called
    @Test
    public void TestSyncVirtualMachineTC4() throws IOException {
    }

    //  prevVCenterINfo : 1 VN 
    //  currVCenterINfo : 1 VN & 1 VM (VM-A)
    //  Afert Virtual-machine sync, plugin should do AddPort
    @Test
    public void TestSyncVmwareVirtualMachinesTC1() throws Exception {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-A";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = false;
        String range          = null;
        boolean externalIpam  = false;

        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = vmUuidB; /* uuid and vm name is same */
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19209");
        hmor.setType("HostSystem");

        // Create virtual-network & Virtual-machine on api-server
        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameB, hostNameB, hmor,
                                                    vrouterIpAddressB, macAddressB,
                                                    VirtualMachinePowerState.poweredOn);
        vmMapInfos.put(vmUuidB, vmwareVmInfo);

        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask,
                                    gatewayAddr, isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, vmMapInfos);

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

    //  prevVCenterINfo : 1 VN & 1 VM (VM-A)
    //  currVCenterINfo : 1 VN 
    //  Afert Virtual-machine sync, Vnc shouldn't have any VMs.
    @Test
    public void TestSyncVmwareVirtualMachinesTC2() throws Exception {
        String vnUuid         = UUID.randomUUID().toString();
        String vnName         = "TestVN-A";
        String subnetAddr     = "192.168.2.0";
        String subnetMask     = "255.255.255.0";
        String gatewayAddr    = "192.168.2.1";
        short primaryVlanId   = 200;
        short isolatedVlanId  = 201;
        boolean ipPoolEnabled = false;
        String range          = null;
        boolean externalIpam  = false;

        // Create virtual-network on api-server
        _vncDB.CreateVirtualNetwork(vnUuid, vnName, subnetAddr, subnetMask,
                                    gatewayAddr, isolatedVlanId, primaryVlanId,
                                    ipPoolEnabled, range, externalIpam, null);

        // Verify virtual-network creation
        VirtualNetwork vn1 = (VirtualNetwork) _api.findById(VirtualNetwork.class, vnUuid);
        assertNotNull(vn1);

        // Populate prevVmwareVirtualNetworkInfo . 1 VM (VM-B)
        String vmUuidB            = UUID.randomUUID().toString();
        String vmNameB            = vmUuidB; /* uuid and vm name is same */
        String hostNameB          = "10.20.30.40";
        String vrouterIpAddressB  = "10.84.24.45";
        String macAddressB        = "00:11:22:33:44:55";
        ManagedObjectReference hmor = new ManagedObjectReference();
        hmor.setVal("host-19208");
        hmor.setType("HostSystem");

        SortedMap<String, VmwareVirtualMachineInfo> vmMapInfos = 
                               new TreeMap<String, VmwareVirtualMachineInfo>();

        VmwareVirtualMachineInfo vmwareVmInfo = new VmwareVirtualMachineInfo(
                                                    vmNameB, hostNameB, hmor,
                                                    vrouterIpAddressB, macAddressB,
                                                    VirtualMachinePowerState.poweredOn);

        String vmiUuid = UUID.randomUUID().toString();
        vmwareVmInfo.setInterfaceUuid(vmiUuid);
        vmMapInfos.put(vmUuidB, vmwareVmInfo);

        VmwareVirtualNetworkInfo prevVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  vmMapInfos, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);

        // Populate currVmwareVirtualNetworkInfo (NO VM-B)
        VmwareVirtualNetworkInfo currVmwareVNInfo =
                                            new VmwareVirtualNetworkInfo(
                                                  vnName, isolatedVlanId, primaryVlanId,
                                                  null, subnetAddr, subnetMask,
                                                  gatewayAddr, ipPoolEnabled, range, externalIpam);
 
        _vcenterMonitorTask.syncVmwareVirtualMachines(vnUuid,
                                     currVmwareVNInfo, prevVmwareVNInfo);

        // VM-B should be present on api-server since it's present on
        // latest vmware database read
        VirtualMachine vmB =(VirtualMachine) _api.findById(VirtualMachine.class, vmUuidB);
        assertNull(vmB);
    }

    //  prevVCenterINfo : 1 VN & 1 VM (VM-A)
    //  currVCenterINfo : 1 VN & 1 VM (VM-B) 
    //  Afert Virtual-machine sync, Vnc should have 1 VM (VN-B). VM-A is deleted.
    @Test
    public void TestSyncVmwareVirtualMachinesTC3() throws Exception {
    }

    //  prevVCenterINfo : 1 VN (TestVn-A)
    //  currVCenterINfo : 0 VN 
    //  Afert Virtual-network sync, Vnc shouldn't have any VNs.
    @Test
    public void TestSyncVmwareVirtualNetworksTC1() throws Exception {
    }
}
