/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import org.apache.log4j.Logger;
import junit.framework.TestCase;
import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

import org.junit.Test;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.MethodSorters;
import org.powermock.api.mockito.PowerMockito;

@RunWith(JUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VCenterDBTest extends TestCase {
    private static VCenterDB vcenterDB;
    private static VncDB vncDB;
    private static ContrailVRouterApi vrouterApi;

    @Before
    public void globalSetUp() throws IOException {
        // Setup VCenter object
        vcenterDB = new VCenterDB("https://10.20.30.40/sdk", "admin", "admin123",
                                   "unittest_dc", null, "unittest_dvs", "unittest_fabric_pg",
                                   Mode.VCENTER_ONLY);
        
        vncDB = mock(VncDB.class);
        doNothing().when(vncDB).createVirtualNetwork(any(VirtualNetworkInfo.class));
        doNothing().when(vncDB).createVirtualMachine(any(VirtualMachineInfo.class));
        doNothing().when(vncDB).createVirtualMachineInterface(any(VirtualMachineInterfaceInfo.class));
        doNothing().when(vncDB).createInstanceIp(any(VirtualMachineInterfaceInfo.class));
        doNothing().when(vncDB).deleteVirtualNetwork(any(VirtualNetworkInfo.class));
        doNothing().when(vncDB).deleteVirtualMachine(any(VirtualMachineInfo.class));
        doNothing().when(vncDB).deleteVirtualMachineInterface(any(VirtualMachineInterfaceInfo.class));
        doNothing().when(vncDB).deleteInstanceIp(any(VirtualMachineInterfaceInfo.class));
        
        PowerMockito.mockStatic(VRouterNotifier.class);
    }

    @Test
    public void testDoIgnoreVirtualMachine() throws IOException {
        /*
        assertTrue(vcenterDB.doIgnoreVirtualMachine("ContrailVM-xyz"));
        assertTrue(vcenterDB.doIgnoreVirtualMachine("abc-ContrailVM-xyz"));
        assertTrue(vcenterDB.doIgnoreVirtualMachine("abc-contrailvm-xyz"));
        assertFalse(vcenterDB.doIgnoreVirtualMachine("Tenent-VM"));
        */
    }

    @Test
    public void testDoIgnoreVirtualNetwork() throws IOException {
        //assertTrue(vcenterDB.doIgnoreVirtualNetwork());
    }
}
