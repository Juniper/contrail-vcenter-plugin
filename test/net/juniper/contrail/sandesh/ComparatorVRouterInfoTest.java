package net.juniper.contrail.sandesh;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class ComparatorVRouterInfoTest {

    @Test
    public void test1() {    
        assertEquals(IpAddressUtil.getIpv4Address("127.10.1.1"), 0x7F0A0101L);
        assertEquals(IpAddressUtil.getIpv4Address("0.0.0.0"), 0x0L);
        assertEquals(IpAddressUtil.getIpv4Address("255.255.255.255"), 
                                                   0xFFFFFFFFL);
        assertEquals(IpAddressUtil.getIpv4Address("192.255.255.255"), 
                                                   0xC0FFFFFFL);
        
        assertTrue(IpAddressUtil.compare("127.10.1.1", "0.0.0.0") > 0);
        assertTrue(IpAddressUtil.compare("127.10.1.1", "255.255.255.255") < 0);
        assertTrue(IpAddressUtil.compare("10.10.1.1", "10.10.1.1") == 0);
        assertTrue(IpAddressUtil.compare("127.10.1.1", "10.5.0.255") > 0);
        assertTrue(IpAddressUtil.compare("127.10.1.1", "128.10.1.1") < 0);
        assertTrue(IpAddressUtil.compare("127.10.1.1", "192.10.1.1") < 0);
        assertTrue(IpAddressUtil.compare("128.255.255.255", 
                                         "127.255.255.255") > 0);
        assertTrue(IpAddressUtil.compare("1.10.1.1", "100.5.0.255") < 0);
        assertTrue(IpAddressUtil.compare("100.10.1.1", "20.5.0.255") > 0);
        assertTrue(IpAddressUtil.compare("100.10.1.1", "FF:AA:01:FF") < 0);
        assertTrue(IpAddressUtil.compare("255.255.255.254", "::01") < 0);
        
        int[] b1 = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0xAA, 0x1, 0xFF };
        assertArrayEquals(IpAddressUtil.getIpv6Address("FF:AA:01:FF"), b1);
        assertArrayEquals(IpAddressUtil.getIpv6Address("00:00:00:FF:AA:01:FF"), b1);
        assertArrayEquals(IpAddressUtil.getIpv6Address(":::FF:AA:01:FF"), b1);
        
        int[] b2 = { 0x22, 0, 0, 0, 0, 0xBB, 0, 0, 0, 0, 0, 0, 0xFF, 0xAB, 0x01, 0x0 };
        assertArrayEquals(
                IpAddressUtil.getIpv6Address("22:::::BB:::::::FF:AB:01:00"), 
                b2);
    }
    
    @Test
    public void test2() {      
        VRouterInfo vr1 = new VRouterInfo();
        vr1.setIpAddr("192.168.2.1");
        vr1.setEsxiHost("10.87.5.54");
        
        VRouterInfo vr2 = new VRouterInfo();
        vr2.setIpAddr("19.168.2.1");
        vr2.setEsxiHost("10.87.5.54");
        
        ComparatorVRouterInfo  comp = new ComparatorVRouterInfo();
        assertTrue(comp.compare(vr1, vr2) > 0 );
        
        VRouterInfo vr3 = new VRouterInfo();
        vr3.setIpAddr("192.168.2.1");
        vr3.setEsxiHost("10.87.5.55");
        assertTrue(comp.compare(vr1, vr3) < 0 );
        
        VRouterInfo vr4 = new VRouterInfo();
        vr4.setIpAddr("192.168.2.1");
        vr4.setEsxiHost("10.87.5.54");
        assertTrue(comp.compare(vr1, vr4) == 0 );
    }
    
    @Test (expected = IllegalArgumentException.class)
    public void test3() {
        IpAddressUtil.compare("127.10.1.1", "-1") ;
    }
    
    @Test (expected = IllegalArgumentException.class)
    public void test4() {
        IpAddressUtil.compare("500.10.1.1", "10.10.10.01") ;
    }
    
    @Test (expected = IllegalArgumentException.class)
    public void test5() {
        IpAddressUtil.compare("500::10:1:1", "10.10.10.01") ;
    }
}
