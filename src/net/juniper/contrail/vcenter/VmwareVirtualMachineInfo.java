package net.juniper.contrail.vcenter;

import com.vmware.vim25.VirtualMachinePowerState;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

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

        vnInfo = new ConcurrentSkipListMap<String, VmwareVirtualNetworkInfo>();
    }

    public VmwareVirtualMachineInfo() {
        vnInfo = new ConcurrentSkipListMap<String, VmwareVirtualNetworkInfo>();
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
        
        return ( !ipAddress.equals(vm.ipAddress)
                || !macAddress.equals(vm.macAddress)
                || !powerState.equals(vm.powerState)
                || !vrouterIpAddress.equals(vm.vrouterIpAddress)
                || !equalNetworks(vm));
    }

    public boolean equals(VmwareVirtualMachineInfo vm) {
        if (vm == null) {
            return false;
        }
        if ((uuid != null && !uuid.equals(vm.uuid))
                || (uuid == null && vm.uuid != null)) {
            return false;
        }
        if ((name != null && !name.equals(vm.name))
                || (name == null && vm.name != null)) {
            return false;
        }
        if ((hmor != null && !hmor.equals(vm.hmor))
                || (hmor == null && vm.hmor != null)) {
            return false;
        }
        if ((vrouterIpAddress != null && !vrouterIpAddress.equals(vm.vrouterIpAddress))
                || (vrouterIpAddress == null && vm.vrouterIpAddress != null)) {
            return false;
        }
        if ((hostName != null && !hostName.equals(vm.hostName))
                || (hostName == null && vm.hostName != null)) {
            return false;
        }
        if ((ipAddress != null && !ipAddress.equals(vm.ipAddress))
                || (ipAddress == null && vm.ipAddress != null)) {
            return false;
        }
        if ((macAddress != null && !macAddress.equals(vm.macAddress))
                || (macAddress == null && vm.macAddress != null)) {
            return false;
        }
        if ((powerState != null && !powerState.equals(vm.powerState))
                || (powerState == null && vm.powerState != null)) {
            return false;
        }
        return equalNetworks(vm);
    }

    public boolean equalNetworks(VmwareVirtualMachineInfo vm) {
        if (vnInfo.size() != vm.vnInfo.size()) {
            return false;
        }
        
        Iterator<Entry<String, VmwareVirtualNetworkInfo>> iter1 =
                vnInfo.entrySet().iterator();
        Iterator<Entry<String, VmwareVirtualNetworkInfo>> iter2 =
                vm.vnInfo.entrySet().iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            Entry<String, VmwareVirtualNetworkInfo> entry1 = iter1.next();
            Entry<String, VmwareVirtualNetworkInfo> entry2 = iter2.next();

            if (!entry1.getKey().equals(entry2.getKey())
                    || !entry1.getValue().equals(entry2.getValue())) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return "VM <name " + name + ", host " + hostName + ", UUID " + uuid + ">";
    }
}
