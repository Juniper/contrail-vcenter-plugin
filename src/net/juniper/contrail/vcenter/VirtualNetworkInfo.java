package net.juniper.contrail.vcenter;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import com.google.common.net.InetAddresses;
import com.vmware.vim25.DVPortSetting;
import com.vmware.vim25.DVSConfigInfo;
import com.vmware.vim25.DistributedVirtualSwitchKeyedOpaqueBlob;
import com.vmware.vim25.Event;
import com.vmware.vim25.IpPool;
import com.vmware.vim25.IpPoolIpPoolConfigInfo;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.VMwareDVSConfigInfo;
import com.vmware.vim25.VMwareDVSPvlanMapEntry;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.util.PropertyCollectorUtil;

public class VirtualNetworkInfo extends VCenterObject {
    private String uuid; // required attribute, key for this object
    private String name;
    private short isolatedVlanId;
    private short primaryVlanId;
    private SortedMap<String, VirtualMachineInterfaceInfo> vmiInfoMap; // key is MAC address
    private Integer ipPoolId;
    private String subnetAddress;
    private String subnetMask;
    private String gatewayAddress;
    private boolean ipPoolEnabled;
    private String range;
    private boolean externalIpam;

    // Vmware
    com.vmware.vim25.mo.Network net;
    DistributedVirtualPortgroup dpg;
    com.vmware.vim25.mo.VmwareDistributedVirtualSwitch dvs;
    String dvsName;
    com.vmware.vim25.mo.Datacenter dc;
    String dcName;

    // API server
    net.juniper.contrail.api.types.VirtualNetwork apiVn;

    public VirtualNetworkInfo(String uuid) {
        this.uuid = uuid;
        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
    }

    public VirtualNetworkInfo(String uuid, String name,
            boolean externalIpam, boolean ipPoolEnabled,
            String subnetAddress,String subnetMask, String range, String gatewayAddress,
            short primaryVlanId, short isolatedVlanId)
    {
        this.uuid = uuid;
        this.name = name;
        this.externalIpam = externalIpam;
        this.ipPoolEnabled = ipPoolEnabled;
        this.subnetAddress = subnetAddress;
        this.subnetMask = subnetMask;
        this.range = range;
        this.gatewayAddress = gatewayAddress;
        this.primaryVlanId = primaryVlanId;
        this.isolatedVlanId = isolatedVlanId;

        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
    }

    public VirtualNetworkInfo(VirtualNetworkInfo vnInfo)
    {
        this.uuid = vnInfo.uuid;
        this.name = vnInfo.name;
        this.subnetAddress = vnInfo.subnetAddress;
        this.subnetMask = vnInfo.subnetMask;
        this.gatewayAddress = vnInfo.gatewayAddress;
        this.primaryVlanId = vnInfo.primaryVlanId;
        this.isolatedVlanId = vnInfo.isolatedVlanId;
        this.ipPoolEnabled = vnInfo.ipPoolEnabled;
        this.range = vnInfo.range;
        this.externalIpam = vnInfo.externalIpam;

        this.vmiInfoMap = vnInfo.vmiInfoMap;
    }

    public VirtualNetworkInfo(net.juniper.contrail.api.types.VirtualNetwork vn) {
        this.apiVn = vn;
        this.uuid = vn.getUuid();
        name = vn.getName();
        this.vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
        this.externalIpam = vn.getExternalIpam();
    }

    public VirtualNetworkInfo(Event event,  VCenterDB vcenterDB) throws Exception {
        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();

        if (event.getDatacenter() != null) {
            dcName = event.getDatacenter().getName();
            dc = vcenterDB.getVmwareDatacenter(dcName);
        }

        if (event.getDvs() != null) {
            dvsName = event.getDvs().getName();
            dvs = vcenterDB.getVmwareDvs(dvsName, dc, dcName);
        } else {
            dvsName = vcenterDB.contrailDvSwitchName;
            dvs = vcenterDB.getVmwareDvs(dvsName, dc, dcName);
        }

        if (event.getNet() != null) {
            name = event.getNet().getName();
            net = vcenterDB.getVmwareNetwork(name, dvs, dvsName, dcName);
        }

        dpg = vcenterDB.getVmwareDpg(name, dvs, dvsName, dcName);
        ManagedObject mo[] = new ManagedObject[1];
        mo[0] = dpg;

        Hashtable[] pTables = PropertyCollectorUtil.retrieveProperties(mo,
                                "DistributedVirtualPortgroup",
                new String[] {"name",
                "config.key",
                "config.defaultPortConfig",
                "config.vendorSpecificConfig",
                "summary.ipPoolId",
                "summary.ipPoolName",
                });

        if (pTables == null || pTables[0] == null) {
            throw new RemoteException("Could not read properties for network " + name);
        }

        populateInfo(vcenterDB, pTables[0]);
    }

    public VirtualNetworkInfo(VCenterDB vcenterDB,
            DistributedVirtualPortgroup dpg, Hashtable pTable,
            com.vmware.vim25.mo.Datacenter dc, String dcName,
            com.vmware.vim25.mo.VmwareDistributedVirtualSwitch dvs,
            String dvsName,
            IpPool[] ipPools,
            VMwareDVSPvlanMapEntry[] pvlanMapArray) throws Exception {

        if (vcenterDB == null || dpg == null || pTable == null
                || dvs == null || dvsName == null
                || dc == null || dcName == null
                || pvlanMapArray == null) {
            throw new IllegalArgumentException();
        }
        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
        this.dc = dc;
        this.dcName = dcName;
        this.dpg = dpg;
        this.dvs = dvs;
        this.dvsName = dvsName;

        populateInfo(vcenterDB, pTable);
    }

    void populateInfo(VCenterDB vcenterDB, Hashtable pTable) throws Exception {
        populateVlans(vcenterDB, pTable);

        name = (String) pTable.get("name");

        switch(vcenterDB.mode) {
        case VCENTER_AS_COMPUTE:
            // UUID is allocated by OpenStack and saved in the name field
            uuid = name;
            break;
        case VCENTER_ONLY:
            // UUID is allocated by the plugin
            String key = (String) pTable.get("config.key");
            byte[] vnKeyBytes = key.getBytes();
            uuid = UUID.nameUUIDFromBytes(vnKeyBytes).toString();
            break;
        default:
            throw new Exception("Unhandled mode " + vcenterDB.mode.name());
        }

        populateAddressManagement(vcenterDB, pTable);
    }

    private void populateAddressManagement(VCenterDB vcenterDB,
            Hashtable pTable)
            throws RuntimeFault, RemoteException, Exception {

        setIpPoolId( (Integer) pTable.get("summary.ipPoolId"), vcenterDB);

        // Read externalIpam flag from custom field
        DistributedVirtualSwitchKeyedOpaqueBlob[] opaqueBlobs = null;
        Object obj = pTable.get("config.vendorSpecificConfig");
        if (obj instanceof DistributedVirtualSwitchKeyedOpaqueBlob[]) {
            opaqueBlobs = (DistributedVirtualSwitchKeyedOpaqueBlob[]) obj;
        }
        externalIpam = vcenterDB.getExternalIpamInfo(opaqueBlobs, name);
    }

    private void populateVlans(VCenterDB vcenterDB, Hashtable pTable) throws Exception {
        // get pvlan/vlan info for the portgroup.
        // Extract private vlan entries for the virtual switch
        DVSConfigInfo dvsConf = dvs.getConfig();

        if (dvsConf == null) {
            throw new Exception("dvSwitch: " + dvsName +
                    " Datacenter: " + dcName + " ConfigInfo " +
                    "is empty");
        }

        if (!(dvsConf instanceof VMwareDVSConfigInfo)) {
            throw new Exception("dvSwitch: " + dvsName +
                    " Datacenter: " + dcName + " ConfigInfo " +
                    "isn't instanceof VMwareDVSConfigInfo");
        }
        VMwareDVSConfigInfo dvsConfigInfo = (VMwareDVSConfigInfo) dvsConf;

        VMwareDVSPvlanMapEntry[] pvlanMapArray = dvsConfigInfo.getPvlanConfig();
        if (pvlanMapArray == null) {
            throw new Exception("dvSwitch: " + dvsName +
                    " Datacenter: " + dcName + " Private VLAN NOT" +
                    "configured");
        }

        // Extract dvPg configuration info and port setting
        DVPortSetting portSetting = (DVPortSetting) pTable.get("config.defaultPortConfig");
        HashMap<String, Short> vlan = vcenterDB.getVlanInfo(dpg, portSetting, pvlanMapArray);
        if (vlan == null) {
            return;
        }

        primaryVlanId = vlan.get("primary-vlan");
        isolatedVlanId = vlan.get("secondary-vlan");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public short getIsolatedVlanId() {
        return isolatedVlanId;
    }

    public void setIsolatedVlanId(short vlanId) {
        this.isolatedVlanId = vlanId;
    }

    public short getPrimaryVlanId() {
        return primaryVlanId;
    }

    public void setPrimaryVlanId(short vlanId) {
        this.primaryVlanId = vlanId;
    }

    // "virtual-network"s parent is "project"
    public String getProjectUuid() {
        return apiVn.getParentUuid();
    }

    public boolean getIpPoolEnabled() {
        return ipPoolEnabled;
    }

    public void setIpPoolEnabled(boolean _ipPoolEnabled) {
        this.ipPoolEnabled = _ipPoolEnabled;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String _range) {
        this.range = _range;
    }

    public SortedMap<String, VirtualMachineInterfaceInfo> getVmiInfo() {
        return vmiInfoMap;
    }

    private IpPool getIpPool(Integer poolid, IpPool[] ipPools) {
        if (poolid == null) {
            // there is a vmware bug in which the ip pool association
            // is lost upon vcenter restart.
            // Retrieve the pool based on name
            // Remove this code if vmware bug is fixed
            String IpPoolForPG = "ip-pool-for-" + name;
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

    public Integer getIpPoolId() {
        return ipPoolId;
    }

    public void setIpPoolId(Integer poolId, VCenterDB vcenterDB)
            throws RuntimeFault, RemoteException {


        // Extract IP Pools
        IpPool[] ipPools = vcenterDB.getIpPoolManager().queryIpPools(dc);
        if ( ipPools == null || ipPools.length == 0) {
            return;
        }

        IpPool ipPool = getIpPool(poolId, ipPools);
        if (ipPool != null) {
            IpPoolIpPoolConfigInfo ipConfigInfo = ipPool.getIpv4Config();

            // ifconfig setting
            subnetAddress = ipConfigInfo.getSubnetAddress();
            subnetMask = ipConfigInfo.getNetmask();
            gatewayAddress = ipConfigInfo.getGateway();
            ipPoolEnabled = ipConfigInfo.getIpPoolEnabled();
            range = ipConfigInfo.getRange();
            this.ipPoolId = ipPool.id;
        } else {
            subnetAddress = null;
            subnetMask = null;
            gatewayAddress = null;
            ipPoolEnabled = false;
            range = null;
            this.ipPoolId = null;
        }
    }

    public String getSubnetAddress() {
        return subnetAddress;
    }

    public void setSubnetAddress(String subnetAddress) {
        this.subnetAddress = subnetAddress;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public String getGatewayAddress() {
        return gatewayAddress;
    }

    public void setGatewayAddress(String gatewayAddress) {
        this.gatewayAddress = gatewayAddress;
    }

    public boolean getExternalIpam() {
        return externalIpam;
    }

    public void setExternalIpam(boolean externalIpam) {
        this.externalIpam = externalIpam;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public DistributedVirtualPortgroup getDpg() {
        return dpg;
    }

    public void setDpg(DistributedVirtualPortgroup dpg) {
        this.dpg = dpg;
    }

    public void created(VirtualMachineInterfaceInfo vmiInfo) {
        vmiInfoMap.put(vmiInfo.getMacAddress(), vmiInfo);
    }

    public void updated(VirtualMachineInterfaceInfo vmiInfo) {
        if (!vmiInfoMap.containsKey(vmiInfo.getMacAddress())) {
            vmiInfoMap.put(vmiInfo.getMacAddress(), vmiInfo);
        }
    }

    public void deleted(VirtualMachineInterfaceInfo vmiInfo) {
        if (vmiInfoMap.containsKey(vmiInfo.getMacAddress())) {
            vmiInfoMap.remove(vmiInfo.getMacAddress());
        }
    }

    public boolean contains(VirtualMachineInterfaceInfo vmiInfo) {
        return vmiInfoMap.containsKey(vmiInfo.getMacAddress());
    }

    public boolean equals(VirtualNetworkInfo vn) {
        if (vn == null) {
            return false;
        }
        if (name != null && !name.equals(vn.name)
                || name == null && vn.name != null) {
            return false;
        }
        if (uuid != null && !uuid.equals(vn.uuid)
                || uuid == null && vn.uuid != null) {
            return false;
        }
        if (isolatedVlanId != vn.isolatedVlanId
                || primaryVlanId != vn.primaryVlanId
                || ipPoolEnabled != vn.ipPoolEnabled
                || externalIpam != vn.externalIpam) {
            return false;
        }
        if (subnetAddress != null && !subnetAddress.equals(vn.subnetAddress)
                || subnetAddress == null && vn.subnetAddress != null) {
            return false;
        }
        if (subnetMask != null && !subnetMask.equals(vn.subnetMask)
                || subnetMask == null && vn.subnetMask != null) {
            return false;
        }
        if (gatewayAddress != null && !gatewayAddress.equals(vn.gatewayAddress)
                || gatewayAddress == null && vn.gatewayAddress != null) {
            return false;
        }
        if (range != null && !range.equals(vn.range)
                || range == null && vn.range != null) {
            return false;
        }
        return true;
    }

    public String toString() {
        return "VN <" + name + ", " + uuid + ">";
    }

    public StringBuffer toStringBuffer() {
        StringBuffer s = new StringBuffer(
                "VN <" + name + ", " + uuid + ">\n\n");

        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
            vmiInfoMap.entrySet()) {
            VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
            s.append("\t")
             .append(vmiInfo).append("\n");
        }
        s.append("\n");
        return s;
    }

    boolean ignore() {
        // Ignore networks that do not have PVLAN/VLAN configured
        return (primaryVlanId == 0 && isolatedVlanId == 0);
    }

    // change the method below to use Observer pattern and get rid of the Vnc param
    @Override
    void create(VncDB vncDB) throws Exception {

        if (ignore()) {
            return;
        }

        vncDB.createVirtualNetwork(this);
        MainDB.created(this);
    }

    @Override
    void update(VCenterObject obj, VncDB vncDB)
                    throws Exception {

        if (ignore()) {
            return;
        }

        VirtualNetworkInfo newVnInfo = (VirtualNetworkInfo)obj;

        if (newVnInfo.name != null) {
            name = newVnInfo.name;
        }
        if (newVnInfo.isolatedVlanId != 0) {
            isolatedVlanId = newVnInfo.isolatedVlanId;
        }

        if (newVnInfo.primaryVlanId != 0) {
            primaryVlanId = newVnInfo.primaryVlanId;
        }

        if (newVnInfo.subnetAddress != null) {
            subnetAddress = newVnInfo.subnetAddress;
        }

        if (newVnInfo.subnetMask != null) {
            subnetAddress = newVnInfo.subnetMask;
        }

        if (newVnInfo.gatewayAddress != null) {
            gatewayAddress = newVnInfo.gatewayAddress;
        }
        if (newVnInfo.ipPoolEnabled != false) {
            ipPoolEnabled = newVnInfo.ipPoolEnabled;
        }

        if (newVnInfo.range != null) {
            range = newVnInfo.range;
        }
        if (newVnInfo.externalIpam != false) {
            externalIpam = newVnInfo.externalIpam;
        }

        if (newVnInfo.net != null) {
            net = newVnInfo.net;
        }
        if (newVnInfo.dpg != null) {
            dpg = newVnInfo.dpg;
        }
        if (newVnInfo.dvs != null) {
            dvs = newVnInfo.dvs;
        }

        if (newVnInfo.dvsName != null) {
            dvsName = newVnInfo.dvsName;
        }

        // notify observers
        // for networks we do not update the API server
    }

    @Override
    void sync(VCenterObject obj, VncDB vncDB)
                    throws Exception {

        VirtualNetworkInfo oldVnInfo = (VirtualNetworkInfo)obj;
        
        if (oldVnInfo.apiVn != null) {
            apiVn = oldVnInfo.apiVn;
        }
    }

    @Override
    void delete(VncDB vncDB)
            throws Exception {

        for (Map.Entry<String, VirtualMachineInterfaceInfo> entry:
            vmiInfoMap.entrySet()) {
            VirtualMachineInterfaceInfo vmiInfo = entry.getValue();
            vmiInfo.delete(vncDB);
        }

        vncDB.deleteVirtualNetwork(this);

        MainDB.deleted(this);
    }

    public boolean isIpAddressInSubnetAndRange(String ipAddress) {
        if (ipAddress == null) {
            return true;
        }

        int addr = InetAddresses.coerceToInteger(InetAddresses.forString(ipAddress));
        int subnet = InetAddresses.coerceToInteger(InetAddresses.forString(subnetAddress));
        int mask = InetAddresses.coerceToInteger(InetAddresses.forString(subnetMask));

        if (((addr & mask) != subnet)) {
            return false;
        }
        if (!ipPoolEnabled || range.isEmpty()) {
            return true;
        }

        String[] pools = range.split("\\#");
        if (pools.length == 2) {
            String start = (pools[0]).replace(" ","");
            String num   = (pools[1]).replace(" ","");
            int start_ip = InetAddresses.coerceToInteger(InetAddresses.forString(start));
            int start_range = start_ip & ~mask;
            int end_ip = start_ip + Integer.parseInt(num) - 1;
            int end_range = end_ip & ~mask;
            int host = addr & ~mask;
            return (start_range <= host) && (host <= end_range);
        }
        return true;
    }
}
