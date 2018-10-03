package net.juniper.contrail.vcenter;

import java.rmi.RemoteException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.log4j.Logger;
import com.google.common.net.InetAddresses;
import com.vmware.vim25.DVPortSetting;
import com.vmware.vim25.DistributedVirtualSwitchKeyedOpaqueBlob;
import com.vmware.vim25.Event;
import com.vmware.vim25.IpPool;
import com.vmware.vim25.IpPoolIpPoolConfigInfo;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.VMwareDVSPortSetting;
import com.vmware.vim25.VMwareDVSPvlanMapEntry;
import com.vmware.vim25.VmwareDistributedVirtualSwitchPvlanSpec;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanIdSpec;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanSpec;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.util.PropertyCollectorUtil;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.VnSubnetsType;
import net.juniper.contrail.api.types.IpamSubnetType;

public class VirtualNetworkInfo extends VCenterObject {
    private final Logger s_logger =
            Logger.getLogger(VirtualNetworkInfo.class);

    private String uuid; // required attribute, key for this object
    private String name;
    private short primaryVlanId;
    private short isolatedVlanId;
    private SortedMap<String, VirtualMachineInterfaceInfo> vmiInfoMap; // key is MAC address
    private Integer ipPoolId;
    private String subnetAddress;
    private String subnetMask;
    private String gatewayAddress;
    private boolean ipPoolEnabled;
    private String range;
    private boolean externalIpam;
    private boolean portSecurityEnabled = true;

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
        if (uuid == null) {
            throw new IllegalArgumentException("Cannot init VN with null uuid");
        }

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
        if (vnInfo == null) {
            throw new IllegalArgumentException("Cannot init VN from null VN");
        }

        this.uuid = vnInfo.uuid;
        this.name = vnInfo.name;
        this.primaryVlanId = vnInfo.primaryVlanId;
        this.isolatedVlanId = vnInfo.isolatedVlanId;
        this.vmiInfoMap = vnInfo.vmiInfoMap;
        this.ipPoolId = vnInfo.ipPoolId;
        this.subnetAddress = vnInfo.subnetAddress;
        this.subnetMask = vnInfo.subnetMask;
        this.gatewayAddress = vnInfo.gatewayAddress;
        this.ipPoolEnabled = vnInfo.ipPoolEnabled;
        this.range = vnInfo.range;
        this.externalIpam = vnInfo.externalIpam;
        this.net = vnInfo.net;
        this.dpg = vnInfo.dpg;
        this.dvs = vnInfo.dvs;
        this.dvsName = vnInfo.dvsName;
        this.dc = vnInfo.dc;
        this.dcName = vnInfo.dcName;
        this.apiVn = vnInfo.apiVn;
    }

    public VirtualNetworkInfo(net.juniper.contrail.api.types.VirtualNetwork vn) {

        if (vn == null) {
            throw new IllegalArgumentException("Cannot init VN from null API VN");
        }

        apiVn = vn;
        uuid = vn.getUuid();
        name = vn.getName();
        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
        externalIpam = false; // vn.getExternalIpam(); FIXME:temprary workaround for plugin exception during network read

        readIpAm();
    }

    private void readIpAm() {
        List<ObjectReference<VnSubnetsType>> objList = apiVn.getNetworkIpam();
        if (objList != null) {
            for (ObjectReference<VnSubnetsType> objRef: Utils.safe(objList)) {
                if (objRef == null) {
                    continue;
                }
                VnSubnetsType subnetsType = objRef.getAttr();
                List<IpamSubnetType> ipamsubList = subnetsType.getIpamSubnets();
                if (ipamsubList == null) {
                    continue;
                }
                for (IpamSubnetType sub: ipamsubList) {
                    if (sub == null) {
                        continue;
                    }
                    subnetAddress = sub.getSubnet().getIpPrefix();
                    int len = sub.getSubnet().getIpPrefixLen();
                    int mask = 0xFFFFFFFF << (32 - len);
                    subnetMask = InetAddresses.fromInteger(mask).getHostAddress();
                    gatewayAddress = sub.getDefaultGateway();
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public VirtualNetworkInfo(Event event,  VCenterDB vcenterDB, VncDB vncDB) throws Exception {
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

        if (vcenterDB.mode == Mode.VCENTER_AS_COMPUTE) {
           apiVn = vncDB.findVirtualNetwork(uuid);
           readIpAm();
        }
    }

    @SuppressWarnings("rawtypes")
    public VirtualNetworkInfo(VCenterDB vcenterDB,
            DistributedVirtualPortgroup dpg, Hashtable pTable,
            com.vmware.vim25.mo.Datacenter dc, String dcName,
            com.vmware.vim25.mo.VmwareDistributedVirtualSwitch dvs,
            String dvsName) throws Exception {

        if (vcenterDB == null || dpg == null || pTable == null
                || dvs == null || dvsName == null
                || dc == null || dcName == null) {
            throw new IllegalArgumentException("Cannot init VN from null arguments");
        }
        vmiInfoMap = new ConcurrentSkipListMap<String, VirtualMachineInterfaceInfo>();
        this.dc = dc;
        this.dcName = dcName;
        this.dpg = dpg;
        this.dvs = dvs;
        this.dvsName = dvsName;

        populateInfo(vcenterDB, pTable);
    }

    @SuppressWarnings("rawtypes")
    void populateInfo(VCenterDB vcenterDB, Hashtable pTable)
            throws Exception {

        name = (String) pTable.get("name");

        switch(vcenterDB.mode) {
        case VCENTER_AS_COMPUTE:
            /*
            From Mitaka nova driver will append cluster_id to port group
            therefore need to extract the appended cluster id
            */
            name = name.substring(Math.max(0, name.length() - 36));
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

        populateVlans(vcenterDB, pTable);

        populateAddressManagement(vcenterDB, pTable);
    }

    @SuppressWarnings("rawtypes")
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

    @SuppressWarnings("rawtypes")
    private void populateVlans(VCenterDB vcenterDB, Hashtable pTable) throws Exception {

        VMwareDVSPvlanMapEntry[] pvlanMapArray = vcenterDB.getDvsPvlanMap(dvsName, dc, dcName);
        if (pvlanMapArray == null) {
            s_logger.error(this + "Cannot populate vlan, private vlan not configured on dvSwitch: " + dvsName +
                    " Datacenter: " + dcName);
            return;
        }

        // Extract dvPg configuration info and port setting
        DVPortSetting portSetting = (DVPortSetting) pTable.get("config.defaultPortConfig");
        if (!(portSetting instanceof VMwareDVSPortSetting)) {
            s_logger.error(this + " Cannot populate vlan, invalid port setting: " +  portSetting);
            return;
        }

        VMwareDVSPortSetting vPortSetting = (VMwareDVSPortSetting) portSetting;
        VmwareDistributedVirtualSwitchVlanSpec vlanSpec = vPortSetting.getVlan();

        if (vlanSpec instanceof VmwareDistributedVirtualSwitchPvlanSpec) {
            // config.defaultPortConfig.vlan contains isolated secondary VLAN Id
            VmwareDistributedVirtualSwitchPvlanSpec pvlanSpec =
                    (VmwareDistributedVirtualSwitchPvlanSpec) vlanSpec;
            isolatedVlanId = (short)pvlanSpec.getPvlanId();
            // Find primaryVLAN corresponding to isolated secondary VLAN
            // by searching in the pvlan Map Array
            for (short i=0; i < pvlanMapArray.length; i++) {
                if (pvlanMapArray[i].getPvlanType().equals("isolated")
                        && (short)pvlanMapArray[i].getSecondaryVlanId() == isolatedVlanId) {
                    primaryVlanId = (short)pvlanMapArray[i].getPrimaryVlanId();
                    s_logger.debug(this + " VlanType = PrivateVLAN"
                            + " PrimaryVLAN = " + primaryVlanId
                            + " IsolatedVLAN = " + isolatedVlanId);
                    return;
                }
            }
        } else if (vlanSpec instanceof VmwareDistributedVirtualSwitchVlanIdSpec) {
            VmwareDistributedVirtualSwitchVlanIdSpec vlanIdSpec =
                    (VmwareDistributedVirtualSwitchVlanIdSpec) vlanSpec;
            primaryVlanId = isolatedVlanId = (short)vlanIdSpec.getVlanId();
            s_logger.debug(this + " VlanType = VLAN " + " VlanId = " + primaryVlanId);
        } else {
            s_logger.error(this + " Cannot populate vlan, invalid vlan spec: " + vlanSpec);
        }
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

    public boolean getPortSecurityEnabled() {
        return portSecurityEnabled;
    }

    public void setPortSecurityEnabled(boolean _portSecurityEnabled) {
        this.portSecurityEnabled = _portSecurityEnabled;
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

    public Integer getIpPoolId() {
        return ipPoolId;
    }

    public void setIpPoolId(Integer poolId, VCenterDB vcenterDB)
            throws RuntimeFault, RemoteException {

        IpPool ipPool = vcenterDB.getIpPoolById(poolId, name, dc, dcName);
        if (ipPool != null) {
            IpPoolIpPoolConfigInfo ipConfigInfo = ipPool.getIpv4Config();

            subnetAddress = ipConfigInfo.getSubnetAddress();
            subnetMask = ipConfigInfo.getNetmask();
            gatewayAddress = ipConfigInfo.getGateway();
            ipPoolEnabled = ipConfigInfo.getIpPoolEnabled();
            range = ipConfigInfo.getRange();
            this.ipPoolId = ipPool.id;
            s_logger.debug("Set ipPoolId to " + ipPoolId + " for " + this);
        } else {
            subnetAddress = null;
            subnetMask = null;
            gatewayAddress = null;
            ipPoolEnabled = false;
            range = null;
            this.ipPoolId = null;
            s_logger.debug("Set ipPoolId to null for " + this);
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
        // notify observers
        MainDB.created(this);
    }

    @Override
    void update(VCenterObject obj, VncDB vncDB)
                    throws Exception {

        if (ignore()) {
            return;
        }

        VirtualNetworkInfo newVnInfo = (VirtualNetworkInfo)obj;

        name = newVnInfo.name;
        primaryVlanId = newVnInfo.primaryVlanId;
        isolatedVlanId = newVnInfo.isolatedVlanId;
        ipPoolId = newVnInfo.ipPoolId;
        subnetAddress = newVnInfo.subnetAddress;
        subnetMask = newVnInfo.subnetMask;
        gatewayAddress = newVnInfo.gatewayAddress;
        ipPoolEnabled = newVnInfo.ipPoolEnabled;
        range = newVnInfo.range;
        externalIpam = newVnInfo.externalIpam;

        vncDB.updateVirtualNetwork(this);
    }

    @Override
    void sync(VCenterObject obj, VncDB vncDB)
                    throws Exception {

        VirtualNetworkInfo oldVnInfo = (VirtualNetworkInfo)obj;

        if (uuid == null && oldVnInfo.uuid != null) {
            uuid = oldVnInfo.uuid;
        }

        if (name == null && oldVnInfo.name != null) {
            name = oldVnInfo.name;
        }

        if (isolatedVlanId == 0 && oldVnInfo.isolatedVlanId != 0) {
            isolatedVlanId = oldVnInfo.isolatedVlanId;
        }

        if (primaryVlanId == 0 && oldVnInfo.primaryVlanId != 0) {
            primaryVlanId = oldVnInfo.primaryVlanId;
        }

        if (ipPoolId == null && oldVnInfo.ipPoolId != null) {
            ipPoolId = oldVnInfo.ipPoolId;
        }

        if (subnetAddress == null && oldVnInfo.subnetAddress != null) {
            subnetAddress = oldVnInfo.subnetAddress;
            externalIpam = oldVnInfo.externalIpam;
        }

        if (subnetMask == null && oldVnInfo.subnetMask != null) {
            subnetMask = oldVnInfo.subnetMask;
        }

        if (gatewayAddress == null && oldVnInfo.gatewayAddress != null) {
            gatewayAddress = oldVnInfo.gatewayAddress;
        }

        if (range == null && oldVnInfo.range != null) {
            range = oldVnInfo.range;
            ipPoolEnabled = oldVnInfo.ipPoolEnabled;
        }

        if (apiVn == null && oldVnInfo.apiVn != null) {
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

        if (subnetAddress == null || subnetMask == null) {
            return false;
        }

        int addr = InetAddresses.coerceToInteger(InetAddresses.forString(ipAddress));
        int subnet = InetAddresses.coerceToInteger(InetAddresses.forString(subnetAddress));
        int mask = InetAddresses.coerceToInteger(InetAddresses.forString(subnetMask));

        if (((addr & mask) != subnet)) {
            return false;
        }
        if (!ipPoolEnabled || range == null || range.isEmpty()) {
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
