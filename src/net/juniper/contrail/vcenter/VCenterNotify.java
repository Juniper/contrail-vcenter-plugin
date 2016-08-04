/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */
package net.juniper.contrail.vcenter;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.vmware.vim25.ArrayOfEvent;
import com.vmware.vim25.ArrayOfGuestNicInfo;
import com.vmware.vim25.Event;
import com.vmware.vim25.EventFilterSpec;
import com.vmware.vim25.EventFilterSpecByEntity;
import com.vmware.vim25.EventFilterSpecByTime;
import com.vmware.vim25.EventFilterSpecRecursionOption;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.PropertyChange;
import com.vmware.vim25.PropertyChangeOp;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertyFilterUpdate;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RequestCanceled;
import com.vmware.vim25.SelectionSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VirtualMachineToolsRunningStatus;
import com.vmware.vim25.WaitOptions;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.EventHistoryCollector;
import com.vmware.vim25.mo.EventManager;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.PropertyCollector;
import com.vmware.vim25.mo.PropertyFilter;
import net.juniper.contrail.watchdog.TaskWatchDog;
import com.vmware.vim25.EnteredMaintenanceModeEvent;
import com.vmware.vim25.ExitMaintenanceModeEvent;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.HostConnectedEvent;
import com.vmware.vim25.HostConnectionLostEvent;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;
import com.google.common.base.Throwables;
import com.vmware.vim25.InvalidState;
import org.apache.log4j.Logger;

/**
 * @author Sachchidanand Vaidya
 *
 */
public class VCenterNotify implements Runnable
{

    private static final Logger s_logger =
            Logger.getLogger(VCenterNotify.class);
    static volatile VCenterDB vcenterDB = null;
    static volatile VncDB vncDB;
    private VCenterEventHandler eventHandler;
    private static boolean vCenterConnected = false;
    private Calendar vcenterConnectedTime;
    private final static String[] guestProps = { "guest.toolsRunningStatus", "guest.net" };
    private final static String[] ipPoolProps = { "summary.ipPoolId" };
    private static Map<String, VirtualMachineInfo> watchedVMs
                = new HashMap<String, VirtualMachineInfo>();
    private static Map<String, VirtualNetworkInfo> watchedVNs
                = new HashMap<String, VirtualNetworkInfo>();
    private static Map<ManagedObject, PropertyFilter> watchedFilters
                = new HashMap<ManagedObject, PropertyFilter>();
    private static List<EventHistoryCollector> collectors = new ArrayList<EventHistoryCollector>();


    private static Boolean shouldRun;
    private static Thread watchUpdates = null;

    private static final int VCENTER_READ_TIMEOUT = 30000; //30 sec
    private static final int VCENTER_CONNECT_TIMEOUT = 30000; //30 sec
    private static final int VCENTER_WAIT_FOR_UPDATES_READ_TIMEOUT = 180000; // 3 minutes
    private static final int VCENTER_WAIT_FOR_UPDATES_SERVER_TIMEOUT = 120; // 2 minutes
    private static final int VCENTER_TIMEOUT_DELTA = 10000; // 10 seconds

    private static PropertyCollector propColl;
    private static EventManager eventManager;

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

        eventHandler = new VCenterEventHandler(vcenterDB, vncDB);
    }

    public static VCenterDB getVcenterDB() {
        return vcenterDB;
    }

    public static boolean getVCenterConnected() {
        return vCenterConnected;
    }

    private void cleanupEventFilters() {
        for (Map.Entry<ManagedObject, PropertyFilter> entry: watchedFilters.entrySet()) {
            try
            {
                PropertyFilter pf = entry.getValue();
                pf.destroyPropertyFilter();
            } catch (RemoteException e)
            {
                // it is ok, we can receive exception if vcenter was restarted
            }
        }
        watchedFilters.clear();
        watchedVMs.clear();
        watchedVNs.clear();

        for(EventHistoryCollector collector: collectors) {
            try {
                collector.destroyCollector();
            } catch (RemoteException e)
            {
                // it is ok, we can receive exception if vcenter was restarted
            }
        }
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
            PropertyFilter pf = propColl.createFilter(pfs, true); //report only nesting properties, not enclosing ones.
            if (pf != null) {
                watchedFilters.put(mos, pf);
            }
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
                s_logger.error("Cannot unwatchManagedObject " + mos + " due to exception " + e);
                s_logger.error(Throwables.getStackTraceAsString(e));
            }
        }
    }

    private EventHistoryCollector createEventHistoryCollector(ManagedObject mo,
            String[] events) throws InvalidState, RuntimeFault, RemoteException
    {
        if (eventManager == null) {
            s_logger.error("Cannot create EventHistoryCollector, eventManager is null");
            return null;
        }
        EventFilterSpec eventFilterSpec = new EventFilterSpec();
        eventFilterSpec.setType(events);

        // Create an Entity Event Filter Spec to
        // specify the MoRef of the MO to be get events filtered for
        EventFilterSpecByEntity entitySpec = new EventFilterSpecByEntity();
        entitySpec.setEntity(mo.getMOR());
        entitySpec.setRecursion(EventFilterSpecRecursionOption.children);
        // set the entity spec in the EventFilter
        eventFilterSpec.setEntity(entitySpec);

        if (vcenterConnectedTime != null) {
            EventFilterSpecByTime timeSpec = new EventFilterSpecByTime();
            timeSpec.setBeginTime(vcenterConnectedTime);
            // set the time spec in the EventFilter
            eventFilterSpec.setTime(timeSpec);
        }

        // create the EventHistoryCollector to monitor events for a VM
        // and get the ManagedObjectReference of the EventHistoryCollector
        // returned

        EventHistoryCollector collector = eventManager.createCollectorForEvents(eventFilterSpec);
        collector.setCollectorPageSize(1000);
        collectors.add(collector);

        return collector;
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
                            s_logger.info("Done processing property update, nothing changed");
                            continue;
                        }
                        VirtualNetworkInfo newVnInfo = new VirtualNetworkInfo(vnInfo);

                        newVnInfo.setIpPoolId(newPoolId, vcenterDB);
                        if ((newVnInfo.getIpPoolId() != null) &&
                               !newVnInfo.getIpPoolId().equals(oldPoolId)) {
                            s_logger.info("IP Pool ID for " + newVnInfo + " set to "
                               + newVnInfo.getIpPoolId());
                            vnInfo.update(newVnInfo, vncDB);
                        }
                    }
                } else if (propName.equals("guest.toolsRunningStatus")) {
                    s_logger.info("Received guest.toolsRunningStatus property change");
                    toolsRunningStatus = (String)value;
                } else if (value instanceof ArrayOfEvent) {
                    s_logger.info("Received ArrayOfEvent");
                    ArrayOfEvent aoe = (ArrayOfEvent) value;
                    Event[] evts = aoe.getEvent();
                    if (evts == null) {
                        s_logger.info("Done processing array of events, null event received");
                        continue;
                    }
                    for (int evtID = 0; evtID < evts.length; ++evtID)
                    {
                        Event anEvent = evts[evtID];
                        if (anEvent == null) {
                            continue;
                        }
                        printEvent(anEvent);
                        eventHandler.handle(anEvent);
                    }
                    s_logger.info("Done processing array of events");
                } else if ((value instanceof EnteredMaintenanceModeEvent) || (value instanceof HostConnectionLostEvent)) {
                    Event anEvent = (Event) value;
                    printEvent(anEvent);
                    String hostName = anEvent.getHost().getName();
                    String vRouterIpAddress = vcenterDB.esxiToVRouterIpMap.get(hostName);
                    if (vRouterIpAddress != null) {
                        VCenterDB.vRouterActiveMap.put(vRouterIpAddress, false);
                        s_logger.info("Entering maintenance mode. Marking the host " + hostName +
                                " inactive. VRouter ip address is " + anEvent.getHost().getName());
                    } else {
                        s_logger.info("Skipping event for unmanaged host " + hostName);
                    }
                    s_logger.info("Done processing event " + anEvent.getFullFormattedMessage());
                } else if ((value instanceof ExitMaintenanceModeEvent) || (value instanceof HostConnectedEvent)) {
                    Event anEvent = (Event) value;
                    printEvent(anEvent);
                    String hostName = anEvent.getHost().getName();
                    String vRouterIpAddress = vcenterDB.esxiToVRouterIpMap.get(hostName);
                    if (vRouterIpAddress != null) {
                        VCenterDB.vRouterActiveMap.put(vRouterIpAddress, true);
                        s_logger.info("\nExit maintenance mode. Marking the host " + hostName
                                + " active. VRouter IP address is " +  vRouterIpAddress);
                    } else {
                        s_logger.info("Skipping event for unmanaged host " + hostName);
                    }
                    s_logger.info("Done processing event " + anEvent.getFullFormattedMessage());
                } else if (value instanceof ArrayOfGuestNicInfo) {
                    s_logger.info("Received event update array of GuestNics");
                    ArrayOfGuestNicInfo aog = (ArrayOfGuestNicInfo) value;
                    nics = aog.getGuestNicInfo();

                } else if (value instanceof Event) {
                    Event anEvent = (Event)value;
                    printEvent(anEvent);
                    eventHandler.handle(anEvent);
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
                    vmInfo.updatedGuestNics(nics, vncDB);
                }
            }
            s_logger.info("Done processing property update");
        }
    }

    public void start() {
        try
        {
            watchUpdates = new Thread(this);
            shouldRun = true;
            watchUpdates.start();
        } catch (Exception e)
        {
            s_logger.error("Caught Exception : " + " Name : "
                    + e.getClass().getName() + " Message : " + e.getMessage()
                    + " Trace : ");
            s_logger.error(Throwables.getStackTraceAsString(e));
        }
    }

    @SuppressWarnings("deprecation")
    public void terminate() throws Exception {
        shouldRun = false;
        propColl.cancelWaitForUpdates();
        cleanupEventFilters();
        vcenterDB.getServiceInstance().getServerConnection().logout();
        watchUpdates.stop();
    }

    private boolean connect2vnc() {
        TaskWatchDog.startMonitoring(this, "Init Vnc",
                300000, TimeUnit.MILLISECONDS);
        try {
            s_logger.info("Connecting to the API server ...");
            while (vncDB.Initialize() != true) {
                s_logger.info("Waiting for API server ...");
                Thread.sleep(5000);
            }
            s_logger.info("Connected to the API server ...");
        } catch (Exception e) {
            s_logger.error("Error while initializing connection with the API server: " + e);
            s_logger.error(Throwables.getStackTraceAsString(e));
            TaskWatchDog.stopMonitoring(this);
            return false;
        }
        TaskWatchDog.stopMonitoring(this);
        return true;
    }

    private void connect2vcenter() {
        TaskWatchDog.startMonitoring(this, "Init VCenter",
                300000, TimeUnit.MILLISECONDS);
        try {
            if (vcenterDB.connect(VCENTER_CONNECT_TIMEOUT) == true) {
                vCenterConnected = true;
                vcenterConnectedTime = vcenterDB.getLastTimeSeenAlive();

                // cache PropertyCollector and EventManager for faster processing
                propColl = vcenterDB.getServiceInstance().getPropertyCollector();

                eventManager = vcenterDB.getServiceInstance().getEventManager();

                createEventFilters();
            }
        } catch (Exception e) {
            s_logger.error("Error while initializing VCenter connection: ");
            s_logger.error(Throwables.getStackTraceAsString(e));
        }
        TaskWatchDog.stopMonitoring(this);
    }

    @Override
    public void run()
    {
        try
        {
            if (connect2vnc() == false) {
                return;
            }

            boolean syncNeeded = true;
            for (String version = "" ; shouldRun; )
            {
                //check if you are the master from time to time
                //sometimes things don't go as planned
                if (VCenterMonitor.isZookeeperLeader() == false) {
                    s_logger.warn("Lost zookeeper leadership. Restarting myself\n");
                    System.exit(0);
                }

                if (vCenterConnected == false) {
                    connect2vcenter();
                    version = "";
                    syncNeeded = true;
                }

                while (vncDB.isVncApiServerAlive() == false) {
                    s_logger.info("Waiting for API server... ");
                    Thread.sleep(5000);
                }

                // Perform sync between VNC and VCenter DBs.
                if (syncNeeded) {
                    s_logger.info("+++++++++++++ Start syncing  +++++++++++++++++++++");

                    TaskWatchDog.startMonitoring(this, "Sync",
                            300000, TimeUnit.MILLISECONDS);

                    // When sync is run, it also does
                    // addPort to vrouter agent for existing VMIs.
                    try {
                        vcenterDB.setReadTimeout(VCENTER_READ_TIMEOUT);
                        MainDB.sync(vcenterDB, vncDB, VCenterMonitor.mode);
                        syncNeeded = false;
                    } catch (Exception e) {
                        vCenterConnected = false;
                        s_logger.error("Error in sync: " + e);
                        s_logger.error(Throwables.getStackTraceAsString(e));
                        TaskWatchDog.stopMonitoring(this);
                        continue;
                    }

                    TaskWatchDog.stopMonitoring(this);

                    s_logger.info("+++++++++++++ Done syncing +++++++++++++++++++++");
                }

                s_logger.info("+++++++++++++ Waiting for events +++++++++++++++++++++");
                try
                {
                    WaitOptions wOpt = new WaitOptions();
                    wOpt.setMaxWaitSeconds(VCENTER_WAIT_FOR_UPDATES_SERVER_TIMEOUT);
                    for ( ; ; ) {
                        vcenterDB.setReadTimeout(VCENTER_WAIT_FOR_UPDATES_READ_TIMEOUT);
                        TaskWatchDog.startMonitoring(this, "WaitForUpdatesEx",
                                VCENTER_WAIT_FOR_UPDATES_READ_TIMEOUT + VCENTER_TIMEOUT_DELTA,
                                TimeUnit.MILLISECONDS);
                        UpdateSet update = propColl.waitForUpdatesEx(version, wOpt);
                        TaskWatchDog.stopMonitoring(this);
                        if (update != null && update.getFilterSet() != null)
                        {
                            version = update.getVersion();

                            this.handleUpdate(update);

                        } else
                        {
                            vcenterDB.setReadTimeout(VCENTER_READ_TIMEOUT);
                            TaskWatchDog.startMonitoring(this, "AlivenessCheck",
                                    VCENTER_READ_TIMEOUT + VCENTER_TIMEOUT_DELTA,
                                    TimeUnit.MILLISECONDS);
                            if (vcenterDB.isAlive() == false) {
                                s_logger.error("Vcenter connection lost, reconnect and resync needed");
                                vCenterConnected = false;
                                TaskWatchDog.stopMonitoring(this);
                                break;
                            }
                            TaskWatchDog.stopMonitoring(this);
                        }
                    }
                } catch (RemoteException e)  {
                    vCenterConnected = false;
                    s_logger.info("Vcenter disconnected, reconnect and resync needed: " + e);
                    s_logger.info(Throwables.getStackTraceAsString(e));
                } catch (Exception e) {
                    vCenterConnected = false;
                    s_logger.error("Error in event handling, reconnect and resync needed");
                    s_logger.error(Throwables.getStackTraceAsString(e));
                }
            }
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
                s_logger.error(Throwables.getStackTraceAsString(e));
            }
        }
    }

    public static void printEvent(Event event) {
        s_logger.info("\n----------" + "\n Event ID: "
              + event.getKey() + "\n Event: "
              + event.getClass().getName()
              + ", happened on: "
              + event.getCreatedTime().getTime()
              + "\n FullFormattedMessage: "
              + event.getFullFormattedMessage()
              + "\n----------\n");
    }

    public static VncDB getVncDB() {
        return vncDB;
    }

    private void createEventFilters() throws RemoteException  {
        cleanupEventFilters();

        createDvsEventFilter(vcenterDB.getContrailDvs());

        createDatacenterEventFilter(vcenterDB.getDatacenter());
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

    private void createDatacenterEventFilter(Datacenter dc) throws RemoteException {

        if (dc == null) {
            s_logger.error("Cannot register for events on null datacenter  ");
            return;
        }
        s_logger.info("Register for events on datacenter ");

        String[] eventNames = {
            // Host events
            "HostConnectionLostEvent",
            "HostConnectedEvent",
            "EnteredMaintenanceModeEvent",
            "ExitMaintenanceModeEvent",

            // VM events
            // VM create events
             "VmCreatedEvent",
            "VmClonedEvent",
            "VmDeployedEvent",
            // VM modify events
            "VmPoweredOnEvent",
            "DrsVmPoweredOnEvent",
            "VmPoweredOffEvent",
            "VmSuspendedEvent",
            "VmRenamedEvent",
            "VmMacChangedEvent",
            "VmMacAssignedEvent",
            "VmReconfiguredEvent",
            // VM Migration events
            "DrsVmMigratedEvent",
            "VmMigratedEvent",
            // VM delete events
            "VmRemovedEvent"
        };

        watchManagedObjectEvents(dc, eventNames);
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

            if (collector == null) {
                s_logger.error("Cannot create EventHistoryCollector for events" + events);
                return;
            }
            PropertyFilterSpec eventFilterSpec = createEventFilterSpec(collector);

            if (eventFilterSpec == null) {
                s_logger.error("Cannot create PropertyFilterSpec for EventHistoryCollector for events"
                            + events);
                return;
            }

            PropertyFilter propFilter = propColl.createFilter(eventFilterSpec, true);

            if (propFilter != null) {
                watchedFilters.put(mos, propFilter);
            } else {
                s_logger.error("Cannot create event filter for managed object ");
            }
        } catch(Exception e)
        {
            s_logger.error("Cannot watchManagedObjectEvents for " + mos + ", exception " + e);
            s_logger.error(Throwables.getStackTraceAsString(e));
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
                s_logger.error("Cannot unwatchManagedObjectEvents for " + mos + ", exception " + e);
                s_logger.error(Throwables.getStackTraceAsString(e));
            }
        }
    }
}
