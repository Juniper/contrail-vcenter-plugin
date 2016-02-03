package net.juniper.contrail.watchdog;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.juniper.contrail.sandesh.ContentType;
import net.juniper.contrail.sandesh.VCenterHttpServices;

@SuppressWarnings("restriction")
public class TaskWatchDogHttpHandler implements HttpHandler {

    public final String styleSheet = "/universal_parse.xsl";

    public TaskWatchDogHttpHandler() {
        VCenterHttpServices.newInstance().registerHandler("/Snh_TaskWatchDog", this);
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        OutputStream os = t.getResponseBody();

        String uri = t.getRequestURI().toString();
        ContentType contentType = ContentType.getContentType(uri);

        TaskWatchDogReq req = null;
        try {
            req = new TaskWatchDogReq(t.getRequestURI());
        } catch (InvalidParameterException e) {
        }

        if (req == null || !uri.startsWith("/") || contentType != ContentType.REQUEST) {
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

        TaskWatchDogResp resp = new TaskWatchDogResp(req);

        // serialize the actual response object in XML
        StringBuilder s = new StringBuilder()
                .append("<?xml-stylesheet type=\"")
                .append(ContentType.XSL)
                .append("\" href=\"")
                .append(styleSheet)
                .append("\"?>");

        resp.writeObject(s);

        os.write(s.toString().getBytes());
        os.close();
    }
}
