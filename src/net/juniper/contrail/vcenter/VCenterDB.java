/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.*;
import java.util.*; 
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Hashtable;
import java.util.Map;
import java.util.SortedMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.lang.RuntimeException;
import java.io.FileNotFoundException;
import com.vmware.vim25.VirtualMachineToolsRunningStatus;
import org.apache.log4j.Logger;
import com.google.common.base.Throwables;
import com.vmware.vim25.DVPortSetting;
import com.vmware.vim25.DVPortgroupConfigInfo;
import com.vmware.vim25.Event;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.IpPool;
import com.vmware.vim25.NetIpConfigInfo;
import com.vmware.vim25.NetIpConfigInfoIpAddress;
import com.vmware.vim25.VMwareDVSPortSetting;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VmwareDistributedVirtualSwitchPvlanSpec;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanIdSpec;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanSpec;
import com.vmware.vim25.DistributedVirtualSwitchKeyedOpaqueBlob;
import com.vmware.vim25.VMwareDVSConfigInfo;
import com.vmware.vim25.VMwareDVSPvlanMapEntry;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.IpPoolManager;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.util.PropertyCollectorUtil;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import com.vmware.vim25.mo.util.*;

public class VCenterDB {
    private static final Logger s_logger =
            Logger.getLogger(VCenterDB.class);
    private static final String esxiToVRouterIpMapFile = "/etc/contrail/ESXiToVRouterIp.map";
    protected final String contrailDvSwitchName;
    public final String contrailDataCenterName;
    public final String contrailClusterName;
    private final String vcenterUrl;
    private final String vcenterUsername;
    private final String vcenterPassword;
    private final String contrailIpFabricPgName;
    final Mode mode;

    private volatile ServiceInstance serviceInstance;
    private volatile Folder rootFolder;
    private volatile InventoryNavigator inventoryNavigator;
    private volatile IpPoolManager ipPoolManager;
    private volatile IpPool[] ipPools;
    protected volatile Datacenter contrailDC;
    protected volatile VmwareDistributedVirtualSwitch contrailDVS;
    private volatile ConcurrentMap<String, Datacenter> datacenters;
    private volatile ConcurrentMap<String, VmwareDistributedVirtualSwitch> dvswitches; // key is dvsName
    private volatile ConcurrentMap<String, VMwareDVSPvlanMapEntry[]> dvsPvlanMap; // key is dvsName
    public volatile Map<String, String> esxiToVRouterIpMap;
    public static final String OK = "Ok";
    private String operationalStatus = OK;
    private Calendar lastTimeSeenAlive;

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public VCenterDB(String vcenterUrl, String vcenterUsername,
                     String vcenterPassword, String contrailDcName, String contrailClusterName,
                     String contrailDvsName, String ipFabricPgName, Mode mode) {
        this.vcenterUrl             = vcenterUrl;
        this.vcenterUsername        = vcenterUsername;
        this.vcenterPassword        = vcenterPassword;
        this.contrailDataCenterName = contrailDcName;
        this.contrailClusterName = contrailClusterName;
        this.contrailDvSwitchName   = contrailDvsName;
        this.contrailIpFabricPgName = ipFabricPgName;
        this.mode                   = mode;

        s_logger.info("VCenterDB(" + contrailDvsName + ", " + ipFabricPgName + ")");
        datacenters = new ConcurrentHashMap<String, Datacenter>();
        dvswitches = new ConcurrentHashMap<String, VmwareDistributedVirtualSwitch>();
        dvsPvlanMap = new ConcurrentHashMap<String, VMwareDVSPvlanMapEntry[]>();

        // Create ESXi host to vRouterVM Ip address map
        buildEsxiToVRouterIpMap();
    }

    private boolean initData() {
        if (!isAlive()) {
            return false;
        }

        datacenters.clear();
        dvswitches.clear();
        dvsPvlanMap.clear();

        rootFolder = null;
        rootFolder = serviceInstance.getRootFolder();
        if (rootFolder == null) {
            operationalStatus = "Failed to get rootfolder for vCenter ";
            s_logger.error(operationalStatus);
            return false;
        }
        s_logger.info("Got rootfolder for vCenter ");

        inventoryNavigator = null;
        inventoryNavigator = new InventoryNavigator(rootFolder);
        if (inventoryNavigator == null) {
            operationalStatus = "Failed to get InventoryNavigator for vCenter ";
            s_logger.error(operationalStatus);
            return false;
        }
        s_logger.info("Got InventoryNavigator for vCenter ");

        ipPoolManager = null;
        ipPoolManager = serviceInstance.getIpPoolManager();
        if (ipPoolManager == null) {
            operationalStatus = "Failed to get ipPoolManager for vCenter ";
            s_logger.error(operationalStatus);
            return false;
        }
        s_logger.info("Got ipPoolManager for datacenter " + contrailDataCenterName);

        contrailDC = null;
        try {
            contrailDC = getVmwareDatacenter(contrailDataCenterName);

        } catch (RemoteException e) {
        }
        if (contrailDC == null) {
            operationalStatus = "Failed to find " + contrailDataCenterName
                           + " DC on vCenter ";
            s_logger.error(operationalStatus);
            return false;
        }
        s_logger.info("Found " + contrailDataCenterName + " DC on vCenter ");

        Folder hostsFolder = null;
        try {
            hostsFolder = contrailDC.getHostFolder();
        } catch (RemoteException e) {
        }

        if (hostsFolder == null) {
            operationalStatus = "Failed to find hostFolder on datacenter " + contrailDataCenterName;
            s_logger.error(operationalStatus);
            return false;
        }

        contrailDVS = null;
        try {
            contrailDVS = getVmwareDvs(contrailDvSwitchName, contrailDC, contrailDataCenterName);
        } catch (RemoteException e) {
        }

        if (contrailDVS == null) {
            operationalStatus = "Failed to find " + contrailDvSwitchName +
                           " DVSwitch on vCenter";
            s_logger.error(operationalStatus);
            return false;
        }
        s_logger.info("Found " + contrailDvSwitchName + " DVSwitch on vCenter ");

        try {
            getDvsPvlanMap(contrailDvSwitchName, contrailDC, contrailDataCenterName);
        } catch (RemoteException e) {
            s_logger.error(this + "Private vlan not configured on dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDataCenterName);
        }

        // All well on vCenter front.
        operationalStatus = OK;
        return true;
    }

    public boolean connect(int timeout) {
        datacenters.clear();
        dvswitches.clear();
        dvsPvlanMap.clear();

        while(!createServiceInstance(timeout)) {
            try{
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                s_logger.error(Throwables.getStackTraceAsString(e));
                return false;
            }

        }

        while(!initData() ) {
            try{
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                s_logger.error(Throwables.getStackTraceAsString(e));
                return false;
            }

        }
        return true;
    }

    private boolean createServiceInstance(int timeout) {
        try {
            serviceInstance = null;
            s_logger.info("Trying to Connect to vCenter Server : " + "("
                    + vcenterUrl + "," + vcenterUsername + ")");
            serviceInstance = new ServiceInstance(new URL(vcenterUrl),
                                        vcenterUsername, vcenterPassword, true);
            if (serviceInstance == null) {
                operationalStatus = "Failed to connect to vCenter Server : " + "("
                                + vcenterUrl + "," + vcenterUsername + ","
                                + vcenterPassword + ")" + "Retrying after 5 secs";
                s_logger.error(operationalStatus);
                return false;
            }
            setConnectTimeout(timeout);
            s_logger.info("Connected to vCenter Server : " + "("
                    + vcenterUrl + "," + vcenterUsername + ","
                    + vcenterPassword + ")");
            operationalStatus = OK;
            return true;
        } catch (MalformedURLException e) {
                operationalStatus = "Re-Connect unsuccessful: " + e.getMessage();
                s_logger.error(operationalStatus);
                return false;
        } catch (RemoteException e) {
                operationalStatus = "Re-Connect unsuccessful: " + e.getMessage();
                s_logger.error(operationalStatus);
                return false;
        } catch (Exception e) {
                operationalStatus = "Error while connecting to vcenter: " + e.getMessage();
                s_logger.error(operationalStatus);
                s_logger.error(Throwables.getStackTraceAsString(e));
                return false;
        }
    }

    public ServiceInstance getServiceInstance() {
        return serviceInstance;
    }

    private boolean buildEsxiToVRouterIpMap() {
        esxiToVRouterIpMap = new HashMap<String, String>();
        try {
            File file = new File("/etc/contrail/ESXiToVRouterIp.map");
            Scanner input = new Scanner(file);
            while(input.hasNext()) {
                String nextLine = input.nextLine();
                String[] part = nextLine.split(":");
                if (part.length >= 2) {
                    s_logger.info(" ESXi IP Address:" + part[0] + " vRouter-IP-Address: " + part[1]);
                    esxiToVRouterIpMap.put(part[0], part[1]);
                    VRouterNotifier.setVrouterActive(part[1], true);
                }
            }
            input.close();
        } catch (FileNotFoundException e) {
            s_logger.error("file not found :" + esxiToVRouterIpMapFile);
            return false;
        }
        return true;
    }

    public Map<String, String> getEsxiToVRouterIpMap() {
        return esxiToVRouterIpMap;
    }

    public void setReadTimeout(int milliSecs) {
       //Set read timeout on connection to vcenter server
       serviceInstance.getServerConnection()
                      .getVimService()
                      .getWsc().setReadTimeout(milliSecs);
    }

    public void setConnectTimeout(int milliSecs) {
        //Set connect timeout on connection to vcenter server
        serviceInstance.getServerConnection()
                       .getVimService()
                       .getWsc().setConnectTimeout(milliSecs);
     }

    public boolean isConnected() {
      return (serviceInstance != null);
    }

    public IpPoolManager getIpPoolManager() {
      return ipPoolManager;
    }

    public void setIpPoolManager(IpPoolManager _ipPoolManager) {
      ipPoolManager = _ipPoolManager;
    }

    public InventoryNavigator getInventoryNavigator() {
      return inventoryNavigator;
    }

    public void setInventoryNavigator(InventoryNavigator _inventoryNavigator) {
      inventoryNavigator = _inventoryNavigator;
    }

    public Datacenter getDatacenter() {
      return contrailDC;
    }

    public void setDatacenter(Datacenter _dc) {
      contrailDC = _dc;
    }

    public VmwareDistributedVirtualSwitch getDvs() {
        return contrailDVS;
    }

    protected static String getVirtualMachineMacAddress(
            VirtualMachineConfigInfo vmConfigInfo,
            DistributedVirtualPortgroup portGroup) {
        VirtualDevice devices[] = vmConfigInfo.getHardware().getDevice();
        for (VirtualDevice device : devices) {
            // Assuming only one interface
            if (device instanceof VirtualEthernetCard) {
                VirtualDeviceBackingInfo backingInfo =
                        device.getBacking();

                if (backingInfo == null)
                    continue;

                // Is it backed by the distributed virtual port group?
                if (backingInfo instanceof
                    VirtualEthernetCardDistributedVirtualPortBackingInfo) {
                    VirtualEthernetCardDistributedVirtualPortBackingInfo
                    dvpBackingInfo =
                    (VirtualEthernetCardDistributedVirtualPortBackingInfo)
                    backingInfo;
                    if ((dvpBackingInfo.getPort() == null) ||
                        (dvpBackingInfo.getPort().getPortgroupKey() == null))
                        continue;

                    if (dvpBackingInfo.getPort().getPortgroupKey().
                            equals(portGroup.getKey())) {
                        String vmMac = ((VirtualEthernetCard) device).
                                getMacAddress();
                        return vmMac;
                    }
                }
            }
        }
        s_logger.error("dvPg: " + portGroup.getName() + " vmConfig: " +
                vmConfigInfo + " MAC Address NOT found");
        return null;
    }

    protected String getVRouterVMIpFabricAddress(String hostName,
            HostSystem host, String vmNamePrefix)
                    throws Exception {
        // Find if vRouter Ip Fabric mapping exists..
        String vRouterIpAddress = esxiToVRouterIpMap.get(hostName);
        if (host.getRuntime().isInMaintenanceMode()) {
            VRouterNotifier.setVrouterActive(vRouterIpAddress, false);
        }

        if (vRouterIpAddress != null) {
            return vRouterIpAddress;
        } else {
            s_logger.debug(" vRouter IP mapping for Host: " + hostName +
                    "does not exist");
        }

        VirtualMachine[] vms = host.getVms();
        for (VirtualMachine vm : vms) {
            String vmName = vm.getName();
            if (!vmName.toLowerCase().contains(vmNamePrefix.toLowerCase())) {
                continue;
            }
            // Assumption here is that VMware Tools are installed
            // and IP address is available
            GuestInfo guestInfo = vm.getGuest();
            if (guestInfo == null) {
                s_logger.debug(" Host: " + hostName +
                        " vm:" + vmName + " GuestInfo - VMware Tools " +
                        " NOT installed");
                continue;
            }
            GuestNicInfo[] nicInfos = guestInfo.getNet();
            if (nicInfos == null) {
                s_logger.debug(" Host: " + hostName +
                        " vm:" + vmName + " GuestNicInfo - VMware Tools " +
                        " NOT installed");
                continue;
            }
            for (GuestNicInfo nicInfo : nicInfos) {
                // Extract the IP address associated with simple port
                // group. Assumption here is that Contrail VRouter VM will
                // have only one standard port group
                String networkName = nicInfo.getNetwork();
                if (networkName == null || !networkName.equals(contrailIpFabricPgName)) {
                    continue;
                }
                Network network = (Network)
                        inventoryNavigator.searchManagedEntity("Network",
                                networkName);
                if (network == null) {
                    s_logger.debug("Host: " +
                            hostName + " vm: " + vmName + " network: " +
                            networkName + " NOT found");
                    continue;
                }
                NetIpConfigInfo ipConfigInfo = nicInfo.getIpConfig();
                if (ipConfigInfo == null) {
                    continue;
                }
                NetIpConfigInfoIpAddress[] ipAddrConfigInfos =
                        ipConfigInfo.getIpAddress();
                if (ipAddrConfigInfos == null ||
                        ipAddrConfigInfos.length == 0) {
                    continue;

                }
                for (NetIpConfigInfoIpAddress ipAddrConfigInfo :
                    ipAddrConfigInfos) {
                    String ipAddress = ipAddrConfigInfo.getIpAddress();
                    // Choose IPv4 only
                    InetAddress ipAddr = InetAddress.getByName(ipAddress);
                    if (ipAddr instanceof Inet4Address) {
                       // found vRouter VM ip-fabric address. Store it.
                        esxiToVRouterIpMap.put(hostName, ipAddress);
                        return ipAddress;
                    }
                }
            }
        }
        return null;
    }

    public static String getVirtualMachineIpAddress(VirtualMachine vm,
            String dvPgName) throws Exception {

        // Assumption here is that VMware Tools are installed
        // and IP address is available
        GuestInfo guestInfo = vm.getGuest();
        String vmName = vm.getName();
        if (guestInfo == null) {
            s_logger.debug("dvPg: " + dvPgName + " vm:" + vmName
                    + " GuestInfo - VMware Tools " + " NOT installed");
            return null;
        }
        GuestNicInfo[] nicInfos = guestInfo.getNet();
        if (nicInfos == null) {
            s_logger.debug("dvPg: " + dvPgName + " vm:" + vmName
                    + " GuestNicInfo - VMware Tools " + " NOT installed");
            return null;
        }
        for (GuestNicInfo nicInfo : nicInfos) {
            // Extract the IP address associated with simple port
            // group. Assumption here is that Contrail VRouter VM will
            // have only one standard port group
            String networkName = nicInfo.getNetwork();
            if (networkName == null || !networkName.equals(dvPgName)) {
                continue;
            }

            NetIpConfigInfo ipConfigInfo = nicInfo.getIpConfig();
            if (ipConfigInfo == null) {
                continue;
            }
            NetIpConfigInfoIpAddress[] ipAddrConfigInfos =
                    ipConfigInfo.getIpAddress();
            if (ipAddrConfigInfos == null ||
                    ipAddrConfigInfos.length == 0) {
                continue;

            }
            for (NetIpConfigInfoIpAddress ipAddrConfigInfo :
                ipAddrConfigInfos) {
                String ipAddress = ipAddrConfigInfo.getIpAddress();
                InetAddress ipAddr = InetAddress.getByName(ipAddress);
                if (ipAddr instanceof Inet4Address) {
                    // the VMI can have multiple IPv4 and IPv6 addresses,
                    // but we pick only the first IPv4 address
                    return ipAddress;
                }
            }
        }
        return null;
    }

    private static boolean doIgnoreVirtualNetwork(DVPortSetting portSetting) {
        // Ignore dvPgs that do not have PVLAN/VLAN configured
        if (portSetting instanceof VMwareDVSPortSetting) {
            VMwareDVSPortSetting vPortSetting =
                    (VMwareDVSPortSetting) portSetting;
            VmwareDistributedVirtualSwitchVlanSpec vlanSpec =
                    vPortSetting.getVlan();
            if (vlanSpec instanceof VmwareDistributedVirtualSwitchPvlanSpec) {
                return false;
            }
            if (vlanSpec instanceof VmwareDistributedVirtualSwitchVlanIdSpec) {
                return false;
            }
        }
        return true;
    }


    public boolean getExternalIpamInfo(DVPortgroupConfigInfo configInfo, String vnName) throws Exception {

        DistributedVirtualSwitchKeyedOpaqueBlob[] opaqueBlobs
                             = configInfo.getVendorSpecificConfig();

        if ((opaqueBlobs == null) || (opaqueBlobs.length == 0)) {
            return false;
        }

        for (DistributedVirtualSwitchKeyedOpaqueBlob opaqueBlob : opaqueBlobs) {
          s_logger.debug("pg (" + vnName + ") " + "opaqueBlob: key ("
                       + opaqueBlob.getKey() + " )  opaqueData ("
                       + opaqueBlob.getOpaqueData() + ")");
          if (opaqueBlob.getKey().equals("external_ipam")) {
              if (opaqueBlob.getOpaqueData().equals("true")) {
                return true;
              } else {
                return false;
              }
          }
        }
        return false;
    }

    public boolean getExternalIpamInfo(DistributedVirtualSwitchKeyedOpaqueBlob[] opaqueBlobs, String vnName) {

        if ((opaqueBlobs == null) || (opaqueBlobs.length == 0)) {
            return false;
        }

        for (DistributedVirtualSwitchKeyedOpaqueBlob opaqueBlob : opaqueBlobs) {
          s_logger.debug("pg (" + vnName + ") " + "opaqueBlob: key ("
                       + opaqueBlob.getKey() + " )  opaqueData ("
                       + opaqueBlob.getOpaqueData() + ")");
          if (opaqueBlob.getKey().equals("external_ipam")) {
              if (opaqueBlob.getOpaqueData().equals("true")) {
                return true;
              } else {
                return false;
              }
          }
        }
        return false;
    }

    public String getVcenterUrl() {
        return vcenterUrl;
    }

    public Datacenter getVmwareDatacenter(String name)
        throws RemoteException {

        if (datacenters.containsKey(name)) {
            return datacenters.get(name);
        }

        String description = "<datacenter " + name + ", vCenter " + vcenterUrl + ">.";

        Folder rootFolder = serviceInstance.getRootFolder();
        if (rootFolder == null) {
            operationalStatus = "Failed to get rootfolder for vCenter " + vcenterUrl;
            s_logger.error(operationalStatus);
            throw new RemoteException(operationalStatus);
        }
        InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);
        Datacenter dc = null;
        try {
            dc = (Datacenter) inventoryNavigator.searchManagedEntity(
                          "Datacenter", name);
        } catch (RemoteException e ) {
            operationalStatus = "Failed to retrieve " + description;
            s_logger.error(operationalStatus);
            throw new RemoteException(operationalStatus, e);
        }

        if (dc == null) {
            operationalStatus = "Failed to retrieve " + description;
            s_logger.error(operationalStatus);
            throw new RemoteException(operationalStatus);
        }

        datacenters.put(name, dc);

        s_logger.info("Found " + description);
        return dc;
    }

    public VmwareDistributedVirtualSwitch getVmwareDvs(String name,
            Datacenter dc, String dcName)
                    throws RemoteException {
        if (dvswitches.containsKey(name)) {
            return dvswitches.get(name);
        }

        String description = "<dvs " + name + ", datacenter " + dcName
                + ", vCenter " + vcenterUrl + ">.";

        InventoryNavigator inventoryNavigator = new InventoryNavigator(dc);

        VmwareDistributedVirtualSwitch dvs = null;
        try {
            dvs = (VmwareDistributedVirtualSwitch)inventoryNavigator.searchManagedEntity(
                    "VmwareDistributedVirtualSwitch", name);
        } catch (RemoteException e ) {
            operationalStatus = "Failed to retrieve " + description;
            s_logger.error(operationalStatus);
            throw new RemoteException(operationalStatus, e);
        }

        if (dvs == null) {
            operationalStatus = "Failed to retrieve " + description;
            s_logger.error(operationalStatus);
            throw new RemoteException(operationalStatus);
        }

        s_logger.info("Found " + description);
        dvswitches.put(name, dvs);
        return dvs;
    }

    public VMwareDVSPvlanMapEntry[] getDvsPvlanMap(String dvsName, Datacenter dc, String dcName)
                    throws RemoteException {
        if (dvsPvlanMap.containsKey(dvsName)) {
            return dvsPvlanMap.get(dvsName);
        }

        VmwareDistributedVirtualSwitch dvs = getVmwareDvs(dvsName, dc, dcName);

        // Extract private vlan entries for the virtual switch
        VMwareDVSConfigInfo dvsConfigInfo = (VMwareDVSConfigInfo) dvs.getConfig();
        if (dvsConfigInfo == null) {
            s_logger.error("dvSwitch: " + dvsName
                    + " Datacenter: " + dcName + " ConfigInfo is empty");
        }

        if (!(dvsConfigInfo instanceof VMwareDVSConfigInfo)) {
            s_logger.error("dvSwitch: " + dvsName +
                    " Datacenter: " + dcName + " ConfigInfo " +
                    "isn't instanceof VMwareDVSConfigInfo");
        }

        VMwareDVSPvlanMapEntry[] pvlanMapArray = dvsConfigInfo.getPvlanConfig();
        if (pvlanMapArray != null) {
            dvsPvlanMap.put(dvsName, pvlanMapArray);
            s_logger.info("Found private vlan map array on dvSwitch: " + dvsName +
                    " Datacenter: " + dcName);
            return pvlanMapArray;
        }

        s_logger.error("dvSwitch: " + dvsName +
                " Datacenter: " + dcName + " Private VLAN NOT" +
                "configured");
        return null;
    }

    public IpPool getIpPoolById(Integer poolid,  String nwName, Datacenter dc, String dcName)
            throws RemoteException {

        if (ipPools == null) {
            ipPools = ipPoolManager.queryIpPools(dc);
            if (ipPools == null || ipPools.length == 0) {
                s_logger.debug(" Datacenter: " + dcName + " IP Pools NOT configured");
                return null;
            }
        }

        // refresh cached pools and try again
        ipPools = ipPoolManager.queryIpPools(dc);
        if (ipPools == null || ipPools.length == 0) {
            s_logger.debug(" Datacenter: " + dcName + " IP Pools NOT configured");
            return null;
        }

        return getIpPool(poolid, nwName);
    }

    private IpPool getIpPool(Integer poolid, String nwName) {
        if (poolid == null) {
            // there is a vmware bug in which the ip pool association
            // is lost upon vcenter restart.
            // Retrieve the pool based on name
            // Remove this code if vmware bug is fixed
            String IpPoolForPG = "ip-pool-for-" + nwName;
            for (IpPool pool : ipPools) {
                if (IpPoolForPG.equals(pool.getName())) {
                    return pool;
                }
            }
            return null;
        }

        for (IpPool pool : ipPools) {
            if (pool.id.equals(poolid)) {
              return pool;
            }
        }

        return null;
    }

    public Network getVmwareNetwork(String name,
            VmwareDistributedVirtualSwitch dvs, String dvsName, String dcName)
            throws RemoteException {
        String description = "<network " + name
                + ", dvs " + dvsName + ", datacenter " + dcName
                + ", vCenter " + vcenterUrl + ">.";

        // funny but search on the dvs does not work, we need to use rootFolder
        InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);

        Network nw = null;
        try {
            nw = (Network)inventoryNavigator.searchManagedEntity(
                    "Network", name);
        } catch (RemoteException e ) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg, e);
        }

        if (nw == null) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg);
        }

        s_logger.info("Found " + description);
        return nw;
    }

    public DistributedVirtualPortgroup getVmwareDpg(String name,
            VmwareDistributedVirtualSwitch dvs, String dvsName, String dcName)
            throws RemoteException {
        String description = "<dpg " + name + ", dvs " + dvsName
                + ", datacenter " + dcName + ", vCenter " + vcenterUrl + ">.";

        // funny but search on the dvs does not work, we need to use rootFolder
        InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);

        DistributedVirtualPortgroup dpg = null;
        try {
            dpg = (DistributedVirtualPortgroup)inventoryNavigator.searchManagedEntity(
                    "DistributedVirtualPortgroup", name);
        } catch (RemoteException e ) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg, e);
        }
        
        if (dpg == null) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg);
        } 

        s_logger.info("Found " + description);
        return dpg;
    }

    public HostSystem getVmwareHost(String name,
            Datacenter dc, String dcName)
        throws RemoteException {
        String description = "<host " + name
                + ", datacenter " + dcName + ", vCenter " + vcenterUrl +">.";
        // narrow the search to the dc level
        InventoryNavigator inventoryNavigator = new InventoryNavigator(dc);

        HostSystem host = null;
        try {
            host = (HostSystem)inventoryNavigator.searchManagedEntity(
                    "HostSystem", name);
        } catch (RemoteException e ) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg, e);
        }

        if (host == null) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg);
        }

        s_logger.info("Found " + description);
        return host;
    }

    public boolean isVmEventOnMonitoredCluster(Event event, String hostName)
        throws RemoteException {
        String dcName;
        Datacenter dc;

        // If contrailClusterName is null, all clusters under datacenter 
        // are monitored by vcenter plugin.
        if (contrailClusterName == null)
            return true;

        if (event.getHost() != null) {
            hostName = event.getHost().getName();
            HostSystem host = getVmwareHost(hostName, contrailDC, contrailDataCenterName);

            if (host != null) {
                ClusterComputeResource cluster = (ClusterComputeResource) host.getParent();
                if (cluster != null) {
                    String clstrName = cluster.getName();
                    if (clstrName != null && clstrName.equals(contrailClusterName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public VirtualMachine getVmwareVirtualMachine(String name,
            HostSystem host, String hostName, String dcName)
        throws RemoteException {
        String description = "<virtual machine " + name + ", host " + hostName
                + ", datacenter " + dcName + ", vCenter " + vcenterUrl +">.";
        // narrow the search to the host level
        InventoryNavigator inventoryNavigator = new InventoryNavigator(host);

        VirtualMachine vm = null;
        try {
            vm = (VirtualMachine)inventoryNavigator.searchManagedEntity(
                    "VirtualMachine", name);
        } catch (RemoteException e ) {
            String msg = "VM1: Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg, e);
        }

        if (vm == null) {
            String msg = "VM2: Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RuntimeException(msg);
        }

        s_logger.info("Found " + description);
        return vm;
    }

    public SortedMap<String, VirtualNetworkInfo> readVirtualNetworks()
            throws Exception {

        s_logger.debug("Start reading virtual networks from vcenter ...");

        SortedMap<String, VirtualNetworkInfo> map =
                new ConcurrentSkipListMap<String, VirtualNetworkInfo>();

        if (contrailDVS == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " NOT configured");
            return map;
        }
        // Extract distributed virtual port groups
        DistributedVirtualPortgroup[] dvPgs = contrailDVS.getPortgroup();
        if (dvPgs == null || dvPgs.length == 0) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Distributed portgroups NOT configured");
            return map;
        }

        @SuppressWarnings("rawtypes")
        Hashtable[] pTables = PropertyCollectorUtil.retrieveProperties(dvPgs,
                                "DistributedVirtualPortgroup",
                new String[] {"name",
                "config.key",
                "config.defaultPortConfig",
                "config.vendorSpecificConfig",
                "summary.ipPoolId",
                "summary.ipPoolName",
                });

        for (int i=0; i < dvPgs.length; i++) {
            // Extract dvPg configuration info and port setting
            DVPortSetting portSetting = (DVPortSetting) pTables[i].get("config.defaultPortConfig");

            if (doIgnoreVirtualNetwork(portSetting)) {
                continue;
            }
            VirtualNetworkInfo vnInfo =
                    new VirtualNetworkInfo(
                            this, dvPgs[i], pTables[i], contrailDC, contrailDataCenterName,
                            contrailDVS, contrailDvSwitchName);

            s_logger.debug("Read from vcenter " + vnInfo + ", ipPoolId " + vnInfo.getIpPoolId());
            VCenterNotify.watchVn(vnInfo);
            map.put(vnInfo.getUuid(), vnInfo);
        }

        s_logger.debug("Done reading from vcenter, found " + map.size() + " Virtual Networks");
        return map;
    }

    private void readVirtualMachines(SortedMap<String, VirtualMachineInfo> map,
            ManagedEntity me,
            Datacenter dc, String dcName)
                throws Exception {

        ManagedEntity[] vms = new InventoryNavigator(me).searchManagedEntities("VirtualMachine");

        if (vms == null || vms.length == 0) {
            s_logger.debug("Datacenter: " + dcName +
                    " NO virtual machines connected");
            return;
        }

        String vrouterIpAddress = null;
        HostSystem host = null;
        // If the passed Managed Entity is at the host-level, then pass
        // the hostName and Contrail VM IP Address instead of finding
        // it every time for every VM which is costly.
        if (me instanceof HostSystem) {
            host = (HostSystem) me;
            String hostName = host.getName();
            vrouterIpAddress = getVRouterVMIpFabricAddress(hostName, host, dcName);
        }

        @SuppressWarnings("rawtypes")
        Hashtable[] pTables = PropertyCollectorUtil.retrieveProperties(vms, "VirtualMachine",
                new String[] {"name",
                "config.instanceUuid",
                "config.annotation",
                "runtime.powerState",
                "runtime.host",
                "guest.toolsRunningStatus",
                "guest.net"
                });

        for (int i=0; i < vms.length; i++) {
            VirtualMachineInfo vmInfo = new VirtualMachineInfo(this,
                    dc, dcName,
                    (VirtualMachine)vms[i], pTables[i],
                    host, vrouterIpAddress);

            readVirtualMachineInterfaces(vmInfo);

            // Ignore virtual machine?
            if (vmInfo.ignore()) {
                s_logger.debug(" Ignoring vm: " + vmInfo.getName());
                continue;
            }

            s_logger.info("Read from vcenter " + vmInfo);

            map.put(vmInfo.getUuid(), vmInfo);
        }
    }

    private void findHostsInFolder(Folder hostsFolder, List<HostSystem> hostsList)
            throws IOException, Exception {
        for (ManagedEntity e : hostsFolder.getChildEntity()) {
            if (e instanceof HostSystem) {
                hostsList.add((HostSystem)e);
            }

            // This is a cluster resource. Delve deeper to
            // find more hosts.
            if (e instanceof ComputeResource) {
                ComputeResource cr = (ComputeResource) e;
                if (e instanceof ClusterComputeResource) {
                    ClusterComputeResource cluster = (ClusterComputeResource) e;
                    if ((contrailClusterName != null) && 
                         (cluster.getName().equals(contrailClusterName) != true)) {
                       continue;
                   }
                }
                for(HostSystem host : cr.getHosts()) {
                    hostsList.add((HostSystem)host);
                }
            }

            if (e instanceof Folder) {
                findHostsInFolder((Folder)e, hostsList);
            }
        }
    }

    SortedMap<String, VirtualMachineInfo> readVirtualMachines()
            throws IOException, Exception {

        s_logger.info("Start reading virtual machines from vcenter ...");

        SortedMap<String, VirtualMachineInfo> map =
                new ConcurrentSkipListMap<String, VirtualMachineInfo>();

        /* the method below can be called in a loop to read multiple
         * datacenters and read VMs per hosts
         * for (dc: datacenters)
         * for (host: dc)
            readVirtualMachines(map, host, dc, dcName);
         */

        Folder hostsFolder = contrailDC.getHostFolder();

        if (hostsFolder == null) {
            s_logger.error("Unable to read VMs, hostFolder is null");
            return map;
        }

        List<HostSystem> hostsList = new ArrayList<HostSystem>();

        findHostsInFolder(hostsFolder, hostsList);

        for (HostSystem host : hostsList) {
            readVirtualMachines(map, host, contrailDC, contrailDataCenterName);
        }

        s_logger.info("Done reading from vcenter, found " + map.size() + " Virtual Machines");
        return map;
    }

    public void readVirtualMachineInterfaces(VirtualMachineInfo vmInfo)
            throws IOException, Exception {
        VirtualMachine vm = vmInfo.vm;
        Network[] nets = vm.getNetworks();

        for (Network net: nets) {

            VirtualNetworkInfo vnInfo = null;
            switch (mode) {
            case VCENTER_ONLY:
                String netName = net.getName();
                vnInfo = MainDB.getVnByName(netName);
                if (vnInfo == null) {
                    if (mode == Mode.VCENTER_ONLY) {
                        s_logger.info("Skipping VMI in unmanaged network " + netName);
                        continue;
                    }
                }
                break;
            case VCENTER_AS_COMPUTE:
                // network is managed by Openstack or other entity
                // UUID is used in the name because name is not unique
                String uuid = net.getName();
                /*
                From Mitaka nova driver will append cluster_id to port group
                therefore need to extract the appended cluster id
                */
                uuid = uuid.substring(Math.max(0, uuid.length() - 36));
                vnInfo = MainDB.getVnById(uuid);
                if (vnInfo == null) {
                    s_logger.info("Skipping VMI in unmanaged network " + uuid);
                    continue;
                }
                break;
            default:
                throw new Exception("Unhandled mode " + mode.name());
            }

            VirtualMachineInterfaceInfo vmiInfo =
                    new VirtualMachineInterfaceInfo(vmInfo, vnInfo);

            vmiInfo.setMacAddress(getVirtualMachineMacAddress(vm.getConfig(), vnInfo.getDpg()));

            if (mode != Mode.VCENTER_AS_COMPUTE && vnInfo.getExternalIpam() ){
                if (vmInfo.getToolsRunningStatus().equals(
                        VirtualMachineToolsRunningStatus.guestToolsRunning.toString())) {
                    // static IP Address & vmWare tools installed
                    // see if we can read it from Guest Nic Info
                    vmiInfo.setIpAddress(getVirtualMachineIpAddress(vm, vnInfo.getName()));
                }
                VCenterNotify.watchVm(vmiInfo.vmInfo);
            }
            vmInfo.created(vmiInfo);
        }
    }

    public boolean isAlive()  {
        try {
            lastTimeSeenAlive = null;
            lastTimeSeenAlive = serviceInstance.currentTime();
            if (lastTimeSeenAlive == null) {
                operationalStatus = "Failed aliveness check for vCenter " + vcenterUrl;
                s_logger.error(operationalStatus);
                return false;
            }
        } catch (Exception e) {
            operationalStatus = "Failed aliveness check for vCenter " + vcenterUrl;
            s_logger.error(operationalStatus);
            return false;
        }
        return true;
    }

    public Calendar getLastTimeSeenAlive() {
        return lastTimeSeenAlive;
    }

    public VmwareDistributedVirtualSwitch getContrailDvs() {
        return contrailDVS;
    }

    private static TaskInfo waitFor(Task task) throws RemoteException, InterruptedException {
        while(true)
        {
            TaskInfo ti = task.getTaskInfo();
            TaskInfoState state = ti.getState();
            if(state == TaskInfoState.success || state == TaskInfoState.error)
            {
                return ti;
            }
            Thread.sleep(1000);
        }
    }

    public DistributedVirtualPortgroup createVmwareDPG(VmwareDistributedVirtualSwitch dvs, String vnName)
            throws DvsFault, DuplicateName, InvalidName, RuntimeFault, RemoteException, InterruptedException {

        InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);
        
        DistributedVirtualPortgroup dpg = null;
        try {
            dpg = (DistributedVirtualPortgroup)inventoryNavigator.searchManagedEntity(
                  "DistributedVirtualPortgroup", vnName);
        } catch (RemoteException e ) {
            // Silently discard the exception, since we are checking to just see
            // port group already exists. We will proceed with creating port group
            // anyways as we don't want exception for non-existing port group lead
            // to not creating valid port group.
        }
        
        if (dpg != null) {
            return dpg;
        }

        // create port group under this DVS 
        DVPortgroupConfigSpec dvpgs = new DVPortgroupConfigSpec();
        dvpgs.setName(vnName);
        dvpgs.setNumPorts(128);
        dvpgs.setType("earlyBinding");

        VMwareDVSPortgroupPolicy pgPolicy = new VMwareDVSPortgroupPolicy();
        pgPolicy.setBlockOverrideAllowed(true);
        pgPolicy.setVlanOverrideAllowed(true);
        dvpgs.setPolicy(pgPolicy);

        VMwareDVSPortSetting dvsPort = new VMwareDVSPortSetting();
        dvpgs.setDefaultPortConfig(dvsPort);
        
        DVPortgroupConfigSpec[] portGroups = new DVPortgroupConfigSpec[] {dvpgs};
        Task task_pg = dvs.addDVPortgroup_Task(portGroups);

        TaskInfo ti = waitFor(task_pg);

        if(ti.getState() == TaskInfoState.error)
        {
            s_logger.error("Failed to create a new Distributed port group." + vnName);
            return null;
        }
        ManagedObjectReference pgMor = (ManagedObjectReference) ti.getResult();
        DistributedVirtualPortgroup pg = (DistributedVirtualPortgroup)
        MorUtil.createExactManagedEntity(dvs.getServerConnection(), pgMor);
        return pg;
    }

    public void deleteVmwarePG(final DistributedVirtualPortgroup portGroup)
        throws RemoteException {
        InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);
        
        DistributedVirtualPortgroup dpg = null;
        try {
            dpg = (DistributedVirtualPortgroup)inventoryNavigator.searchManagedEntity(
                  "DistributedVirtualPortgroup", portGroup.getName());
        } catch (RemoteException e ) {
            // Silently discard the exception, since we are checking to just see
            // port group already exists. We will proceed with deleting port group
            // anyways as we don't want exception for existing port group lead
            // to not deleting valid port group.
        }
        
        if (dpg == null) {
            return;
        }
        try
        {
            portGroup.destroy_Task();
        }
        catch (Exception e)
        {
            String message =
                "Could not delete the port group '" + portGroup.getName() + "' because of: "
                    + e.getMessage();

            s_logger.error(message);
            throw new RemoteException(message, e);
        }
    }


}
