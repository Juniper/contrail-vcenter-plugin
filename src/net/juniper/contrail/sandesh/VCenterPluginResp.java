package net.juniper.contrail.sandesh;

import java.util.Map;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;
import net.juniper.contrail.vcenter.VCenterMonitor;
import net.juniper.contrail.vcenter.VCenterNotify;
import net.juniper.contrail.vcenter.VRouterNotifier;
import net.juniper.contrail.vcenter.VncDB;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;

public class VCenterPluginResp {
    private VCenterPlugin vCenterPluginInfo;

    public VCenterPluginResp(VCenterPluginReq req) {
        vCenterPluginInfo = new VCenterPlugin();

        vCenterPluginInfo.setMaster(VCenterMonitor.isZookeeperLeader());

        if (VCenterMonitor.isZookeeperLeader()) {
            populateVRouterStats();
            populateApiServerInfo();
            populateVCenterServerInfo();
            populatePluginState();
        }
    }

    private void populatePluginState() {
        vCenterPluginInfo.setPluginSessions(
                (vCenterPluginInfo.getApiServerInfo().getConnected() == true)
                && (vCenterPluginInfo.getVCenterServerInfo().getConnected() == true)
                && (( vCenterPluginInfo.getVRouterStats().getDown() == 0)));
    }

    private void populateVRouterStats() {
        int up = 0;
        int down = 0;

        Map<String, ContrailVRouterApi> apiMap = VRouterNotifier.getVrouterApiMap();

        if (apiMap == null) {
            return;
        }

        for (Map.Entry<String, ContrailVRouterApi> entry: apiMap.entrySet()) {
            Boolean active = (entry.getValue() != null);
            if (active == Boolean.TRUE) {
                up++;
            } else {
                down++;
            }
        }
        vCenterPluginInfo.getVRouterStats().setTotal(apiMap.size());
        vCenterPluginInfo.getVRouterStats().setUp(up);
        vCenterPluginInfo.getVRouterStats().setDown(down);
    }

    private void populateApiServerInfo() {
        ApiServerInfo apiServerInfo = vCenterPluginInfo.getApiServerInfo();
        VncDB vncDB = VCenterNotify.getVncDB();
        if (vncDB != null) {
            apiServerInfo.setIpAddr(vncDB.getApiServerAddress());
            apiServerInfo.setPort(vncDB.getApiServerPort());
            apiServerInfo.setConnected(vncDB.isServerAlive());
        }
    }

    private void populateVCenterServerInfo() {
        VCenterServerInfo vCenterServerInfo = vCenterPluginInfo.getVCenterServerInfo();

        if (VCenterNotify.getVcenterDB() != null) {
            vCenterServerInfo.setUrl(VCenterNotify.getVcenterDB().getVcenterUrl() );

            vCenterServerInfo.setConnected(VCenterNotify.getVCenterConnected());

            vCenterServerInfo.setOperationalStatus(VCenterNotify.getVcenterDB().getOperationalStatus());
            Datacenter dc = VCenterNotify.getVcenterDB().getDatacenter();
            if (dc != null && dc.getMOR() != null) {
                vCenterServerInfo.setDatacenterMor(dc.getMOR().getVal());
            }
            VmwareDistributedVirtualSwitch dvs = VCenterNotify.getVcenterDB().getDvs();
            if (dvs != null && dvs.getMOR() != null) {
                vCenterServerInfo.setDvsMor(dvs.getMOR().getVal());
            }
        }
    }

    public void writeObject(StringBuilder s) {
        if (s == null) {
            // log error
            return;
        }
        s.append("<vCenterPluginIntrospect type=\"sandesh\">");
        vCenterPluginInfo.writeObject(s);
        s.append("</vCenterPluginIntrospect>");
    }
}
