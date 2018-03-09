/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import org.apache.log4j.Logger;
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
import net.juniper.contrail.api.types.Project;

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
            if (!_api.create(vProject).isSuccess()) {
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

        MainDB.vncDB = vncDB;
        VirtualNetworkInfoTest.vncDB = vncDB;
        VirtualNetworkInfoTest.api = _api;
        VirtualMachineInfoTest.vncDB = vncDB;
        VirtualMachineInfoTest.api = _api;
        VirtualMachineInterfaceInfoTest.vncDB = vncDB;
        VirtualMachineInterfaceInfoTest.api = _api;
    }

    @Test
    public void testVirtualNetworkAddDelete() throws IOException {
        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(VirtualNetworkInfoTest.BLUE);

        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);

        // Verify virtual-network creation
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vnInfo);

        // Delete virtual-network from api-server
        s_logger.info("Deleting " + vnInfo);
        vncDB.deleteVirtualNetwork(vnInfo);

        s_logger.info("Verifying VN is deleted " + vnInfo);
        VirtualNetworkInfoTest.verifyVirtualNetworkAbsent(vnInfo);
    }

    @Test
    public void testAddDeleteOneByOne() throws IOException {
        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(VirtualNetworkInfoTest.BLUE);

        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);

        // Verify virtual-network creation
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vnInfo);

        // Create Virtual Machine
        VirtualMachineInfo vmInfo = new VirtualMachineInfo(VirtualMachineInfoTest.VM1);

        vncDB.createVirtualMachine(vmInfo);

        // Verify virtual-machine is created on api-server
        VirtualMachineInfoTest.verifyVirtualMachinePresent(vmInfo);

        VirtualMachineInterfaceInfo vmiInfo = new VirtualMachineInterfaceInfo(VirtualMachineInterfaceInfoTest.VMI1);
        vmiInfo.setVnInfo(vnInfo);
        vmiInfo.setVmInfo(vmInfo);
        vncDB.createVirtualMachineInterface(vmiInfo);

        // Verify virtual-machine-interface is created on api-server
        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfacePresent(vmiInfo);

        vncDB.createInstanceIp(vmiInfo);

        InstanceIp instanceIp = VirtualMachineInterfaceInfoTest.verifyInstanceIpPresent(vmiInfo);

        // Delete
        vncDB.deleteInstanceIp(vmiInfo);

        // Verify instance-ip is deleted from  api-server
        VirtualMachineInterfaceInfoTest.verifyInstanceIpAbsent(instanceIp);

        vncDB.deleteVirtualMachineInterface(vmiInfo);

        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfaceAbsent(vmiInfo);

        vncDB.deleteVirtualMachine(vmInfo);

        // Verify virtual-machine is deleted from  api-server
        VirtualMachineInfoTest.verifyVirtualMachineAbsent(vmInfo);

        // Delete virtual-network from api-server
        vncDB.deleteVirtualNetwork(vnInfo);

        // Verify virtual-network is deleted
        VirtualNetworkInfoTest.verifyVirtualNetworkAbsent(vnInfo);
    }

    @Test
    public void testAddHierarchicalDelete() throws IOException {
        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(VirtualNetworkInfoTest.BLUE);

        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);

        // Verify virtual-network creation
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vnInfo);

        // Create Virtual Machine
        VirtualMachineInfo vmInfo = new VirtualMachineInfo(VirtualMachineInfoTest.VM1);

        vncDB.createVirtualMachine(vmInfo);

        // Verify virtual-machine is created on api-server
        VirtualMachineInfoTest.verifyVirtualMachinePresent(vmInfo);

        VirtualMachineInterfaceInfo vmiInfo = new VirtualMachineInterfaceInfo(VirtualMachineInterfaceInfoTest.VMI1);
        vmiInfo.setVnInfo(vnInfo);
        vmiInfo.setVmInfo(vmInfo);
        vncDB.createVirtualMachineInterface(vmiInfo);

        // Verify virtual-machine-interface is created on api-server
        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfacePresent(vmiInfo);

        vncDB.createInstanceIp(vmiInfo);

        InstanceIp instanceIp = VirtualMachineInterfaceInfoTest.verifyInstanceIpPresent(vmiInfo);

        // Delete virtual-network from api-server
        // This should in turn delete thr virtual-machine,
        // virtual-machine-interfce, instance-ip etc
        vncDB.deleteVirtualNetwork(vnInfo);

        // Verify instance-ip is deleted from  api-server
        VirtualMachineInterfaceInfoTest.verifyInstanceIpAbsent(instanceIp);

        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfaceAbsent(vmiInfo);

        // Verify virtual-network is deleted
        VirtualNetworkInfoTest.verifyVirtualNetworkAbsent(vnInfo);

        // verify VM is still there
        VirtualMachineInfoTest.verifyVirtualMachinePresent(vmInfo);
    }

    @Test
    public void testAddHierarchicalDeleteVM() throws IOException {
        VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(VirtualNetworkInfoTest.BLUE);

        // Create virtual-network on api-server
        vncDB.createVirtualNetwork(vnInfo);

        // Verify virtual-network creation
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vnInfo);

        // Create Virtual Machine
        VirtualMachineInfo vmInfo = new VirtualMachineInfo(VirtualMachineInfoTest.VM1);

        vncDB.createVirtualMachine(vmInfo);

        // Verify virtual-machine is created on api-server
        VirtualMachineInfoTest.verifyVirtualMachinePresent(vmInfo);

        VirtualMachineInterfaceInfo vmiInfo = new VirtualMachineInterfaceInfo(VirtualMachineInterfaceInfoTest.VMI1);
        vmiInfo.setVnInfo(vnInfo);
        vmiInfo.setVmInfo(vmInfo);
        vncDB.createVirtualMachineInterface(vmiInfo);

        // Verify virtual-machine-interface is created on api-server
        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfacePresent(vmiInfo);

        vncDB.createInstanceIp(vmiInfo);

        InstanceIp instanceIp = VirtualMachineInterfaceInfoTest.verifyInstanceIpPresent(vmiInfo);

        // Delete virtual machine from api-server
        // This should in turn delete the virtual-machine,
        // virtual-machine-interface, instance-ip etc
        vncDB.deleteVirtualMachine(vmInfo);

        // Verify instance-ip is deleted from  api-server
        VirtualMachineInterfaceInfoTest.verifyInstanceIpAbsent(instanceIp);

        VirtualMachineInterfaceInfoTest.verifyVirtualMachineInterfaceAbsent(vmiInfo);

        // Verify virtual-network is still there
        VirtualNetworkInfoTest.verifyVirtualNetworkPresent(vnInfo);

        // verify VM is still there
        VirtualMachineInfoTest.verifyVirtualMachineAbsent(vmInfo);    }

    @Test(expected=IllegalArgumentException.class)
    public void testDeleteVirtualNetworkNullInput() throws IOException {
        vncDB.deleteVirtualNetwork(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDeleteVirtualMachineNullInput() throws IOException {
        vncDB.deleteVirtualMachine(null);
    }
}
