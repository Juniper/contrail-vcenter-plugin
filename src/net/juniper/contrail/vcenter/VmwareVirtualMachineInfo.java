package net.juniper.contrail.vcenter;

import com.vmware.vim25.VirtualMachinePowerState;

import java.util.SortedMap;

import com.vmware.vim25.ManagedObjectReference;

public class VmwareVirtualMachineInfo {
    ManagedObjectReference hmor;
    private String hostName;
    private String vrouterIpAddress;
    private String macAddress;
    private String ipAddress;
    private String name;
    private String uuid;
    private String interfaceUuid;
    private VirtualMachinePowerState powerState;
    private SortedMap<String, VmwareVirtualNetworkInfo> vnInfo;
    
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
    
    public VmwareVirtualMachineInfo() {}

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

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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
    
    public SortedMap<String, VmwareVirtualNetworkInfo> getVnInfo() {
        return vnInfo;
    }

    public void setVnInfo(SortedMap<String, VmwareVirtualNetworkInfo> vnInfo) {
        this.vnInfo = vnInfo;
    }
    
    public boolean updateVrouterNeeded(VmwareVirtualMachineInfo vm) {
        if (vm == null) {
            return true;
        }
        
        return (!ipAddress.equals(vm.ipAddress))
                || (!macAddress.equals(vm.macAddress))
                || (!powerState.equals(vm.powerState));
    }
    
    public boolean equals(VmwareVirtualMachineInfo vm) {
        if (vm == null) {
            return false;
        }
        
        //TODO check all fields
        return uuid.equals(vm.uuid)
            && name.equals(name)
            && vrouterIpAddress.equals(vrouterIpAddress)
            && hostName.equals(hostName)
            && ipAddress.equals(vm.ipAddress)
            && macAddress.equals(vm.macAddress)
            && powerState.equals(vm.powerState);
    }
    
    public String toString() {
        return "VM <name " + name + ", host " + hostName + ", UUID " + uuid + ">";
    }
}
