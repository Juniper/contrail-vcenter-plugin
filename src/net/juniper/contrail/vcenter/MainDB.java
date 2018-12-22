/**
 * Copyright (c) 2015 Juniper Networks, Inc. All rights reserved.
 *
 * @author Andra Cismaru
 */
package net.juniper.contrail.vcenter;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

public class MainDB {
    private static volatile SortedMap<String, VirtualNetworkInfo> vmwareVNs =
            new ConcurrentSkipListMap<String, VirtualNetworkInfo>();
    private static volatile SortedMap<String, VirtualMachineInfo> vmwareVMs =
            new ConcurrentSkipListMap<String, VirtualMachineInfo>();
    private static volatile SortedMap<String, VirtualNetworkInfo> oldVNs =
            new ConcurrentSkipListMap<String, VirtualNetworkInfo>();

    static volatile VncDB vncDB;
    private static volatile VCenterDB vcenterDB;
    private static volatile Mode mode;
    private final static Logger s_logger =
            Logger.getLogger(MainDB.class);
    private final static int maxPollWorkTime = 5000; // Maximum work to be done during sync is 5 sec interval
    private static volatile long startTime;

    public static SortedMap<String, VirtualNetworkInfo> getVNs() {
        return vmwareVNs;
    }

    public static SortedMap<String, VirtualMachineInfo> getVMs() {
        return vmwareVMs;
    }

    public static VirtualNetworkInfo getVnByName(String name) {
        for (VirtualNetworkInfo vnInfo: vmwareVNs.values()) {
            if (vnInfo.getName().equals(name)) {
                return vnInfo;
            }
        }
        return null;
    }

    public static VirtualMachineInfo getVmByName(String name) {
        for (VirtualMachineInfo vmInfo: vmwareVMs.values()) {
            if (vmInfo.getName().equals(name)) {
                return vmInfo;
            }
        }
        return null;
    }

    public static VirtualNetworkInfo getVnById(String uuid) {
        if (vmwareVNs.containsKey(uuid)) {
            return vmwareVNs.get(uuid);
        }
        return null;
    }

    public static void created(VirtualNetworkInfo vnInfo) {
        vmwareVNs.put(vnInfo.getUuid(), vnInfo);
    }

    public static void updated(VirtualNetworkInfo vnInfo) {
        if (!vmwareVNs.containsKey(vnInfo.getUuid())) {
            vmwareVNs.put(vnInfo.getUuid(), vnInfo);
        }
    }

    public static void deleted(VirtualNetworkInfo vnInfo) {
        if (vmwareVNs.containsKey(vnInfo.getUuid())) {
            vmwareVNs.remove(vnInfo.getUuid());
        }
    }

    public static void deleteVirtualNetwork(VirtualNetworkInfo vnInfo) {
        if (vmwareVNs.containsKey(vnInfo.getUuid())) {
            vmwareVNs.remove(vnInfo.getUuid());
        }
    }

    public static VirtualMachineInfo getVmById(String uuid) {
        if (vmwareVMs.containsKey(uuid)) {
            return vmwareVMs.get(uuid);
        }
        return null;
    }

    public static void created(VirtualMachineInfo vmInfo) {
        vmwareVMs.put(vmInfo.getUuid(), vmInfo);
    }

    public static void updated(VirtualMachineInfo vmInfo) {
        if (!vmwareVMs.containsKey(vmInfo.getUuid())) {
            vmwareVMs.put(vmInfo.getUuid(), vmInfo);
        }
    }

    public static void deleted(VirtualMachineInfo vmInfo) {
        if (vmwareVMs.containsKey(vmInfo.getUuid())) {
            vmwareVMs.remove(vmInfo.getUuid());
        }
    }

    private static void sleepDelta() {
        long currTime = System.currentTimeMillis();
        if (currTime - startTime >= maxPollWorkTime) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                s_logger.error("Unable to sleep for 1 sec");
            }
            startTime = System.currentTimeMillis();
        }
    }

    public static <K extends Comparable<K>, V extends VCenterObject>
    void sync(SortedMap<K, V> oldMap, SortedMap<K, V> newMap) {

        Iterator<Entry<K, V>> oldIter = oldMap.entrySet().iterator();
        Entry<K, V> oldEntry = oldIter.hasNext()? oldIter.next() : null;
        Iterator<Entry<K, V>> newIter = newMap.entrySet().iterator();
        Entry<K, V> newEntry = newIter.hasNext()? newIter.next() : null;

        startTime = System.currentTimeMillis();
        while (oldEntry != null && newEntry != null) {
            Integer cmp = newEntry.getKey().compareTo(oldEntry.getKey());
            try {
                if (cmp == 0) {
                    oldEntry = oldIter.hasNext()? oldIter.next() : null;
                    newEntry = newIter.hasNext()? newIter.next() : null;

                } else if (cmp < 0) {
                    if (mode != Mode.VCENTER_AS_COMPUTE) {
                        sleepDelta();
                        vcenterDB.createVmwareDPG(vcenterDB.getContrailDvs(), newEntry.getValue().getName());
                        s_logger.info("Create Vmware DPG [" + newEntry.getValue().getName() + "]" );
                    }
                    newEntry = newIter.hasNext()? newIter.next() : null;
                } else {
                    if (mode != Mode.VCENTER_AS_COMPUTE) {
                        sleepDelta();
                        vcenterDB.deleteVmwarePG(vcenterDB.getVmwareDpg(oldEntry.getValue().getName(),
                       	        vcenterDB.getContrailDvs(), vcenterDB.contrailDvSwitchName,
                                vcenterDB.contrailDataCenterName));
                        s_logger.info("Delete Vmware DPG [" + oldEntry.getValue().getName() + "]" );
                    }
                    oldEntry = oldIter.hasNext()? oldIter.next() : null;
                }
            } catch (Exception e) {
                s_logger.error("Cannot sync old [" + oldEntry.getKey() + ", " + oldEntry.getValue() + "] with new [" +
                        newEntry.getKey() + ", " + newEntry.getValue() + "]" );
            }
        }

        while (newEntry != null) {
            try {
                sleepDelta();
                vcenterDB.createVmwareDPG(vcenterDB.getContrailDvs(), newEntry.getValue().getName());
                s_logger.info("Create Vmware DPG [" + newEntry.getValue().getName() + "]" );
            } catch (Exception e) {
                s_logger.error("Cannot create new ["  + newEntry.getKey() + ", " + newEntry.getValue() + "]" );
            }
            newEntry = newIter.hasNext()? newIter.next() : null;
        }
    }

    public static <K extends Comparable<K>, V extends VCenterObject>
    void update(SortedMap<K, V> oldMap, SortedMap<K, V> newMap) {

        Iterator<Entry<K, V>> oldIter = oldMap.entrySet().iterator();
        Entry<K, V> oldEntry = oldIter.hasNext()? oldIter.next() : null;
        Iterator<Entry<K, V>> newIter = newMap.entrySet().iterator();
        Entry<K, V> newEntry = newIter.hasNext()? newIter.next() : null;

        startTime = System.currentTimeMillis();
        while (newEntry != null) {
            oldIter = oldMap.entrySet().iterator();
            oldEntry = oldIter.hasNext()? oldIter.next() : null;
            boolean newVncVn = true;
            while (oldEntry != null) {
                Integer cmp = newEntry.getValue().getName().compareTo(oldEntry.getValue().getName());
                try {
                    if (cmp == 0) {
                        newVncVn = false;
                        break;
                    } else {
                        oldEntry = oldIter.hasNext()? oldIter.next() : null;
                    }
                } catch (Exception e) {
                    s_logger.error("Cannot update old [" + oldEntry.getKey() + ", " + oldEntry.getValue() + "] with new [" +
                        newEntry.getKey() + ", " + newEntry.getValue() + "]" );
                }
            }
            if (newVncVn) {
                try {
                    sleepDelta();
                    vcenterDB.createVmwareDPG(vcenterDB.getContrailDvs(), newEntry.getValue().getName());
                    s_logger.info("Create Vmware DPG [" + newEntry.getValue().getName() + "]" );
                } catch (Exception e) {
                    s_logger.error("Cannot create new ["  + newEntry.getKey() + ", " + newEntry.getValue() + "]" );
                }
            }
            newEntry = newIter.hasNext()? newIter.next() : null;
        }
        oldIter = oldMap.entrySet().iterator();
        oldEntry = oldIter.hasNext()? oldIter.next() : null;
        while (oldEntry != null) {
            newIter = newMap.entrySet().iterator();
            newEntry = newIter.hasNext()? newIter.next() : null;
            boolean newVcenterPg = true;
            while (newEntry != null) {
                Integer cmp = newEntry.getValue().getName().compareTo(oldEntry.getValue().getName());
                try {
                    if (cmp == 0) {
                        newVcenterPg = false;
                        break;
                    } else {
                        newEntry = newIter.hasNext()? newIter.next() : null;
                    }
                } catch (Exception e) {
                    s_logger.error("Cannot update old [" + oldEntry.getKey() + ", " + oldEntry.getValue() + "] with new [" +
                        newEntry.getKey() + ", " + newEntry.getValue() + "]" );
                }
            }
            if (newVcenterPg) {
                try {
                    sleepDelta();
                    vcenterDB.deleteVmwarePG(vcenterDB.getVmwareDpg(oldEntry.getValue().getName(),
                            vcenterDB.getContrailDvs(), vcenterDB.contrailDvSwitchName,
                            vcenterDB.contrailDataCenterName));
                    s_logger.info("Delete Vmware DPG [" + oldEntry.getValue().getName() + "]" );
                } catch (Exception e) {
                    s_logger.error("Cannot delete ["  + oldEntry.getKey() + ", " + oldEntry.getValue() + "]" );
                }
            }
            oldEntry = oldIter.hasNext()? oldIter.next() : null;
        }
    }

    public static void sync(VCenterDB _vcenterDB, VncDB _vncDB, Mode _mode)
            throws Exception {
        vcenterDB = _vcenterDB;
        vncDB = _vncDB;
        mode = _mode;

        vmwareVNs.clear();

        SortedMap<String, VirtualNetworkInfo> newVNs = vncDB.readVirtualNetworks();
        sync(oldVNs, newVNs);
        oldVNs = newVNs; 

        vmwareVNs = vcenterDB.readVirtualNetworks();
        update(vmwareVNs, newVNs);

        printInfo();
    }

    private static void printInfo() {
        s_logger.debug("\nFound " + vmwareVNs.size() + " Virtual Networks after sync:");
        for (VirtualNetworkInfo vnInfo: vmwareVNs.values()) {
            s_logger.debug(vnInfo.toStringBuffer());
        }

        s_logger.debug("\nFound " + vmwareVMs.size() + " Virtual Machines after sync:");
        for (VirtualMachineInfo vmInfo: vmwareVMs.values()) {
            s_logger.debug(vmInfo.toStringBuffer());
        }
        s_logger.debug("\n");
    }
}
