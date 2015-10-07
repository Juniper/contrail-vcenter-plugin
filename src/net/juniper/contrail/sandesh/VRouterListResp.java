package net.juniper.contrail.sandesh;

import java.util.Map;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;
import net.juniper.contrail.vcenter.VCenterMonitor;

public class VRouterListResp {    
    private SandeshObjectList<VRouterInfo> vrouterInfoList;
    
    public VRouterListResp(VRouterListReq req) {
        vrouterInfoList = 
                new SandeshObjectList<VRouterInfo>(VRouterInfo.class, 
                                                    new ComparatorVRouterInfo());
                
        Map<String, String> host2VrouterMap = VCenterMonitor.getVcenterDB().esxiToVRouterIpMap;
        Map<String, ContrailVRouterApi> vRouters = VCenterMonitor.getVncDB().getVRouterApiMap();

        for (Map.Entry<String, ContrailVRouterApi> entry: vRouters.entrySet()) {
            boolean state_up = (entry.getValue()!= null);
            if (req.total || (req.up && state_up) || (req.down && !state_up)) {
                VRouterInfo vrInfo = new VRouterInfo();
                String vRouterIPAddr = entry.getKey();
                vrInfo.setIpAddr(vRouterIPAddr);
                vrInfo.setState(entry.getValue()!= null);
                for (Map.Entry<String, String> map_entry : host2VrouterMap.entrySet()) {
                    if (map_entry.getValue().equals(vRouterIPAddr)) {
                        vrInfo.setEsxiHost(map_entry.getKey());
                    }
                }
                vrouterInfoList.add(vrInfo);
            }
        }
    }
    
    public void writeObject(StringBuilder s) {
        s.append("<vRouterListResp type=\"sandesh\">");
        vrouterInfoList.writeObject(s, "VirtualRouters", DetailLevel.REGULAR, 1);
        s.append("</vRouterListResp>");
    }
}
