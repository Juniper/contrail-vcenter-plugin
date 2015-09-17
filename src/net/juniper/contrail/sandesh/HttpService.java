package net.juniper.contrail.sandesh;

import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public interface HttpService {
    public void registerHandler(String path, HttpHandler h);
}
