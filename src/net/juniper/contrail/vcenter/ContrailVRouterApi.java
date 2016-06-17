package net.juniper.contrail.vcenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import org.apache.log4j.Logger;
import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorFactory;
import net.juniper.contrail.api.Port;

public class ContrailVRouterApi {
    private static final Logger s_logger =
            Logger.getLogger(ContrailVRouterApi.class);

    private final String serverAddress;
    private final int serverPort;
    private final String username;
    private final String password;
    private final String tenant;
    private final String authtype;
    private final String authurl;

    protected ApiConnector apiConnector;
    private Map<String, Port> ports;
    private boolean alive;

    public ContrailVRouterApi(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = null;
        this.password = null;
        this.tenant = null;
        this.authtype = null;
        this.authurl = null;
        this.ports = new HashMap<String, Port>();
    }

    public ContrailVRouterApi(String serverAddress, int serverPort,
            String username, String password, String tenant,
            String authtype, String authurl) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.password = password;
        this.tenant   = tenant;
        this.authtype = authtype;
        this.authurl  = authurl;
        this.ports = new HashMap<String, Port>();
    }

    public boolean isServerAlive() {
        if (apiConnector == null) {
            apiConnector = ApiConnectorFactory.build(serverAddress,
                                                     serverPort);
            if (authurl != null || username != null) {
                apiConnector.credentials(username, password)
                            .tenantName(tenant)
                            .authServer(authtype, authurl);
            }
            if (apiConnector == null) {
                s_logger.error(" failed to create ApiConnector.. retry later");
                alive = false;
                return false;
            }
            s_logger.info("Server " + this + " alive. Got the pulse..");
        }

        alive = true;
        return true;
    }

    public boolean getAlive() {
        return alive;
    }

    protected void setApiConnector(ApiConnector apiConnector) {
        this.apiConnector = apiConnector;
    }

    @Override
    public String toString() {
        return "VRouterApi " + serverAddress + ":" + serverPort;
    }

    /**
     * Get current list of ports
     * @return Port Map
     */
    public Map<String, Port> getPorts() {
        return ports;
    }

    /**
     * Add a port to the agent. The information is stored in the ports
     * map since the vrouter agent may not be running at the
     * moment or the RPC may fail.
     *
     * @param vif_uuid         String of the VIF/Port
     * @param vm_uuid          String of the instance
     * @param interface_name   Name of the VIF/Port
     * @param interface_ip     IP address associated with the VIF
     * @param mac_address      MAC address of the VIF
     * @param network_uuid     String of the associated virtual network
     */
    public boolean addPort(String vif_uuid, String vm_uuid, String interface_name,
            String interface_ip, String mac_address, String network_uuid, short vlanId,
            short primaryVlanId, String vm_name) {
        addPort(vif_uuid, vm_uuid, interface_name, interface_ip,
                mac_address, network_uuid, vlanId, primaryVlanId, vm_name, null);
        return true;
    }

    /**
     * Add a port to the agent. The information is stored in the ports
     * map since the vrouter agent may not be running at the
     * moment or the RPC may fail.
     *
     * @param vif_uuid         String of the VIF/Port
     * @param vm_uuid          String of the instance
     * @param interface_name   Name of the VIF/Port
     * @param interface_ip     IP address associated with the VIF
     * @param mac_address      MAC address of the VIF
     * @param network_uuid     String of the associated virtual network
     * @param project_uuid     String of the associated project
     */
    public boolean addPort(String vif_uuid, String vm_uuid, String interface_name,
            String interface_ip, String mac_address, String network_uuid, short primaryVlanId,
            short isolatedVlanId, String vm_name,
            String project_uuid) {
        Port aport = new Port();
        aport.setUuid(vif_uuid);
        aport.setName(vif_uuid);
        aport.setId(vif_uuid);
        aport.setInstance_id(vm_uuid);
        aport.setSystem_name(interface_name);
        aport.setIp_address(interface_ip);
        aport.setMac_address(mac_address);
        aport.setVn_id(network_uuid);
        aport.setRx_vlan_id(primaryVlanId);
        aport.setTx_vlan_id(isolatedVlanId);
        aport.setDisplay_name(vm_name);
        aport.setVm_project_id(project_uuid);

        ports.put(vif_uuid, aport);
        if (apiConnector == null) {
            if (!isServerAlive()) {
                s_logger.error(this +
                        " AddPort: " + vif_uuid + "(" + interface_name +
                        ") FAILED");
                return false;
            }
        } else {
            List<Port> aports = new ArrayList<Port>();
            aports.add(aport);
            try {
                apiConnector.create(aport);
            } catch (IOException e) {
                s_logger.error(this +
                        " AddPort: " + vif_uuid + "(" +
                        interface_name + ") Exception: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Delete a port from the agent. The port is first removed from the
     * internal ports map
     *
     * @param vif_uuid  String of the VIF/Port
     */
    public boolean deletePort(String vif_uuid) {
        ports.remove(vif_uuid);
        if (apiConnector == null) {
            if (!isServerAlive()) {
                s_logger.error(this +
                        " deletePort: " + vif_uuid + " FAILED");
                return false;
            }
        } else {
            try {
                apiConnector.delete(Port.class, vif_uuid.toString());

            } catch (Exception e) {
                s_logger.error(this +
                        " deletePort: " + vif_uuid +
                        " Exception: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    public boolean syncPorts() {
        if (apiConnector == null) {
            if (!isServerAlive()) {
                s_logger.error(this +
                        " syncPorts FAILED, vrouter is not alive");
                return false;
            }
        }
        try {
            boolean res = apiConnector.sync("/sync");

            if (!res) {
                s_logger.error(this + " syncPorts request failed");
                return false;
            }
        } catch (Exception e) {
            s_logger.error(this +
                    " syncPorts exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
