/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.io.FileNotFoundException;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.VirtualMachineToolsRunningStatus;
import org.apache.log4j.Logger;
import com.vmware.vim25.DVPortSetting;
import com.vmware.vim25.DVPortgroupConfigInfo;
import com.vmware.vim25.DVSConfigInfo;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.IpPool;
import com.vmware.vim25.IpPoolIpPoolConfigInfo;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.NetIpConfigInfo;
import com.vmware.vim25.NetIpConfigInfoIpAddress;
import com.vmware.vim25.NetworkSummary;
import com.vmware.vim25.VMwareDVSPortSetting;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
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
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.util.PropertyCollectorUtil;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;
import com.vmware.vim25.mo.ComputeResource; 

public class VCenterDB {
    private static final Logger s_logger =
            Logger.getLogger(VCenterDB.class);
    private static final String esxiToVRouterIpMapFile = "/etc/contrail/ESXiToVRouterIp.map";
    static final int VCENTER_READ_TIMEOUT = 30000; //30 sec
    protected final String contrailDvSwitchName;
    private final String contrailDataCenterName;
    private final String vcenterUrl;
    private final String vcenterUsername;
    private final String vcenterPassword;
    private final String contrailIpFabricPgName;
    final Mode mode;
    
    private volatile ServiceInstance serviceInstance;
    private volatile Folder rootFolder;
    private volatile InventoryNavigator inventoryNavigator;
    private volatile IpPoolManager ipPoolManager;
    protected volatile Datacenter contrailDC;
    protected volatile VmwareDistributedVirtualSwitch contrailDVS;
    private volatile SortedMap<String, VirtualNetworkInfo> prevVmwareVNInfos;
    private volatile ConcurrentMap<String, Datacenter> datacenters;
    private volatile ConcurrentMap<String, VmwareDistributedVirtualSwitch> dvswitches;

    public volatile Map<String, String> esxiToVRouterIpMap;
    public static volatile Map<String, Boolean> vRouterActiveMap;

    public VCenterDB(String vcenterUrl, String vcenterUsername,
                     String vcenterPassword, String contrailDcName,
                     String contrailDvsName, String ipFabricPgName, Mode mode) {
        this.vcenterUrl             = vcenterUrl;
        this.vcenterUsername        = vcenterUsername;
        this.vcenterPassword        = vcenterPassword;
        this.contrailDataCenterName = contrailDcName;
        this.contrailDvSwitchName   = contrailDvsName;
        this.contrailIpFabricPgName = ipFabricPgName;
        this.mode                   = mode;

        s_logger.info("VCenterDB(" + contrailDvsName + ", " + ipFabricPgName + ")");        
        vRouterActiveMap = new HashMap<String, Boolean>();
        datacenters = new ConcurrentHashMap<String, Datacenter>();
        dvswitches = new ConcurrentHashMap<String, VmwareDistributedVirtualSwitch>();
        
        // Create ESXi host to vRouterVM Ip address map
        buildEsxiToVRouterIpMap();
    }

    private boolean initData() {
        rootFolder = null;
        rootFolder = serviceInstance.getRootFolder();
        if (rootFolder == null) {
            s_logger.error("Failed to get rootfolder for vCenter ");
            return false;
        }
        s_logger.info("Got rootfolder for vCenter ");

        inventoryNavigator = null;
        inventoryNavigator = new InventoryNavigator(rootFolder);
        if (inventoryNavigator == null) {
            s_logger.error("Failed to get InventoryNavigator for vCenter ");
            return false;
        }
        s_logger.info("Got InventoryNavigator for vCenter ");

        ipPoolManager = null;
        ipPoolManager = serviceInstance.getIpPoolManager();
        if (ipPoolManager == null) {
            s_logger.error("Failed to get ipPoolManager for vCenter ");
            return false;
        }
        s_logger.info("Got ipPoolManager for vCenter ");

        // Search contrailDc
        contrailDC = null;
        try {
            contrailDC = (Datacenter) inventoryNavigator.searchManagedEntity(
                                      "Datacenter", contrailDataCenterName);
        } catch (InvalidProperty e) {
                return false;
        } catch (RuntimeFault e) {
                return false;
        } catch (RemoteException e) {
                return false;
        }
        if (contrailDC == null) {
            s_logger.error("Failed to find " + contrailDataCenterName 
                           + " DC on vCenter ");
            return false;
        }
        s_logger.info("Found " + contrailDataCenterName + " DC on vCenter ");
        datacenters.put(contrailDataCenterName, contrailDC);

        // Search contrailDvSwitch
        contrailDVS = null;
        try {
            contrailDVS = (VmwareDistributedVirtualSwitch)
                            inventoryNavigator.searchManagedEntity(
                                    "VmwareDistributedVirtualSwitch",
                                    contrailDvSwitchName);
        } catch (InvalidProperty e) {
                return false;
        } catch (RuntimeFault e) {
                return false;
        } catch (RemoteException e) {
                return false;
        }

        if (contrailDVS == null) {
            s_logger.error("Failed to find " + contrailDvSwitchName + 
                           " DVSwitch on vCenter");
            return false;
        }
        s_logger.info("Found " + contrailDvSwitchName + " DVSwitch on vCenter ");
        dvswitches.put(contrailDvSwitchName, contrailDVS);

        // All well on vCenter front.
        return true;
    }

    public boolean connect() {        
        while(!createServiceInstance()) {
            try{
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }

        }

        while(!initData() ) {
            try{
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }

        }
        return true;
    }

    private boolean createServiceInstance() {
        try {
            serviceInstance = null;
            s_logger.info("Trying to Connect to vCenter Server : " + "("
                    + vcenterUrl + "," + vcenterUsername + ")");
            serviceInstance = new ServiceInstance(new URL(vcenterUrl),
                                        vcenterUsername, vcenterPassword, true);
            if (serviceInstance == null) {
                s_logger.error("Failed to connect to vCenter Server : " + "("
                                + vcenterUrl + "," + vcenterUsername + ","
                                + vcenterPassword + ")" + "Retrying after 5 secs");
                return false;
            }
            s_logger.info("Connected to vCenter Server : " + "("
                    + vcenterUrl + "," + vcenterUsername + "," 
                    + vcenterPassword + ")");
            return true;
        } catch (MalformedURLException e) {
                s_logger.info("Re-Connect unsuccessful: " + e.getMessage());
                return false;
        } catch (RemoteException e) {
                s_logger.info("Re-Connect unsuccessful: " + e.getMessage());
                return false;
        } catch (Exception e) {
                s_logger.error("Error while connecting to vcenter: " + e.getMessage());
                e.printStackTrace();
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
                s_logger.info(" ESXi IP Address:" + part[0] + " vRouter-IP-Address: " + part[1]);
                esxiToVRouterIpMap.put(part[0], part[1]);
                vRouterActiveMap.put(part[1], true);
            }
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
       s_logger.info("ServiceInstance read timeout set to " + 
           serviceInstance.getServerConnection().getVimService().getWsc().getReadTimeout());
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
    
    protected static String getVirtualMachineMacAddress(
            VirtualMachineConfigInfo vmConfigInfo,
            DistributedVirtualPortgroup portGroup) {
        VirtualDevice devices[] = vmConfigInfo.getHardware().getDevice();
        for (VirtualDevice device : devices) {
            // XXX Assuming only one interface
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
        if (host.getRuntime().isInMaintenanceMode())
            vRouterActiveMap.put(vRouterIpAddress, false);
        if (vRouterIpAddress != null) {
            return vRouterIpAddress;
        }

        VirtualMachine[] vms = host.getVms();
        for (VirtualMachine vm : vms) {
            String vmName = vm.getName();
            if (!vmName.toLowerCase().contains(vmNamePrefix.toLowerCase())) {
                continue;
            }
            // XXX Assumption here is that VMware Tools are installed
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


    private String getVirtualMachineIpAddress(String dvPgName,
            String hostName, HostSystem host, String vmNamePrefix) 
                    throws Exception {
        VirtualMachine[] vms = host.getVms();
        for (VirtualMachine vm : vms) {
            String vmName = vm.getName();
            if (!vmName.toLowerCase().contains(vmNamePrefix.toLowerCase())) {
                continue;
            }
            // XXX Assumption here is that VMware Tools are installed
            // and IP address is available
            GuestInfo guestInfo = vm.getGuest();
            if (guestInfo == null) {
                s_logger.debug("dvPg: " + dvPgName + " host: " + hostName +
                        " vm:" + vmName + " GuestInfo - VMware Tools " +
                        " NOT installed");
                continue;
            }
            GuestNicInfo[] nicInfos = guestInfo.getNet();
            if (nicInfos == null) {
                s_logger.debug("dvPg: " + dvPgName + " host: " + hostName +
                        " vm:" + vmName + " GuestNicInfo - VMware Tools " +
                        " NOT installed");
                continue;
            }
            for (GuestNicInfo nicInfo : nicInfos) {
                // Extract the IP address associated with simple port 
                // group. Assumption here is that Contrail VRouter VM will
                // have only one standard port group
                String networkName = nicInfo.getNetwork();
                if (networkName == null) {
                    continue;
                }
                Network network = (Network)
                        inventoryNavigator.searchManagedEntity("Network",
                                networkName);
                if (network == null) {
                    s_logger.debug("dvPg: " + dvPgName + "host: " + 
                            hostName + " vm: " + vmName + " network: " +
                            networkName + " NOT found");
                    continue;
                }
                if (network instanceof DistributedVirtualPortgroup) {
                    s_logger.debug("dvPg: " + dvPgName + "host: " + 
                            hostName + "vm: " + vmName + " network: " +
                            networkName + " is distributed virtual port " +
                            "group");
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
                        return ipAddress;
                    }
                }

            }
        }
        return null;
    }

    String getVirtualMachineIpAddress(VirtualMachine vm, String dvPgName)
                    throws Exception {

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
            if (networkName == null) {
                continue;
            }

            if (!networkName.equals(dvPgName)) {
                continue;
            }

            Network network = (Network) 
                    inventoryNavigator.searchManagedEntity("Network",
                            networkName);
            if (network == null) {
                s_logger.debug("dvPg: " + dvPgName
                        + " vm: " + vmName + " network: " + 
                        networkName + " NOT found");
                continue;
            }
            if (network instanceof DistributedVirtualPortgroup) {
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
                        return ipAddress;
                    }
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

    public HashMap<String, Short> getVlanInfo(DistributedVirtualPortgroup dvPg,
        DVPortSetting portSetting,
        VMwareDVSPvlanMapEntry[] pvlanMapArray) {

        // Create HashMap which will store private vlan info
        HashMap<String, Short> vlan = new HashMap<String, Short>();

        if (portSetting instanceof VMwareDVSPortSetting) {
            VMwareDVSPortSetting vPortSetting = 
                    (VMwareDVSPortSetting) portSetting;
            VmwareDistributedVirtualSwitchVlanSpec vlanSpec = 
                    vPortSetting.getVlan();
            if (vlanSpec instanceof VmwareDistributedVirtualSwitchPvlanSpec) {
                // Find isolated secondary VLAN Id
                VmwareDistributedVirtualSwitchPvlanSpec pvlanSpec = 
                        (VmwareDistributedVirtualSwitchPvlanSpec) vlanSpec;
                short isolatedVlanId = (short)pvlanSpec.getPvlanId();
                // Find primaryVLAN corresponding to isolated secondary VLAN
                short primaryVlanId = 0;
                for (short i=0; i < pvlanMapArray.length; i++) {
                    if ((short)pvlanMapArray[i].getSecondaryVlanId() != isolatedVlanId)
                        continue;
                    if (!pvlanMapArray[i].getPvlanType().equals("isolated"))
                        continue;
                    s_logger.debug("    VlanType = PrivateVLAN"
                                  + " PrimaryVLAN = " + pvlanMapArray[i].getPrimaryVlanId() 
                                  + " IsolatedVLAN = " + pvlanMapArray[i].getSecondaryVlanId());
                    primaryVlanId = (short)pvlanMapArray[i].getPrimaryVlanId();
                }
                vlan.put("primary-vlan", primaryVlanId);
                vlan.put("secondary-vlan", isolatedVlanId);
            } else if (vlanSpec instanceof VmwareDistributedVirtualSwitchVlanIdSpec) {
                VmwareDistributedVirtualSwitchVlanIdSpec vlanIdSpec = 
                        (VmwareDistributedVirtualSwitchVlanIdSpec) vlanSpec;
                short vlanId = (short)vlanIdSpec.getVlanId();
                s_logger.debug("    VlanType = VLAN " + " VlanId = " + vlanId);
                vlan.put("primary-vlan", vlanId);
                vlan.put("secondary-vlan", vlanId);
            } else {
                s_logger.error("dvPg: " + dvPg.getName() + 
                        " port setting: " +  vPortSetting + 
                        ": INVALID vlan spec: " + vlanSpec);
                return null;
            }
        }

        return vlan;
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

    private String getVirtualMachineIpAddress(GuestNicInfo[] nicInfos, 
                                              String dvPgName, 
                                              String vmName, String vmMac)
                                             throws Exception {

        // Assumption here is that VMware Tools are installed
        // and IP address is available
        if (nicInfos == null) {
            s_logger.debug("dvPg: " + dvPgName + " vm:" + vmName
                    + " GuestNicInfo - VMware Tools " + " NOT installed");
            return null;
        }
        for (GuestNicInfo nicInfo : nicInfos) {
            // Extract the IP address associated with interface based on macAddress.
            String guestMac = nicInfo.getMacAddress();

            if (guestMac == null) {
                continue;
            }

            if (!guestMac.equals(vmMac)) {
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
                    return ipAddress;
                }
            }
        }
        return null;
    }
  
    public String getVcenterUrl() { 
        return vcenterUrl; 
    }
    
    public Datacenter getVmwareDatacenter(String name)
        throws RemoteException {
        String description = "<datacenter " + name
                + ", vCenter " + vcenterUrl + ">.";

        if (datacenters.containsKey(name)) {
            s_logger.info("Found in cache " + description);
            return datacenters.get(name);
        }

        Folder rootFolder = serviceInstance.getRootFolder();
        if (rootFolder == null) {
            String msg = "Failed to get rootfolder for vCenter " + vcenterUrl;
            s_logger.error(msg);
            throw new RemoteException(msg);
        }
        InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);
        Datacenter dc = null;
        try {
            dc = (Datacenter) inventoryNavigator.searchManagedEntity(
                          "Datacenter", name);
        } catch (RemoteException e ) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg, e);
        }

        if (dc == null) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg);
        }

        datacenters.put(name, dc);

        s_logger.info("Found " + description);
        return dc;
    }

    public VmwareDistributedVirtualSwitch getVmwareDvs(String name,
            Datacenter dc, String dcName)
                    throws RemoteException {
        String description = "<dvs " + name
                + ", datacenter " + dcName
                + ", vCenter " + vcenterUrl + ">.";

        if (dvswitches.containsKey(name)) {
            s_logger.info("Found in cache " + description);
            return dvswitches.get(name);
        }

        InventoryNavigator inventoryNavigator = new InventoryNavigator(dc);

        VmwareDistributedVirtualSwitch dvs = null;
        try {
            dvs = (VmwareDistributedVirtualSwitch)inventoryNavigator.searchManagedEntity(
                    "VmwareDistributedVirtualSwitch", name);
        } catch (RemoteException e ) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg, e);
        }

        if (dvs == null) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg);
        }

        s_logger.info("Found " + description);
        dvswitches.put(name, dvs);
        return dvs;
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
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg, e);
        }

        if (vm == null) {
            String msg = "Failed to retrieve " + description;
            s_logger.error(msg);
            throw new RemoteException(msg);
        }

        s_logger.info("Found " + description);
        return vm;
    }
      
    public SortedMap<String, VirtualNetworkInfo> readVirtualNetworks() 
            throws Exception {

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
        
        // Extract IP Pools
        IpPool[] ipPools = ipPoolManager.queryIpPools(contrailDC);
        if ((mode != Mode.VCENTER_AS_COMPUTE) && (ipPools == null || ipPools.length == 0)) {
            s_logger.debug("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " IP Pools NOT " +
                    "configured");
            return map;
        }
    
        // Extract private vlan entries for the virtual switch
        VMwareDVSConfigInfo dvsConfigInfo = (VMwareDVSConfigInfo) contrailDVS.getConfig();
        if (dvsConfigInfo == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " ConfigInfo " +
                    "is empty");
            return map;
        }
    
        if (!(dvsConfigInfo instanceof VMwareDVSConfigInfo)) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " ConfigInfo " +
                    "isn't instanceof VMwareDVSConfigInfo");
            return map;
        }
    
        VMwareDVSPvlanMapEntry[] pvlanMapArray = dvsConfigInfo.getPvlanConfig();
        if (pvlanMapArray == null) {
            s_logger.error("dvSwitch: " + contrailDvSwitchName +
                    " Datacenter: " + contrailDC.getName() + " Private VLAN NOT" +
                    "configured");
            return map;
        }
    
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
                            contrailDVS, contrailDvSwitchName,
                            ipPools, pvlanMapArray);
            
            VCenterNotify.watchVn(vnInfo);
            map.put(vnInfo.getUuid(), vnInfo);
        }

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

        Hashtable[] pTables = PropertyCollectorUtil.retrieveProperties(vms, "VirtualMachine",
                new String[] {"name",
                "config.instanceUuid",
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

            map.put(vmInfo.getUuid(), vmInfo);
        }
    }

    SortedMap<String, VirtualMachineInfo> readVirtualMachines() 
            throws IOException, Exception {
        
        SortedMap<String, VirtualMachineInfo> map =
                new ConcurrentSkipListMap<String, VirtualMachineInfo>();
        
        /* the method below can be called in a loop to read multiple  
         * datacenters and read VMs per hosts     
         * for (dc: datacenters)
         * for (host: dc)
            readVirtualMachines(map, host, dc, dcName);
         */

        Folder hostsFolder = contrailDC.getHostFolder();
        List<HostSystem> hostsList = new ArrayList<HostSystem>();

        for (ManagedEntity e : hostsFolder.getChildEntity()) {
            // This is a cluster resource. Delve deeper to 
            // find more hosts.
            if (e instanceof ComputeResource) {
                ComputeResource cluster = (ComputeResource) e;
                for(HostSystem host : cluster.getHosts()) {
                    hostsList.add((HostSystem)host);
                }
            }

            if (e instanceof HostSystem) {
                hostsList.add((HostSystem)e);
            }
        }

        for (HostSystem host : hostsList) {
            readVirtualMachines(map, host, contrailDC, contrailDataCenterName);
        }
        
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
            
            if (mode != Mode.VCENTER_AS_COMPUTE && vnInfo.getExternalIpam() 
                && vmInfo.getToolsRunningStatus().equals(VirtualMachineToolsRunningStatus.guestToolsRunning)) {
                // static IP Address & vmWare tools installed
                // see if we can read it from Guest Nic Info
                String ipAddr = getVirtualMachineIpAddress(vm, vnInfo.getName());
                vmiInfo.setIpAddress(ipAddr);
                VCenterNotify.watchVm(vmiInfo.vmInfo);
            }
            vmInfo.created(vmiInfo);
        }
    }

    public boolean isAlive()  {
        Folder folder = serviceInstance.getRootFolder();
        if (folder == null) {
            String msg = "Failed aliveness check for vCenter " + vcenterUrl;
            s_logger.error(msg);
            return false;
        }
        return true;
    }
}
