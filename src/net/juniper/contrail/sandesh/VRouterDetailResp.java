package net.juniper.contrail.sandesh;

import java.util.Map;
import java.util.SortedMap;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;
import net.juniper.contrail.vcenter.MainDB;
import net.juniper.contrail.vcenter.VCenterDB;
import net.juniper.contrail.vcenter.VCenterMonitor;
import net.juniper.contrail.vcenter.VCenterNotify;
import net.juniper.contrail.vcenter.VRouterNotifier;
import net.juniper.contrail.vcenter.VirtualMachineInfo;
import net.juniper.contrail.vcenter.VirtualMachineInterfaceInfo;
import net.juniper.contrail.vcenter.VirtualNetworkInfo;

public class VRouterDetailResp {    
    private VRouterInfo vrouter;
    
    public VRouterDetailResp(VRouterDetailReq req) {
        vrouter = new VRouterInfo();
        
        //populate here the info
        Map<String, ContrailVRouterApi> vRouters = VRouterNotifier.getVrouterApiMap();
        
        if (!vRouters.containsKey(req.ipAddr)) {
            return;
        }
        vrouter.setIpAddr(req.ipAddr);
        ContrailVRouterApi api = vRouters.get(req.ipAddr);
        vrouter.setState(api!= null);
        
        Map<String, String> host2VrouterMap = VCenterNotify.getVcenterDB().getEsxiToVRouterIpMap();
        
        for (Map.Entry<String, String> map_entry : host2VrouterMap.entrySet()) {
            if (map_entry.getValue().equals(req.ipAddr)) {
                vrouter.setEsxiHost(map_entry.getKey());
            }
        }
        
        populateVNetworks(vrouter.getVNetworks());
    }
    
    private void populateVNetworks(SandeshObjectList<VirtualNetworkSandesh> vNetworks) {
        SortedMap<String, VirtualNetworkInfo> entries = 
                MainDB.getVNs();
        
        if (entries == null) {
            return;
        }
        
        for (Map.Entry<String, VirtualNetworkInfo> entry: entries.entrySet()) {
            VirtualNetworkInfo vmwareVN = entry.getValue(); 
            VirtualNetworkSandesh vn = new VirtualNetworkSandesh();
            populateVMIs(vn, vmwareVN);
            if (vn.getVMachines().size() > 0) {
                vn.setName(vmwareVN.getName());
                vNetworks.add(vn);
            }
        }
    }
    
    private void populateVMIs(VirtualNetworkSandesh vn, VirtualNetworkInfo vmwareVN) {
        SandeshObjectList<VirtualMachineSandesh> vMachines = vn.getVMachines();
        
        if (vMachines == null) {
            return;
        }
        SortedMap<String, VirtualMachineInterfaceInfo> map 
                    = vmwareVN.getVmiInfo();
        
        if (map == null) {
            return;
        }
        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry : map.entrySet()) {
            VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
            VirtualMachineInfo vmInfo = vmiInfo.getVmInfo();
            
            if (!vrouter.getIpAddr().trim().equals(vmInfo.getVrouterIpAddress().trim())) {
                continue;
            }

            VirtualMachineSandesh vm = new VirtualMachineSandesh();
            vm.setName(vmInfo.getName());
            vm.setIpAddr(vmiInfo.getIpAddress());
            vm.setMacAddr(vmiInfo.getMacAddress());
            vm.setEsxiHost(vmInfo.getHostName());
            vm.setPowerState(vmInfo.getPowerState().name());
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
