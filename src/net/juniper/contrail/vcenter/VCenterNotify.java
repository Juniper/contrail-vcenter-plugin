/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */
package net.juniper.contrail.vcenter;

import java.net.URL;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.ArrayOfEvent;
import com.vmware.vim25.Event;
import com.vmware.vim25.EventFilterSpec;
import com.vmware.vim25.EventFilterSpecByEntity;
import com.vmware.vim25.EventFilterSpecRecursionOption;
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
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VmEvent;
import com.vmware.vim25.VmPoweredOnEvent;
import com.vmware.vim25.VmPoweredOffEvent;
import com.vmware.vim25.WaitOptions;
import com.vmware.vim25.DvsEvent;
import com.vmware.vim25.DVPortgroupEvent;
import com.vmware.vim25.DVPortgroupCreatedEvent;
import com.vmware.vim25.DVPortgroupDestroyedEvent;
import com.vmware.vim25.DVPortgroupReconfiguredEvent;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.EventHistoryCollector;
import com.vmware.vim25.mo.EventManager;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.PropertyCollector;
import com.vmware.vim25.mo.PropertyFilter;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;
import com.vmware.vim25.VmMigratedEvent; 
import com.vmware.vim25.EnteredMaintenanceModeEvent;
import com.vmware.vim25.ExitMaintenanceModeEvent;
import com.vmware.vim25.HostConnectedEvent;
import com.vmware.vim25.HostConnectionLostEvent;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ManagedObject;
import com.google.common.base.Throwables;
import com.vmware.vim25.InvalidState;
import com.vmware.vim25.RuntimeFault;
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

    private final String contrailDataCenterName;
    private final String contrailDvsName;
    private final String vcenterUrl;
    private final String vcenterUsername;
    private final String vcenterPassword;
    private volatile static ServiceInstance serviceInstance;
    private Folder rootFolder;
    private InventoryNavigator inventoryNavigator;
    private Datacenter _contrailDC;
    private VmwareDistributedVirtualSwitch contrailDVS;
    static final int VCENTER_WAIT_FOR_UPDATES_TIMEOUT = 120; // 120 seconds

    private static Boolean shouldRun;
    private static Thread watchUpdates = null;
    
    private static Map<ManagedObject, PropertyFilter> watchedFilters
                    = new HashMap<ManagedObject, PropertyFilter>();

    public VCenterNotify(VCenterMonitorTask _monitorTask, 
                         String vcenterUrl, String vcenterUsername,
                         String vcenterPassword, String contrailDcName,
                         String contrailDvsName)
    {
        this.monitorTask            = _monitorTask;
        this.vcenterUrl             = vcenterUrl;
        this.vcenterUsername        = vcenterUsername;
        this.vcenterPassword        = vcenterPassword;
        this.contrailDataCenterName = contrailDcName;
        this.contrailDvsName        = contrailDvsName;
    }

    /**
     * Initialize the necessary Managed Object References needed here
     */
    private boolean initialize() {
        // Connect to VCenter
        s_logger.info("Connecting to vCenter Server : " + "("
                                + vcenterUrl + "," + vcenterUsername + ")");
        if (serviceInstance == null) {
            try {
                serviceInstance = new ServiceInstance(new URL(vcenterUrl),
                                            vcenterUsername, vcenterPassword, true);
                if (serviceInstance == null) {
                    s_logger.error("Failed to connect to vCenter Server : " + "("
                                    + vcenterUrl + "," + vcenterUsername + "," 
                                    + vcenterPassword + ")");
                }
            } catch (MalformedURLException e) {
                s_logger.error("MalformedURL exception while connecting to vcenter" + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (RemoteException e) {
                s_logger.error("Remote exception while connecting to vcenter" + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (Exception e) {
                s_logger.error("Error while connecting to vcenter" + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }
        }
        s_logger.info("Connected to vCenter Server : " + "("
                                + vcenterUrl + "," + vcenterUsername + "," 
                                + vcenterPassword + ")");
        return true;
    }

    private boolean Initialize_data() {

        if (rootFolder == null) {
            rootFolder = serviceInstance.getRootFolder();
            if (rootFolder == null) {
                s_logger.error("Failed to get rootfolder for vCenter ");
                return false;
            }
        }
        s_logger.info("Got rootfolder for vCenter ");

        if (inventoryNavigator == null) {
            inventoryNavigator = new InventoryNavigator(rootFolder);
            if (inventoryNavigator == null) {
                s_logger.error("Failed to get InventoryNavigator for vCenter ");
                return false;
            }
        }
        s_logger.info("Got InventoryNavigator for vCenter ");

        // Search contrailDc
        if (_contrailDC == null) {
            try {
                _contrailDC = (Datacenter) inventoryNavigator.searchManagedEntity(
                                          "Datacenter", contrailDataCenterName);
            } catch (InvalidProperty e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (RuntimeFault e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (RemoteException e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (Exception e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }
            if (_contrailDC == null) {
                s_logger.error("Failed to find " + contrailDataCenterName 
                               + " DC on vCenter ");
                return false;
            }
        }
        s_logger.info("Found " + contrailDataCenterName + " DC on vCenter ");
        
        // Search contrailDvSwitch
        if (contrailDVS == null) {
            try {
                contrailDVS = (VmwareDistributedVirtualSwitch)
                                inventoryNavigator.searchManagedEntity(
                                        "VmwareDistributedVirtualSwitch",
                                        contrailDvsName);
            } catch (InvalidProperty e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (RuntimeFault e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (RemoteException e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            } catch (Exception e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }

            if (contrailDVS == null) {
                s_logger.error("Failed to find " + contrailDvsName + 
                               " DVSwitch on vCenter");
                return false;
            }
        }
        s_logger.info("Found " + contrailDvsName + " DVSwitch on vCenter ");
      
        return true;
    }

    public void Cleanup() {
        watchedFilters.clear();
        contrailDVS        = null;
        _contrailDC        = null;
        inventoryNavigator = null;
        rootFolder         = null;
        serviceInstance    = null;
    }

    private EventHistoryCollector createEventHistoryCollector(ManagedObject mo, 
           String[] events) throws InvalidState, RuntimeFault, RemoteException
    {
        // Create an Entity Event Filter Spec to
        // specify the MoRef of the VM to be get events filtered for
        EventFilterSpecByEntity entitySpec = new EventFilterSpecByEntity();
        entitySpec.setEntity(mo.getMOR());
        entitySpec.setRecursion(EventFilterSpecRecursionOption.children);

        // set the entity spec in the EventFilter
        EventFilterSpec eventFilter = new EventFilterSpec();
        eventFilter.setEntity(entitySpec);
        eventFilter.setType(events);

        // create the EventHistoryCollector to monitor events for a VM
        // and get the ManagedObjectReference of the EventHistoryCollector
        // returned
        
        EventManager eventManager = serviceInstance.getEventManager();
       
        if (eventManager != null) {
            return eventManager.createCollectorForEvents(eventFilter);
        }
        return null;
    }

    private PropertyFilterSpec createEventFilterSpec(
            EventHistoryCollector collector)
    {
        // Set up a PropertySpec to use the latestPage attribute
        // of the EventHistoryCollector

        PropertySpec propSpec = new PropertySpec();
        propSpec.setAll(new Boolean(false));
        propSpec.setPathSet(new String[] { "latestPage" });
        propSpec.setType(collector.getMOR().getType());

        // PropertySpecs are wrapped in a PropertySpec array
        PropertySpec[] propSpecAry = new PropertySpec[] { propSpec };

        // Set up an ObjectSpec with the above PropertySpec for the
        // EventHistoryCollector we just created
        // as the Root or Starting Object to get Attributes for.
        ObjectSpec objSpec = new ObjectSpec();
        objSpec.setObj(collector.getMOR());
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
        if (changes == null) {
            return;
        }
        
        for (int pci = 0; pci < changes.length; ++pci)
        {
            if (changes[pci] == null) {
                continue;
            }
            Object value = changes[pci].getVal();
            if (value == null) {
                continue;
            }
            PropertyChangeOp op = changes[pci].getOp();
            if (op!= PropertyChangeOp.remove) {
                s_logger.info("===============");
                s_logger.info("\nEvent Details follows:");
                if (value instanceof ArrayOfEvent) {
                    ArrayOfEvent aoe = (ArrayOfEvent) value;
                    Event[] evts = aoe.getEvent();
                    if (evts == null) {
                        continue;
                    }
                    for (int evtID = 0; evtID < evts.length; ++evtID)
                    {
                        Event anEvent = evts[evtID];
                        if (anEvent == null) {
                            continue;
                        }
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
                    }
                } else if ((value instanceof EnteredMaintenanceModeEvent) || (value instanceof HostConnectionLostEvent)) {
                    Event anEvent = (Event) value;
                    String vRouterIpAddress = monitorTask.getVCenterDB().esxiToVRouterIpMap.get(anEvent.getHost().getName());
                    if (vRouterIpAddress != null) {
                        monitorTask.getVCenterDB().vRouterActiveMap.put(vRouterIpAddress, false);
                    s_logger.info("\nEntering maintenance mode. Marking the host " + vRouterIpAddress +" inactive");
                    } else {
                        s_logger.info("\nNot managing the host " + vRouterIpAddress +" inactive");
                    }
                } else if ((value instanceof ExitMaintenanceModeEvent) || (value instanceof HostConnectedEvent)) {
                    Event anEvent = (Event) value;
                    String vRouterIpAddress = monitorTask.getVCenterDB().esxiToVRouterIpMap.get(anEvent.getHost().getName());
                    if (vRouterIpAddress != null) {
                        monitorTask.getVCenterDB().vRouterActiveMap.put(vRouterIpAddress, true);
                    s_logger.info("\nExit maintenance mode. Marking the host " + vRouterIpAddress +" active");
                    } else {
                        s_logger.info("\nNot managing the host " + vRouterIpAddress +" inactive");
                    }
                } else if (value instanceof VmPoweredOffEvent) {
                    printVmEvent(value);
                    try {
                        monitorTask.syncVmwareVirtualNetworks();
                    } catch (Exception e) {
                        String stackTrace = Throwables.getStackTraceAsString(e);
                        s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
                        s_logger.error(stackTrace);
                    }
                } else if (value instanceof VmMigratedEvent) {
                    printVmEvent(value);
                    try {
                        monitorTask.syncVmwareVirtualNetworks();
                    } catch (Exception e) {
                        String stackTrace = Throwables.getStackTraceAsString(e);
                        s_logger.error("Error while syncVmwareVirtualNetworks: " + e); 
                        s_logger.error(stackTrace);
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
            } else if (op == PropertyChangeOp.remove) {

            }
        }
        s_logger.info("+++++++++++++Update Processing Complete +++++++++++++++++++++");
    }


     public void startThread() {
        try
        {

            watchUpdates = new Thread(this);
            shouldRun = true;
            watchUpdates.start();
        } catch (Exception e)
        {
            System.out.println("Caught Exception : " + " Name : "
                    + e.getClass().getName() + " Message : " + e.getMessage()
                    + " Trace : ");
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
        }
    }

    public static void terminate() throws Exception {
        shouldRun = false;
        PropertyCollector propColl = serviceInstance.getPropertyCollector();
        propColl.cancelWaitForUpdates();
        cleanupEventFilters();
        serviceInstance.getServerConnection().logout();
        watchUpdates.stop();
    }

    public boolean initWithRetry() {
        while(true) {
            Cleanup();
            if(initialize() == false) {
                try {
                    Thread.sleep(2000); // 2 sec
                } catch (java.lang.InterruptedException e) {
                    String stackTrace = Throwables.getStackTraceAsString(e);
                    s_logger.error(stackTrace);
                }
                continue;
            }

            if(Initialize_data() == false) {
                try {
                    Thread.sleep(2000); // 2 sec
                } catch (java.lang.InterruptedException e) {
                    String stackTrace = Throwables.getStackTraceAsString(e);
                    s_logger.error(stackTrace);
                }
                continue;
            }

            try {
                createEventFilters();
            } catch (RemoteException e) {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                continue;
            }
            break;
        }

        return true;
    }

    public void run()
    {
        String version = "";
        try
        {
            do
            {
                // Check if VCenter Server is Alive
                if (monitorTask.getVCenterDB().isVCenterAlive() == false) {
                    s_logger.error("Problem with connection to vCenter-Server");
                    do {
                        s_logger.error("Waiting for Periodic Thread to Reconnect...");
                        Thread.sleep(2000);
                        if (monitorTask.VCenterNotifyForceRefresh) {
                            s_logger.info("Periodic thread reconnect successful.. initializing Notify Thread..");
                            initWithRetry();
                            monitorTask.VCenterNotifyForceRefresh = false;
                            version = "";
                            s_logger.info("reInit of Notify Thread Complete..");
                            break;
                        }
                    } while (true);
                }

                // Wait for updates from vCenterServer with timeout
                try
                {
                    WaitOptions wOpt = new WaitOptions();
                    wOpt.setMaxWaitSeconds(VCENTER_WAIT_FOR_UPDATES_TIMEOUT);
                    PropertyCollector propColl = serviceInstance.getPropertyCollector();
                    UpdateSet update = propColl.waitForUpdatesEx(version, wOpt);
                    if (update != null && update.getFilterSet() != null)
                    {
                        version = update.getVersion();
                        this.handleUpdate(update);
                    } else
                    {
                        // It could be b'cos of timeout. Go back and wait for update.
                    }
                } catch (Exception e)
                {
                    String stackTrace = Throwables.getStackTraceAsString(e);
                    s_logger.error(stackTrace);
                }
            } while (shouldRun);
        } catch (Exception e)
        {
            if (e instanceof RequestCanceled)
            {
                System.out.println("OK");
            } else
            {
                s_logger.error("Caught Exception : " + " Name : "
                        + e.getClass().getName() + " Message : "
                        + e.getMessage() + " Trace : ");
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
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

    public static void stopUpdates() {
        PropertyCollector propColl = serviceInstance.getPropertyCollector();
        propColl.stopUpdates();
    }

    private void createEventFilters() throws RemoteException  {
        cleanupEventFilters();

        createDvsEventFilter(contrailDVS);

        for (String hostName : monitorTask.getVCenterDB().esxiToVRouterIpMap.keySet()) {
            createHostEventFilter(hostName);
        }
    }

    private void createDvsEventFilter(VmwareDistributedVirtualSwitch dvs) 
            throws RemoteException {
        String[] dvsEventNames = {
                "DVPortgroupCreatedEvent", "DVPortgroupDestroyedEvent", 
                "DVPortgroupReconfiguredEvent", "DVPortgroupRenamedEvent", 
                "DvsPortCreatedEvent", "DvsPortDeletedEvent", "DvsPortJoinPortgroupEvent", 
                "DvsPortLeavePortgroupEvent"};

        watchManagedObjectEvents(dvs, dvsEventNames);
    }

    private void createHostEventFilter(String hostName) throws RemoteException {
        HostSystem host = monitorTask.getVCenterDB().getVmwareHost(hostName, 
                    _contrailDC, contrailDataCenterName);

        if (host == null) {
            s_logger.error("Cannot register for events for host " + hostName + ", not found.");
            return;
        }
        s_logger.info("Register for events on host " + hostName);

        String[] hostEventNames = {"HostConnectionLostEvent", "HostConnectedEvent", 
                "EnteredMaintenanceModeEvent", "ExitMaintenanceModeEvent", 
                "VmPoweredOnEvent", "VmPoweredOffEvent", "VmRenamedEvent", 
                "MigrationEvent","VmEmigratingEvent","VmMigratedEvent","VmBeingMigratedEvent",
                "VmBeingHotMigratedEvent"};

        watchManagedObjectEvents(host, hostEventNames);
    }

    private static void cleanupEventFilters() {
        for (Map.Entry<ManagedObject, PropertyFilter> entry: watchedFilters.entrySet()) {
            try 
            {
                PropertyFilter pf = entry.getValue();
                pf.destroyPropertyFilter();
            } catch (RemoteException e) 
            {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
            }
        }
        watchedFilters.clear();
    }

    private void watchManagedObjectEvents(ManagedObject mos, String[] events)
    {
        if (mos == null || events == null) {
            s_logger.error("Null arguments in watchManagedObjectEvents");
            return;
        }
        try
        {
            EventHistoryCollector collector = 
                    createEventHistoryCollector(mos, events);

            PropertyFilterSpec eventFilterSpec = createEventFilterSpec(collector);
            PropertyCollector propColl = serviceInstance.getPropertyCollector();

            PropertyFilter propFilter = propColl.createFilter(eventFilterSpec, true); 
                                //report only nesting properties, not enclosing ones.
            if (propFilter != null) {
                watchedFilters.put(mos, propFilter);
            } else {
                s_logger.error("Cannot create event filter for managed object ");
            }
        } catch(Exception e)
        {
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
            throw new RuntimeException(e);
        }
    }
    
    private void unwatchManagedObjectEvents(ManagedObject mos)
    {
        if (mos == null) {
            s_logger.error("Null arguments in unwatchManagedObjectEvents");
            return;
        }

        if (watchedFilters.containsKey(mos)) {
            try 
            {
                PropertyFilter pf = watchedFilters.remove(mos);
                if (pf != null) {
                    pf.destroyPropertyFilter();
                }
            } catch (RemoteException e) 
            {
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
            }
        }
    }
}
