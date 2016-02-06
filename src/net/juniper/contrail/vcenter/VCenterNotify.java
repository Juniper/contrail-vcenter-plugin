/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */
package net.juniper.contrail.vcenter;

import java.net.URL;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.ArrayOfEvent;
import com.vmware.vim25.ArrayOfGuestNicInfo;
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
import com.vmware.vim25.VirtualMachineToolsRunningStatus;
import com.vmware.vim25.VmEvent;
import com.vmware.vim25.VmMacChangedEvent;
import com.vmware.vim25.VmPoweredOnEvent;
import com.vmware.vim25.VmReconfiguredEvent;
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
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.PropertyCollector;
import com.vmware.vim25.mo.PropertyFilter;
import com.vmware.vim25.mo.ServiceInstance;
import net.juniper.contrail.watchdog.TaskWatchDog;
import com.vmware.vim25.VmMigratedEvent;
import com.vmware.vim25.EnteredMaintenanceModeEvent;
import com.vmware.vim25.ExitMaintenanceModeEvent;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.HostConnectedEvent;
import com.vmware.vim25.HostConnectionLostEvent;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;
import com.vmware.vim25.mo.HostSystem;
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
    static VCenterMonitorTask monitorTask = null;
    static volatile VCenterDB vcenterDB;
    static volatile VncDB vncDB;
    private boolean AddPortSyncAtPluginStart = true;
    private boolean VncDBInitComplete = false;
    private boolean VcenterDBInitComplete = false;
    public boolean VCenterNotifyForceRefresh = false;
    static volatile boolean syncNeeded = true;

    private final static String[] guestProps = { "guest.toolsRunningStatus", "guest.net" };
    private final static String[] ipPoolProps = { "summary.ipPoolId" };
    private static Map<String, VirtualMachineInfo> watchedVMs
                = new HashMap<String, VirtualMachineInfo>();
    private static Map<String, VirtualNetworkInfo> watchedVNs
                = new HashMap<String, VirtualNetworkInfo>();
    private static Map<ManagedObject, PropertyFilter> watchedFilters
                = new HashMap<ManagedObject, PropertyFilter>();

    private static Boolean shouldRun;
    private static Thread watchUpdates = null;

    public VCenterNotify(
            String vcenterUrl, String vcenterUsername,
            String vcenterPassword, String dcName,
            String dvsName, String ipFabricPgName,
            String _apiServerAddress, int _apiServerPort,
            String _username, String _password,
            String _tenant,
            String _authtype, String _authurl, Mode mode)
    {
        vcenterDB = new VCenterDB(vcenterUrl, vcenterUsername, vcenterPassword,
                dcName, dvsName, ipFabricPgName, mode);

        switch (mode) {
        case VCENTER_ONLY:
            vncDB = new VncDB(_apiServerAddress, _apiServerPort, mode);
            break;
        case VCENTER_AS_COMPUTE:
            vncDB = new VncDB(_apiServerAddress, _apiServerPort, _username, _password,
                    _tenant,
                    _authtype, _authurl, mode);
            break;
        default:
            vncDB = new VncDB(_apiServerAddress, _apiServerPort, mode);
        }
    }

    public static VCenterDB getVcenterDB() {
        return vcenterDB;
    }

    public boolean getVCenterNotifyForceRefresh() {
        return VCenterNotifyForceRefresh;
    }

    public void setVCenterNotifyForceRefresh(boolean _VCenterNotifyForceRefresh) {
        VCenterNotifyForceRefresh = _VCenterNotifyForceRefresh;
    }

    public void setAddPortSyncAtPluginStart(boolean _AddPortSyncAtPluginStart)
    {
        AddPortSyncAtPluginStart = _AddPortSyncAtPluginStart;
    }

    public boolean getAddPortSyncAtPluginStart()
    {
        return AddPortSyncAtPluginStart;
    }

    private void cleanupEventFilters() {
        for (Map.Entry<ManagedObject, PropertyFilter> entry: watchedFilters.entrySet())
        try
        {
            PropertyFilter pf = entry.getValue();
            pf.destroyPropertyFilter();
        } catch (RemoteException e)
        {
            // it is ok, we can receive exception if vcenter was restarted
        }
        watchedFilters.clear();
        watchedVMs.clear();
        watchedVNs.clear();
    }

    public static void watchVm(VirtualMachineInfo vmInfo) {
        if (vmInfo.vm == null) {
            return;
        }
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE
            || watchedVMs.containsKey(vmInfo.vm.getMOR().getVal())) {
            return;
        }
        watchedVMs.put(vmInfo.vm.getMOR().getVal(), vmInfo);
        watchManagedObject(vmInfo.vm, guestProps);
    }

    public static void unwatchVm(VirtualMachineInfo vmInfo) {
        if (vmInfo.vm == null) {
            return;
        }
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE
            || !watchedVMs.containsKey(vmInfo.vm.getMOR().getVal())) {
            return;
        }
        watchedVMs.remove(vmInfo.vm.getMOR().getVal());
        unwatchManagedObject(vmInfo.vm);
    }

    public static void watchVn(VirtualNetworkInfo vnInfo) {
        if (vnInfo.dpg == null) {
            return;
        }
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE
                || watchedVNs.containsKey(vnInfo.dpg.getMOR().getVal())) {
            return;
        }
        watchedVNs.put(vnInfo.dpg.getMOR().getVal(), vnInfo);
        watchManagedObject(vnInfo.dpg, ipPoolProps);
    }

    public static void unwatchVn(VirtualNetworkInfo vnInfo) {
        if (vnInfo.dpg == null) {
            return;
        }
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE
            || !watchedVNs.containsKey(vnInfo.dpg.getMOR().getVal())) {
            return;
        }
        watchedVNs.remove(vnInfo.dpg.getMOR().getVal());
        unwatchManagedObject(vnInfo.dpg);
    }

    private static void watchManagedObject(ManagedObject mos, String[] propNames)
    {
        PropertyFilterSpec pfs = new PropertyFilterSpec();

        ObjectSpec[] oss = new ObjectSpec[1];
        oss[0] = new ObjectSpec();
        oss[0].setObj(mos.getMOR());
        pfs.setObjectSet(oss);

        PropertySpec ps = new PropertySpec();
        ps.setType(mos.getMOR().getType());
        ps.setPathSet(propNames);
        pfs.setPropSet(new PropertySpec[] { ps });

        try
        {
            PropertyCollector propColl = vcenterDB.getServiceInstance().getPropertyCollector();
            PropertyFilter pf = propColl.createFilter(pfs, true); //report only nesting properties, not enclosing ones.
            watchedFilters.put(mos, pf);
        } catch(RemoteException re)
        {
            throw new RuntimeException(re);
        }

    }

    private static void unwatchManagedObject(ManagedObject mos)
    {
        if (watchedFilters.containsKey(mos)) {
            try
            {
                PropertyFilter pf = watchedFilters.remove(mos);
                pf.destroyPropertyFilter();
            } catch (RemoteException e)
            {
                e.printStackTrace();
            }
        }
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

        // we are only interested in getting events for the VM.
        // Add as many events you want to track relating to vm.
        // Refer to API Data Object vmEvent and see the extends class list for
        // elaborate list of vmEvents

        eventFilter.setType(events);

        // create the EventHistoryCollector to monitor events for a VM
        // and get the ManagedObjectReference of the EventHistoryCollector
        // returned

        EventManager eventManager = vcenterDB.getServiceInstance().getEventManager();

        if (eventManager != null) {
            return eventManager.createCollectorForEvents(eventFilter);
        }
        return null;
    }

    private PropertyFilterSpec createEventFilterSpec(EventHistoryCollector collector)
            throws Exception
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
        // and no "traversal" further, so, no SelectionSpec is specified
        objSpec.setSelectSet(new SelectionSpec[] {});

        // ObjectSpecs are wrapped in an ObjectSpec array
        ObjectSpec[] objSpecAry = new ObjectSpec[] { objSpec };

        PropertyFilterSpec spec = new PropertyFilterSpec();
        spec.setPropSet(propSpecAry);
        spec.setObjectSet(objSpecAry);
        return spec;
    }

    private void handleUpdate(UpdateSet update) throws Exception
    {
        ObjectUpdate[] vmUpdates;
        PropertyFilterUpdate[] pfus = update.getFilterSet();

        if (pfus == null) {
            return;
        }
        for (int pfui = 0; pfui < pfus.length; pfui++)
        {
            vmUpdates = pfus[pfui].getObjectSet();

            if (vmUpdates == null) {
                continue;
            }
            for (ObjectUpdate vmi : vmUpdates)
            {
                handleChanges(vmi);
            }
        }
    }

    void handleChanges(ObjectUpdate oUpdate) throws Exception
    {
        s_logger.info("+++++++++++++Received vcenter update of type "
                        + oUpdate.getKind() + "+++++++++++++");

        PropertyChange[] changes = oUpdate.getChangeSet();
        if (changes == null) {
            return;
        }

        String toolsRunningStatus = null;
        GuestNicInfo[] nics = null;
        for (int pci = 0; pci < changes.length; ++pci)
        {
            if (changes[pci] == null) {
                continue;
            }
            Object value = changes[pci].getVal();
            String propName = changes[pci].getName();
            PropertyChangeOp op = changes[pci].getOp();
            if (op!= PropertyChangeOp.remove) {
                if (propName.equals("summary.ipPoolId")) {
                    Integer newPoolId = (Integer)value;
                    ManagedObjectReference mor = oUpdate.getObj();
                    if (watchedVNs.containsKey(mor.getVal())) {
                        VirtualNetworkInfo vnInfo = watchedVNs.get(mor.getVal());
                        Integer oldPoolId = vnInfo.getIpPoolId();
                        if ((oldPoolId == null && newPoolId == null)
                                || (oldPoolId != null && newPoolId != null
                                        && oldPoolId.equals(newPoolId))) {
                            continue;
                        }
                        vnInfo.setIpPoolId(newPoolId, vcenterDB);
                        s_logger.info("IP Pool ID for " + vnInfo + " set to " + newPoolId);
                        vncDB.updateVirtualNetwork(vnInfo);
                    }
                } else if (propName.equals("guest.toolsRunningStatus")) {
                    toolsRunningStatus = (String)value;
                } else if (value instanceof ArrayOfEvent) {
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
                } else if ((value instanceof EnteredMaintenanceModeEvent) || (value instanceof HostConnectionLostEvent)) {
                    Event anEvent = (Event) value;
                    String vRouterIpAddress = vcenterDB.esxiToVRouterIpMap.get(anEvent.getHost().getName());
                    if (vRouterIpAddress != null) {
                        VCenterDB.vRouterActiveMap.put(vRouterIpAddress, false);
                        s_logger.info("\nEntering maintenance mode. Marking the host " + vRouterIpAddress +" inactive");
                    } else {
                        s_logger.info("\nNot managing the host " + vRouterIpAddress +" inactive");
                    }
                } else if ((value instanceof ExitMaintenanceModeEvent) || (value instanceof HostConnectedEvent)) {
                    Event anEvent = (Event) value;
                    String vRouterIpAddress = vcenterDB.esxiToVRouterIpMap.get(anEvent.getHost().getName());
                    if (vRouterIpAddress != null) {
                        VCenterDB.vRouterActiveMap.put(vRouterIpAddress, true);
                        s_logger.info("\nExit maintenance mode. Marking the host " + vRouterIpAddress +" active");
                    } else {
                        s_logger.info("\nNot managing the host " + vRouterIpAddress +" inactive");
                    }
                } else if (value instanceof ArrayOfGuestNicInfo) {
                    s_logger.info("Received update array of GuestNics");
                    ArrayOfGuestNicInfo aog = (ArrayOfGuestNicInfo) value;
                    nics = aog.getGuestNicInfo();

                } else if (value instanceof Event) {
                    VCenterEventHandler handler = new VCenterEventHandler(
                            (Event) value, vcenterDB, vncDB);
                    handler.handle();
                } else {
                    if (value != null) {
                        s_logger.info("\n Received unhandled property");
                    } else {
                        s_logger.info("\n Received unhandled null value");
                    }
                }
            } else if (op == PropertyChangeOp.remove) {

            }
        }

        if (toolsRunningStatus != null || nics != null) {
            ManagedObjectReference mor = oUpdate.getObj();
            if (watchedVMs.containsKey(mor.getVal())) {
                VirtualMachineInfo vmInfo = watchedVMs.get(mor.getVal());
                if (toolsRunningStatus != null) {
                    vmInfo.setToolsRunningStatus(toolsRunningStatus);
                }
                if (vmInfo.getToolsRunningStatus().equals(
                        VirtualMachineToolsRunningStatus.guestToolsRunning.toString())) {
                    vmInfo.updatedGuestNics(vncDB);
                }
            }
        }
        s_logger.info("+++++++++++++Update Processing Complete +++++++++++++++++++++");
    }

    public void start() {
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
            e.printStackTrace();
        }
    }

    public void terminate() throws Exception {
        shouldRun = false;
        PropertyCollector propColl = vcenterDB.getServiceInstance().getPropertyCollector();
        propColl.cancelWaitForUpdates();
        cleanupEventFilters();
        vcenterDB.getServiceInstance().getServerConnection().logout();
        watchUpdates.stop();
    }

    private void connect2vnc() {
        TaskWatchDog.startMonitoring(this, "Init Vnc",
                300000, TimeUnit.MILLISECONDS);
        try {
            if (vncDB.Initialize() == true) {
                VncDBInitComplete = true;
            }
        } catch (Exception e) {
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error("Error while initializing Vnc connection: " + e);
            s_logger.error(stackTrace);
            e.printStackTrace();
        }
        TaskWatchDog.stopMonitoring(this);
    }

    private void connect2vcenter() {
        TaskWatchDog.startMonitoring(this, "Init VCenter",
                300000, TimeUnit.MILLISECONDS);
        try {
            if (vcenterDB.connect() == true) {
                createEventFilters();
                VcenterDBInitComplete = true;
            }
        } catch (Exception e) {
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error("Error while initializing VCenter connection: " + e);
            s_logger.error(stackTrace);
            e.printStackTrace();
        }
        TaskWatchDog.stopMonitoring(this);
    }

    @Override
    public void run()
    {
        String version = "";
        try
        {
            do
            {
                //check if you are the master from time to time
                //sometimes things dont go as planned
                if (VCenterMonitor.isZookeeperLeader() == false) {
                    s_logger.debug("Lost zookeeper leadership. Restarting myself\n");
                    System.exit(0);
                }

                if (VncDBInitComplete == false) {
                    connect2vnc();
                }
                if (VcenterDBInitComplete == false) {
                    connect2vcenter();
                }

                // Perform sync between VNC and VCenter DBs.
                if (getAddPortSyncAtPluginStart() == true || syncNeeded) {
                    while (vncDB.isVncApiServerAlive() == false) {
                        s_logger.error("Waiting for API server before starting sync");
                        Thread.sleep(5000);
                    }

                    TaskWatchDog.startMonitoring(this, "Sync",
                            300000, TimeUnit.MILLISECONDS);

                    // When syncVirtualNetworks is run the first time, it also does
                    // addPort to vrouter agent for existing VMIs.
                    // Clear the flag  on first run of syncVirtualNetworks.
                    try {
                        vcenterDB.setReadTimeout(VCenterDB.VCENTER_READ_TIMEOUT);
                        MainDB.sync(vcenterDB, vncDB, VCenterMonitor.mode);
                        vcenterDB.setReadTimeout(0);
                        syncNeeded = false;
                        setAddPortSyncAtPluginStart(false);
                    } catch (Exception e) {
                        String stackTrace = Throwables.getStackTraceAsString(e);
                        s_logger.error("Error in sync: " + e);
                        s_logger.error(stackTrace);
                        e.printStackTrace();
                        if (stackTrace.contains("java.net.ConnectException: Connection refused") ||
                            stackTrace.contains("java.rmi.RemoteException: VI SDK invoke"))   {
                                //Remote Exception. Some issue with connection to vcenter-server
                                // Exception on accessing remote objects.
                                // Try to reinitialize the VCenter connection.
                                //For some reason RemoteException not thrown
                                s_logger.error("Problem with connection to vCenter-Server");
                                s_logger.error("Restart connection and reSync");
                                connect2vcenter();
                                version = "";
                        }
                    }
                    TaskWatchDog.stopMonitoring(this);
                }

                try
                {
                    PropertyCollector propColl = vcenterDB.getServiceInstance().getPropertyCollector();
                    UpdateSet update = propColl.waitForUpdates(version);
                    if (update != null && update.getFilterSet() != null)
                    {

                        version = update.getVersion();

                        this.handleUpdate(update);

                    } else
                    {
                        s_logger.error("No update is present!");
                    }
                } catch (Exception e)
                {
                    syncNeeded = true;
                    s_logger.error("Error in event handling, resync needed");
                    String stackTrace = Throwables.getStackTraceAsString(e);
                    s_logger.error(stackTrace);
                    e.printStackTrace();
                    if (stackTrace.contains("java.net.ConnectException: Connection refused") ||
                        stackTrace.contains("java.rmi.RemoteException: VI SDK invoke"))   {
                            //Remote Exception. Some issue with connection to vcenter-server
                            // Exception on accessing remote objects.
                            // Try to reinitialize the VCenter connection.
                            //For some reason RemoteException not thrown
                            s_logger.error("Problem with connection to vCenter-Server");
                            s_logger.error("Restart connection and reSync");
                            connect2vcenter();
                            version = "";
                    }
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

    public static void stopUpdates() {
        PropertyCollector propColl = vcenterDB.getServiceInstance().getPropertyCollector();
        propColl.stopUpdates();
    }

    public static VncDB getVncDB() {
        return vncDB;
    }

    private void createEventFilters() throws RemoteException  {
        cleanupEventFilters();

        createDvsEventFilter(vcenterDB.getContrailDvs());

        for (String hostName : vcenterDB.esxiToVRouterIpMap.keySet()) {
            createHostEventFilter(hostName);
        }
    }

    private void createDvsEventFilter(VmwareDistributedVirtualSwitch dvs)
            throws RemoteException {
        String[] dvsEventNames = {
            // DV Port group events
            // DV port create
            "DVPortgroupCreatedEvent",
            // DV port modify
            "DVPortgroupReconfiguredEvent",
            "DVPortgroupRenamedEvent",
            // DV port delete
            "DVPortgroupDestroyedEvent"
        };

        watchManagedObjectEvents(dvs, dvsEventNames);
    }

    private void createHostEventFilter(String hostName) throws RemoteException {
        HostSystem host = vcenterDB.getVmwareHost(hostName,
                    vcenterDB.contrailDC, vcenterDB.contrailDataCenterName);

        if (host == null) {
            s_logger.error("Cannot register for events for host " + hostName + ", not found.");
            return;
        }
        s_logger.info("Register for events on host " + hostName);

        String[] hostEventNames = {
            // Host events
            "HostConnectionLostEvent",
            "HostConnectedEvent",
            "EnteredMaintenanceModeEvent",
            "ExitMaintenanceModeEvent",

            // VM events
            // VM create events
            "VmBeingCreated",
            "VmCreatedEvent",
            "VmClonedEvent",
            "VmCloneEvent",
            "VmDeployedEvent",
            // VM modify events
            "VmPoweredOnEvent",
            "VmPoweredOffEvent",
            "VmRenamedEvent",
            "VmMacChangedEvent",
            "VmMacAssignedEvent",
            "VmReconfiguredEvent",
            "VmEmigratingEvent",
            "VmMigratedEvent",
            "VmBeingMigratedEvent",
            "VmBeingHotMigratedEvent",
            // VM delete events
            "VmRemovedEvent"
        };

        watchManagedObjectEvents(host, hostEventNames);
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
            PropertyCollector propColl = vcenterDB.getServiceInstance().getPropertyCollector();

            PropertyFilter propFilter = propColl.createFilter(eventFilterSpec, true);
                                //report only nesting properties, not enclosing ones.
            if (propFilter != null) {
                watchedFilters.put(mos, propFilter);
            } else {
                s_logger.error("Cannot create event filter for managed object ");
            }
        } catch(Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
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
                e.printStackTrace();
            }
        }
    }
}
