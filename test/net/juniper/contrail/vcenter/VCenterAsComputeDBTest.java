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

import com.vmware.vim25.VirtualMachinePowerState;

@RunWith(JUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VCenterAsComputeDBTest extends TestCase {
    private static final Logger s_logger =
        Logger.getLogger(VCenterAsComputeDBTest.class);
    private static VCenterAsComputeDB vcenterDB;

    @Before
    public void globalSetUp() throws IOException {
        // Setup VCenter object
        vcenterDB = new VCenterAsComputeDB("https://10.20.30.40/sdk", "admin", "admin123",
                                   "unittest_dc", "unittest_dvs", "unittest_fabric_pg");
    }

    @Test
    public void testDoIgnoreVirtualMachine() throws IOException {
        assertTrue(vcenterDB.doIgnoreVirtualMachine("ContrailVM-xyz"));
        assertTrue(vcenterDB.doIgnoreVirtualMachine("abc-ContrailVM-xyz"));
        assertTrue(vcenterDB.doIgnoreVirtualMachine("abc-contrailvm-xyz"));
        assertFalse(vcenterDB.doIgnoreVirtualMachine("Tenent-VM"));
    }
}
