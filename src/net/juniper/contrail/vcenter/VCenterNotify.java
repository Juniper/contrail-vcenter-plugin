/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */
package net.juniper.contrail.vcenter;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
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
    private static Map<String, String> watchedVMs
                = new HashMap<String, String>();
    private static Map<String, String> watchedVNs
                = new HashMap<String, String>();
    private static Map<ManagedObject, PropertyFilter> watchedFilters
                = new HashMap<ManagedObject, PropertyFilter>();
    private static List<EventHistoryCollector> collectors = new ArrayList<EventHistoryCollector>();

    private static final int MAX_CACHED_EVENTS = 100;
    @SuppressWarnings("serial")
    private static Map<Integer, Event> eventsLRUCache = new LinkedHashMap<Integer, Event>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Event> eldest) {
           return size() > MAX_CACHED_EVENTS;
        }
    };

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
            String vcenterPassword, String dcName, String clusterName,
            String dvsName, String ipFabricPgName,
            String _apiServerAddress, int _apiServerPort,
            String _username, String _password,
            String _tenant,
            String _authtype, String _authurl, Mode mode)
    {
        vcenterDB = new VCenterDB(vcenterUrl, vcenterUsername, vcenterPassword,
                dcName, clusterName, dvsName, ipFabricPgName, mode);

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
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        if (vmInfo.vm == null) {
            return;
        }
        String key = vmInfo.vm.getMOR().getVal();
        if (watchedVMs.containsKey(key)) {
            String val = watchedVMs.get(key);
            if (!val.equals(vmInfo.getUuid())) {

                s_logger.error("Found different UUID in watched VMs, expected " +
                        vmInfo.getUuid() + ", actual " + val +
                        ", for mor " + vmInfo.vm.getMOR().getType() +  ", value " + key);
                watchedVMs.put(key, vmInfo.getUuid());
            }
            return;
        }
        s_logger.info("Now watching VM with MOR " + key + ", " + vmInfo);
        watchedVMs.put(key, vmInfo.getUuid());
        watchManagedObject(vmInfo.vm, guestProps);
    }

    public static void unwatchVm(VirtualMachineInfo vmInfo) {
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        if (vmInfo.vm == null) {
            return;
        }
        String key = vmInfo.vm.getMOR().getVal();
        if (!watchedVMs.containsKey(key)) {
            return;
        }
        s_logger.info("Unwatching VM with MOR " + key + ", " + vmInfo);
        watchedVMs.remove(key);
        unwatchManagedObject(vmInfo.vm);
    }

    public static void watchVn(VirtualNetworkInfo vnInfo) {
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        if (vnInfo.dpg == null) {
            return;
        }
        String key = vnInfo.dpg.getMOR().getVal();
        if (watchedVNs.containsKey(key)) {

            String val = watchedVNs.get(key);
            if (!val.equals(vnInfo.getUuid())) {

                s_logger.error("Found different UUID in watched VNs, expected " +
                        vnInfo.getUuid() + ", actual " + val +
                        ", for mor " + vnInfo.dpg.getMOR().getType() +  " value " + key );
                watchedVNs.put(key, vnInfo.getUuid());
            }
            return;
        }
        s_logger.debug("Now watching VN with MOR " + key + ", " + vnInfo);
        watchedVNs.put(key, vnInfo.getUuid());
        watchManagedObject(vnInfo.dpg, ipPoolProps);
    }

    public static void unwatchVn(VirtualNetworkInfo vnInfo) {
        if (VCenterMonitor.mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        if (vnInfo.dpg == null) {
            return;
        }
        String key = vnInfo.dpg.getMOR().getVal();
        if (!watchedVNs.containsKey(key)) {
            return;
        }
        s_logger.info("Unwatching VN with MOR " + key + ", " + vnInfo);
        watchedVNs.remove(key);
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
                        String vnUuid = watchedVNs.get(mor.getVal());
                        VirtualNetworkInfo vnInfo = MainDB.getVnById(vnUuid);
                        Integer oldPoolId = vnInfo.getIpPoolId();
                        s_logger.info("Received Event ID summary.ipPoolId change to " + newPoolId +
                                " from " + oldPoolId + " for " + vnInfo);
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
                    } else {
                        s_logger.info("Received Event ID summary.ipPoolId property change to "
                                    + newPoolId + " for unwatched VN");
                    }
                } else if (propName.equals("guest.toolsRunningStatus")) {
                    toolsRunningStatus = (String)value;
                    s_logger.info("Received Event ID guest.toolsRunningStatus property change to "
                                    + toolsRunningStatus);
                } else if (value instanceof ArrayOfEvent) {
                    s_logger.info("Received ArrayOfEvent");
                    ArrayOfEvent aoe = (ArrayOfEvent) value;
                    Event[] evts = aoe.getEvent();
                    if (evts == null) {
                        s_logger.info("Done processing array of events, null event received");
                        continue;
                    }
                    Arrays.sort(evts, new Comparator<Event>() {
                        public int compare(Event e1, Event e2) {
                            if (e1.getKey() == e2.getKey()) {
                                return 0;
                            }
                            if (e1.getKey() < e2.getKey()) {
                                return -1;
                            }
                            return 1;
                        }
                    });

                    for (int evtID = 0; evtID < evts.length; ++evtID)
                    {
                        Event anEvent = evts[evtID];
                        if (anEvent == null) {
                            continue;
                        }
                        if (eventsLRUCache.containsKey(anEvent.getKey())) {
                            s_logger.info("Skipping already handled Event ID " + anEvent.getKey());
                            continue;
                        }
                        eventsLRUCache.put(anEvent.getKey(), anEvent);
                        printEvent(anEvent);
                        eventHandler.handle(anEvent);
                    }
                    s_logger.info("Done processing array of events");
                } else if ((value instanceof EnteredMaintenanceModeEvent) || (value instanceof HostConnectionLostEvent)) {
                    Event anEvent = (Event) value;
                    if (eventsLRUCache.containsKey(anEvent.getKey())) {
                        s_logger.info("Skipping already handled Event ID " + anEvent.getKey());
                    } else {
                        eventsLRUCache.put(anEvent.getKey(), anEvent);
                        printEvent(anEvent);
                        String hostName = anEvent.getHost().getName();
                        String vRouterIpAddress = vcenterDB.esxiToVRouterIpMap.get(hostName);
                        if (vRouterIpAddress != null) {
                            s_logger.info("Entering maintenance mode. Marking the host " + hostName +
                                    " inactive. VRouter ip address is " + anEvent.getHost().getName());
                            VRouterNotifier.setVrouterActive(vRouterIpAddress, false);
                        } else {
                            s_logger.info("Skipping event for unmanaged host " + hostName);
                        }
                        s_logger.info("Done processing event " + anEvent.getFullFormattedMessage());
                    }
                } else if ((value instanceof ExitMaintenanceModeEvent) || (value instanceof HostConnectedEvent)) {
                    Event anEvent = (Event) value;
                    if (eventsLRUCache.containsKey(anEvent.getKey())) {
                        s_logger.info("Skipping already handled Event ID " + anEvent.getKey());
                    } else {
                        eventsLRUCache.put(anEvent.getKey(), anEvent);
                        printEvent(anEvent);
                        String hostName = anEvent.getHost().getName();
                        String vRouterIpAddress = vcenterDB.esxiToVRouterIpMap.get(hostName);
                        if (vRouterIpAddress != null) {
                            s_logger.info("\nExit maintenance mode. Marking the host " + hostName
                                    + " active. VRouter IP address is " +  vRouterIpAddress);
                            VRouterNotifier.setVrouterActive(vRouterIpAddress, true);
                        } else {
                            s_logger.info("Skipping event for unmanaged host " + hostName);
                        }
                        s_logger.info("Done processing event " + anEvent.getFullFormattedMessage());
                    }
                } else if (value instanceof ArrayOfGuestNicInfo) {
                    s_logger.info("Received event ID array of GuestNics");
                    ArrayOfGuestNicInfo aog = (ArrayOfGuestNicInfo) value;
                    nics = aog.getGuestNicInfo();

                } else if (value instanceof Event) {
                    Event anEvent = (Event)value;
                    if (eventsLRUCache.containsKey(anEvent.getKey())) {
                        s_logger.info("Skipping already handled Event ID " + anEvent.getKey());
                    } else {
                        eventsLRUCache.put(anEvent.getKey(), anEvent);
                        printEvent(anEvent);
                        eventHandler.handle(anEvent);
                    }
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
                String vmUuid = watchedVMs.get(mor.getVal());
                VirtualMachineInfo vmInfo = MainDB.getVmById(vmUuid);
                if (toolsRunningStatus != null) {
                    s_logger.info("Set toolsRunning status to " + toolsRunningStatus + " for " + vmInfo);
                    vmInfo.setToolsRunningStatus(toolsRunningStatus);
                }
                if (vmInfo.getToolsRunningStatus().equals(
                        VirtualMachineToolsRunningStatus.guestToolsRunning.toString())) {
                    vmInfo.updatedGuestNics(nics, vncDB);
                } else {
                    s_logger.warn("Received guestNic info, but guestToolsRunningStatus is " + toolsRunningStatus + ", for " + vmInfo);
                }
            }
            s_logger.info("Done processing property update for MOR type " + mor.getType() + " , value " + mor.getVal());
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
                eventsLRUCache.clear();
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
                // we will yield mastership if we couldn't get response
                // from api server after 10 attempts which is 50 Sec.

                short retryAttempt = 10;
                while (vncDB.isVncApiServerAlive() == false &&  retryAttempt > 0) {
                    s_logger.info("Waiting for API server... ");
                    retryAttempt--;
                    Thread.sleep(5000);
                }
                
                if (retryAttempt == 0) {
                    VCenterMonitor.zookeeperClose();
                    s_logger.error("API server is down, so yielding mastership and exit");
                    System.exit(0);
                }
                 
                syncNeeded = true;
                // Perform sync between VNC and VCenter DBs.
                if (syncNeeded) {
                    s_logger.debug("+++++++++++++ Start syncing  +++++++++++++++++++++");

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
                    Thread.sleep(5000);

                    s_logger.debug("+++++++++++++ Done syncing +++++++++++++++++++++");
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
