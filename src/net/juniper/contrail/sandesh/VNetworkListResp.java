package net.juniper.contrail.sandesh;

import java.util.Map;

import net.juniper.contrail.vcenter.MainDB;
import net.juniper.contrail.vcenter.VirtualNetworkInfo;

public class VNetworkListResp {
    private SandeshObjectList<VirtualNetworkSandesh> vNetworkList;

    public VNetworkListResp(VNetworkListReq req) {
        vNetworkList =
                new SandeshObjectList<VirtualNetworkSandesh>(VirtualNetworkSandesh.class,
                                                    new ComparatorVirtualNetworkSandesh());

        for (Map.Entry<String, VirtualNetworkInfo> entry: MainDB.getVNs().entrySet()) {
            VirtualNetworkInfo vnInfo = entry.getValue();
            VirtualNetworkSandesh vn = new VirtualNetworkSandesh();
            vn.setName(vnInfo.getName());
            vn.setPrimaryVlanId(vnInfo.getPrimaryVlanId());
            vn.setIsolatedVlanId(vnInfo.getIsolatedVlanId());
            vn.setSubnetAddress(vnInfo.getSubnetAddress());
            vn.setSubnetMask(vnInfo.getSubnetMask());
            vn.setGatewayAddress(vnInfo.getGatewayAddress());
            vn.setExternalIpam(vnInfo.getExternalIpam());

            vNetworkList.add(vn);
        }
    }

    public void writeObject(StringBuilder s) {
        s.append("<vNetworkListResp type=\"sandesh\">");
        vNetworkList.writeObject(s, "VirtualNetworks", DetailLevel.REGULAR, 1);
        s.append("</vNetworkListResp>");
    }
}
