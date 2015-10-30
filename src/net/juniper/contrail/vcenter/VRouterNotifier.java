/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 */

package net.juniper.contrail.vcenter;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.UUID;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

public class VRouterNotifier {
    static volatile HashMap<String, ContrailVRouterApi> vrouterApiMap;
    static final int vrouterApiPort = 9090;

    public VRouterNotifier() {
        vrouterApiMap = new HashMap<String, ContrailVRouterApi>();
    }

    public static void addPort(EventData event) {

        String vrouterIpAddress = event.vrouterIpAddress;

        // Plug notification to vrouter
        if (vrouterIpAddress == null) {
            /*s_logger.warn("Virtual machine: " + vmName + " esxi host: " + hostName
                + " addPort notification NOT sent as vRouterIp Address not known");*/
            return;
        }
        try {
            ContrailVRouterApi vrouterApi = vrouterApiMap.get(vrouterIpAddress);
            if (vrouterApi == null) {
                vrouterApi = new ContrailVRouterApi(
                        InetAddress.getByName(vrouterIpAddress),
                        vrouterApiPort, false, 1000);
                vrouterApiMap.put(vrouterIpAddress, vrouterApi);
            }
            if (event.vmInfo.isPoweredOnState()) {
                boolean ret = vrouterApi.AddPort(UUID.fromString(event.apiVmi.getUuid()),
                        UUID.fromString(event.apiVm.getUuid()), event.apiVmi.getName(),
                        //TODO make sure these are set
                        InetAddress.getByName(event.vmInfo.getIpAddress()),
                        Utils.parseMacAddress(event.vmInfo.getMacAddress()),
                        UUID.fromString(event.apiVn.getUuid()),
                        event.vnInfo.getIsolatedVlanId(),
                        event.vnInfo.getPrimaryVlanId(), event.vmInfo.getName());
                if ( ret == true) {
                    /*
                    s_logger.info("VRouterAPi Add Port success - interface name:"
                                  +  vmInterface.getDisplayName()
                                  + "(" + vmInterface.getName() + ")"
                                  + ", VM=" + vmName
                                  + ", VN=" + network.getName()
                                  + ", vmIpAddress=" + vmIpAddress
                                  + ", vlan=" + primaryVlanId + "/" + isolatedVlanId);
                     */
                } else {
                    // log failure but don't worry. Periodic KeepAlive task will
                    // attempt to connect to vRouter Agent and replay AddPorts.
                    /*
                    s_logger.error("VRouterAPi Add Port failed - interface name: "
                                  +  vmInterface.getDisplayName()
                                  + "(" + vmInterface.getName() + ")"
                                  + ", VM=" + vmName
                                  + ", VN=" + network.getName()
                                  + ", vmIpAddress=" + vmIpAddress
                                  + ", vlan=" + primaryVlanId + "/" + isolatedVlanId);
                     */
                }
            } else {
                //s_logger.info("VM (" + vmName + ") is PoweredOff. Skip AddPort now.");
            }
        }catch(Throwable e) {
            //s_logger.error("Exception : " + e);
            e.printStackTrace();
        }
    }

    public static void deletePort(EventData event) {
    }
}
