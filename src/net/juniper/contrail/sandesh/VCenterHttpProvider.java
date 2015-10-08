package net.juniper.contrail.sandesh;

public class VCenterHttpProvider implements HttpProvider {
    private HttpService service;
    
    public VCenterHttpProvider() {
        service = VCenterHttpServer.INSTANCE;
    }
    
    public HttpService newService() {
        return service;
    }
}
