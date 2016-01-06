/**
 * Copyright (c) 2014 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.commons.net.util.SubnetUtils;
import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorFactory;
import net.juniper.contrail.api.ApiPropertyBase;
import net.juniper.contrail.api.ObjectReference;
import net.juniper.contrail.api.types.InstanceIp;
import net.juniper.contrail.api.types.FloatingIp;
import net.juniper.contrail.api.types.MacAddressesType;
import net.juniper.contrail.api.types.NetworkIpam;
import net.juniper.contrail.api.types.SecurityGroup;
import net.juniper.contrail.api.types.PolicyEntriesType;
import net.juniper.contrail.api.types.SubnetType;
import net.juniper.contrail.api.types.VirtualMachine;
import net.juniper.contrail.api.types.VirtualMachineInterface;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.VnSubnetsType;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.IdPermsType;
import net.juniper.contrail.contrail_vrouter_api.ContrailVRouterApi;

import com.google.common.base.Throwables;

public class VncDB {
    private static final Logger s_logger = 
            Logger.getLogger(VncDB.class);
    protected static final int vrouterApiPort = 9090;
    protected final String apiServerAddress;
    protected final int apiServerPort;
    protected final String username;
    protected final String password;
    protected final String tenant;
    protected final String authtype;
    protected final String authurl;
    protected volatile ApiConnector apiConnector;
    private boolean alive;
    private Project vCenterProject;
    private NetworkIpam vCenterIpam;
    private SecurityGroup vCenterDefSecGrp;
    private IdPermsType vCenterIdPerms;
    Mode mode;

    public static final String VNC_ROOT_DOMAIN     = "default-domain";
    public static final String VNC_VCENTER_PROJECT = "vCenter";
    public static final String VNC_VCENTER_IPAM    = "vCenter-ipam";
    public static final String VNC_VCENTER_DEFAULT_SG    = "default";
    public static final String VNC_VCENTER_PLUGIN  = "vcenter-plugin";
    public static final String VNC_VCENTER_TEST_PROJECT = "vCenter-test";
    public static final String VNC_VCENTER_TEST_IPAM    = "vCenter-ipam-test";
    
    public VncDB(String apiServerAddress, int apiServerPort, Mode mode) {
        this.apiServerAddress = apiServerAddress;
        this.apiServerPort = apiServerPort;
        this.mode = mode;

        if (mode == Mode.VCENTER_ONLY) {
            // Create global id-perms object.
            vCenterIdPerms = new IdPermsType();
            vCenterIdPerms.setCreator("vcenter-plugin");
            vCenterIdPerms.setEnable(true);
        }
        this.username = null;
        this.password = null;
        this.tenant   = null;
        this.authtype = null;
        this.authurl  = null;
    }

    public VncDB(String apiServerAddress, int apiServerPort,
            String username, String password,
            String tenant,
            String authtype, String authurl, Mode mode) {
        this.apiServerAddress = apiServerAddress;
        this.apiServerPort = apiServerPort;
        this.mode = mode;

        if (mode == Mode.VCENTER_ONLY) {
            // Create global id-perms object.
            vCenterIdPerms = new IdPermsType();
            vCenterIdPerms.setCreator("vcenter-plugin");
            vCenterIdPerms.setEnable(true);
        }

        this.username = username;
        this.password = password;
        this.tenant   = tenant;
        this.authtype = authtype;
        this.authurl  = authurl;
    }
    
    public void setApiConnector(ApiConnector _apiConnector) {
        apiConnector = _apiConnector;
    }

    public ApiConnector getApiConnector() {
        return apiConnector;
    }

    public String getApiServerAddress() {
        return apiServerAddress;
    }
    
    public int getApiServerPort() {
        return apiServerPort;
    }

    public IdPermsType getVCenterIdPerms() {
        return vCenterIdPerms;
    }

    public Project getVCenterProject() {
        return vCenterProject;
    }

    public boolean isServerAlive() {
        return alive;
    }
    
    public boolean isVncApiServerAlive() {
        if (apiConnector == null) {
            apiConnector = ApiConnectorFactory.build(apiServerAddress,
                                                     apiServerPort);
            if (mode == Mode.VCENTER_AS_COMPUTE) {
                apiConnector.credentials(username, password)
                            .tenantName(tenant)
                            .authServer(authtype, authurl);
            }
            if (apiConnector == null) {
                s_logger.error(" failed to create ApiConnector.. retry later");
                alive = false;
                return false;
            }
        }

        // Read project list as a life check
        s_logger.info(" Checking if api-server is alive and kicking..");

        try {
            List<Project> projects = (List<Project>) apiConnector.list(Project.class, null);
            if (projects == null) {
                s_logger.error(" ApiServer not fully awake yet.. retry again..");
                alive = false;
                return false;
            }
        } catch (Exception e) {
            alive = false;
            return false;
        }

        alive = true;
        s_logger.info(" Api-server alive. Got the pulse..");
        return true;

    }

    public boolean Initialize() {

        // Check if api-server is alive
        if (isVncApiServerAlive() == false) {
            return false;
        }

        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return true;
        }
        
        // create objects specific to VCENTER_ONLY mode
        // Check if Vmware Project exists on VNC. If not, create one.
        try {
            vCenterProject = (Project) apiConnector.findByFQN(Project.class, 
                                        VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT);
        } catch (Exception e) {
            s_logger.error("Exception : " + e);
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
            return false;
        }
        s_logger.info(" fqn-to-uuid complete..");
        
        if (vCenterProject == null) {
            s_logger.info(" vCenter project not present, creating ");
            vCenterProject = new Project();
            vCenterProject.setName("vCenter");
            vCenterProject.setIdPerms(vCenterIdPerms);
            try {
                if (!apiConnector.create(vCenterProject)) {
                    s_logger.error("Unable to create project: " + vCenterProject.getName());
                    return false;
                }
            } catch (Exception e) {
                s_logger.error("Exception : " + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }
        } else {
            s_logger.info(" vCenter project present, continue ");
        }

        // Check if VMWare vCenter-ipam exists on VNC. If not, create one.
        try {
            vCenterIpam = (NetworkIpam) apiConnector.findByFQN(NetworkIpam.class,
                       VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT + ":" + VNC_VCENTER_IPAM);
        } catch (Exception e) {
            s_logger.error("Exception : " + e);
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
            return false;
        }

        if (vCenterIpam == null) {
            s_logger.info(" vCenter Ipam not present, creating ...");
            vCenterIpam = new NetworkIpam();
            vCenterIpam.setParent(vCenterProject);
            vCenterIpam.setName("vCenter-ipam");
            vCenterIpam.setIdPerms(vCenterIdPerms);
            try {
                if (!apiConnector.create(vCenterIpam)) {
                    s_logger.error("Unable to create Ipam: " + vCenterIpam.getName());
                }
            } catch (Exception e) {
                s_logger.error("Exception : " + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }
        } else {
            s_logger.info(" vCenter Ipam present, continue ");
        }

        // Check if VMWare vCenter default security-group exists on VNC. If not, create one.
        try {
            vCenterDefSecGrp = (SecurityGroup) apiConnector.findByFQN(SecurityGroup.class,
                       VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT + ":" + VNC_VCENTER_DEFAULT_SG);
        } catch (Exception e) {
            s_logger.error("Exception : " + e);
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
            return false;
        }

        if (vCenterDefSecGrp == null) {
            s_logger.info(" vCenter default Security-group not present, creating ...");
            vCenterDefSecGrp = new SecurityGroup();
            vCenterDefSecGrp.setParent(vCenterProject);
            vCenterDefSecGrp.setName("default");
            vCenterDefSecGrp.setIdPerms(vCenterIdPerms);

            PolicyEntriesType sg_rules = new PolicyEntriesType();

            PolicyEntriesType.PolicyRuleType ingress_rule = 
                              new PolicyEntriesType.PolicyRuleType(
                                      null,
                                      UUID.randomUUID().toString(),
                                      ">",
                                      "any",
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(null, null, VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT + ":" + "default", null, null)}),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //src_ports
                                       null, //application
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(null, null, "local", null, null) }),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //dest_ports
                                       null, // action_list
                                       "IPv4"); // ethertype
            sg_rules.addPolicyRule(ingress_rule);

            PolicyEntriesType.PolicyRuleType egress_rule  = 
                              new PolicyEntriesType.PolicyRuleType(
                                      null,
                                      UUID.randomUUID().toString(),
                                      ">",
                                      "any",
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(null, null, "local", null, null) }),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //src_ports
                                       null, //application
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(new SubnetType("0.0.0.0", 0), null, null, null, null) }),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //dest_ports
                                       null, // action_list
                                       "IPv4"); // ethertype);
            sg_rules.addPolicyRule(egress_rule);

            vCenterDefSecGrp.setEntries(sg_rules);

            try {
                if (!apiConnector.create(vCenterDefSecGrp)) {
                    s_logger.error("Unable to create defSecGrp: " + vCenterDefSecGrp.getName());
                }
            } catch (Exception e) {
                s_logger.error("Exception : " + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }
        } else {
            s_logger.info(" vCenter default sec-group present, continue ");
        }


        return true;
    }

 public boolean TestInitialize() {

        // Check if Vmware Test Project exists on VNC. If not, create one.
        try {
            vCenterProject = (Project) apiConnector.findByFQN(Project.class, 
                                        VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_TEST_PROJECT);
        } catch (IOException e) {
            return false;
        }
        if (vCenterProject == null) {
            s_logger.info(" vCenter-test project not present, creating ");
            vCenterProject = new Project();
            vCenterProject.setName("vCenter-test");
            try {
                if (!apiConnector.create(vCenterProject)) {
                    s_logger.error("Unable to create project: " + vCenterProject.getName());
                    return false;
                }
            } catch (IOException e) { 
                s_logger.error("Exception : " + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }
        } else {
            s_logger.info(" vCenter-test project present, continue ");
        }

        // Check if VMWare vCenter-test ipam exists on VNC. If not, create one.
        try {
            vCenterIpam = (NetworkIpam) apiConnector.findByFQN(NetworkIpam.class,
                       VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT + ":" + VNC_VCENTER_TEST_IPAM);
        } catch (IOException e) {
            return false;
        }

        if (vCenterIpam == null) {
            s_logger.info(" vCenter test Ipam not present, creating ...");
            vCenterIpam = new NetworkIpam();
            vCenterIpam.setParent(vCenterProject);
            vCenterIpam.setName("vCenter-ipam-test");
            try {
                if (!apiConnector.create(vCenterIpam)) {
                    s_logger.error("Unable to create test Ipam: " + vCenterIpam.getName());
                }
            } catch (IOException e) { 
                s_logger.error("Exception : " + e);
                e.printStackTrace();
                return false;
            }
        } else {
            s_logger.info(" vCenter test Ipam present, continue ");
        }

        // Check if VMWare vCenter default security-group exists on VNC. If not, create one.
        try {
            vCenterDefSecGrp = (SecurityGroup) apiConnector.findByFQN(SecurityGroup.class,
                       VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT + ":" + VNC_VCENTER_DEFAULT_SG);
        } catch (Exception e) {
            s_logger.error("Exception : " + e);
            String stackTrace = Throwables.getStackTraceAsString(e);
            s_logger.error(stackTrace);
            return false;
        }

        if (vCenterDefSecGrp == null) {
            s_logger.info(" vCenter default Security-group not present, creating ...");
            vCenterDefSecGrp = new SecurityGroup();
            vCenterDefSecGrp.setParent(vCenterProject);
            vCenterDefSecGrp.setName("default");

            PolicyEntriesType sg_rules = new PolicyEntriesType();

            PolicyEntriesType.PolicyRuleType ingress_rule = 
                              new PolicyEntriesType.PolicyRuleType(
                                      null,
                                      UUID.randomUUID().toString(),
                                      ">",
                                      "any",
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(null, null, VNC_ROOT_DOMAIN + ":" + VNC_VCENTER_PROJECT + ":" + "default", null, null)}),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //src_ports
                                       null, //application
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(null, null, "local", null, null) }),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //dest_ports
                                       null, // action_list
                                       "IPv4"); // ethertype
            sg_rules.addPolicyRule(ingress_rule);

            PolicyEntriesType.PolicyRuleType egress_rule  = 
                              new PolicyEntriesType.PolicyRuleType(
                                      null,
                                      UUID.randomUUID().toString(),
                                      ">",
                                      "any",
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(null, null, "local", null, null) }),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //src_ports
                                       null, //application
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.AddressType[] {new PolicyEntriesType.PolicyRuleType.AddressType(new SubnetType("0.0.0.0", 0), null, null, null, null) }),
                                       Arrays.asList(new PolicyEntriesType.PolicyRuleType.PortType[] {new PolicyEntriesType.PolicyRuleType.PortType(0,65535)}), //dest_ports
                                       null, // action_list
                                       "IPv4"); // ethertype);
            sg_rules.addPolicyRule(egress_rule);

            vCenterDefSecGrp.setEntries(sg_rules);

            try {
                if (!apiConnector.create(vCenterDefSecGrp)) {
                    s_logger.error("Unable to create def sec grp: " + vCenterDefSecGrp.getName());
                }
            } catch (Exception e) {
                s_logger.error("Exception : " + e);
                String stackTrace = Throwables.getStackTraceAsString(e);
                s_logger.error(stackTrace);
                return false;
            }
        } else {
            s_logger.info(" vCenter default sec-group present, continue ");
        }


        return true;
    }
 
    protected static boolean doIgnoreVirtualNetwork(String name) {
        // Ignore default, fabric, and link-local networks
        if (name.equals("__link_local__") || 
                name.equals("default-virtual-network") || 
                name.equals("ip-fabric")) {
            return true;
        }
        return false;
    }

    public void createVirtualNetwork(VirtualNetworkInfo vnInfo)
            throws IOException {
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        
        if (vnInfo == null) {
            s_logger.error("Null pointer argument");
            throw new IllegalArgumentException();
        }
           
      
        VirtualNetwork    vn = new VirtualNetwork();
        vnInfo.apiVn = vn;
        
        vn.setUuid(vnInfo.getUuid());
        vn.setName(vnInfo.getName());
        vn.setDisplayName(vnInfo.getName());
        vn.setIdPerms(vCenterIdPerms);
        vn.setParent(vCenterProject);
        vn.setExternalIpam(vnInfo.getExternalIpam());

        VnSubnetsType subnet = getSubnet(vnInfo, vn);
        if (subnet != null) {
            vn.setNetworkIpam(vCenterIpam, subnet);
        }
        
        apiConnector.create(vn);
        s_logger.info("Created " + vnInfo);
    }

    public void updateVirtualNetwork(VirtualNetworkInfo vnInfo)
            throws IOException {
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        
        if (vnInfo == null) {
            s_logger.error("Null pointer argument");
            throw new IllegalArgumentException();
        }
      
        VirtualNetwork    vn = new VirtualNetwork();
        vn.setUuid(vnInfo.getUuid());
        
        VnSubnetsType subnet = getSubnet(vnInfo, vn);
        if (subnet != null) {
            vn.setNetworkIpam(vCenterIpam, subnet);
        }
        apiConnector.update(vn);
        s_logger.info("Updated " + vnInfo);
    }

    private VnSubnetsType getSubnet(VirtualNetworkInfo vnInfo, VirtualNetwork vn) {
        s_logger.info("getSubnet address " + vnInfo.getSubnetAddress() + ", mask " + vnInfo.getSubnetMask());
        if (vnInfo.getSubnetAddress() == null || vnInfo.getSubnetMask() == null) {
            return null;
        }
        SubnetUtils subnetUtils = new SubnetUtils(vnInfo.getSubnetAddress(), vnInfo.getSubnetMask());
        String cidr = subnetUtils.getInfo().getCidrSignature();
        String[] addr_pair = cidr.split("\\/");
        
        List<VnSubnetsType.IpamSubnetType.AllocationPoolType> allocation_pools = null;
        if (vnInfo.getIpPoolEnabled() == true && !vnInfo.getRange().isEmpty()) {
            String[] pools = vnInfo.getRange().split("\\#");
            if (pools.length == 2) {
                allocation_pools = new ArrayList<VnSubnetsType.IpamSubnetType.AllocationPoolType>();
                String start = (pools[0]).replace(" ","");
                String num   = (pools[1]).replace(" ","");
                String[] bytes = start.split("\\.");
                String end   = bytes[0] + "." + bytes[1] + "." + bytes[2] + "."
                               + Integer.toString(Integer.parseInt(bytes[3]) +  Integer.parseInt(num) - 1);
                s_logger.info("Subnet IP Range :  Start:"  + start + " End:" + end);
                VnSubnetsType.IpamSubnetType.AllocationPoolType pool1 = new
                        VnSubnetsType.IpamSubnetType.AllocationPoolType(start, end);
                allocation_pools.add(pool1);
            }
        }
        
        // if gateway address is empty string, don't pass empty string to
        // api-server. INstead set it to null so that java binding will
        // drop gateway address from json content for virtual-network create
        if (vnInfo.getGatewayAddress() != null) {
            if (vnInfo.getGatewayAddress().trim().isEmpty())
              vnInfo.setGatewayAddress(null);
        }

        VnSubnetsType subnet = new VnSubnetsType();
        subnet.addIpamSubnets(new VnSubnetsType.IpamSubnetType(
                                   new SubnetType(addr_pair[0],
                                       Integer.parseInt(addr_pair[1])),
                                       vnInfo.getGatewayAddress(),
                                       null,                          // dns_server_address
                                       UUID.randomUUID().toString(),  // subnet_uuid
                                       true,                          // enable_dhcp
                                       null,                          // dns_nameservers
                                       allocation_pools,
                                       true,                          // addr_from_start
                                       null,                          // dhcp_options_list
                                       null,                          // host_routes
                                       vn.getName() + "-subnet"));
        return subnet;
    }

    public void deleteVirtualNetwork(VirtualNetworkInfo vnInfo)
            throws IOException {
        
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }

        if (vnInfo == null) {
            s_logger.error("Cannot delete API VN: null arguments");
            throw new IllegalArgumentException("Null arguments");
        }
        
        VirtualNetwork apiVn = (VirtualNetwork) apiConnector.findById(
                    VirtualNetwork.class, vnInfo.getUuid());
   
        if (apiVn == null) {
            s_logger.error("Cannot delete, not found: " + vnInfo);
            return;
        }
 
        deleteInstanceIps(apiVn);
        
        deleteVirtualMachineInterfaces(apiVn);
        
        apiConnector.delete(apiVn);
        vnInfo.apiVn = null;
        s_logger.info("Deleted " + vnInfo);
    }


    public void createVirtualMachine(VirtualMachineInfo vmInfo)
            throws IOException {
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        
        if (vmInfo == null) {
            s_logger.error("Null argument");
            throw new IllegalArgumentException("vmInfo is null");
        }
        
        String vmUuid = vmInfo.getUuid();
        VirtualMachine vm = new VirtualMachine();
        vmInfo.apiVm = vm;
        vm.setName(vmUuid);
        vm.setUuid(vmUuid);

        // Encode VRouter IP address in display name
        if (vmInfo.getVrouterIpAddress() != null) {
            vm.setDisplayName(vmInfo.getVrouterIpAddress());
        }
        vm.setIdPerms(vCenterIdPerms); 
        apiConnector.create(vm);
        s_logger.info("Created " + vm);
    }
    
    public void deleteVirtualMachine(VirtualMachineInfo vmInfo)
            throws IOException {
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        
        if (vmInfo == null) {
            s_logger.error("Cannot delete VM: null arguments");
            throw new IllegalArgumentException("Null arguments");
        }
        VirtualMachine apiVm = (VirtualMachine) apiConnector.findById(
                VirtualMachine.class, vmInfo.getUuid());
        
        if (apiVm == null) {
            s_logger.error("Cannot delete VM, it does not exist in the API server " 
                            + vmInfo);
            return;
        }

        deleteVirtualMachineInterfaces(apiVm);
        
        apiConnector.delete(apiVm);
        vmInfo.apiVm = null;
        s_logger.info("Deleted " + vmInfo);
    }

    public void createVirtualMachineInterface(
            VirtualMachineInterfaceInfo vmiInfo)
            throws IOException {

        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }

        VirtualMachineInfo vmInfo = vmiInfo.vmInfo;
        VirtualNetworkInfo vnInfo = vmiInfo.vnInfo;
        
        VirtualMachine vm = vmInfo.apiVm;
        if (vm == null) {
            vm = vmInfo.apiVm = (VirtualMachine) apiConnector.findById(
                    VirtualMachine.class, vmInfo.getUuid());
           
            if (vm == null) {
                s_logger.error("Cannot find " + vmInfo);
                return;
            }
        }
        
        VirtualNetwork network = vnInfo.apiVn;
        if (network == null) {
            
            network = (VirtualNetwork) apiConnector.findById(
                    VirtualNetwork.class, vnInfo.getUuid());
   
            if (network == null) {
                s_logger.error("Cannot find " + vnInfo);
                return;
            }
            vnInfo.apiVn = network;
        }

        VirtualMachineInterface vmi = vmiInfo.apiVmi;
        if (vmiInfo.apiVmi != null) {
            return;
        }
        VirtualMachineInterface apiVmi = readVirtualMachineInterface(vmiInfo);

        if (apiVmi != null) {
            return;
        }
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            s_logger.error("VMI not found in the API server " + vmiInfo);
            return;
        }

        // create Virtual machine interface
        String vmInterfaceName = "vmi-" + vnInfo.getName()
                + "-" + vmInfo.getName();
       
        VirtualMachineInterface vmInterface = new VirtualMachineInterface();
        vmInterface.setDisplayName(vmInterfaceName);
        
        if (vmiInfo.getUuid() == null) {
            vmiInfo.setUuid(UUID.randomUUID().toString());
        }
        vmInterface.setUuid(vmiInfo.getUuid());
        vmInterface.setName(vmiInfo.getUuid());
        vmInterface.setParent(vCenterProject);
        vmInterface.setSecurityGroup(vCenterDefSecGrp);
        vmInterface.setVirtualNetwork(network);
        vmInterface.addVirtualMachine(vm);
        MacAddressesType macAddrType = new MacAddressesType();
        macAddrType.addMacAddress(vmiInfo.getMacAddress());
        vmInterface.setMacAddresses(macAddrType);
        vmInterface.setIdPerms(vCenterIdPerms);
        apiConnector.create(vmInterface);
        apiConnector.read(vmInterface);
        vmiInfo.apiVmi = vmInterface;
        s_logger.debug("Created " + vmiInfo);
    }

    public void deleteVirtualMachineInterface(
            VirtualMachineInterfaceInfo vmiInfo)
            throws IOException {
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        
        if (vmiInfo == null) {
            s_logger.error("Cannot delete VMI: null argument");
            throw new IllegalArgumentException("Null arguments");
        }
          
        VirtualMachineInterface apiVmi = (VirtualMachineInterface) apiConnector.findById(
                    VirtualMachineInterface.class, vmiInfo.getUuid());
        if (apiVmi == null) {
            s_logger.error("Cannot delete VMI, it does not exist " + vmiInfo);
            return;
        }
        
        clearSecurityGroups(apiVmi);

        clearFloatingIp(apiVmi);
        
        deleteInstanceIps(apiVmi);
        
        apiConnector.delete(apiVmi);
        vmiInfo.apiVmi = null;
        s_logger.info("Deleted " + vmiInfo);
    }

    private void clearFloatingIp(VirtualMachineInterface apiVmi) throws IOException {
        // Clear floating-ip associations if it exists on VMInterface
        List<ObjectReference<ApiPropertyBase>> floatingIpRefs = 
                apiVmi.getFloatingIpBackRefs();
        if ((floatingIpRefs != null) && !floatingIpRefs.isEmpty()) {
            s_logger.info("floatingIp association exists for VMInterface:" + apiVmi.getUuid());
            // there can be one floating-ip per VMI.
            FloatingIp floatingIp = (FloatingIp)
                apiConnector.findById(FloatingIp.class, 
                                      floatingIpRefs.get(0).getUuid());
            // clear VMInterface back reference.
            FloatingIp fip = new FloatingIp();
            fip.setParent(floatingIp.getParent());
            fip.setName(floatingIp.getName());
            fip.setUuid(floatingIp.getUuid());
            fip.setVirtualMachineInterface(apiVmi);
            fip.clearVirtualMachineInterface();
            apiConnector.update(fip);
            floatingIp.clearVirtualMachineInterface();
            s_logger.info("Removed floatingIp association for VMInterface:" + apiVmi.getUuid());
        }
    }

    private void clearSecurityGroups(VirtualMachineInterface apiVmi) throws IOException {
        // Clear security-group associations if it exists on VMInterface
        List<ObjectReference<ApiPropertyBase>> secGroupRefs = 
                apiVmi.getSecurityGroup();
        if ((secGroupRefs != null) && !secGroupRefs.isEmpty()) {
            s_logger.info("SecurityGroup association exists for VMInterface:" + apiVmi.getUuid());
            SecurityGroup secGroup = (SecurityGroup)
                apiConnector.findById(SecurityGroup.class, 
                                      secGroupRefs.get(0).getUuid());
            VirtualMachineInterface vmi = new VirtualMachineInterface();
            vmi.setParent(apiVmi.getParent());
            vmi.setName(apiVmi.getName());
            vmi.setUuid(apiVmi.getUuid());
            if (secGroup != null) {
                vmi.addSecurityGroup(secGroup);
            }
            vmi.clearSecurityGroup();
            apiConnector.update(vmi);
            apiVmi.clearSecurityGroup();
            s_logger.info("Removed SecurityGroup association for VMInterface:" + apiVmi.getUuid());
        }
    }

    public void createInstanceIp(VirtualMachineInterfaceInfo vmiInfo)
            throws IOException {
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        
        VirtualNetwork network = vmiInfo.vnInfo.apiVn;
        VirtualMachine vm = vmiInfo.vmInfo.apiVm;
        
        if (vm == null) {
            vm = vmiInfo.vmInfo.apiVm = (VirtualMachine) apiConnector.findById(
                    VirtualMachine.class, vmiInfo.vmInfo.getUuid());
            
            if (vm == null) {
                s_logger.error("Cannot find " + vmiInfo);
                return;
            }
        }
        VirtualMachineInterface vmIntf = vmiInfo.apiVmi;
        String instanceIpName = "ip-" + network.getName() + "-" + vmiInfo.vmInfo.getName() ;
        String instIpUuid = UUID.randomUUID().toString();
        
        InstanceIp instanceIp = new InstanceIp();
        if (vmiInfo.getIpAddress() != null) {
            instanceIp.setAddress(vmiInfo.getIpAddress());
        }
        instanceIp.setDisplayName(instanceIpName);
        instanceIp.setUuid(instIpUuid);
        instanceIp.setName(instIpUuid);
        instanceIp.setVirtualNetwork(network);
        instanceIp.setVirtualMachineInterface(vmIntf);
        instanceIp.setIdPerms(vCenterIdPerms);
        apiConnector.create(instanceIp);
        apiConnector.read(instanceIp);

        vmiInfo.apiInstanceIp = instanceIp;
        vmiInfo.setIpAddress(instanceIp.getAddress());
        
        s_logger.debug("Created instanceIP:" + instanceIp.getName() + ": " +
                instanceIp.getAddress());
    }

    SortedMap<String, VirtualNetworkInfo> readVirtualNetworks() {
        SortedMap<String, VirtualNetworkInfo>  map = 
                new ConcurrentSkipListMap<String, VirtualNetworkInfo>();

        List<VirtualNetwork> apiObjs = null;
        try {
            apiObjs = (List<VirtualNetwork>) 
                    apiConnector.list(VirtualNetwork.class, null);
        } catch (Exception ex) {
            s_logger.error("Exception in api.list(VirtualNetworks): " + ex);
            ex.printStackTrace();
            return map;
        }
        
        for (VirtualNetwork vn : apiObjs) {
            try {
                //TODO can we get rid of this call by reading everything with the list?
                apiConnector.read(vn);
                // Ignore network ?
                if (doIgnoreVirtualNetwork(vn.getName())) {
                    continue;
                }
                // Ignore objects where creator isn't "vcenter-plugin"
                if ((mode == Mode.VCENTER_ONLY) && 
                        ((vn.getIdPerms().getCreator() == null)  ||
                    !(vn.getIdPerms().getCreator().equals(VNC_VCENTER_PLUGIN)))) {
                    continue;
                }
                VirtualNetworkInfo vnInfo = new VirtualNetworkInfo(vn);
                
                map.put(vnInfo.getUuid(), vnInfo);
                
            } catch (Exception e) {
                s_logger.error("Cannot read VN " + vn.getName());
            }
        }

        return map;
    }

 
    SortedMap<String, VirtualMachineInfo> readVirtualMachines() {
        
        List<VirtualMachine> apiVms = null;
        SortedMap<String, VirtualMachineInfo>  map = 
                new ConcurrentSkipListMap<String, VirtualMachineInfo>();
        
        try {
            apiVms = (List<VirtualMachine>) 
                    apiConnector.list(VirtualMachine.class, null);
        } catch (Exception e) {
            s_logger.error("Exception in api.list(VirtualMachine): " + e);
            e.printStackTrace();
            return map;
        }
       
        for (VirtualMachine vm : apiVms) {
            try {
                //TODO can we get rid of this call by reading everything with the list?
                apiConnector.read(vm);
              
                // Ignore objects where creator isn't "vcenter-plugin"
                if ((mode == Mode.VCENTER_ONLY) && 
                        ((vm.getIdPerms().getCreator() == null)  ||
                        !(vm.getIdPerms().getCreator().equals(VNC_VCENTER_PLUGIN)))) {
                    continue;
                }
                
                VirtualMachineInfo vmInfo = new VirtualMachineInfo(vm);
                readVirtualMachineInterfaces(vmInfo);

                map.put(vmInfo.getUuid(), vmInfo);
            } catch (Exception e) {
                s_logger.error("Cannot sync VM " + vm.getName());
            }
        }
        
        return map;
    }

    public void readVirtualMachineInterfaces(VirtualMachineInfo vmInfo)
        throws IOException {
        
        VirtualMachine vm = vmInfo.apiVm;
        if (vm == null) {
            vm = vmInfo.apiVm = (VirtualMachine) apiConnector.findById(
                    VirtualMachine.class, vmInfo.getUuid());
           
            if (vm == null) {
                s_logger.error("Cannot find " + vmInfo);
                return;
            }
        }
        
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vm.getVirtualMachineInterfaceBackRefs();
        
        for (ObjectReference<ApiPropertyBase> vmInterfaceRef :
            Utils.safe(vmInterfaceRefs)) {
            String vmInterfaceUuid = vmInterfaceRef.getUuid();
            VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                    apiConnector.findById(VirtualMachineInterface.class,
                            vmInterfaceUuid);
            apiConnector.read(vmInterface);
            
            List<ObjectReference<ApiPropertyBase>> vnRefs =
                                            vmInterface.getVirtualNetwork();
            for (ObjectReference<ApiPropertyBase> vnRef : vnRefs) {
                VirtualNetworkInfo vnInfo = MainDB.getVnById(vnRef.getUuid());
                VirtualMachineInterfaceInfo vmiInfo = 
                        new VirtualMachineInterfaceInfo(vmInfo, vnInfo);
                
                vmiInfo.apiVmi = vmInterface;
                vmiInfo.setUuid(vmInterfaceUuid);
                readMacAddress(vmiInfo);            
                readInstanceIp(vmiInfo);

                vmInfo.created(vmiInfo);
            }
        }
    }

    private void readMacAddress(VirtualMachineInterfaceInfo vmiInfo) 
        throws IOException {
        VirtualMachineInterface apiVmi = vmiInfo.apiVmi;
        if (apiVmi == null) {
            apiVmi = (VirtualMachineInterface) apiConnector.findById(
                    VirtualMachineInterface.class, vmiInfo.getUuid());
            if (apiVmi == null) {
                return;
            }
            vmiInfo.apiVmi = apiVmi;
        }
        List<String> macAddresses = apiVmi.getMacAddresses().getMacAddress();
        if (macAddresses.size() > 0) {
            vmiInfo.setMacAddress(macAddresses.get(0));
        }
    }

    private void readInstanceIp(VirtualMachineInterfaceInfo vmiInfo) 
            throws IOException {
        VirtualMachineInterface apiVmi = 
                (VirtualMachineInterface) apiConnector.findById(
                    VirtualMachineInterface.class, vmiInfo.getUuid());
        if (apiVmi == null) {
            return;
        }
        vmiInfo.apiVmi = apiVmi;

        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                apiVmi.getInstanceIpBackRefs();
        
        for (ObjectReference<ApiPropertyBase> instanceIpRef : 
            Utils.safe(instanceIpRefs)) {
            InstanceIp inst = (InstanceIp)
                    apiConnector.findById(InstanceIp.class,
                            instanceIpRef.getUuid());
            if (inst != null) {
                vmiInfo.setIpAddress(inst.getAddress());
                vmiInfo.apiInstanceIp = inst;
                //TODO this is in fact a list of IP addresses
                // but we only support one
                break;
            }
        }
    }

    public VirtualMachineInterface readVirtualMachineInterface(
            VirtualMachineInterfaceInfo vmiInfo) throws IOException {
        if (vmiInfo == null) {
            return null;
        }
        
        if (vmiInfo.vmInfo.apiVm == null) {
            vmiInfo.vmInfo.apiVm = (VirtualMachine) apiConnector.findById(
                    VirtualMachine.class, vmiInfo.vmInfo.getUuid());
            
            if (vmiInfo.vmInfo.apiVm == null) {
                return null;
            }
        }
        // find VMI matching vmUuid & vnUuid
        List<ObjectReference<ApiPropertyBase>> vmInterfaceRefs =
                vmiInfo.vmInfo.apiVm.getVirtualMachineInterfaceBackRefs();
        for (ObjectReference<ApiPropertyBase> vmInterfaceRef :
            Utils.safe(vmInterfaceRefs)) {
            String vmInterfaceUuid = vmInterfaceRef.getUuid();
            VirtualMachineInterface vmInterface = (VirtualMachineInterface)
                    apiConnector.findById(VirtualMachineInterface.class,
                            vmInterfaceUuid);
            List<ObjectReference<ApiPropertyBase>> vnRefs =
                                            vmInterface.getVirtualNetwork();
            for (ObjectReference<ApiPropertyBase> vnRef : vnRefs) {
                if (vnRef.getUuid().equals(vmiInfo.vnInfo.getUuid())) {
                    vmiInfo.apiVmi = vmInterface;
                    vmiInfo.setUuid(vmInterface.getUuid());
                    readMacAddress(vmiInfo);
                    readInstanceIp(vmiInfo);
                    return vmInterface;
               }
            }
        }
        return null;
    }
    
    public void deleteInstanceIps()
            throws IOException {
            
        List<InstanceIp> apiObjs = null;
        try {
            apiObjs = (List<InstanceIp>) 
                    apiConnector.list(InstanceIp.class, null);
        } catch (Exception ex) {
            s_logger.error("Exception in api.list: " + ex);
            ex.printStackTrace();
            return ;
        }
        
        for (InstanceIp vn : apiObjs) {
            apiConnector.delete(vn);
        }
    }
    
    public void deleteInstanceIp(VirtualMachineInterfaceInfo vmiInfo)
            throws IOException {
        if (mode == Mode.VCENTER_AS_COMPUTE) {
            return;
        }
        
        if (vmiInfo == null) {
            s_logger.info("Null argument");
            return;
        }
        VirtualMachineInterface apiVmi = (VirtualMachineInterface) apiConnector.findById(
                    VirtualMachineInterface.class, vmiInfo.getUuid());
        if (apiVmi == null) {
            return;
        }
        vmiInfo.apiVmi = apiVmi;
        
       
        deleteInstanceIp(apiVmi);
        
        vmiInfo.apiInstanceIp = null;
    }

    public void deleteInstanceIp(VirtualMachineInterface apiVmi) throws IOException {
        // delete instance Ip
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                apiVmi.getInstanceIpBackRefs();
        for (ObjectReference<ApiPropertyBase> instanceIpRef : 
            Utils.safe(instanceIpRefs)) {
            s_logger.info("Delete instance IP: " + 
                    instanceIpRef.getReferredName());
            apiConnector.delete(InstanceIp.class, 
                    instanceIpRef.getUuid());
            s_logger.info("Deleted Ip Instance " + instanceIpRef.getUuid());
        }
    }

    public void deleteInstanceIps(VirtualNetwork apiVn) 
            throws IOException {
        // delete all instance Ip back refs, if there are any left
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                apiVn.getInstanceIpBackRefs();
        for (ObjectReference<ApiPropertyBase> instanceIpRef : 
            Utils.safe(instanceIpRefs)) {
            s_logger.info("Delete instance IP: " + 
                    instanceIpRef.getReferredName());
            apiConnector.delete(InstanceIp.class, 
                    instanceIpRef.getUuid());
            s_logger.info("Deleted Ip Instance " + instanceIpRef.getUuid());
        }
    }

    public void deleteInstanceIps(VirtualMachineInterface apiVmi) 
            throws IOException {
        // delete all instance Ip back refs, if there are any left
        List<ObjectReference<ApiPropertyBase>> instanceIpRefs = 
                apiVmi.getInstanceIpBackRefs();
        for (ObjectReference<ApiPropertyBase> instanceIpRef : 
            Utils.safe(instanceIpRefs)) {
            s_logger.info("Delete instance IP: " + 
                    instanceIpRef.getReferredName());
            apiConnector.delete(InstanceIp.class, 
                    instanceIpRef.getUuid());
            s_logger.info("Deleted Ip Instance " + instanceIpRef.getUuid());
        }
    }
    
    public void deleteVirtualMachineInterfaces()
            throws IOException {
            
        List<VirtualMachineInterface> apiObjs = null;
        try {
            apiObjs = (List<VirtualMachineInterface>) 
                    apiConnector.list(VirtualMachineInterface.class, null);
        } catch (Exception ex) {
            s_logger.error("Exception in api.list: " + ex);
            ex.printStackTrace();
            return ;
        }
        
        for (VirtualMachineInterface vmInterface : apiObjs) {
            deleteInstanceIps(vmInterface);
            apiConnector.delete(vmInterface);
        }
    }

    public void deleteVirtualMachineInterfaces(VirtualNetwork apiVn) 
            throws IOException {
        // delete all VMIs back refs, if there are any left
        List<ObjectReference<ApiPropertyBase>> vmiRefs = 
                apiVn.getVirtualMachineInterfaceBackRefs();
        for (ObjectReference<ApiPropertyBase> vmiRef : 
            Utils.safe(vmiRefs)) {
            VirtualMachineInterface apiVmi = 
                    (VirtualMachineInterface) apiConnector.findById(
                    VirtualMachineInterface.class, vmiRef.getUuid());
            
            if (apiVmi == null) {
                s_logger.error("Cannot delete VMI, it does not exist in the API server " 
                                + vmiRef.getUuid());
                continue;
            }
            
            deleteInstanceIps(apiVmi);
            
            s_logger.info("Delete Virtual Machine Interface: " + 
                    vmiRef.getReferredName());
            apiConnector.delete(VirtualMachineInterface.class, 
                    vmiRef.getUuid());
            s_logger.info("Deleted Virtual Machine Interface " 
                    + vmiRef.getUuid());
        }
    }

    public void deleteVirtualMachineInterfaces(VirtualMachine apiVm) 
            throws IOException {
        // delete all VMIs back refs, if there are any left
        List<ObjectReference<ApiPropertyBase>> vmiRefs = 
                apiVm.getVirtualMachineInterfaceBackRefs();
        for (ObjectReference<ApiPropertyBase> vmiRef : 
            Utils.safe(vmiRefs)) {
            VirtualMachineInterface apiVmi = 
                    (VirtualMachineInterface) apiConnector.findById(
                    VirtualMachineInterface.class, vmiRef.getUuid());
            
            if (apiVmi == null) {
                s_logger.error("Cannot delete VMI, it does not exist in the API server " 
                                + vmiRef.getUuid());
                continue;
            }
            
            deleteInstanceIps(apiVmi);
            
            s_logger.info("Delete Virtual Machine Interface: " + 
                    vmiRef.getReferredName());
            apiConnector.delete(VirtualMachineInterface.class, 
                    vmiRef.getUuid());
            s_logger.info("Deleted Virtual Machine Interface " 
                    + vmiRef.getUuid());
        }
    }

    public void deleteVirtualMachines()
            throws IOException {
            
        List<VirtualMachine> apiObjs = null;
        try {
            apiObjs = (List<VirtualMachine>) 
                    apiConnector.list(VirtualMachine.class, null);
        } catch (Exception ex) {
            s_logger.error("Exception in api.list: " + ex);
            ex.printStackTrace();
            return ;
        }
        
        for (VirtualMachine vm : apiObjs) {
            apiConnector.delete(vm);
        }
    }
    
    public void deleteVirtualNetworks()
            throws IOException {
            
        List<VirtualNetwork> apiObjs = null;
        try {
            apiObjs = (List<VirtualNetwork>) 
                    apiConnector.list(VirtualNetwork.class, null);
        } catch (Exception ex) {
            s_logger.error("Exception in api.list: " + ex);
            ex.printStackTrace();
            return ;
        }
        
        for (VirtualNetwork vn : apiObjs) {
            apiConnector.delete(vn);
        }
    }
    
    public void deleteAll() {
        try {
        deleteInstanceIps();
        deleteVirtualMachineInterfaces();
        deleteVirtualMachines();
        deleteVirtualNetworks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
