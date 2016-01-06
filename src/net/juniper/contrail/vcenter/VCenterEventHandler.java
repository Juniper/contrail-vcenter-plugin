/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 * 
 * Handles functionality related to events received from VCenter
 */
package net.juniper.contrail.vcenter;

import java.io.IOException;
import org.apache.log4j.Logger;
import com.vmware.vim25.DVPortgroupCreatedEvent;
import com.vmware.vim25.DVPortgroupDestroyedEvent;
import com.vmware.vim25.DVPortgroupReconfiguredEvent;
import com.vmware.vim25.DVPortgroupRenamedEvent;
import com.vmware.vim25.Event;
import com.vmware.vim25.VmBeingCreatedEvent;
import com.vmware.vim25.VmCloneEvent;
import com.vmware.vim25.VmClonedEvent;
import com.vmware.vim25.VmCreatedEvent;
import com.vmware.vim25.VmDeployedEvent;
import com.vmware.vim25.VmMacAssignedEvent;
import com.vmware.vim25.VmMacChangedEvent;
import com.vmware.vim25.VmMigratedEvent;
import com.vmware.vim25.VmPoweredOffEvent;
import com.vmware.vim25.VmPoweredOnEvent;
import com.vmware.vim25.VmReconfiguredEvent;
import com.vmware.vim25.VmRemovedEvent;
import com.vmware.vim25.VmRenamedEvent;

public class VCenterEventHandler {
    Event event;
    VCenterDB vcenterDB;
    VncDB vncDB;
    private final Logger s_logger =
            Logger.getLogger(VCenterEventHandler.class);

    VCenterEventHandler(Event event, VCenterDB vcenterDB, VncDB vncDB) {
        this.event = event;
        this.vcenterDB = vcenterDB;
        this.vncDB = vncDB;
    }

    private void printEvent() {
        s_logger.info("===============");
        s_logger.info("\nEvent Details follows:");

        s_logger.info("\n----------" + "\n Event ID: "
                + event.getKey() + "\n Event: "
                + event.getClass().getName()
                + "\n FullFormattedMessage: "
                + event.getFullFormattedMessage()
                + "\n----------\n");
    }

    public void handle() throws Exception {
        printEvent();
       
        if (event instanceof VmBeingCreatedEvent
            || event instanceof VmCreatedEvent
            || event instanceof VmClonedEvent
            || event instanceof VmCloneEvent
            || event instanceof VmDeployedEvent
            || event instanceof VmReconfiguredEvent
            || event instanceof  VmRenamedEvent
            || event instanceof VmMacChangedEvent
            || event instanceof VmMacAssignedEvent
            || event instanceof VmMigratedEvent
            || event instanceof VmPoweredOnEvent
            || event instanceof VmPoweredOffEvent) {
            handleVmUpdateEvent();
        } else if (event instanceof VmRemovedEvent) {
            handleVmDeleteEvent();
        } else if (event instanceof DVPortgroupCreatedEvent
                || event instanceof DVPortgroupReconfiguredEvent
                || event instanceof DVPortgroupRenamedEvent) {
            handleNetworkUpdateEvent();
        } else if (event instanceof DVPortgroupDestroyedEvent) {
            handleNetworkDeleteEvent();
        } else {
            handleEvent(event);
        }
    }

    private void handleVmUpdateEvent() throws Exception {
        VirtualMachineInfo newVmInfo = new VirtualMachineInfo(event, vcenterDB, vncDB);
         
        // Ignore virtual machine?
        if (newVmInfo.ignore()) {
            s_logger.debug(" Ignoring update vm: " + newVmInfo.getName());
            return;
        }
        
        VirtualMachineInfo oldVmInfo = MainDB.getVmById(newVmInfo.getUuid());
        
        if (oldVmInfo != null) {
            oldVmInfo.update(newVmInfo, vncDB);
        } else {
            newVmInfo.create(vncDB);
            // add a watch on this Vm guest OS to be notified of guest OS changes,
            // for instance IP address changes
            VCenterNotify.watchVm(newVmInfo);
        }
    }

    private void handleVmDeleteEvent() throws Exception {
        VirtualMachineInfo vmInfo = MainDB.getVmByName(event.getVm().getName());
        
        if (vmInfo == null) {
            return;
        }

        VCenterNotify.unwatchVm(vmInfo);  
        vmInfo.delete(vncDB);
    }

    private void handleNetworkUpdateEvent() throws Exception {
        VirtualNetworkInfo newVnInfo = 
                new VirtualNetworkInfo(event, vcenterDB);
        
        VirtualNetworkInfo oldVnInfo = MainDB.getVnByName(newVnInfo.getName());
        
        if (oldVnInfo != null) {
            oldVnInfo.update(newVnInfo, vncDB);
        } else {
            newVnInfo.create(vncDB);
            VCenterNotify.watchVn(newVnInfo);
        }
    }

    private void handleNetworkDeleteEvent() throws Exception {
        
        VirtualNetworkInfo vnInfo = MainDB.getVnByName(event.getNet().getName());
        
        if (vnInfo == null) {
            return;
        }
        VCenterNotify.unwatchVn(vnInfo);  
        vnInfo.delete(vncDB);
    }

    private void handleEvent(Event event) {
        s_logger.error("Buddy you need to get a hold off this event");
    }
}
