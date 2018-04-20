package net.juniper.contrail.vcenter;

import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineToolsRunningStatus;
import com.vmware.vim25.mo.HostSystem;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import com.vmware.vim25.Event;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.ManagedObjectReference;

public class VirtualMachineInfo extends VCenterObject {
    private String uuid; // required attribute, key for this object
    private String name;
    private String displayName;
    private String hostName;
    private String vrouterIpAddress;
    private VirtualMachinePowerState powerState;
    private String toolsRunningStatus = VirtualMachineToolsRunningStatus.guestToolsNotRunning.toString();
    private SortedMap<String, VirtualMachineInterfaceInfo> vmiInfoMap =
            new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
        /* keyed by MAC address, contains only interfaces
         * belonging to managed networks
         */
    protected static final String contrailVRouterVmNamePrefix = "contrailVM";

    // Vmware objects
    com.vmware.vim25.mo.VirtualMachine vm;
    com.vmware.vim25.mo.HostSystem host;
    com.vmware.vim25.mo.VmwareDistributedVirtualSwitch dvs;
    String dvsName;
    com.vmware.vim25.mo.Datacenter dc;
    String dcName;

    //API server objects
    net.juniper.contrail.api.types.VirtualMachine apiVm;

    public VirtualMachineInfo(String uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Cannot init VM with null uuid");
        }
        this.uuid = uuid;
    }

    public VirtualMachineInfo(String uuid, String name, String hostName, String vrouterIpAddress,
            VirtualMachinePowerState powerState)
    {
        this.uuid = uuid;
        this.name = name;
        this.displayName = name;
        this.hostName = hostName;
        this.vrouterIpAddress = vrouterIpAddress;
        this.powerState = powerState;
    }

    public VirtualMachineInfo(VirtualMachineInfo vmInfo)
    {
        if (vmInfo == null) {
            throw new IllegalArgumentException("Cannot init VM from null VM");
        }
        this.uuid = vmInfo.uuid;
        this.name = vmInfo.name;
        this.displayName = vmInfo.displayName;
        this.hostName = vmInfo.hostName;
        this.vrouterIpAddress = vmInfo.vrouterIpAddress;
        this.powerState = vmInfo.powerState;
        this.vmiInfoMap = vmInfo.vmiInfoMap;
    }

    public VirtualMachineInfo(Event event,  VCenterDB vcenterDB, VncDB vncDB) throws Exception {
        if (event.getDatacenter() != null) {
            dcName = event.getDatacenter().getName();
            dc = vcenterDB.getVmwareDatacenter(dcName);
        }

        if (event.getDvs() != null) {
            dvsName = event.getDvs().getName();
            dvs = vcenterDB.getVmwareDvs(dvsName, dc, dcName);
        } else {
            dvsName = vcenterDB.contrailDvSwitchName;
            dvs = vcenterDB.getVmwareDvs(dvsName, dc, dcName);
        }

        if (event.getHost() != null) {
            hostName = event.getHost().getName();
            host = vcenterDB.getVmwareHost(hostName, dc, dcName);

            if (event.getVm() != null) {

                name = event.getVm().getName();

                vm = vcenterDB.getVmwareVirtualMachine(name, host, hostName, dcName);

                if (vcenterDB.mode == Mode.VCENTER_AS_COMPUTE
                        && !name.toLowerCase().contains(
                        contrailVRouterVmNamePrefix.toLowerCase())) {
                    displayName = vm.getConfig().getAnnotation();
                    if(displayName.contains(":")) {
                       String parts[] = displayName.split("\\s");
                       displayName = parts[0];
                       String name[] = displayName.split(":");
                       displayName = name[1];
                    }
                } else {
                    displayName = name;
                }
             }
        }

        vrouterIpAddress = vcenterDB.getVRouterVMIpFabricAddress(
                hostName, host, contrailVRouterVmNamePrefix);

        VirtualMachineConfigInfo config = vm.getConfig();
        if (config != null) {
            uuid = config.getInstanceUuid();
        }

        VirtualMachineRuntimeInfo vmRuntimeInfo = vm.getRuntime();
        if (vmRuntimeInfo != null) {
            powerState = vmRuntimeInfo.getPowerState();
        }
        GuestInfo guest = vm.getGuest();
        if (guest != null) {
            toolsRunningStatus = guest.getToolsRunningStatus();
        }

        setContrailVmActiveState();

        vcenterDB.readVirtualMachineInterfaces(this);

        if (vcenterDB.mode == Mode.VCENTER_AS_COMPUTE) {
            // ipAddress and UUID must be read from Vnc
            for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
                vmiInfoMap.entrySet()) {
                VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
                vncDB.readVirtualMachineInterface(vmiInfo);
            }
        }
    }

    private void setContrailVmActiveState() {
        if (name.toLowerCase().contains(contrailVRouterVmNamePrefix.toLowerCase())) {
            // this is a Contrail VM
            if (!powerState.equals(VirtualMachinePowerState.poweredOn)) {
                VRouterNotifier.setVrouterActive(vrouterIpAddress, false);
            } else if (host != null) {
                if (host.getRuntime().isInMaintenanceMode()) {
                    VRouterNotifier.setVrouterActive(vrouterIpAddress, false);
                } else {
                    VRouterNotifier.setVrouterActive(vrouterIpAddress, true);
                }
            }
        }
    }

    public VirtualMachineInfo(net.juniper.contrail.api.types.VirtualMachine vm) {
        if (vm == null) {
            throw new IllegalArgumentException("Cannot init VM with null API VM");
        }
        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();

        apiVm = vm;
        uuid = vm.getUuid();
        name = vm.getName();
        displayName = vm.getDisplayName();
    }

    public VirtualMachineInfo(VCenterDB vcenterDB,
            com.vmware.vim25.mo.Datacenter dc, String dcName,
            com.vmware.vim25.mo.VirtualMachine vm, @SuppressWarnings("rawtypes") Hashtable pTable,
            com.vmware.vim25.mo.HostSystem host,
            String vrouterIpAddress)
                    throws Exception {

        if (vcenterDB == null || dc == null || dcName == null
                || vm == null || pTable == null) {
            throw new IllegalArgumentException("Cannot init VM");
        }

        this.dc = dc;
        this.dcName = dcName;
        this.vm = vm;

        // Name
        uuid  = (String)  pTable.get("config.instanceUuid");
        name = (String) pTable.get("name");

        if (vcenterDB.mode == Mode.VCENTER_AS_COMPUTE
                && !name.toLowerCase().contains(
                contrailVRouterVmNamePrefix.toLowerCase())) {
            displayName = (String)  pTable.get("config.annotation");
        } else {
            displayName = name;
        }

        if (host == null) {
            ManagedObjectReference hostHmor = (ManagedObjectReference) pTable.get("runtime.host");
            host = new HostSystem(vm.getServerConnection(), hostHmor);
        }
        this.host = host;
        hostName = host.getName();

        powerState = (VirtualMachinePowerState)pTable.get("runtime.powerState");
        toolsRunningStatus  = (String)  pTable.get("guest.toolsRunningStatus");

        if (vrouterIpAddress == null) {
            vrouterIpAddress = vcenterDB.getVRouterVMIpFabricAddress(
                    hostName, host, contrailVRouterVmNamePrefix);
        }
        this.vrouterIpAddress = vrouterIpAddress;

        setContrailVmActiveState();
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getVrouterIpAddress() {
        return vrouterIpAddress;
    }

    public void setVrouterIpAddress(String vrouterIpAddress) {
        this.vrouterIpAddress = vrouterIpAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public String getToolsRunningStatus() {
        return toolsRunningStatus;
    }

    public void setToolsRunningStatus(String toolsRunningStatus) {
        this.toolsRunningStatus = toolsRunningStatus;
    }

    public SortedMap<String, VirtualMachineInterfaceInfo> getVmiInfo() {
        return vmiInfoMap;
    }

    public void setVmiInfo(SortedMap<String, VirtualMachineInterfaceInfo> vmiInfoMap) {
        this.vmiInfoMap = vmiInfoMap;
    }

    public void updatedGuestNics(GuestNicInfo[] nics, VncDB vncDB)
            throws Exception {
        if (nics == null) {
            return;
        }

        for (GuestNicInfo nic: nics) {
            if (nic == null) {
                continue;
            }
            String mac = nic.getMacAddress();

            if (vmiInfoMap.containsKey(mac)) {
                VirtualMachineInterfaceInfo oldVmi = vmiInfoMap.get(mac);
                oldVmi.updatedGuestNic(nic, vncDB);
            }
        }
    }

    public void created(VirtualMachineInterfaceInfo vmiInfo) {
        vmiInfoMap.put(vmiInfo.getMacAddress(), vmiInfo);
    }

    public void updated(VirtualMachineInterfaceInfo vmiInfo) {
        if (!vmiInfoMap.containsKey(vmiInfo.getMacAddress())) {
            vmiInfoMap.put(vmiInfo.getMacAddress(), vmiInfo);
        }
    }

    public void deleted(VirtualMachineInterfaceInfo vmiInfo) {
        if (vmiInfoMap.containsKey(vmiInfo.getMacAddress())) {
            vmiInfoMap.remove(vmiInfo.getMacAddress());
        }
    }

    public boolean contains(VirtualMachineInterfaceInfo vmiInfo) {
        return vmiInfoMap.containsKey(vmiInfo.getMacAddress());
    }

    public boolean equals(VirtualMachineInfo vm) {
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
        if ((displayName != null && !displayName.equals(vm.displayName))
                || (displayName == null && vm.displayName != null)) {
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
        if ((powerState != null && !powerState.equals(vm.powerState))
                || (powerState == null && vm.powerState != null)) {
            return false;
        }
        return equalVmi(vm);
    }

    public boolean equalVmi(VirtualMachineInfo vm) {
        if (vm == null) {
            return false;
        }

        if (vmiInfoMap.size() != vm.vmiInfoMap.size()) {
            return false;
        }

        Iterator<Entry<String, VirtualMachineInterfaceInfo>> iter1 =
                vmiInfoMap.entrySet().iterator();
        Iterator<Entry<String, VirtualMachineInterfaceInfo>> iter2 =
                vm.vmiInfoMap.entrySet().iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            Entry<String, VirtualMachineInterfaceInfo> entry1 = iter1.next();
            Entry<String, VirtualMachineInterfaceInfo> entry2 = iter2.next();

            if (!entry1.getKey().equals(entry2.getKey())
                    || !entry1.getValue().equals(entry2.getValue())) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return "VM <" + displayName + ", host " + hostName + ", " + uuid + ">";
    }

    public StringBuffer toStringBuffer() {
        StringBuffer s = new StringBuffer(
                "VM <" + displayName + ", host " + hostName + ", " + uuid + ">\n\n");
        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
            vmiInfoMap.entrySet()) {

            VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
            s.append("\t")
             .append(vmiInfo).append("\n");
        }
        s.append("\n");
        return s;
    }

    boolean ignore() {
        // by design we skip unconnected VMs
        if (vrouterIpAddress == null || vmiInfoMap.size() == 0) {
            return true;
        }

        return false;
    }

    @Override
    void create(VncDB vncDB) throws Exception {
        if (ignore()) {
            return;
        }

        vncDB.createVirtualMachine(this);

        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
            vmiInfoMap.entrySet()) {
           VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
           vmiInfo.create(vncDB);

       }

       MainDB.created(this);
    }

    @Override
    void update(
            VCenterObject obj,
            VncDB vncDB) throws Exception {

        VirtualMachineInfo newVmInfo = (VirtualMachineInfo)obj;

        if (newVmInfo.hostName != null) {
            hostName = newVmInfo.hostName;
        }
        if (newVmInfo.name != null) {
            name = newVmInfo.name;
        }
        if (newVmInfo.displayName != null) {
            displayName = newVmInfo.displayName;
        }
        if (newVmInfo.powerState != null) {
            powerState = newVmInfo.powerState;
        }
        if (newVmInfo.toolsRunningStatus != null) {
            toolsRunningStatus = newVmInfo.toolsRunningStatus;
        }
        if (newVmInfo.vm != null) {
            vm = newVmInfo.vm;
        }
        if (newVmInfo.host != null) {
            host = newVmInfo.host;
        }
        if (newVmInfo.dvs != null) {
            dvs = newVmInfo.dvs;
        }
        if (newVmInfo.dvsName != null) {
            dvsName = newVmInfo.dvsName;
        }
        if (newVmInfo.dc != null) {
            dc = newVmInfo.dc;
        }
        if (newVmInfo.dcName != null) {
            dcName = newVmInfo.dcName;
        }
        if (newVmInfo.apiVm != null) {
            apiVm = newVmInfo.apiVm;
        }
        if (apiVm != null) {
            newVmInfo.apiVm = apiVm;
        }

        if (newVmInfo.vrouterIpAddress != null
                && newVmInfo.vrouterIpAddress.equals(vrouterIpAddress)) {
            for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
                newVmInfo.vmiInfoMap.entrySet()) {
               VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
               vmiInfo.setVmInfo(this);
            }
            MainDB.update(vmiInfoMap, newVmInfo.vmiInfoMap);
        } else {
            // VM has been migrated to new host
            // delete ports on the old vrouter and create ports on the new vrouter

            for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
                vmiInfoMap.entrySet()) {
               VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
               vmiInfo.deletePort();
            }
            vrouterIpAddress = newVmInfo.vrouterIpAddress;
            for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
                vmiInfoMap.entrySet()) {
               VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
               vmiInfo.addPort();
               if (vmiInfo.getEnablePort()) {
                   vmiInfo.setEnablePort(false);
               }
               vmiInfo.enablePort();
            }
        }
        if (vmiInfoMap.size() == 0) {
            delete(vncDB);
        }
    }

    @Override
    void sync(
            VCenterObject obj,
            VncDB vncDB) throws Exception {

        VirtualMachineInfo oldVmInfo = (VirtualMachineInfo)obj;

        if (apiVm == null && oldVmInfo.apiVm != null) {
            apiVm = oldVmInfo.apiVm;
        }

        if (vrouterIpAddress != null && oldVmInfo.vrouterIpAddress == null) {
            oldVmInfo.vrouterIpAddress = vrouterIpAddress;
        }
        // in what cases do we really update the VM
        //vncDB.updateVirtualMachine(this);

        MainDB.sync(oldVmInfo.vmiInfoMap, this.vmiInfoMap);

        if (vmiInfoMap.size() == 0) {
            delete(vncDB);
        }
    }

    @Override
    void delete(VncDB vncDB)
            throws IOException {

        VCenterNotify.unwatchVm(this);

        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
                 vmiInfoMap.entrySet()) {
            VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
            vmiInfo.delete(vncDB);

        }

        vncDB.deleteVirtualMachine(this);

        MainDB.deleted(this);
    }

    public boolean ignore(String vmName) {
        // Ignore contrailVRouterVMs since those should not be reflected in
        // Contrail VNC
        if (name.toLowerCase().contains(
                contrailVRouterVmNamePrefix.toLowerCase())) {
            return true;
        }
        return false;
    }
}
