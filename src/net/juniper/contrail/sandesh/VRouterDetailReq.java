package net.juniper.contrail.sandesh;

import java.net.URI;
import java.security.InvalidParameterException;
import java.util.Map;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;
import net.juniper.contrail.vcenter.VCenterMonitor;
import net.juniper.contrail.vcenter.VRouterNotifier;

public class VRouterDetailReq {
    URI uri;
    // required params parsed from URI
    String ipAddr;
    
    // optional params
    
    public VRouterDetailReq(URI uri) {
        this.uri = uri;       
        String req = uri.toString();
        int idx = req.indexOf("Snh_vRouterDetail?x=");
        this.ipAddr = req.substring(idx + "Snh_vRouterDetail?x=".length());
        
        Map<String, ContrailVRouterApi> vRouters = VRouterNotifier.getVrouterApiMap();
        
        if (!vRouters.containsKey(this.ipAddr)) {
            throw new InvalidParameterException(); 
        }
    }
}
