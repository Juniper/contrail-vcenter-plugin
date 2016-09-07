package net.juniper.contrail.sandesh;

import java.net.URI;

public class VNetworkListReq {
    URI uri;

    // required params parsed from

    // optional params
    boolean total;

    public VNetworkListReq(URI uri) {
        this.uri = uri;

        String req = uri.toString();
        total = req.startsWith("/Snh_vNetworksTotal") ? true : false;
    }
}
