/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 */

package net.juniper.contrail.vcenter;

import com.google.common.base.Throwables;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

public class VRouterNotifier {
    static volatile Map<String, ContrailVRouterApi> vrouterApiMap =
            new ConcurrentHashMap<String, ContrailVRouterApi>();
    static final int vrouterApiPort = 9091;

    private final static Logger s_logger =
            Logger.getLogger(VRouterNotifier.class);

    public static Map<String, ContrailVRouterApi> getVrouterApiMap() {
        return vrouterApiMap;
    }

    public static void created(VirtualMachineInterfaceInfo vmiInfo) {
        if (vmiInfo == null) {
            s_logger.error("Null vmiInfo argument, cannot perform addPort");
            return;
        }

        if (vmiInfo.vmInfo == null)  {
            s_logger.error("Null vmInfo, cannot perform addPort for " + vmiInfo);
            return;
        }
        if (vmiInfo.vnInfo == null)  {
            s_logger.error("Null vnInfo, cannot perform addPort for " + vmiInfo);
            return;
        }

        if (vmiInfo.getUuid() == null)  {
            s_logger.error("Null uuid, cannot perform addPort for " + vmiInfo);
            return;
        }

        String vrouterIpAddress = vmiInfo.getVmInfo().getVrouterIpAddress();
        String ipAddress = vmiInfo.getIpAddress();
        VirtualMachineInfo vmInfo = vmiInfo.vmInfo;
        VirtualNetworkInfo vnInfo = vmiInfo.vnInfo;

        if (vrouterIpAddress == null) {
            s_logger.error(
                "addPort notification NOT sent as vRouterIp Address not known for " + vmiInfo);
            return;
        }
        if (ipAddress == null) {
            ipAddress = "0.0.0.0";
        }
        try {
            ContrailVRouterApi vrouterApi = getVrouterApi(vrouterIpAddress);

            boolean ret = vrouterApi.addPort(vmiInfo.getUuid(),
                    vmInfo.getUuid(), vmiInfo.getUuid(),
                    ipAddress,
                    vmiInfo.getMacAddress(),
                    vnInfo.getUuid(),
                    vnInfo.getPrimaryVlanId(),
                    vnInfo.getIsolatedVlanId(),
                    vmInfo.getDisplayName(),
                    vnInfo.getProjectUuid());
            if (ret) {
                s_logger.info("vRouter " + vrouterIpAddress + " addPort success for " + vmiInfo);
            } else {
                // log failure but don't worry. Periodic KeepAlive task will
                // attempt to connect to vRouter Agent and replay AddPorts.
                s_logger.warn("vRouter " + vrouterIpAddress + " addPort failed for " + vmiInfo);
            }
        } catch(Throwable e) {
            s_logger.warn("vRouter " + vrouterIpAddress + " Exception in addPort for "
                    + vmiInfo + ": " + e.getMessage());
            s_logger.error(Throwables.getStackTraceAsString(e));
        }
    }

    public static void update(VirtualMachineInterfaceInfo vmiInfo) {
        if (vmiInfo == null) {
            s_logger.error("Null vmiInfo argument, cannot perform enablePort");
            return;
        }

        if (vmiInfo.vmInfo == null)  {
            s_logger.error("Null vmInfo, cannot perform enablePort for " + vmiInfo);
            return;
        }
        if (vmiInfo.vnInfo == null)  {
            s_logger.error("Null vnInfo, cannot perform enablePort for " + vmiInfo);
            return;
        }

        if (vmiInfo.getUuid() == null)  {
            s_logger.error("Null uuid, cannot perform enablePort for " + vmiInfo);
            return;
        }

        String vrouterIpAddress = vmiInfo.getVmInfo().getVrouterIpAddress();
        String ipAddress = vmiInfo.getIpAddress();
        VirtualMachineInfo vmInfo = vmiInfo.vmInfo;
        VirtualNetworkInfo vnInfo = vmiInfo.vnInfo;

        if (vrouterIpAddress == null) {
            s_logger.error(
                "enablePort notification NOT sent as vRouterIp Address not known for " + vmiInfo);
            return;
        }
        if (ipAddress == null) {
            ipAddress = "0.0.0.0";
        }
        ContrailVRouterApi vrouterApi = getVrouterApi(vrouterIpAddress);
        if (!vmInfo.isPoweredOnState()) {
            try {
                boolean ret = vrouterApi.disablePort(vmiInfo.getUuid(), vmiInfo.getUuid());
                if (ret) {
                    s_logger.info("vRouter " + vrouterIpAddress + " disablePort success for " + vmiInfo);
                } else {
                    s_logger.warn("vRouter " + vrouterIpAddress + " disablePort failed for " + vmiInfo);
                }
            } catch(Throwable e) {
                s_logger.warn("vRouter " + vrouterIpAddress + " Exception in disablePort for "
                        + vmiInfo + ": " + e.getMessage());
                s_logger.error(Throwables.getStackTraceAsString(e));
            }
        } else {
            try {
                boolean ret = vrouterApi.enablePort(vmiInfo.getUuid(), vmiInfo.getUuid());
                if (ret) {
                    s_logger.info("vRouter " + vrouterIpAddress + " enablePort success for " + vmiInfo);
                } else {
                    s_logger.warn("vRouter " + vrouterIpAddress + " enablePort failed for " + vmiInfo);
                }
            } catch(Throwable e) {
                s_logger.warn("vRouter " + vrouterIpAddress + " Exception in enablePort for "
                        + vmiInfo + ": " + e.getMessage());
                s_logger.error(Throwables.getStackTraceAsString(e));
            }
        }
    }

    public static void deleted(VirtualMachineInterfaceInfo vmiInfo) {
        if (vmiInfo == null) {
            s_logger.error("Null vmiInfo argument, cannot perform deletePort");
            return;
        }

        if (vmiInfo.vmInfo == null)  {
            s_logger.error("Null vmInfo, cannot perform deletePort for " + vmiInfo);
            return;
        }
        if (vmiInfo.vnInfo == null)  {
            s_logger.error("Null vnInfo, cannot perform deletePort for " + vmiInfo);
            return;
        }
        if (vmiInfo.getUuid() == null)  {
            s_logger.error("Null uuid, cannot perform deletePort for " + vmiInfo);
            return;
        }
        String vrouterIpAddress = vmiInfo.getVmInfo().getVrouterIpAddress();
        String ipAddress = vmiInfo.getIpAddress();

        if (vrouterIpAddress == null) {
            s_logger.error(
                "deletePort notification NOT sent as vRouterIp Address not known for " + vmiInfo);
            return;
        }
        if (ipAddress == null) {
            ipAddress = "0.0.0.0";
        }

        ContrailVRouterApi vrouterApi = getVrouterApi(vrouterIpAddress);
        boolean ret = vrouterApi.deletePort(vmiInfo.getUuid());

        if (ret) {
            s_logger.info("vRouter " + vrouterIpAddress +
                    " DeletePort success for " + vmiInfo);
        } else {
            // log failure but don't worry. Periodic KeepAlive task will
            // attempt to connect to vRouter Agent and replay DeletePorts.
            s_logger.warn("vRouter " + vrouterIpAddress + " DeletePort failed for " + vmiInfo);
        }
    }

    private static ContrailVRouterApi getVrouterApi(String vrouterIpAddress) {
        ContrailVRouterApi vrouterApi = null;

        if (vrouterIpAddress == null) {
            s_logger.error(
                "vRouterIp Address is null, cannot search in vrouterApiMap");
            return null;
        }

        if (vrouterApiMap.containsKey(vrouterIpAddress)) {
            vrouterApi = vrouterApiMap.get(vrouterIpAddress);
        }
        if (vrouterApi == null) {
            vrouterApi = new ContrailVRouterApi(vrouterIpAddress, vrouterApiPort);

            vrouterApiMap.put(vrouterIpAddress, vrouterApi);
        }
        return vrouterApi;
    }

    // KeepAlive with all active vRouter Agent Connections.
    public static void vrouterAgentPeriodicConnectionCheck() {

        for (Map.Entry<String, ContrailVRouterApi> entry: vrouterApiMap.entrySet()) {
            String vrouterIpAddress = entry.getKey();
            ContrailVRouterApi vrouterApi = getVrouterApi(vrouterIpAddress);
            if (!vrouterApi.getActive()) {
                // host is in maintenance mode or Contrail VM is down
                continue;
            }
            if (!vrouterApi.periodicCheck()) {
                s_logger.warn(vrouterIpAddress + " periodic check failed");
            }
        }
    }

    public static void syncVrouterAgent() {

        for (Map.Entry<String, ContrailVRouterApi> entry: vrouterApiMap.entrySet()) {
            String vrouterIpAddress = entry.getKey();
            ContrailVRouterApi vrouterApi = getVrouterApi(vrouterIpAddress);
            if (!vrouterApi.getActive()) {
                // host is in maintenance mode or Contrail VM is down
                continue;
            }
            boolean ret = vrouterApi.sync();

            if (ret) {
                s_logger.info("vRouter " + vrouterIpAddress +
                        " sync request success");
            } else {
                // log failure but don't worry. Periodic  task will
                // attempt to connect to vRouter Agent and replay sync.
                s_logger.warn("vRouter " + vrouterIpAddress + " sync request failed");
            }
        }
    }

    public static void setVrouterActive(String vrouterIpAddress, boolean active) {
       ContrailVRouterApi vrouterApi = getVrouterApi(vrouterIpAddress);
        vrouterApi.setActive(active);
    }
}
