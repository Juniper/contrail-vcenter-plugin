/**
 * Copyright (c) 2016 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.SortedMap;
import org.apache.log4j.Logger;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.verify;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.MethodSorters;
import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorMock;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.Project;
import com.vmware.vim25.VirtualMachinePowerState;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

@RunWith(JUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VirtualMachineInfoTest extends TestCase {
    private static final Logger s_logger =
        Logger.getLogger(VirtualMachineInfoTest.class);
    public static VncDB vncDB;
    public static ApiConnector api;
    private static VirtualNetworkInfo vnInfo;
    private static ContrailVRouterApi vrouterApi;

    private final static String vmUuid1            = UUID.randomUUID().toString();
    private final static String vmName1            = "VM1";
    private final static String vrouterIpAddress1  = "10.84.24.45";
    private final static String hostName1          = "10.20.30.40";
    private final static VirtualMachinePowerState powerState1 = VirtualMachinePowerState.poweredOn;

    public static VirtualMachineInfo VM1 = new VirtualMachineInfo(vmUuid1, vmName1,
            hostName1, vrouterIpAddress1, powerState1);
    
    private final static String vmUuid2            = UUID.randomUUID().toString();
    private final static String vmName2            = "VM2";
    private final static String vrouterIpAddress2  = "10.84.24.45";
    private final static String hostName2          = "10.20.30.40";
    private final static VirtualMachinePowerState powerState2 = VirtualMachinePowerState.poweredOn;
    
    public static VirtualMachineInfo VM2 = new VirtualMachineInfo(vmUuid2, vmName2,
            hostName2, vrouterIpAddress2, powerState2);
    
    public static VirtualMachineInfo newInstance(int selection) {
        switch (selection) {
        case 1:
            return new VirtualMachineInfo(VM1);
        case 2:
            return new VirtualMachineInfo(VM2);
        default:
            ;
        }
        return new VirtualMachineInfo(VM1);
    }
    
    public static VirtualMachine verifyVirtualMachinePresent(VirtualMachineInfo vmInfo) 
            throws IOException {
        
        VirtualMachine vm = (VirtualMachine) api.findById(VirtualMachine.class, vmInfo.getUuid());
        assertNotNull(vm);
        assertEquals(vm.getUuid(), vmInfo.getUuid());
        assertEquals(vm.getName(), vmInfo.getUuid());
        assertEquals(vm.getDisplayName(), vmInfo.getVrouterIpAddress());
        assertEquals(vm.getIdPerms(), vncDB.getVCenterIdPerms());
        return vm;
    }

    public static void verifyVirtualMachineAbsent(VirtualMachineInfo vmInfo) throws IOException {
        VirtualMachine vm = (VirtualMachine)  api.findById(VirtualMachine.class, vmInfo.getUuid());
        assertNull(vm);
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
        VirtualMachineInterfaceInfoTest.api = api;
        VirtualMachineInterfaceInfoTest.vncDB = vncDB;
        MainDB.vncDB = vncDB;

        // Setup mock ContrailVRouterApi connection for vrouterIp = 10.84.24.45
        Map<String, ContrailVRouterApi> vrouterApiMap = VRouterNotifier.getVrouterApiMap();
        vrouterApi = mock(ContrailVRouterApi.class);
        when(vrouterApi.AddPort(any(UUID.class), any(UUID.class), anyString(), any(InetAddress.class),
                                any(byte[].class), any(UUID.class), anyShort(), anyShort(),
                                anyString())).thenReturn(true);
        when(vrouterApi.DeletePort(any(UUID.class))).thenReturn(true);
        vrouterApiMap.put("10.84.24.45", vrouterApi);

        vnInfo = VirtualNetworkInfoTest.newInstance(1);
        
        try {
            vnInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + vnInfo);
        }

        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vnInfo);
    }
    
    @Test
    public void testInstances() throws IOException {
        VirtualMachineInfo vmInfo1 = newInstance(1);
        assertNotNull(vmInfo1);
        VirtualMachineInfo vmInfo2 = newInstance(2);
        assertNotNull(vmInfo2);
    }
    
    @Test
    public void testVirtualMachineCreateIgnore() throws IOException {
        VirtualMachineInfo vmInfo = newInstance(1);
        try {
            vmInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VM " + vmInfo);
        }
        // VM will be ignored since there are no VMIs in managed networks
        verifyVirtualMachineAbsent(vmInfo);
    }

    @Test
    public void testVirtualMachineCreateDelete() throws IOException {       
        // Create Virtual Machine and VMIs
        VirtualMachineInfo vmInfo = newInstance(1);
        
        VirtualMachineInterfaceInfo vmiInfo = VirtualMachineInterfaceInfoTest.newInstance(2);
        vmiInfo.setVnInfo(vnInfo);
        vmiInfo.setVmInfo(vmInfo);
        
        // add this interface to the VM
        vmInfo.created(vmiInfo);
        // verify VMI has been added in the VMI map of this VM
        assertTrue(vmInfo.contains(vmiInfo));
        
        // create VM and VMI
        try {
            vmInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VM " + vmInfo);
        }
        
        assertTrue(vnInfo.contains(vmiInfo));
        assertTrue(MainDB.getVMs().containsKey(vmInfo.getUuid()));
        
        verifyVirtualMachinePresent(vmInfo);     
        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfacePresent(vmiInfo);        
        InstanceIp instanceIp = VirtualMachineInterfaceInfoTest.verifyInstanceIpPresent(vmiInfo);
        verify(vrouterApi).AddPort(any(UUID.class), any(UUID.class), anyString(), any(InetAddress.class),
                any(byte[].class), any(UUID.class), anyShort(), anyShort(),
                anyString());

        // delete the VM
        try {
            vmInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VM " + vmInfo);
        }

        assertFalse(MainDB.getVMs().containsKey(vmInfo.getUuid()));
        
        VirtualMachineInterfaceInfoTest.verifyInstanceIpAbsent(instanceIp);
        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfaceAbsent(vmiInfo);
        verifyVirtualMachineAbsent(vmInfo);

        verify(vrouterApi).DeletePort(any(UUID.class));
    }
    
    @Test
    public void testSyncVirtualMachine() throws IOException {
        
        SortedMap<String, VirtualMachineInfo> oldVMs =
                new ConcurrentSkipListMap<String, VirtualMachineInfo>();
        
        VirtualMachineInfo newVmInfo = newInstance(1);       
        VirtualMachineInterfaceInfo newVmiInfo = VirtualMachineInterfaceInfoTest.newInstance(2);
        newVmiInfo.setVnInfo(vnInfo);
        newVmiInfo.setVmInfo(newVmInfo);
        newVmInfo.created(newVmiInfo);
        assertTrue(newVmInfo.contains(newVmiInfo));
 
        SortedMap<String, VirtualMachineInfo> newVMs = new ConcurrentSkipListMap<String, VirtualMachineInfo>();
        newVMs.put(newVmInfo.getUuid(), newVmInfo);

        s_logger.info("Sync create VM");
        MainDB.sync(oldVMs, newVMs);
        
        verifyVirtualMachinePresent(newVmInfo);
        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfacePresent(newVmiInfo);
        InstanceIp instanceIp = VirtualMachineInterfaceInfoTest.verifyInstanceIpPresent(newVmiInfo);

        // verify VMI has been added in the VMI map of VN
        assertTrue(vnInfo.contains(newVmiInfo));
        
        oldVMs = vncDB.readVirtualMachines();
        newVMs = new ConcurrentSkipListMap<String, VirtualMachineInfo>();
        
        s_logger.info("Sync delete VM");
        MainDB.sync(oldVMs, newVMs);
        
        verifyVirtualMachineAbsent(newVmInfo);   
        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfaceAbsent(newVmiInfo);
        VirtualMachineInterfaceInfoTest.verifyInstanceIpAbsent(instanceIp);
    }
}
