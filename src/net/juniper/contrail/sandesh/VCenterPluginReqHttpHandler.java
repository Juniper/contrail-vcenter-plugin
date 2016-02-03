package net.juniper.contrail.sandesh;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class VCenterPluginReqHttpHandler implements HttpHandler {
    
    public final String styleSheet = "/universal_parse.xsl";
    
    public VCenterPluginReqHttpHandler() {
        VCenterHttpServices.newInstance().registerHandler("/", this);

    }
    
    @Override
    public void handle(HttpExchange t) throws IOException {
        OutputStream os = t.getResponseBody();
        URI uri = t.getRequestURI();
        
        if (!uri.toString().startsWith("/")) {
            /* suspecting path traversal attack */
            String response = "403 (Forbidden)\n";
            t.sendResponseHeaders(403, response.getBytes().length);
            os.write(response.getBytes());
            os.close();
            return;
        }
        
        ContentType contentType;
        if (uri.toString().equals("/")) {
            contentType = ContentType.HTML;
        } else {
            contentType = ContentType.getContentType(uri.toString());
        }
        if (contentType != ContentType.REQUEST) {
            handleFile(t, contentType);
            return;
        }
        
        //Presentation layer on of VCenterPluginResp
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
        
        // serialize the actual response object in XML
        VCenterPluginReq req = new VCenterPluginReq(uri);
        VCenterPluginResp resp = new VCenterPluginResp(req);
        resp.writeObject(s);
        
        os.write(s.toString().getBytes());
        os.close();
    }
    
    private void handleFile(HttpExchange t, ContentType contentType) 
            throws IOException, FileNotFoundException {
        
        OutputStream os = t.getResponseBody();
        
        String fileName = t.getRequestURI().toString();
        
        if (fileName.equals("/")) {
            fileName = "/vcenter-plugin.html";
        }
        File file = new File(VCenterHttpServer.getWebRoot() + fileName)
                            .getCanonicalFile();
        
        if (!file.getPath().startsWith(VCenterHttpServer.getWebRoot())) {
            // Suspected path traversal attack: reject with 403 error.
            String response = "403 (Forbidden)\n";
            t.sendResponseHeaders(403, response.getBytes().length); 
            os.write(response.getBytes());
            os.close();
            return;
        }
        if (!file.isFile()) {
            // Object does not exist or is not a file: reject with 404 error.
            //s_logger.error(" Cannot load " + fileName);
            String response = "404 (Not Found)\n";
            t.sendResponseHeaders(404, response.length());
            os.write(response.getBytes());
            os.close();
            return;
        }
       
        // Object exists and is a file: accept with response code 200.
        Headers h = t.getResponseHeaders();
        h.set("Content-Type", contentType.toString());
        t.sendResponseHeaders(200, 0);
        
        FileInputStream fs = new FileInputStream(file);
        final byte[] buffer = new byte[0x100000];
        int count = 0;
        while ((count = fs.read(buffer)) >= 0) {
            os.write(buffer,0,count);
        }
        fs.close();
        
        os.close();
    }
}
