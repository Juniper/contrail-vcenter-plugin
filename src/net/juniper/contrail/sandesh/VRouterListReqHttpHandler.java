package net.juniper.contrail.sandesh;

import java.io.IOException;
import java.io.OutputStream;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class VRouterListReqHttpHandler implements HttpHandler {

    private final String styleSheet = "/universal_parse.xsl";
    
    public VRouterListReqHttpHandler() {
        VCenterHttpServices.newInstance().registerHandler("/Snh_vRoutersTotal", this);
        VCenterHttpServices.newInstance().registerHandler("/Snh_vRoutersUp", this);
        VCenterHttpServices.newInstance().registerHandler("/Snh_vRoutersDown", this);
    }
    
    @Override
    public void handle(HttpExchange t) throws IOException {    
        OutputStream os = t.getResponseBody();
        
        String uri = t.getRequestURI().toString();
        ContentType contentType = ContentType.getContentType(uri);
        
        if (!uri.startsWith("/") || contentType != ContentType.REQUEST) {
            // suspecting path traversal attack
                  
            Headers h = t.getResponseHeaders();
            h.set("Content-Type", ContentType.HTML.toString());
            String response = "403 (Forbidden)\n";
            t.sendResponseHeaders(403, response.getBytes().length);
            os.close();
            return;
        }
        
        // Presentation layer
        // Accept with response code 200.
        t.sendResponseHeaders(200, 0);
        Headers h = t.getResponseHeaders();
        h.set("Content-Type", contentType.toString());
        
        StringBuilder s = new StringBuilder()
                .append("<?xml-stylesheet type=\"")
                .append(ContentType.XSL)
                .append("\" href=\"")
                .append(styleSheet)
                .append("\"?>");
         
        
        // serialize the actual object in XML
        VRouterListReq req = new VRouterListReq(t.getRequestURI());
        VRouterListResp resp = new VRouterListResp(req);
        resp.writeObject(s);
       
        os.write(s.toString().getBytes());
        os.close();
    }
}
