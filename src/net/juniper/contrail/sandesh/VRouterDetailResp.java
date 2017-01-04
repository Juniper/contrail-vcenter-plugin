package net.juniper.contrail.sandesh;

import java.util.Map;
import java.util.SortedMap;

import com.vmware.vim25.VirtualMachineToolsRunningStatus;

import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;
import net.juniper.contrail.vcenter.MainDB;
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
        vrouter.setState(api.getActive());

        Map<String, String> host2VrouterMap = VCenterNotify.getVcenterDB().getEsxiToVRouterIpMap();

        for (Map.Entry<String, String> map_entry : host2VrouterMap.entrySet()) {
            if (map_entry.getValue().equals(req.ipAddr)) {
                vrouter.setEsxiHost(map_entry.getKey());
            }
        }

        SortedMap<String, VirtualNetworkInfo> vnInfoMap = MainDB.getVNs();
        populateVNetworks(vrouter.getVNetworks(), vnInfoMap);
    }

    private void populateVNetworks(SandeshObjectList<VirtualNetworkSandesh> vNetworks,
            SortedMap<String, VirtualNetworkInfo> vnInfoMap) {

        if (vnInfoMap == null) {
            return;
        }

        for (Map.Entry<String, VirtualNetworkInfo> entry: vnInfoMap.entrySet()) {
            VirtualNetworkInfo vnInfo = entry.getValue();
            VirtualNetworkSandesh vn = new VirtualNetworkSandesh();
            populateVMIs(vn, vnInfo);
            if (vn.getVInterfaces().size() > 0) {
                vn.setName(vnInfo.getName());
                vNetworks.add(vn);
            }
        }
    }

    private void populateVMIs(VirtualNetworkSandesh vn, VirtualNetworkInfo vnInfo) {
        SandeshObjectList<VirtualMachineInterfaceSandesh> vInterfaces = vn.getVInterfaces();

        if (vInterfaces == null) {
            return;
        }
        SortedMap<String, VirtualMachineInterfaceInfo> map
                    = vnInfo.getVmiInfo();

        if (map == null) {
            return;
        }
        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry : map.entrySet()) {
            VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
            VirtualMachineInfo vmInfo = vmiInfo.getVmInfo();

            if (!vrouter.getIpAddr().trim().equals(vmInfo.getVrouterIpAddress().trim())) {
                continue;
            }
            VirtualNetworkInfo vnInfo1 = vmiInfo.getVnInfo();

            VirtualMachineInterfaceSandesh vmi = new VirtualMachineInterfaceSandesh();
            vmi.setMacAddress(vmiInfo.getMacAddress());
            vmi.setNetwork(vnInfo1.getName());
            vmi.setVirtualMachine(vmInfo.getDisplayName());
            String ipAddress = vmiInfo.getIpAddress();
            if (ipAddress == null && vnInfo.getExternalIpam()
                 && vmInfo.getToolsRunningStatus().equals(
                         VirtualMachineToolsRunningStatus.guestToolsNotRunning.toString())) {
                vmi.setIpAddress("unknown");
            } else {
                vmi.setIpAddress(ipAddress);
            }

            vmi.setPoweredOn(vmInfo.isPoweredOnState());
            vmi.setPortAdded(vmiInfo.getPortAdded());

            vInterfaces.add(vmi);
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
