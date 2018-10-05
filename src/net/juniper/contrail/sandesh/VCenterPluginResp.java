package net.juniper.contrail.sandesh;

import java.util.Map;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;
import net.juniper.contrail.vcenter.MainDB;
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
            populateApiServerInfo();
            populateVCenterServerInfo();
            populatePluginState();
            populateVNetworkStats();
        }
    }

    private void populatePluginState() {
        vCenterPluginInfo.setPluginSessions(
                (vCenterPluginInfo.getApiServerInfo().getConnected() == true)
                && (vCenterPluginInfo.getVCenterServerInfo().getConnected() == true));
                
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

    private void populateVNetworkStats() {
        vCenterPluginInfo.getVNetworkStats().setTotal(MainDB.getVNs().size());
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
