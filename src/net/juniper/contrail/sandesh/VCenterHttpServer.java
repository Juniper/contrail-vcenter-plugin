package net.juniper.contrail.sandesh;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.apache.log4j.Logger;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public enum VCenterHttpServer implements HttpService {
    INSTANCE;
    
    private HttpServer server;
    private final String webRoot = "/usr/share/contrail-vcenter-plugin/webs";
    private final Logger s_logger =
            Logger.getLogger(VCenterHttpServer.class);
    
    public String getWebRoot() {
        return webRoot;
    }
    
    private VCenterHttpServer() {
        // register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (server != null) {
                    s_logger.info("Stopping HTTP server ...");
                    server.stop(0);
                }
            }
        }, "shutdownHook"));

        try {
            server = HttpServer.create(new InetSocketAddress(8777), 0);
            server.setExecutor(null); // creates a default executor
            server.start();
            
        } catch (IOException e) {
            s_logger.error(" Cannot start HTTP server, introspection will not" +
                           " be available");
        }

        s_logger.info("HTTP server for introspection started");
    }

    @Override
    public void registerHandler(String path, HttpHandler h) {
        if (server != null) {
            server.createContext(path, h);
        }
    }
}
