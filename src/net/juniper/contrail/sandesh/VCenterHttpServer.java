package net.juniper.contrail.sandesh;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import org.apache.log4j.Logger;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public class VCenterHttpServer implements HttpService {
    private HttpServer server;
    private int port = 8234;
    private final static String webRoot = "/usr/share/contrail-vcenter-plugin/webs";
    private final Logger s_logger =
            Logger.getLogger(VCenterHttpServer.class);
    
    public static String getWebRoot() {
        return webRoot;
    }
    
    public VCenterHttpServer(Properties configProps) {

        String portStr = configProps.getProperty("introspect.port");
        int portConfig = port;
        if (portStr != null && portStr.length() > 0) {
            try {
                portConfig = Integer.parseInt(portStr);

                if (portConfig < 0 || portConfig > 0xFFFF) {
                    s_logger.error(" Bad HTTP server port " + portStr +
                            " specified in the config file. Reverting to default " + port);
                } else {
                    port = portConfig;
                }
            } catch (NumberFormatException e) {
                s_logger.error(" Bad HTTP server port " + portStr +
                        " specified in the config file. Reverting to default " + port);
            }
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(null); // creates a default executor
            server.start();
        } catch (IOException e) {
            s_logger.error(" Cannot start HTTP server on port " + port);
            e.printStackTrace();
        }

        if (server == null) {
            s_logger.error(" Cannot start HTTP server on port " + port);
        } else {
            s_logger.info("HTTP server on port " + port + " started.");
        }
    }
    
    public void registerHandler(String path, HttpHandler h) {
        if (server != null) {
            server.createContext(path, h);
        }
    }
}
