package net.juniper.contrail.sandesh;

import java.util.Properties;

public class VCenterHttpProvider implements HttpProvider {
    private HttpService service;
    
    public VCenterHttpProvider(Properties configProps) {
        service = new VCenterHttpServer(configProps);
    }
    
    public HttpService newService() {
        return service;
    }
}
