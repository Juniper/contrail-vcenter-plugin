/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 */

package net.juniper.contrail.vcenter;

import com.google.common.base.Throwables;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.log4j.Logger;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

public class VRouterNotifier {
    static volatile HashMap<String, ContrailVRouterApi> vrouterApiMap =
            new HashMap<String, ContrailVRouterApi>();
    static final int vrouterApiPort = 9090;

    private final static Logger s_logger =
            Logger.getLogger(VRouterNotifier.class);

    public static Map<String, ContrailVRouterApi> getVrouterApiMap() {
        return vrouterApiMap;
    }

    public static void created(VirtualMachineInterfaceInfo vmiInfo) {
        if (vmiInfo == null) {
            s_logger.error("Null vmiInfo argument, cannot perform AddPort");
            return;
        }

        if (vmiInfo.vmInfo == null)  {
            s_logger.error("Null vmInfo, cannot perform AddPort for " + vmiInfo);
            return;
        }
        if (vmiInfo.vnInfo == null)  {
            s_logger.error("Null vnInfo, cannot perform AddPort for " + vmiInfo);
            return;
        }

        if (vmiInfo.getUuid() == null)  {
            s_logger.error("Null uuid, cannot perform DeletePort for " + vmiInfo);
            return;
        }

        String vrouterIpAddress = vmiInfo.getVmInfo().getVrouterIpAddress();
        String ipAddress = vmiInfo.getIpAddress();
        VirtualMachineInfo vmInfo = vmiInfo.vmInfo;
        VirtualNetworkInfo vnInfo = vmiInfo.vnInfo;

        if (vrouterIpAddress == null) {
            s_logger.error(
                "AddPort notification NOT sent as vRouterIp Address not known for " + vmiInfo);
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
                vrouterApi = new ContrailVRouterApi(
                        InetAddress.getByName(vrouterIpAddress),
                        vrouterApiPort, false, 1000);
                vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }

            boolean ret = vrouterApi.AddPort(UUID.fromString(vmiInfo.getUuid()),
                    UUID.fromString(vmInfo.getUuid()), vmiInfo.getUuid(),
                    InetAddress.getByName(ipAddress),
                    Utils.parseMacAddress(vmiInfo.getMacAddress()),
                    UUID.fromString(vnInfo.getUuid()),
                    vnInfo.getIsolatedVlanId(),
                    vnInfo.getPrimaryVlanId(), vmInfo.getName(),
                    UUID.fromString(vnInfo.getProjectUuid()));
            if (ret) {
                s_logger.info("vRouter " + vrouterIpAddress + " AddPort success for " + vmiInfo);
            } else {
                // log failure but don't worry. Periodic KeepAlive task will
                // attempt to connect to vRouter Agent and replay AddPorts.
                s_logger.error("vRouter " + vrouterIpAddress + " AddPort failed for " + vmiInfo);
            }
        } catch(Throwable e) {
            s_logger.error("vRouter " + vrouterIpAddress + " Exception in AddPort for "
                    + vmiInfo + ": " + e.getMessage());
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
            e.printStackTrace();
        }
    }

    public static void deleted(VirtualMachineInterfaceInfo vmiInfo) {
        if (vmiInfo == null) {
            s_logger.error("Null vmiInfo argument, cannot perform DeletePort");
            return;
        }

        if (vmiInfo.vmInfo == null)  {
            s_logger.error("Null vmInfo, cannot perform DeletePort for " + vmiInfo);
            return;
        }
        if (vmiInfo.vnInfo == null)  {
            s_logger.error("Null vnInfo, cannot perform DeletePort for " + vmiInfo);
            return;
        }
        if (vmiInfo.getUuid() == null)  {
            s_logger.error("Null uuid, cannot perform DeletePort for " + vmiInfo);
            return;
        }
        String vrouterIpAddress = vmiInfo.getVmInfo().getVrouterIpAddress();
        String ipAddress = vmiInfo.getIpAddress();

        if (vrouterIpAddress == null) {
            s_logger.error(
                "DeletePort notification NOT sent as vRouterIp Address not known for " + vmiInfo);
            return;
        }
        if (ipAddress == null) {
            ipAddress = "0.0.0.0";
        }

        ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
        if (vrouterApi == null) {
            try {
            vrouterApi = new ContrailVRouterApi(
                    InetAddress.getByName(vrouterIpAddress),
                    vrouterApiPort, false, 1000);
            } catch (UnknownHostException e) {
                // log error ("Incorrect vrouter address");
                s_logger.error("DeletePort failed due to unknown vrouter " + vrouterIpAddress);
                return;
            }
            vrouterApiMap.put(vrouterIpAddress, vrouterApi);
        }
        boolean ret = vrouterApi.DeletePort(UUID.fromString(vmiInfo.getUuid()));

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
                try {
                    vrouterApi = new ContrailVRouterApi(
                          InetAddress.getByName(vrouterIpAddress),
                          vrouterApiPort, false, 1000);
                } catch (UnknownHostException e) {
                }
                if (vrouterApi == null) {
                    continue;
                }
                vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }
            // run Keep Alive with vRouter Agent.
            vrouterApi.PeriodicConnectionCheck();
        }
    }
}
