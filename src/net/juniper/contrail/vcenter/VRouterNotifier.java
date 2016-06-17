/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 */

package net.juniper.contrail.vcenter;

import com.google.common.base.Throwables;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import net.juniper.contrail.vcenter.ContrailVRouterApi;

public class VRouterNotifier {
    static volatile HashMap<String, ContrailVRouterApi> vrouterApiMap =
            new HashMap<String, ContrailVRouterApi>();
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
        if (!vmInfo.isPoweredOnState()) {
            s_logger.info(vmInfo + " is PoweredOff. Skip AddPort now for " + vmiInfo);
            return;
        }
        if (ipAddress == null) {
            ipAddress = "0.0.0.0";
        }
        try {
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            if (vrouterApi == null) {
                vrouterApi = new ContrailVRouterApi(vrouterIpAddress, vrouterApiPort);
                vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }

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
                s_logger.error("vRouter " + vrouterIpAddress + " addPort failed for " + vmiInfo);
            }
        } catch(Throwable e) {
            s_logger.error("vRouter " + vrouterIpAddress + " Exception in addPort for "
                    + vmiInfo + ": " + e.getMessage());
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
            e.printStackTrace();
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

        ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
        if (vrouterApi == null) {
            vrouterApi = new ContrailVRouterApi(vrouterIpAddress, vrouterApiPort);

            vrouterApiMap.put(vrouterIpAddress, vrouterApi);
        }
        boolean ret = vrouterApi.deletePort(vmiInfo.getUuid());

        if (ret) {
            s_logger.info("vRouter " + vrouterIpAddress +
                    " DeletePort success for " + vmiInfo);
        } else {
            // log failure but don't worry. Periodic KeepAlive task will
            // attempt to connect to vRouter Agent and replay DeletePorts.
            s_logger.error("vRouter " + vrouterIpAddress + " DeletePort failed for " + vmiInfo);
        }
    }

    // KeepAlive with all active vRouter Agent Connections.
    public static void vrouterAgentPeriodicConnectionCheck() {

        Map<String, Boolean> vRouterActiveMap = VCenterDB.vRouterActiveMap;

        for (Map.Entry<String, Boolean> entry: vRouterActiveMap.entrySet()) {
            if (entry.getValue() == Boolean.FALSE) {
                // host is in maintenance mode
                continue;
            }

            String vrouterIpAddress = entry.getKey();
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            if (vrouterApi == null) {
                    vrouterApi = new ContrailVRouterApi(
                          vrouterIpAddress,
                          vrouterApiPort);

                vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }
            // run Keep Alive with vRouter Agent.
            if (!vrouterApi.isServerAlive()) {
                s_logger.warn(vrouterIpAddress + " not reachable");
            }
        }
    }

    public static void syncVrouterAgent() {

        Map<String, Boolean> vRouterActiveMap = VCenterDB.vRouterActiveMap;

        for (Map.Entry<String, Boolean> entry: vRouterActiveMap.entrySet()) {
            if (entry.getValue() == Boolean.FALSE) {
                // host is in maintenance mode
                continue;
            }

            String vrouterIpAddress = entry.getKey();
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            if (vrouterApi == null) {
                    vrouterApi = new ContrailVRouterApi(
                          vrouterIpAddress,
                          vrouterApiPort);

                vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }
            boolean ret = vrouterApi.syncPorts();

            if (ret) {
                s_logger.info("vRouter " + vrouterIpAddress +
                        "syncPorts success");
            } else {
                // log failure but don't worry. Periodic KeepAlive task will
                // attempt to connect to vRouter Agent and replay DeletePorts.
                s_logger.error("vRouter " + vrouterIpAddress + " syncPorts failed");
            }
        }
    }
}
