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
import org.apache.log4j.Logger;

public class MainDB {
    private static volatile SortedMap<String, VirtualNetworkInfo> vmwareVNs =
            new ConcurrentSkipListMap<String, VirtualNetworkInfo>();
    private static volatile SortedMap<String, VirtualMachineInfo> vmwareVMs =
            new ConcurrentSkipListMap<String, VirtualMachineInfo>();

    static volatile VncDB vncDB;
    private static volatile VCenterDB vcenterDB;
    private static volatile Mode mode;
    private final static Logger s_logger =
            Logger.getLogger(MainDB.class);

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

    public static <K extends Comparable<K>, V extends VCenterObject>
    void sync(SortedMap<K, V> oldMap, SortedMap<K, V> newMap) {

        Iterator<Entry<K, V>> oldIter = oldMap.entrySet().iterator();
        Entry<K, V> oldEntry = oldIter.hasNext()? oldIter.next() : null;
        Iterator<Entry<K, V>> newIter = newMap.entrySet().iterator();
        Entry<K, V> newEntry = newIter.hasNext()? newIter.next() : null;

        while (oldEntry != null && newEntry != null) {
            Integer cmp = newEntry.getKey().compareTo(oldEntry.getKey());
            try {
                if (cmp == 0) {
                    newEntry.getValue().sync(oldEntry.getValue(), vncDB);
                    oldEntry = oldIter.hasNext()? oldIter.next() : null;
                    newEntry = newIter.hasNext()? newIter.next() : null;
                } else if (cmp < 0) {
                    if (mode != Mode.VCENTER_AS_COMPUTE) {
                        newEntry.getValue().create(vncDB);
                    }
                    newEntry = newIter.hasNext()? newIter.next() : null;
                } else {
                    if (mode != Mode.VCENTER_AS_COMPUTE) {
                        oldEntry.getValue().delete(vncDB);
                    }
                    oldEntry = oldIter.hasNext()? oldIter.next() : null;
                }
            } catch (Exception e) {
                s_logger.error("Cannot sync old [" + oldEntry.getKey() + ", " + oldEntry.getValue() + "] with new [" +
                        newEntry.getKey() + ", " + newEntry.getValue() + "]" );
            }
        }

        if (mode != Mode.VCENTER_AS_COMPUTE) {
            while (oldEntry != null) {
                try {
                    oldEntry.getValue().delete(vncDB);
                } catch (Exception e) {
                    s_logger.error("Cannot delete old [" + oldEntry.getKey() + ", " + oldEntry.getValue() + "]");
                }
                oldEntry = oldIter.hasNext()? oldIter.next() : null;
            }
        }

        while (newEntry != null) {
            try {
                newEntry.getValue().create(vncDB);
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

        while (oldEntry != null && newEntry != null) {
            Integer cmp = newEntry.getKey().compareTo(oldEntry.getKey());
            try {
                if (cmp == 0) {
                    oldEntry.getValue().update(newEntry.getValue(), vncDB);
                    oldEntry = oldIter.hasNext()? oldIter.next() : null;
                    newEntry = newIter.hasNext()? newIter.next() : null;
                } else if (cmp < 0) {
                    newEntry.getValue().create(vncDB);
                    newEntry = newIter.hasNext()? newIter.next() : null;
                } else {
                    oldEntry.getValue().delete(vncDB);
                    oldEntry = oldIter.hasNext()? oldIter.next() : null;
                }
            } catch (Exception e) {
                s_logger.error("Cannot update old [" + oldEntry.getKey() + ", " + oldEntry.getValue() + "] with new [" +
                        newEntry.getKey() + ", " + newEntry.getValue() + "]" );
            }
        }

        while (oldEntry != null) {
            try {
                oldEntry.getValue().delete(vncDB);
            } catch (Exception e) {
                s_logger.error("Cannot delete old [" + oldEntry.getKey() + ", " + oldEntry.getValue() + "]");
            }
            oldEntry = oldIter.hasNext()? oldIter.next() : null;
        }

        while (newEntry != null) {
            try {
                newEntry.getValue().create(vncDB);
            } catch (Exception e) {
                s_logger.error("Cannot create new ["  + newEntry.getKey() + ", " + newEntry.getValue() + "]" );
            }
            newEntry = newIter.hasNext()? newIter.next() : null;
        }
    }

    public static void sync(VCenterDB _vcenterDB, VncDB _vncDB, Mode _mode)
            throws Exception {
        vcenterDB = _vcenterDB;
        vncDB = _vncDB;
        mode = _mode;

        vmwareVNs.clear();
        vmwareVMs.clear();

        vmwareVNs = vcenterDB.readVirtualNetworks();
        SortedMap<String, VirtualNetworkInfo> oldVNs = vncDB.readVirtualNetworks();
        sync(oldVNs, vmwareVNs);

        vmwareVMs = vcenterDB.readVirtualMachines();
        SortedMap<String, VirtualMachineInfo> oldVMs = vncDB.readVirtualMachines();
        VRouterNotifier.syncVrouterAgent();
        sync(oldVMs, vmwareVMs);

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
