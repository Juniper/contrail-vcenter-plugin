package net.juniper.contrail.vcenter;

import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.VirtualMachineToolsRunningStatus;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.Network;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import com.vmware.vim25.Event;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.NetIpConfigInfo;
import com.vmware.vim25.NetIpConfigInfoIpAddress;
import com.vmware.vim25.RuntimeFault;
import net.juniper.contrail.api.ApiPropertyBase;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.VirtualMachine;

public class VirtualMachineInfo extends VCenterObject {
    private String uuid; // required attribute, key for this object
    ManagedObjectReference hmor;
    private String hostName;
    private String vrouterIpAddress;
    private String macAddress;
    private String ipAddress;
    private String name;
    private String interfaceUuid;
    private VirtualMachinePowerState powerState;
    private String toolsRunningStatus;
    private SortedMap<String, VirtualMachineInterfaceInfo> vmiInfoMap; // key is MAC address
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
        this.uuid             = uuid;

        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
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
             }
        }
        
        vrouterIpAddress = vcenterDB.getVRouterVMIpFabricAddress(
                hostName, host, contrailVRouterVmNamePrefix);
        
        uuid = vm.getConfig().getInstanceUuid();

        VirtualMachineRuntimeInfo vmRuntimeInfo = vm.getRuntime();
        powerState = vmRuntimeInfo.getPowerState();
        toolsRunningStatus = vm.getGuest().getToolsRunningStatus();

        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
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

    public VirtualMachineInfo(net.juniper.contrail.api.types.VirtualMachine vm) {
        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
        
        if (vm == null) {
            return;
        }
        
        apiVm = vm;
        uuid = vm.getUuid();
    }

    public VirtualMachineInfo(VCenterDB vcenterDB,
            com.vmware.vim25.mo.Datacenter dc, String dcName,
            com.vmware.vim25.mo.VirtualMachine vm, Hashtable pTable, 
            com.vmware.vim25.mo.HostSystem host,
            String vrouterIpAddress) 
                    throws Exception {

        if (vcenterDB == null || dc == null || dcName == null
                || vm == null || pTable == null) {
            throw new IllegalArgumentException();
        }
        
        this.dc = dc;
        this.dcName = dcName;
        this.vm = vm;
        
        // Name
        uuid  = (String)  pTable.get("config.instanceUuid");
        name = (String) pTable.get("name");

        if (host == null) {
            ManagedObjectReference hostHmor = (ManagedObjectReference) pTable.get("runtime.host");
            host = new HostSystem(vm.getServerConnection(), hostHmor);
        }
        hostName = host.getName();

        powerState = (VirtualMachinePowerState)pTable.get("runtime.powerState");
        toolsRunningStatus  = (String)  pTable.get("guest.toolsRunningStatus");

        if (vrouterIpAddress == null) {
            vrouterIpAddress = vcenterDB.getVRouterVMIpFabricAddress(
                    hostName, host, contrailVRouterVmNamePrefix);
        }

        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
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
        return "VM <" + name + ", host " + hostName + ", " + uuid + ">";
    }
    
    public StringBuffer toStringBuffer() {
        StringBuffer s = new StringBuffer(
                "VM <" + name + ", host " + hostName + ", " + uuid + ">\n\n");
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
        if (newVmInfo.ignore()) {
            return;
        } 
        
        if (newVmInfo.hostName != null) {
            hostName = newVmInfo.hostName;
        }
        if (newVmInfo.vrouterIpAddress != null) {
            vrouterIpAddress = newVmInfo.vrouterIpAddress;
        }
        if (newVmInfo.macAddress != null) {
            macAddress = newVmInfo.macAddress;
        }
        if (newVmInfo.ipAddress != null) {
            ipAddress = newVmInfo.ipAddress;
        }
        if (newVmInfo.name != null) {
            name = newVmInfo.name;
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
        
        // in what cases do we really update
        //vncDB.updateVirtualMachine(this);
        
        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry: 
            newVmInfo.vmiInfoMap.entrySet()) {
           VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
           vmiInfo.setVmInfo(this);
        }
        
        MainDB.update(vmiInfoMap, newVmInfo.vmiInfoMap);
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
    }

    @Override
    void delete(VncDB vncDB)
            throws IOException {
        
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
