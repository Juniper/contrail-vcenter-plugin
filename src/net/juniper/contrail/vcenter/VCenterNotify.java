/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */
package net.juniper.contrail.vcenter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import com.vmware.vim25.ArrayOfEvent;
import com.vmware.vim25.Event;
import com.vmware.vim25.EventFilterSpec;
import com.vmware.vim25.EventFilterSpecByEntity;
import com.vmware.vim25.EventFilterSpecRecursionOption;
import com.vmware.vim25.IpPool;
import com.vmware.vim25.IpPoolIpPoolConfigInfo;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.PropertyChange;
import com.vmware.vim25.PropertyChangeOp;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertyFilterUpdate;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RequestCanceled;
import com.vmware.vim25.SelectionSpec;
import com.vmware.vim25.TraversalSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VmEvent;
import com.vmware.vim25.VmPoweredOnEvent;
import com.vmware.vim25.VmPoweredOffEvent;
import com.vmware.vim25.DvsEvent;
import com.vmware.vim25.DVPortgroupEvent;
import com.vmware.vim25.DVPortgroupCreatedEvent;
import com.vmware.vim25.DVPortgroupDestroyedEvent;
import com.vmware.vim25.DVPortgroupReconfiguredEvent;
import com.vmware.vim25.mo.EventHistoryCollector;
import com.vmware.vim25.mo.EventManager;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.PropertyCollector;
import com.vmware.vim25.mo.PropertyFilter;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.MigrationEvent;
import com.vmware.vim25.VmEmigratingEvent; 
import com.vmware.vim25.VmMigratedEvent; 
import com.vmware.vim25.VmBeingMigratedEvent; 
import com.vmware.vim25.VmBeingHotMigratedEvent; 

import com.google.common.base.Throwables;

import org.apache.log4j.Logger;

/**
 * @author Sachchidanand Vaidya
 * 
 */
public class VCenterNotify implements Runnable
{

    private static final Logger s_logger = 
            Logger.getLogger(VCenterNotify.class);
    private static VCenterMonitorTask monitorTask = null;

    private Folder _rootFolder;

    // EventManager and EventHistoryCollector References
    private EventManager _eventManager;
    private EventHistoryCollector _eventHistoryCollector;
    private static PropertyFilter propFilter;
    private static PropertyCollector propColl;
    private static Boolean shouldRun;
    private static Thread watchUpdates = null;

    public VCenterNotify(VCenterMonitorTask _monitorTask)
    {
        monitorTask = _monitorTask;
     }

    /**
     * Initialize the necessary Managed Object References needed here
     */
    private void initialize()
    {
        _eventManager = monitorTask.getVCenterDB().getServiceInstance().getEventManager();
        _rootFolder = monitorTask.getVCenterDB().getServiceInstance().getRootFolder();
    }

    private void createEventHistoryCollector() throws Exception
    {
        // Create an Entity Event Filter Spec to
        // specify the MoRef of the VM to be get events filtered for
        EventFilterSpecByEntity entitySpec = new EventFilterSpecByEntity();
        entitySpec.setEntity(_rootFolder.getMOR());
        entitySpec.setRecursion(EventFilterSpecRecursionOption.children);

        // set the entity spec in the EventFilter
        EventFilterSpec eventFilter = new EventFilterSpec();
        eventFilter.setEntity(entitySpec);

        // we are only interested in getting events for the VM.
        // Add as many events you want to track relating to vm.
        // Refer to API Data Object vmEvent and see the extends class list for
        // elaborate list of vmEvents
        eventFilter.setType(new String[] { "VmPoweredOnEvent", "VmPoweredOffEvent", 
                                           "VmRenamedEvent", 
                                           "DVPortgroupCreatedEvent", "DVPortgroupDestroyedEvent", 
                                           "DVPortgroupReconfiguredEvent", "DVPortgroupRenamedEvent", 
                                           "DvsPortCreatedEvent", "DvsPortDeletedEvent", "DvsPortJoinPortgroupEvent", "DvsPortLeavePortgroupEvent","MigrationEvent","VmEmigratingEvent","VmMigratedEvent","VmBeingMigratedEvent","VmBeingHotMigratedEvent"});

        // create the EventHistoryCollector to monitor events for a VM
        // and get the ManagedObjectReference of the EventHistoryCollector
        // returned
        _eventHistoryCollector = _eventManager
                .createCollectorForEvents(eventFilter);
    }

    private PropertyFilterSpec createEventFilterSpec()
    {
        // Set up a PropertySpec to use the latestPage attribute
        // of the EventHistoryCollector

        PropertySpec propSpec = new PropertySpec();
        propSpec.setAll(new Boolean(false));
        propSpec.setPathSet(new String[] { "latestPage" });
        propSpec.setType(_eventHistoryCollector.getMOR().getType());

        // PropertySpecs are wrapped in a PropertySpec array
        PropertySpec[] propSpecAry = new PropertySpec[] { propSpec };

        // Set up an ObjectSpec with the above PropertySpec for the
        // EventHistoryCollector we just created
        // as the Root or Starting Object to get Attributes for.
        ObjectSpec objSpec = new ObjectSpec();
        objSpec.setObj(_eventHistoryCollector.getMOR());
        objSpec.setSkip(new Boolean(false));

        // Get Event objects in "latestPage" from "EventHistoryCollector"
        // and no "traversl" further, so, no SelectionSpec is specified
        objSpec.setSelectSet(new SelectionSpec[] {});

        // ObjectSpecs are wrapped in an ObjectSpec array
        ObjectSpec[] objSpecAry = new ObjectSpec[] { objSpec };

        PropertyFilterSpec spec = new PropertyFilterSpec();
        spec.setPropSet(propSpecAry);
        spec.setObjectSet(objSpecAry);
        return spec;
    }

    void handleUpdate(UpdateSet update)
    {
        ObjectUpdate[] vmUpdates;
        PropertyFilterUpdate[] pfus = update.getFilterSet();
        for (int pfui = 0; pfui < pfus.length; pfui++)
        {
            System.out.println("Virtual Machine updates:");
            vmUpdates = pfus[pfui].getObjectSet();
            for (ObjectUpdate vmi : vmUpdates)
            {
                System.out.println("Handling object update");
                handleObjectUpdate(vmi);
            }
        }
    }

    void handleObjectUpdate(ObjectUpdate oUpdate)
    {
        PropertyChange[] pc = oUpdate.getChangeSet();
        System.out.println("Update kind = " + oUpdate.getKind());
        if (oUpdate.getKind() == ObjectUpdateKind.enter)
        {
            System.out.println(" New Data:");
            handleChanges(pc);
        } else if (oUpdate.getKind() == ObjectUpdateKind.leave)
        {
            System.out.println(" Removed Data:");
            handleChanges(pc);
        } else if (oUpdate.getKind() == ObjectUpdateKind.modify)
        {
            System.out.println(" Changed Data:");
            handleChanges(pc);
        }

    }

    void handleChanges(PropertyChange[] changes)
    {
        for (int pci = 0; pci < changes.length; ++pci)
        {
            String name = changes[pci].getName();
            Object value = changes[pci].getVal();
            PropertyChangeOp op = changes[pci].getOp();
            if (value != null && op!= PropertyChangeOp.remove) {
                s_logger.info("===============");
                s_logger.info("\nEvent Details follows:");
                if (value instanceof ArrayOfEvent) {
                    ArrayOfEvent aoe = (ArrayOfEvent) value;
                    Event[] evts = aoe.getEvent();
                    for (int evtID = 0; evtID < evts.length; ++evtID)
                    {
                        Event anEvent = evts[evtID];
                        s_logger.info("\n----------" + "\n Event ID: "
                                + anEvent.getKey() + "\n Event: "
                                + anEvent.getClass().getName()
                                + "\n FullFormattedMessage: "
                                + anEvent.getFullFormattedMessage()
                                + "\n----------\n");
                    }
                } else if (value instanceof VmPoweredOnEvent) {
                    printVmEvent(value);
                    try {
                        monitorTask.syncVmwareVirtualNetworks();
                    } catch (Exception e) {
                        String stackTrace = Throwables.getStackTraceAsString(e);
                        s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
                        s_logger.error(stackTrace); 
                        e.printStackTrace();
                    }

                } else if (value instanceof VmPoweredOffEvent) {
                    printVmEvent(value);
                    try {
                        monitorTask.syncVmwareVirtualNetworks();
                    } catch (Exception e) {
                        String stackTrace = Throwables.getStackTraceAsString(e);
                        s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
                        s_logger.error(stackTrace); 
                        e.printStackTrace();
                    }
                } else if (value instanceof VmMigratedEvent) {
                    printVmEvent(value);
                    try {
                        monitorTask.syncVmwareVirtualNetworks();
                    } catch (Exception e) {
                        String stackTrace = Throwables.getStackTraceAsString(e);
                        s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
                        s_logger.error(stackTrace); 
                        e.printStackTrace();
                    }

                } else if (value instanceof DVPortgroupCreatedEvent) {
                    printDvsPortgroupEvent(value);
                } else if (value instanceof DVPortgroupDestroyedEvent) {
                    printDvsPortgroupEvent(value);
                    try {
                        monitorTask.syncVmwareVirtualNetworks();
                    } catch (Exception e) {
                        String stackTrace = Throwables.getStackTraceAsString(e);
                        s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
                        s_logger.error(stackTrace); 
                        e.printStackTrace();
                    }

                } else if (value instanceof DVPortgroupReconfiguredEvent) {
                    printDvsPortgroupEvent(value);
                } else if (value instanceof DvsEvent) {
                    DvsEvent anEvent = (DvsEvent) value;
                    s_logger.info("\n----------" + "\n Event ID: "
                            + anEvent.getKey() + "\n Event: "
                            + anEvent.getClass().getName()
                            + "\n FullFormattedMessage: "
                            + anEvent.getFullFormattedMessage()
                            + "\n DVS Port Reference: "
                            + anEvent.getDvs().getDvs().get_value()
                            + "\n----------\n");
                } else {
                    Event anEvent = (Event) value;
                    s_logger.info("\n----------" 
                            + "\n Event ID: " + anEvent.getKey() 
                            + "\n Event: " + anEvent.getClass().getName()
                            + "\n FullFormattedMessage: " + anEvent.getFullFormattedMessage()
                            + "\n----------\n");
                }
                s_logger.info("===============");
            } else if (value != null && op == PropertyChangeOp.remove) {

            }
        }
        s_logger.info("+++++++++++++Update Processing Complete +++++++++++++++++++++");
    }


     public void start() {
        try
        {
            System.out.println("info---" + 
                monitorTask.getVCenterDB().getServiceInstance().getAboutInfo().getFullName());
            this.initialize();
            this.createEventHistoryCollector();

            PropertyFilterSpec eventFilterSpec = this
                    .createEventFilterSpec();
            propColl = monitorTask.getVCenterDB().getServiceInstance().getPropertyCollector();

            propFilter = propColl.createFilter(eventFilterSpec, true);

            watchUpdates = new Thread(this);
            shouldRun = true;
            watchUpdates.start();
        } catch (Exception e)
        {
            System.out.println("Caught Exception : " + " Name : "
                    + e.getClass().getName() + " Message : " + e.getMessage()
                    + " Trace : ");
            e.printStackTrace();
        }
    }

    public static void terminate() throws Exception {
        shouldRun = false;
        propColl.cancelWaitForUpdates();
        propFilter.destroyPropertyFilter();
        monitorTask.getVCenterDB().getServiceInstance().getServerConnection().logout();
        watchUpdates.stop();
    }

    public void run()
    {
        String version = "";
        try
        {
            do
            {
                try
                {
                    UpdateSet update = propColl.waitForUpdates(version);
                    if (update != null && update.getFilterSet() != null)
                    {

                        version = update.getVersion();
                        System.out.println(" Current Version: " + version);
                        
                        this.handleUpdate(update);

                    } else
                    {
                        System.out.println("No update is present!");
                    }
                } catch (Exception e)
                {
                	e.printStackTrace();
                }
                if (monitorTask.VCenterNotifyForceRefresh) {
                        this.initialize();
                        this.createEventHistoryCollector();
                        PropertyFilterSpec eventFilterSpec =
                                   this.createEventFilterSpec();
                        propColl = monitorTask.getVCenterDB().getServiceInstance().getPropertyCollector();
                        propFilter = propColl.createFilter(eventFilterSpec, true);
                        monitorTask.VCenterNotifyForceRefresh = false;
               }
            } while (shouldRun);
        } catch (Exception e)
        {
            if (e instanceof RequestCanceled)
            {
                System.out.println("OK");
            } else
            {
                System.out.println("Caught Exception : " + " Name : "
                        + e.getClass().getName() + " Message : "
                        + e.getMessage() + " Trace : ");
            }
        }
    }

    void printVmEvent(Object value)
    {
        VmEvent anEvent = (VmEvent) value;
        s_logger.info("\n----------" + "\n Event ID: "
                + anEvent.getKey() + "\n Event: "
                + anEvent.getClass().getName()
                + "\n FullFormattedMessage: "
                + anEvent.getFullFormattedMessage()
                + "\n VM Reference: "
                + anEvent.getVm().getVm().get_value()
                + "\n createdTime : "
                + anEvent.getCreatedTime().getTime()
                + "\n----------\n");
    }

    void printDvsPortgroupEvent(Object value)
    {
        DVPortgroupEvent anEvent = (DVPortgroupEvent) value;
        s_logger.info("\n----------" + "\n Event ID: "
                + anEvent.getKey() + "\n Event: "
                + anEvent.getClass().getName()
                + "\n FullFormattedMessage: "
                + anEvent.getFullFormattedMessage()
                + "\n DVS Portgroup Reference: "
                + anEvent.getDvs().getDvs().get_value()
                + "\n----------\n");
    }
}
