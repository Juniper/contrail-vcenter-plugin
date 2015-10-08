/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import com.google.common.base.Throwables;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.watchdog.MonitoredTask;
import net.juniper.contrail.watchdog.TaskWatchDog;


public interface VCenterMonitorTask extends Runnable {
    public void Initialize();

    public VCenterDB getVCenterDB();
    public void setVCenterDB(VCenterDB _vcenterDB);

    public VncDB getVncDB();

    public boolean getVCenterNotifyForceRefresh();
    public void setVCenterNotifyForceRefresh(boolean _VCenterNotifyForceRefresh);

    public void setAddPortSyncAtPluginStart(boolean _AddPortSyncAtPluginStart);

    public boolean getAddPortSyncAtPluginStart();

    void syncVirtualMachines(String vnUuid, 
            VmwareVirtualNetworkInfo vmwareNetworkInfo,
            VncVirtualNetworkInfo vncNetworkInfo) throws Exception;

    public void syncVirtualNetworks() throws Exception;

    void syncVmwareVirtualMachines(String vnUuid,
            VmwareVirtualNetworkInfo curVmwareVNInfo,
            VmwareVirtualNetworkInfo prevVmwareVNInfo) throws Exception;

    public void syncVmwareVirtualNetworks() throws Exception;
}
