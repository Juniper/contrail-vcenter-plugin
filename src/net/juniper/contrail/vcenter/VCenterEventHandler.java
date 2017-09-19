/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 *
 * Handles functionality related to events received from VCenter
 */
package net.juniper.contrail.vcenter;

import org.apache.log4j.Logger;
import java.lang.RuntimeException;

import com.google.common.base.Throwables;
import com.vmware.vim25.DVPortgroupCreatedEvent;
import com.vmware.vim25.DVPortgroupDestroyedEvent;
import com.vmware.vim25.DVPortgroupReconfiguredEvent;
import com.vmware.vim25.DVPortgroupRenamedEvent;
import com.vmware.vim25.Event;
import com.vmware.vim25.VmClonedEvent;
import com.vmware.vim25.VmCreatedEvent;
import com.vmware.vim25.VmDeployedEvent;
import com.vmware.vim25.VmMacAssignedEvent;
import com.vmware.vim25.VmMacChangedEvent;
import com.vmware.vim25.DrsVmMigratedEvent;
import com.vmware.vim25.VmMigratedEvent;
import com.vmware.vim25.VmPoweredOffEvent;
import com.vmware.vim25.VmPoweredOnEvent;
import com.vmware.vim25.DrsVmPoweredOnEvent;
import com.vmware.vim25.VmReconfiguredEvent;
import com.vmware.vim25.VmRemovedEvent;
import com.vmware.vim25.VmRenamedEvent;
import com.vmware.vim25.VmSuspendedEvent;

public class VCenterEventHandler {
    VCenterDB vcenterDB;
    VncDB vncDB;
    private static final Logger s_logger =
            Logger.getLogger(VCenterEventHandler.class);

    VCenterEventHandler(VCenterDB vcenterDB, VncDB vncDB) {
        this.vcenterDB = vcenterDB;
        this.vncDB = vncDB;
    }

    public void handle(Event event) throws Exception {
        s_logger.info("Process event " + event.getFullFormattedMessage());
        if (event instanceof VmCreatedEvent
            || event instanceof VmClonedEvent
            || event instanceof VmDeployedEvent
            || event instanceof VmReconfiguredEvent
            || event instanceof  VmRenamedEvent
            || event instanceof VmMacChangedEvent
            || event instanceof VmMacAssignedEvent
            || event instanceof DrsVmMigratedEvent
            || event instanceof DrsVmPoweredOnEvent
            || event instanceof VmMigratedEvent
            || event instanceof VmPoweredOnEvent
            || event instanceof VmPoweredOffEvent
            || event instanceof VmSuspendedEvent) {
            handleVmUpdateEvent(event);
        } else if (event instanceof VmRemovedEvent) {
            handleVmDeleteEvent(event);
        } else if (event instanceof DVPortgroupCreatedEvent
                || event instanceof DVPortgroupReconfiguredEvent
                || event instanceof DVPortgroupRenamedEvent) {
            handleNetworkUpdateEvent(event);
        } else if (event instanceof DVPortgroupDestroyedEvent) {
            handleNetworkDeleteEvent(event);
        } else {
            handleEvent(event);
        }
        s_logger.info("Done processing event " + event.getFullFormattedMessage());
    }

    private void handleVmUpdateEvent(Event event) throws Exception {
        VirtualMachineInfo newVmInfo = null;
        if (event.getHost() != null) {
            String hostName = event.getHost().getName();
            if (!vcenterDB.esxiToVRouterIpMap.containsKey(hostName)) {
                s_logger.info("Skipping event for unmanaged host " + hostName);
                return;
            }
            if (!vcenterDB.isVmEventOnMonitoredCluster(event, hostName)) {
                s_logger.info("Skipping vm event from host " + hostName);
                return;
            }
        }

        try {
            newVmInfo = new VirtualMachineInfo(event, vcenterDB, vncDB);
        } catch (RuntimeException e) {
            s_logger.error(e.getMessage());
            return;
        }

        VirtualMachineInfo oldVmInfo = MainDB.getVmById(newVmInfo.getUuid());

        if (oldVmInfo != null) {
            oldVmInfo.update(newVmInfo, vncDB);
        } else {
            newVmInfo.create(vncDB);
        }
    }

    private void handleVmDeleteEvent(Event event) throws Exception {
        if (event.getHost() != null) {
            String hostName = event.getHost().getName();
            if (!vcenterDB.esxiToVRouterIpMap.containsKey(hostName)) {
                s_logger.info("Skipping event for unmanaged host " + hostName);
                return;
            }
        }

        VirtualMachineInfo vmInfo = MainDB.getVmByName(event.getVm().getName());

        if (vmInfo == null) {
            return;
        }

        vmInfo.delete(vncDB);
    }

    private void handleNetworkUpdateEvent(Event event) throws Exception {
        VirtualNetworkInfo newVnInfo = new VirtualNetworkInfo(event, vcenterDB, vncDB);

        VirtualNetworkInfo oldVnInfo = MainDB.getVnByName(newVnInfo.getName());

        if (oldVnInfo != null) {
            oldVnInfo.update(newVnInfo, vncDB);
        } else {
            newVnInfo.create(vncDB);
            VCenterNotify.watchVn(newVnInfo);
        }
    }

    private void handleNetworkDeleteEvent(Event event) throws Exception {
	String net_name = event.getNet().getName();
	/*
	From Mitaka nova driver will append cluster_id to port group
	therefore need to extract the appended cluster id
	*/
	if (vcenterDB.mode == Mode.VCENTER_AS_COMPUTE) {
            net_name = net_name.substring(Math.max(0, net_name.length() - 36));
	}
        VirtualNetworkInfo vnInfo = MainDB.getVnByName(net_name);

        if (vnInfo == null) {
            return;
        }
        VCenterNotify.unwatchVn(vnInfo);
        vnInfo.delete(vncDB);
    }

    private void handleEvent(Event event) {
        s_logger.error("Event "+ event.getClass().getName() + " received, but not handled.");
    }
}
