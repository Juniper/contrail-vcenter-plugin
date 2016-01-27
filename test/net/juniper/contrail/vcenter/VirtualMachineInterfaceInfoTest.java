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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.MethodSorters;
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

@RunWith(JUnit4.class)
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

    public static VirtualMachineInterfaceInfo newInstance(int selection) {
        switch (selection) {
        case 1:
            return new VirtualMachineInterfaceInfo(VMI1);
        case 2:
            return new VirtualMachineInterfaceInfo(VMI2);
        case 3:
            return new VirtualMachineInterfaceInfo(VMI3);
        default:
            ;
        }
        return new VirtualMachineInterfaceInfo(VMI1);
    }
        
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
            if (!api.create(vProject)) {
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
        Map<String, ContrailVRouterApi> vrouterApiMap = VRouterNotifier.getVrouterApiMap();
        vrouterApi = mock(ContrailVRouterApi.class);
        when(vrouterApi.AddPort(any(UUID.class), any(UUID.class), anyString(), any(InetAddress.class),
                                any(byte[].class), any(UUID.class), anyShort(), anyShort(),
                                anyString())).thenReturn(true);
        when(vrouterApi.DeletePort(any(UUID.class))).thenReturn(true);
        vrouterApiMap.put("10.84.24.45", vrouterApi);
        
        vnInfo = VirtualNetworkInfoTest.BLUE;
        try {
            vnInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + VirtualNetworkInfoTest.BLUE);
        }

        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(VirtualNetworkInfoTest.BLUE);
        
        // Create Virtual Machine and first VMI
        vmInfo = VirtualMachineInfoTest.VM1;  
        firstVmiInfo = newInstance(1);
        firstVmiInfo.setVnInfo(vnInfo);
        firstVmiInfo.setVmInfo(vmInfo);
        
        // add this interface to the VM
        vmInfo.created(firstVmiInfo);
        // verify VMI has been added in the VMI map of this VM
        assertTrue(vmInfo.contains(firstVmiInfo));
        
        // create VM and VMI
        try {
            vmInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VM " + vmInfo);
        }
        
        VirtualMachineInfoTest.verifyVirtualMachinePresent(vmInfo);
        verifyVirtualMachineInterfacePresent(firstVmiInfo);
        firstInstanceIp = verifyInstanceIpPresent(firstVmiInfo);
    }

    @Test
    public void test1() throws IOException {
        VirtualMachineInterfaceInfo vmiInfo1 = newInstance(1);
        assertNotNull(vmiInfo1);
        VirtualMachineInterfaceInfo vmiInfo2 = newInstance(2);
        assertNotNull(vmiInfo2);
        VirtualMachineInterfaceInfo vmiInfo3 = newInstance(3);
        assertNotNull(vmiInfo3);
    }
    
    @Test
    public void testVirtualMachineInterfaceCreateDelete() throws IOException {
        // Create Virtual Machine and second VMI
        VirtualMachineInterfaceInfo secondVmiInfo = newInstance(2);
        secondVmiInfo.setVnInfo(vnInfo);
        secondVmiInfo.setVmInfo(vmInfo);
        // add this interface to the VM
        vmInfo.created(secondVmiInfo);
        // verify VMI has been added in the VMI map of this VM
        assertTrue(vmInfo.contains(secondVmiInfo));
        
        // create VM and VMI
        try {
            secondVmiInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VMI " + secondVmiInfo);
        }
        
        assertTrue(vnInfo.contains(secondVmiInfo));
        assertTrue(vmInfo.contains(secondVmiInfo));
            
        verifyVirtualMachineInterfacePresent(secondVmiInfo);
        InstanceIp secondInstanceIp = verifyInstanceIpPresent(secondVmiInfo);
        verify(vrouterApi, times(2)).AddPort(any(UUID.class), any(UUID.class), anyString(), any(InetAddress.class),
                any(byte[].class), any(UUID.class), anyShort(), anyShort(),
                anyString());

        try {
            secondVmiInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VMI " + secondVmiInfo);
        }
        
        verifyInstanceIpAbsent(secondInstanceIp);
        verifyVirtualMachineInterfaceAbsent(secondVmiInfo);

        verify(vrouterApi).DeletePort(any(UUID.class));
        
        
        try {
            firstVmiInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VMI " + secondVmiInfo);
        }
        
        verifyInstanceIpAbsent(firstInstanceIp);
        verifyVirtualMachineInterfaceAbsent(firstVmiInfo);
        verify(vrouterApi, times(2)).DeletePort(any(UUID.class));
        
        try {
            vmInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VM " + vmInfo);
        }
        
        VirtualMachineInfoTest.verifyVirtualMachineAbsent(vmInfo);
    }

    @Ignore
    public void testSyncVirtualMachineInterfaceCreate() throws IOException {
        /*
        SortedMap<String, VirtualMachineInfo> oldVMs =
                new ConcurrentSkipListMap<String, VirtualMachineInfo>();
        oldVMs.put(vmInfo.getUuid(), vmInfo);
        
        VirtualMachineInfo newVmInfo = TestCommon.initVirtualMachine();       
        VirtualMachineInterfaceInfo newVmiInfo = TestCommon.initVirtualMachineInterface(vnInfo, newVmInfo);
        
        // add this interface to the VM
        newVmInfo.created(newVmiInfo);
        // verify VMI has been added in the VMI map of this VM
        assertTrue(newVmInfo.contains(newVmiInfo));
 
        SortedMap<String, VirtualMachineInfo> newVMs =
                new ConcurrentSkipListMap<String, VirtualMachineInfo>();
        newVMs.put(newVmInfo.getUuid(), newVmInfo);

        s_logger.info("Sync delete VMI");
        MainDB.sync(oldVMs, newVMs);
        
        VirtualMachine vm = TestCommon.verifyVirtualMachinePresent();
        
        // Verify virtual-machine-interface is created on api-server      
        VirtualMachineInterface vmInterface = TestCommon.verifyVirtualMachineInterfacePresent(vn, vm, newVmiInfo);

        InstanceIp instanceIp = TestCommon.verifyInstanceIpPresent(vn, vmInterface);

        // verify VMI has been added in the VMI map of VN
        assertTrue(vnInfo.contains(newVmiInfo));
 
        newVMs = new ConcurrentSkipListMap<String, VirtualMachineInfo>();
                
        s_logger.info("Sync delete VMI");
        MainDB.sync(oldVMs, newVMs);
        
        TestCommon.verifyVirtualMachineAbsent();      
        TestCommon.verifyVirtualMachineInterfaceAbsent(vmInterface);
        TestCommon.verifyInstanceIpAbsent(instanceIp);
        assertFalse(vnInfo.contains(newVmiInfo));
        */
    }
}
