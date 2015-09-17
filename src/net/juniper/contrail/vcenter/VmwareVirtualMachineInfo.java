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

import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.ManagedObjectReference;

public class VmwareVirtualMachineInfo {
    ManagedObjectReference hmor;
    private String hostName;
    private String vrouterIpAddress;
    private String macAddress;
    private String ipAddress;
    private String name;
    private String interfaceUuid;
    private VirtualMachinePowerState powerState;
    
    public VmwareVirtualMachineInfo(String name, String hostName, 
            ManagedObjectReference hmor,
            String vrouterIpAddress, String macAddress,
            VirtualMachinePowerState powerState) {
        this.name             = name;
        this.hostName         = hostName;
        this.vrouterIpAddress = vrouterIpAddress;
        this.macAddress       = macAddress;
        this.powerState       = powerState;
        this.hmor             = hmor;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public ManagedObjectReference getHmor() {
        return hmor;
    }

    public void setHmor(ManagedObjectReference hmor) {
        this.hmor = hmor;
    }

    public String getVrouterIpAddress() {
        return vrouterIpAddress;
    }

    public void setVrouterIpAddress(String vrouterIpAddress) {
        this.vrouterIpAddress = vrouterIpAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInterfaceUuid() {
        return interfaceUuid;
    }

    public void setInterfaceUuid(String uuid) {
        this.interfaceUuid = uuid;
    }

    public VirtualMachinePowerState getPowerState() {
        return powerState;
    }

    public void setPowerState(VirtualMachinePowerState powerState) {
        this.powerState = powerState;
    }

    public boolean isPowerStateEqual(VirtualMachinePowerState powerState) {
        if (this.powerState == powerState)
            return true;
        else
            return false;
    }
    public boolean isPoweredOnState() {
        if (powerState == VirtualMachinePowerState.poweredOn)
            return true;
        else
            return false;
    }
}
