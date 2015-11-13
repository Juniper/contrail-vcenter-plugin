package net.juniper.contrail.sandesh;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import net.juniper.contrail.watchdog.TaskWatchDogHttpHandler;

public class VCenterHttpServices {
    
    private VCenterHttpServices() { }
    
    // Maps service names to services
    private static final Map<String, HttpProvider> providers =
        new ConcurrentHashMap<String, HttpProvider>();
    
    public static final String DEFAULT_PROVIDER_NAME = "VCenterHttpProvider";

    // Provider registration API
    public static void registerDefaultProvider(HttpProvider p) {
        registerProvider(DEFAULT_PROVIDER_NAME, p);
    }
    
    public static void registerProvider(String name, HttpProvider p){
        providers.put(name, p);
    }
    
    // Service access API
    public static HttpService newInstance() {
        return newInstance(DEFAULT_PROVIDER_NAME);
    }
    public static HttpService newInstance(String name) {
        HttpProvider p = providers.get(name);
        if (p == null)
            throw new IllegalArgumentException(
                "No provider registered with name: " + name);
        return p.newService();
    }
    
    public static void registerHttpHandlers() {
        new VCenterPluginReqHttpHandler();
        new VRouterListReqHttpHandler();
        new VRouterDetailReqHttpHandler();
        new TaskWatchDogHttpHandler();
    }
    
    public static void init(Properties configProps) {
        registerDefaultProvider(new VCenterHttpProvider(configProps));
        registerHttpHandlers();
    }
}
