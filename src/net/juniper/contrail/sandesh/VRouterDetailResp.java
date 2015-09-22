package net.juniper.contrail.sandesh;

import java.util.Map;
import java.util.SortedMap;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;
import net.juniper.contrail.vcenter.VCenterMonitor;
import net.juniper.contrail.vcenter.VmwareVirtualMachineInfo;
import net.juniper.contrail.vcenter.VmwareVirtualNetworkInfo;

public class VRouterDetailResp {    
    private VRouterInfo vrouter;
    
    public VRouterDetailResp(VRouterDetailReq req) {
        vrouter = new VRouterInfo();
        
        //populate here the info
        Map<String, ContrailVRouterApi> vRouters = VCenterMonitor.getVncDB().getVRouterApiMap();
        
        if (!vRouters.containsKey(req.ipAddr)) {
            return;
        }
        vrouter.setIpAddr(req.ipAddr);
        ContrailVRouterApi api = vRouters.get(req.ipAddr);
        vrouter.setState(api!= null);
        
        Map<String, String> host2VrouterMap = VCenterMonitor.getVcenterDB().esxiToVRouterIpMap;
        
        for (Map.Entry<String, String> map_entry : host2VrouterMap.entrySet()) {
            if (map_entry.getValue().equals(req.ipAddr)) {
                vrouter.setEsxiHost(map_entry.getKey());
            }
        }
        
        populateVNetworks(vrouter.getVNetworks());
    }
    
    private void populateVNetworks(SandeshObjectList<VirtualNetworkInfo> vNetworks) {
        SortedMap<String, VmwareVirtualNetworkInfo> entries = 
                VCenterMonitor.getVcenterDB().getPrevVmwareVNInfos();
        
        if (entries == null) {
            return;
        }
        
        for (Map.Entry<String, VmwareVirtualNetworkInfo> entry: entries.entrySet()) {
            VmwareVirtualNetworkInfo vmwareVN = entry.getValue(); 
            VirtualNetworkInfo vn = new VirtualNetworkInfo();
            populateVMs(vn, vmwareVN);
            if (vn.getVMachines().size() > 0) {
                vn.setName(vmwareVN.getName());
                vNetworks.add(vn);
            }
        }
    }
    
    private void populateVMs(VirtualNetworkInfo vn, VmwareVirtualNetworkInfo vmwareVN) {
        SandeshObjectList<VirtualMachineInfo> vMachines = vn.getVMachines();
        
        if (vMachines == null) {
            return;
        }
        SortedMap<String, VmwareVirtualMachineInfo> map = vmwareVN.getVmInfo();
        
        if (map == null) {
            return;
        }
        for (Map.Entry<String, VmwareVirtualMachineInfo> entry : map.entrySet()) {
            VmwareVirtualMachineInfo vmwareVM = entry.getValue();
            
            if (!vrouter.getEsxiHost().trim().equals(vmwareVM.getHostName().trim())) {
                continue;
            }

            VirtualMachineInfo vm = new VirtualMachineInfo();
            vm.setName(vmwareVM.getName());
            vm.setIpAddr(vmwareVM.getIpAddress());
            vm.setMacAddr(vmwareVM.getMacAddress());
            vm.setEsxiHost(vmwareVM.getHostName());
            vm.setPowerState(vmwareVM.getPowerState().name());
            vm.setNetwork(vn.getName());
            
            vMachines.add(vm);
        }
    }
    
    public void writeObject(StringBuilder s) {
        if (s == null) {
            // log error
            return;
        }
        s.append("<vRouterDetailResp type=\"sandesh\">");
        vrouter.writeObject(s, DetailLevel.FULL, 1);
        s.append("</vRouterDetailResp>");          
    }
}
