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

import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;

class VncVirtualMachineInfo {
    private VirtualMachine vmInfo;
    private VirtualMachineInterface vmInterfaceInfo;
    
    public VncVirtualMachineInfo(VirtualMachine vmInfo,
            VirtualMachineInterface vmInterfaceInfo) {
        this.vmInfo = vmInfo;
        this.vmInterfaceInfo = vmInterfaceInfo;
    }

    public VirtualMachine getVmInfo() {
        return vmInfo;
    }

    public void setVmInfo(VirtualMachine vmInfo) {
        this.vmInfo = vmInfo;
    }

    public VirtualMachineInterface getVmInterfaceInfo() {
        return vmInterfaceInfo;
    }

    public void setVmInterfaceInfo(VirtualMachineInterface vmInterfaceInfo) {
        this.vmInterfaceInfo = vmInterfaceInfo;
    }
}
