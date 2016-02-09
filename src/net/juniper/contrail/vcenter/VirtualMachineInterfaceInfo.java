/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 */
package net.juniper.contrail.vcenter;

import java.io.IOException;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.NetIpConfigInfo;
import com.vmware.vim25.NetIpConfigInfoIpAddress;
import com.vmware.vim25.VirtualMachineToolsRunningStatus;

public class VirtualMachineInterfaceInfo extends VCenterObject {
    private String uuid;
    VirtualMachineInfo vmInfo;
    VirtualNetworkInfo vnInfo;
    private String ipAddress;
    private String macAddress;
    private boolean portAdded;

    //API server objects
    net.juniper.contrail.api.types.VirtualMachineInterface apiVmi;
    net.juniper.contrail.api.types.InstanceIp apiInstanceIp;

    VirtualMachineInterfaceInfo(VirtualMachineInfo vmInfo,
            VirtualNetworkInfo vnInfo) {
        this.vmInfo = vmInfo;
        this.vnInfo = vnInfo;
    }

    VirtualMachineInterfaceInfo(String macAddress)
    {
        this.macAddress = macAddress;
    }

    VirtualMachineInterfaceInfo(VirtualMachineInterfaceInfo vmiInfo)
    {
        this.vmInfo = vmiInfo.vmInfo;
        this.vnInfo = vmiInfo.vnInfo;
        this.macAddress = vmiInfo.macAddress;
        this.uuid = vmiInfo.uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public VirtualMachineInfo getVmInfo() {
        return vmInfo;
    }

    public void setVmInfo(VirtualMachineInfo vmInfo) {
        this.vmInfo = vmInfo;
    }

    public VirtualNetworkInfo getVnInfo() {
        return vnInfo;
    }

    public void setVnInfo(VirtualNetworkInfo vnInfo) {
        this.vnInfo = vnInfo;
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

    public boolean getPortAdded() {
        return portAdded;
    }

    public void setPortAdded(boolean portAdded) {
        this.portAdded = portAdded;
    }

    public void updatedGuestNic(GuestNicInfo nic, VncDB vncDB)
                throws Exception {
        if (nic == null) {
            return;
        }
        NetIpConfigInfo ipConfig = nic.getIpConfig();
        if (ipConfig == null ) {
            return;
        }
        NetIpConfigInfoIpAddress[] ipAddrs = nic.getIpConfig().getIpAddress();
        if (ipAddrs == null || ipAddrs.length <= 0) {
            return;
        }
        String newIpAddress = ipAddrs[0].getIpAddress();
        if (newIpAddress != null && newIpAddress.equals(ipAddress)) {
            // IP address has not changed
            return;
        }

         if (ipAddress != null) {
            vncDB.deleteInstanceIp(this);
        }

        if (portAdded) {
            portAdded = false;
            VRouterNotifier.deleted(this);
        }

        ipAddress = newIpAddress;

        if (ipAddress != null || vnInfo.getExternalIpam() == false) {
            vncDB.createInstanceIp(this);
        }

        if (vmInfo.isPoweredOnState()) {
            portAdded = true;
            VRouterNotifier.created(this);
        }
    }

    public boolean equals(VirtualMachineInterfaceInfo vmi) {
        if (vmi == null) {
            return false;
        }

        if (!vmInfo.getUuid().equals(vmi.vmInfo.getUuid())) {
            return false;
        }

        if (!vnInfo.getUuid().equals(vmi.vnInfo.getUuid())) {
            return false;
        }

        if ((ipAddress != null && !ipAddress.equals(vmi.ipAddress))
                || (ipAddress == null && vmi.ipAddress != null)) {
            return false;
        }
        if ((macAddress != null && !macAddress.equals(vmi.macAddress))
                || (macAddress == null && vmi.macAddress != null)) {
            return false;
        }
        return true;
    }

    public String toString() {
        return "VMI <" + vmInfo.getName() + ", " + vnInfo.getName()
            + ", " + uuid + ", " + ipAddress + ", " + macAddress + ">";
    }

    @Override
    void create(VncDB vncDB) throws Exception {
        if (vncDB.mode != Mode.VCENTER_AS_COMPUTE && vnInfo.getExternalIpam() == true) {
            if (vmInfo.getToolsRunningStatus().equals(VirtualMachineToolsRunningStatus.guestToolsRunning.toString())) {
                // static IP Address & vmWare tools installed
                // see if we can read it from Guest Nic Info
                ipAddress = VCenterDB.getVirtualMachineIpAddress(vmInfo.vm, vnInfo.getName());
            }
            VCenterNotify.watchVm(vmInfo);
        }

        vncDB.createVirtualMachineInterface(this);

        if (ipAddress != null || vnInfo.getExternalIpam() == false) {
            vncDB.createInstanceIp(this);
        }

        if (vmInfo.isPoweredOnState() && !portAdded) {
            portAdded = true;
            VRouterNotifier.created(this);
        }

        vnInfo.created(this);
        vmInfo.created(this);
    }

    @Override
    void update(VCenterObject obj,
            VncDB vncDB) throws Exception {

        VirtualMachineInterfaceInfo newVmiInfo = (VirtualMachineInterfaceInfo)obj;

        // change of Ip Address, MAC address or network triggers a delete / recreate
        if ((newVmiInfo.ipAddress != null && !newVmiInfo.ipAddress.equals(ipAddress))
                || (newVmiInfo.macAddress != null && !newVmiInfo.macAddress.equals(macAddress))
                || vnInfo != newVmiInfo.vnInfo) {
            if (portAdded) {
                portAdded = false;
                VRouterNotifier.deleted(this);
            }

            vncDB.deleteInstanceIp(this);

            if (vnInfo != newVmiInfo.vnInfo) {
                // change of network
                vnInfo.deleted(this);
                vnInfo = newVmiInfo.vnInfo;
                ipAddress = newVmiInfo.ipAddress;
                macAddress = newVmiInfo.macAddress;
                vnInfo.created(this);
            } else {
                if (newVmiInfo.ipAddress != null) {
                    ipAddress = newVmiInfo.ipAddress;
                }

                if (newVmiInfo.macAddress != null) {
                    macAddress = newVmiInfo.macAddress;
                }
            }

            if (ipAddress != null || vnInfo.getExternalIpam() == false) {
                vncDB.createInstanceIp(this);
            }
        }

        if (newVmiInfo.uuid != null && !newVmiInfo.uuid.equals(uuid)) {
            uuid = newVmiInfo.uuid;
        }
        if (vmInfo.isPoweredOnState()) {
            if (!portAdded) {
                portAdded = true;
                VRouterNotifier.created(this);
            }
        } else {
            if (portAdded) {
                portAdded = false;
                VRouterNotifier.deleted(this);
            }
        }
    }

    @Override
    void sync(VCenterObject obj,
            VncDB vncDB) throws Exception {

        VirtualMachineInterfaceInfo oldVmiInfo = (VirtualMachineInterfaceInfo)obj;

        if (apiVmi == null && oldVmiInfo.apiVmi != null) {
            apiVmi = oldVmiInfo.apiVmi;
        }

        if (vnInfo == oldVmiInfo.vnInfo) {
            // network is the same
            // reuse the old address
            if (apiInstanceIp == null && oldVmiInfo.apiInstanceIp != null) {
                apiInstanceIp = oldVmiInfo.apiInstanceIp;
            }

            if (ipAddress == null && oldVmiInfo.ipAddress != null) {
                ipAddress = oldVmiInfo.ipAddress;
            }

            if (macAddress == null && oldVmiInfo.macAddress != null) {
                macAddress = oldVmiInfo.macAddress;
            }

            if (uuid == null && oldVmiInfo.uuid != null) {
                uuid = oldVmiInfo.uuid;
            }
        }

        if ((oldVmiInfo.ipAddress != null && !oldVmiInfo.ipAddress.equals(ipAddress))
                || (oldVmiInfo.macAddress != null && !oldVmiInfo.macAddress.equals(macAddress))
                || (vnInfo != oldVmiInfo.vnInfo)) {
            portAdded = false;
            VRouterNotifier.deleted(oldVmiInfo);
        }

        if ((oldVmiInfo.ipAddress != null && !oldVmiInfo.ipAddress.equals(ipAddress))
                || (vnInfo != oldVmiInfo.vnInfo)) {
            vncDB.deleteInstanceIp(oldVmiInfo);
            oldVmiInfo.vnInfo.deleted(oldVmiInfo);
        }

        if (vnInfo != oldVmiInfo.vnInfo) {
            oldVmiInfo.vnInfo.deleted(oldVmiInfo);
        }

        if ((ipAddress != null || vnInfo.getExternalIpam() == false)
                && apiInstanceIp == null) {
            vncDB.createInstanceIp(this);
        }

        if (vmInfo.isPoweredOnState()) {
            portAdded = true;
            VRouterNotifier.created(this);
        }

        vnInfo.created(this);
    }

    @Override
    void delete(VncDB vncDB)
            throws IOException {
        if (portAdded) {
            VRouterNotifier.deleted(this);
            portAdded = false;
        }

        vncDB.deleteInstanceIp(this);

        vncDB.deleteVirtualMachineInterface(this);

        vnInfo.deleted(this);
        vmInfo.deleted(this);
    }
}
