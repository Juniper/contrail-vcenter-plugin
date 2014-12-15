package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;


class VncVirtualNetworkInfo {
    private String name;
    private SortedMap<String, VncVirtualMachineInfo> vmInfo;
    
    public VncVirtualNetworkInfo(String name,
            SortedMap<String, VncVirtualMachineInfo> vmInfo) {
        this.name = name;
        this.vmInfo = vmInfo;
    }

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public SortedMap<String, VncVirtualMachineInfo> getVmInfo() {
        return vmInfo;
    }

    public void setVmInfo(SortedMap<String, VncVirtualMachineInfo> vmInfo) {
        this.vmInfo = vmInfo;
    }
}
