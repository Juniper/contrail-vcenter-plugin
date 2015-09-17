package net.juniper.contrail.sandesh;

import java.net.URI;

public class VRouterListReq {
    URI uri;
    
    // required params parsed from
    
    // optional params
    boolean total;
    boolean up;
    boolean down;
    
    public VRouterListReq(URI uri) {
        this.uri = uri;
 
        String req = uri.toString();
        total = req.startsWith("/Snh_vRoutersTotal") ? true : false;
        up = req.startsWith("/Snh_vRoutersUp")? true : false;
        down = req.startsWith("/Snh_vRoutersDown")? true : false;            
    }

}
