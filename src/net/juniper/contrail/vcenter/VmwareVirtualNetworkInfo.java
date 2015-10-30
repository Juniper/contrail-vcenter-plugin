package net.juniper.contrail.vcenter;

import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class VmwareVirtualNetworkInfo {
    private String name;
    private String uuid;
    private short isolatedVlanId;
    private short primaryVlanId;
    private SortedMap<String, VmwareVirtualMachineInfo> vmInfo;
    private String subnetAddress;
    private String subnetMask;
    private String gatewayAddress;
    private boolean ipPoolEnabled;
    private String range;
    private boolean externalIpam;

    public VmwareVirtualNetworkInfo(String name, short isolatedVlanId,
            short primaryVlanId, SortedMap<String, VmwareVirtualMachineInfo> vmInfo,
            String subnetAddress, String subnetMask, String gatewayAddress,
            boolean ipPoolEnabled, String range, boolean externalIpam) {
        this.name = name;
        this.isolatedVlanId = isolatedVlanId;
        this.primaryVlanId = primaryVlanId;
        this.vmInfo = vmInfo;
        this.subnetAddress = subnetAddress;
        this.subnetMask = subnetMask;
        this.gatewayAddress = gatewayAddress;
        this.ipPoolEnabled = ipPoolEnabled;
        this.range = range;
        this.externalIpam = externalIpam;
    }

    public VmwareVirtualNetworkInfo() {
        vmInfo = new ConcurrentSkipListMap<String, VmwareVirtualMachineInfo>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public short getIsolatedVlanId() {
        return isolatedVlanId;
    }

    public void setIsolatedVlanId(short vlanId) {
        this.isolatedVlanId = vlanId;
    }

    public short getPrimaryVlanId() {
        return primaryVlanId;
    }

    public void setPrimaryVlanId(short vlanId) {
        this.primaryVlanId = vlanId;
    }

    public boolean getIpPoolEnabled() {
        return ipPoolEnabled;
    }

    public void setIpPoolEnabled(boolean _ipPoolEnabled) {
        this.ipPoolEnabled = _ipPoolEnabled;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String _range) {
        this.range = _range;
    }

    public SortedMap<String, VmwareVirtualMachineInfo> getVmInfo() {
        return vmInfo;
    }

    public void setVmInfo(SortedMap<String, VmwareVirtualMachineInfo> vmInfo) {
        this.vmInfo = vmInfo;
    }

    public String getSubnetAddress() {
        return subnetAddress;
    }

    public void setSubnetAddress(String subnetAddress) {
        this.subnetAddress = subnetAddress;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public String getGatewayAddress() {
        return gatewayAddress;
    }

    public void setGatewayAddress(String gatewayAddress) {
        this.gatewayAddress = gatewayAddress;
    }

    public boolean getExternalIpam() {
        return externalIpam;
    }

    public void setExternalIpam(boolean externalIpam) {
        this.externalIpam = externalIpam;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean equals(VmwareVirtualNetworkInfo vn) {
        if (name != null && !name.equals(vn.name)
                || name == null && vn.name != null) {
            return false;
        }
        if (uuid != null && !uuid.equals(vn.uuid)
                || uuid == null && vn.uuid != null) {
            return false;
        }
        if (isolatedVlanId != vn.isolatedVlanId
                || primaryVlanId != vn.primaryVlanId
                || ipPoolEnabled != vn.ipPoolEnabled
                || externalIpam != vn.externalIpam) {
            return false;
        }
        if (subnetAddress != null && !subnetAddress.equals(vn.subnetAddress)
                || subnetAddress == null && vn.subnetAddress != null) {
            return false;
        }
        if (subnetMask != null && !subnetMask.equals(vn.subnetMask)
                || subnetMask == null && vn.subnetMask != null) {
            return false;
        }
        if (gatewayAddress != null && !gatewayAddress.equals(vn.gatewayAddress)
                || gatewayAddress == null && vn.gatewayAddress != null) {
            return false;
        }
        if (range != null && !range.equals(vn.range)
                || range == null && vn.range != null) {
            return false;
        }
        return true;
    }
}
