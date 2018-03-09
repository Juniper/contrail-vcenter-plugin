/**
 * Copyright (c) 2016 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.util.UUID;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.MethodSorters;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.core.classloader.annotations.PrepareForTest;
import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorMock;
import net.juniper.contrail.api.ApiPropertyBase;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

@RunWith(PowerMockRunner.class)
@PrepareForTest(VCenterNotify.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VirtualMachineInterfaceInfoTest extends TestCase {
    private static final Logger s_logger =
        Logger.getLogger(VirtualMachineInterfaceInfoTest.class);
    public static VncDB vncDB;
    public static ApiConnector api;
    private static VirtualNetworkInfo vnInfo;
    private static VirtualMachineInfo vmInfo;
    private static VirtualMachineInterfaceInfo firstVmiInfo;
    private static InstanceIp firstInstanceIp;
    private static ContrailVRouterApi vrouterApi;

    private final static String macAddress1 = "11:11:22:33:44:11";
    public static VirtualMachineInterfaceInfo VMI1 = new VirtualMachineInterfaceInfo(macAddress1);

    private final static String macAddress2 = "22:11:22:33:44:22";
    public static VirtualMachineInterfaceInfo VMI2 = new VirtualMachineInterfaceInfo(macAddress2);

    private final static String macAddress3 = "33:11:FF:33:44:33";
    public static VirtualMachineInterfaceInfo VMI3 = new VirtualMachineInterfaceInfo(macAddress3);

    public static VirtualMachineInterface verifyVirtualMachineInterfacePresent(
            VirtualMachineInterfaceInfo vmiInfo) throws IOException {

        VirtualNetwork vn = VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vmiInfo.vnInfo);
        VirtualMachine vm = VirtualMachineInfoTest.verifyVirtualMachinePresent(vmiInfo.vmInfo);


        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        assertNotNull(vmInterfaceRefs);
        assertTrue(vmInterfaceRefs.size() >= 1);

        for (ObjectReference<ApiPropertyBase> vmInterfaceRef :
            Utils.safe(vmInterfaceRefs)) {
            String vmInterfaceUuid = vmInterfaceRef.getUuid();
            VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                    api.findById(VirtualMachineInterface.class,
                            vmInterfaceUuid);
            api.read(vmInterface);

            List<String> macAddresses = vmInterface.getMacAddresses().getMacAddress();
            assertTrue(macAddresses.size() > 0);
            if (!macAddresses.get(0).equals(vmiInfo.getMacAddress())) {
                continue;
            }

            List<ObjectReference<ApiPropertyBase>> vnRefs =
                                            vmInterface.getVirtualNetwork();
            for (ObjectReference<ApiPropertyBase> vnRef : vnRefs) {
                if (vnRef.getUuid().equals(vn.getUuid())) {
                    assertNotNull(vmInterface);

                    assertEquals(vmInterface.getUuid(), vmiInfo.getUuid());
                    assertEquals(vmInterface.getName(), vmiInfo.getUuid());

                    assertEquals(vmInterface.getIdPerms(), vncDB.getVCenterIdPerms());
                    assertEquals(vmInterface.getParent(), vncDB.getVCenterProject());

                    String vmInterfaceName = "vmi-" + vn.getName() + "-" + vmiInfo.vmInfo.getName();
                    assertEquals(vmInterface.getDisplayName(), vmInterfaceName);

                    List<ObjectReference<ApiPropertyBase>> vmRefs = vmInterface.getVirtualMachine();
                    assertNotNull(vmRefs);
                    assertEquals(1, vmRefs.size());
                    assertEquals(vm.getUuid(), vmRefs.get(0).getUuid());

                    List<ObjectReference<ApiPropertyBase>> vmiVnRefs = vmInterface.getVirtualNetwork();
                    assertNotNull(vmiVnRefs);
                    assertEquals(1, vmiVnRefs.size());
                    assertEquals(vn.getUuid(), vmiVnRefs.get(0).getUuid());

                    return vmInterface;
                }
            }
        }
        return null;
    }

    public static void verifyVirtualMachineInterfaceAbsent(VirtualMachineInterfaceInfo vmiInfo)
            throws IOException {
        // Verify virtual-machine-interface is deleted from  api-server
        VirtualMachineInterface vmi1 =(VirtualMachineInterface)
                                    api.findById(VirtualMachineInterface.class,
                                            vmiInfo.getUuid());
        assertNull(vmi1);
    }

    public static InstanceIp verifyInstanceIpPresent(VirtualMachineInterfaceInfo vmiInfo)
            throws IOException {

        VirtualNetwork vn = VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vmiInfo.vnInfo);
        VirtualMachineInterface vmInterface = verifyVirtualMachineInterfacePresent(vmiInfo);

        // find instance-ip corresponding to virtual-machine-interface
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs =
                vmInterface.getInstanceIpBackRefs();
        assertNotNull(instanceIpRefs);
        assertEquals(1, instanceIpRefs.size());
        ObjectReference<ApiPropertyBase> instanceIpRef = instanceIpRefs.get(0);
        assertNotNull(instanceIpRef);

        InstanceIp instanceIp = (InstanceIp)
                api.findById(InstanceIp.class, instanceIpRef.getUuid());
        assertNotNull(instanceIp);
        String instanceIpName = "ip-" + vn.getName() + "-" + vmiInfo.vmInfo.getName();
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

        s_logger.info("Found instanceIP " + instanceIp.getAddress());
        return instanceIp;
    }

    public static void verifyInstanceIpAbsent(InstanceIp instanceIp) throws IOException {
        InstanceIp ip1 = (InstanceIp) api.findById(InstanceIp.class, instanceIp.getUuid());
        assertNull(ip1);
    }

    @Before
    public void globalSetUp() throws IOException {
        api   = new ApiConnectorMock(null, 0);
        assertNotNull(api);

        // Create default-domain,default-project
        Project vProject = new Project();
        vProject.setName("default-project");
        try {
            if (!api.create(vProject).isSuccess()) {
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
        vncDB.setApiConnector(api);
        assertNotNull(vncDB.getApiConnector());
        assertTrue(vncDB.isVncApiServerAlive());
        assertTrue(vncDB.Initialize());

        VirtualNetworkInfoTest.api = api;
        VirtualNetworkInfoTest.vncDB = vncDB;
        VirtualMachineInfoTest.api = api;
        VirtualMachineInfoTest.vncDB = vncDB;
        MainDB.vncDB = vncDB;

        // Setup mock ContrailVRouterApi connection for vrouterIp = 10.84.24.45
        vrouterApi = mock(ContrailVRouterApi.class);
        when(vrouterApi.addPort(anyString(), anyString(), anyString(), anyString(),
                                anyString(), anyString(), anyShort(), anyShort(),
                                anyString(), anyString())).thenReturn(true);
        when(vrouterApi.deletePort(anyString())).thenReturn(true);
        Map<String, ContrailVRouterApi> vrouterApiMap = VRouterNotifier.getVrouterApiMap();
        vrouterApiMap.put("10.84.24.45", vrouterApi);

        PowerMockito.mockStatic(VCenterNotify.class);

        vnInfo = VirtualNetworkInfoTest.BLUE;
        try {
            s_logger.info("Create " + vnInfo);
            vnInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + VirtualNetworkInfoTest.BLUE);
        }

        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(VirtualNetworkInfoTest.BLUE);

        // Create Virtual Machine and first VMI
        vmInfo = VirtualMachineInfoTest.VM1;
        firstVmiInfo = new VirtualMachineInterfaceInfo(VMI1);
        firstVmiInfo.setVnInfo(vnInfo);
        firstVmiInfo.setVmInfo(vmInfo);

        // add this interface to the VM
        vmInfo.created(firstVmiInfo);
        // verify VMI has been added in the VMI map of this VM
        assertTrue(vmInfo.contains(firstVmiInfo));

        // create VM and VMI
        try {
            s_logger.info("Create " + vmInfo);
            vmInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VM " + vmInfo);
        }

        VirtualMachineInfoTest.verifyVirtualMachinePresent(vmInfo);
        verifyVirtualMachineInterfacePresent(firstVmiInfo);
        firstInstanceIp = verifyInstanceIpPresent(firstVmiInfo);
        verify(vrouterApi).addPort(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyShort(), anyShort(),
                anyString(), anyString());
    }

    @Test
    public void testVirtualMachineInterfaceCreateDelete() throws IOException {
        // Create Virtual Machine and second VMI
        VirtualMachineInterfaceInfo secondVmiInfo = new VirtualMachineInterfaceInfo(VMI2);;
        secondVmiInfo.setVnInfo(vnInfo);
        secondVmiInfo.setVmInfo(vmInfo);
        // add this interface to the VM
        vmInfo.created(secondVmiInfo);
        // verify VMI has been added in the VMI map of this VM
        assertTrue(vmInfo.contains(secondVmiInfo));

        // create VM and VMI
        try {
            s_logger.info("Create second VMI " + secondVmiInfo);
            secondVmiInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VMI " + secondVmiInfo);
        }

        assertTrue(vnInfo.contains(secondVmiInfo));
        assertTrue(vmInfo.contains(secondVmiInfo));

        verifyVirtualMachineInterfacePresent(secondVmiInfo);
        InstanceIp secondInstanceIp = verifyInstanceIpPresent(secondVmiInfo);
        verify(vrouterApi, times(2)).addPort(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyShort(), anyShort(),anyString(), anyString());

        // update should have no effect
        try {
            s_logger.info("Update second VMI " + secondVmiInfo);
            secondVmiInfo.update(secondVmiInfo, vncDB);
        } catch (Exception e) {
            fail("Cannot update VMI " + secondVmiInfo);
        }
        verifyNoMoreInteractions(vrouterApi);

        try {
            s_logger.info("Delete second VMI " + secondVmiInfo);
            secondVmiInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VMI " + secondVmiInfo);
        }

        verifyInstanceIpAbsent(secondInstanceIp);
        verifyVirtualMachineInterfaceAbsent(secondVmiInfo);

        verify(vrouterApi).deletePort(anyString());


        try {
            s_logger.info("Delete first VMI " + firstVmiInfo);
            firstVmiInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VMI " + secondVmiInfo);
        }

        verifyInstanceIpAbsent(firstInstanceIp);
        verifyVirtualMachineInterfaceAbsent(firstVmiInfo);
        verify(vrouterApi, times(2)).deletePort(anyString());

        try {
            s_logger.info("Delete " + vmInfo);
            vmInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VM " + vmInfo);
        }

        VirtualMachineInfoTest.verifyVirtualMachineAbsent(vmInfo);
    }

    @Test
    public void testVirtualMachineInterfaceStaticIpAddressing() throws IOException {
        VirtualNetworkInfo staticVnInfo = VirtualNetworkInfoTest.STATIC_IP;
        assertEquals(staticVnInfo.getExternalIpam(), true);
        try {
            s_logger.info("Create " + staticVnInfo);
            staticVnInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + VirtualNetworkInfoTest.STATIC_IP + " "
                    + e.getMessage());
            e.printStackTrace();
        }

        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(staticVnInfo);

        VirtualMachineInterfaceInfo vmiInfo = new VirtualMachineInterfaceInfo(VMI3);;
        vmiInfo.setVnInfo(staticVnInfo);
        vmiInfo.setVmInfo(vmInfo);

        // add this interface to the VM
        vmInfo.created(vmiInfo);
        // verify VMI has been added in the VMI map of this VM
        assertTrue(vmInfo.contains(vmiInfo));

        // create VM and VMI
        try {
            s_logger.info("Create " + vmiInfo);
            vmiInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VMI " + vmiInfo);
        }

        VirtualMachineInfoTest.verifyVirtualMachinePresent(vmInfo);
        verifyVirtualMachineInterfacePresent(vmiInfo);
        assertNull(vmiInfo.getIpAddress());
        assertNull(vmiInfo.apiInstanceIp);

        verify(vrouterApi, times(2)).addPort(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyShort(), anyShort(),
                anyString(), anyString());
    }

    @Test
    public void testNetworkChange() throws IOException {
        s_logger.info("testNetworkChange");
        VirtualNetworkInfo redVnInfo = VirtualNetworkInfoTest.RED;
        try {
            s_logger.info("Create " + redVnInfo);
            redVnInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + redVnInfo);
        }
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(redVnInfo);

        s_logger.info("Change VM1 nw adapter from network BLUE (dhcp) to network RED (dhcp)");
        VirtualMachineInterfaceInfo newVmiInfo = new VirtualMachineInterfaceInfo(firstVmiInfo);
        newVmiInfo.setVnInfo(redVnInfo);

        try {
            firstVmiInfo.update(newVmiInfo, vncDB);
        } catch (Exception e) {
            fail("Cannot update VMIs " + firstVmiInfo);
        }

        assertTrue(vmInfo.contains(firstVmiInfo));
        assertTrue(redVnInfo.contains(firstVmiInfo));
        verifyVirtualMachineInterfacePresent(firstVmiInfo);
        InstanceIp redIp = verifyInstanceIpPresent(firstVmiInfo);
        s_logger.info("New IP Address is " + redIp.getAddress());

        verify(vrouterApi, times(2)).addPort(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyShort(), anyShort(),
                anyString(), anyString());

        verify(vrouterApi).deletePort(anyString());
        verifyInstanceIpAbsent(firstInstanceIp);

        VirtualNetworkInfo staticVnInfo = VirtualNetworkInfoTest.STATIC_IP;
        try {
            s_logger.info("Create " + staticVnInfo);
            staticVnInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + staticVnInfo);
        }
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(staticVnInfo);

        s_logger.info("Change VM1 nw adapter from network RED (dhcp) to network STATIC_IP (dhcp)");
        newVmiInfo = new VirtualMachineInterfaceInfo(firstVmiInfo);
        newVmiInfo.setVnInfo(staticVnInfo);
        newVmiInfo.setIpAddress("192.168.3.2");

        try {
            firstVmiInfo.update(newVmiInfo, vncDB);
        } catch (Exception e) {
            fail("Cannot update VMIs " + firstVmiInfo);
        }

        assertTrue(vmInfo.contains(firstVmiInfo));
        assertTrue(staticVnInfo.contains(firstVmiInfo));
        verifyVirtualMachineInterfacePresent(firstVmiInfo);
        InstanceIp staticIp = verifyInstanceIpPresent(firstVmiInfo);
        assertEquals("192.168.3.2", staticIp.getAddress());

        verify(vrouterApi, times(3)).addPort(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyShort(), anyShort(),
                anyString(), anyString());

        verify(vrouterApi, times(2)).deletePort(anyString());
        verifyInstanceIpAbsent(redIp);

        VirtualNetworkInfo staticVnInfo2 = VirtualNetworkInfoTest.STATIC_IP2;
        try {
            s_logger.info("Create " + staticVnInfo2);
            staticVnInfo2.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + staticVnInfo2);
        }
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(staticVnInfo2);

        s_logger.info("Change VM1 nw adapter from network STATIC_IP (static) to network STATIC_IP2 (static)");
        newVmiInfo = new VirtualMachineInterfaceInfo(firstVmiInfo);
        newVmiInfo.setVnInfo(staticVnInfo2);
        newVmiInfo.setIpAddress("192.168.4.2");

        try {
            firstVmiInfo.update(newVmiInfo, vncDB);
        } catch (Exception e) {
            fail("Cannot update VMIs " + firstVmiInfo);
        }

        assertTrue(vmInfo.contains(firstVmiInfo));
        assertTrue(staticVnInfo2.contains(firstVmiInfo));
        verifyVirtualMachineInterfacePresent(firstVmiInfo);
        InstanceIp staticIp2 = verifyInstanceIpPresent(firstVmiInfo);
        assertEquals("192.168.4.2", staticIp2.getAddress());
        verify(vrouterApi, times(4)).addPort(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyShort(), anyShort(),
                anyString(), anyString());

        verify(vrouterApi, times(3)).deletePort(anyString());
        verifyInstanceIpAbsent(staticIp);

        s_logger.info("Change VM1 nw adapter from network STATIC_IP2 (static) to network BLUE (dhcp)");
        newVmiInfo = new VirtualMachineInterfaceInfo(firstVmiInfo);
        newVmiInfo.setVnInfo(VirtualNetworkInfoTest.BLUE);
        newVmiInfo.setIpAddress(null);

        try {
            firstVmiInfo.update(newVmiInfo, vncDB);
        } catch (Exception e) {
            fail("Cannot update VMIs " + firstVmiInfo);
        }

        assertTrue(vmInfo.contains(firstVmiInfo));
        assertTrue(VirtualNetworkInfoTest.BLUE.contains(firstVmiInfo));
        verifyVirtualMachineInterfacePresent(firstVmiInfo);
        InstanceIp blueIp = verifyInstanceIpPresent(firstVmiInfo);
        assertNotSame(null, blueIp.getAddress());
        verify(vrouterApi, times(5)).addPort(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyShort(), anyShort(),
                anyString(), anyString());

        verify(vrouterApi, times(4)).deletePort(anyString());
        verifyInstanceIpAbsent(staticIp2);
    }
}
