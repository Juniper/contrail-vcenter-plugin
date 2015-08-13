# Script to create and delete multiple VNs in vcenter
# eg: python vcenter_vn_scaling.py create|delete
# adjust the cidr, mask and count iterator in the file to set number
# of VNs.

# create number of VNs in vcenter
from pyVim import connect
from pyVmomi import vim
from fabfile.testbeds import testbed
import sys, time
from netaddr import IPNetwork
from math import sqrt

# open vcenter handle
vcenter_ip = testbed.env.vcenter['server']
vcenter_port = testbed.env.vcenter['port']
vcenter_user = testbed.env.vcenter['username']
vcenter_password = testbed.env.vcenter['password']
dc_name = testbed.env.vcenter['datacenter']
guest_dvs_name = testbed.env.vcenter['dv_switch']['dv_switch_name']
vn_namepfx = 'testvn1'
vn_cidr = '2.0.0.0/8'
netmask = 27

def wait_for_task (task):
    while (task.info.state == vim.TaskInfo.State.running or
           task.info.state == vim.TaskInfo.State.queued):
        time.sleep(2)
    if task.info.state != vim.TaskInfo.State.success:
        raise ValueError("Something went wrong in wait_for_task")
    return

def match_obj(objs, name):
    for obj in objs:
        if obj.name == name:
            return obj

def reconfig_dvs(dvs_obj):
    cfg_spec = vim.DistributedVirtualSwitch.ConfigSpec()
    cfg_spec.configVersion = dvs_obj.config.configVersion
    cfg_spec.maxPorts = 60000
    wait_for_task(dvs_obj.ReconfigureDvs_Task(cfg_spec))

def get_vn_prefix():
    for each in IPNetwork(vn_cidr).subnet(netmask):
        yield each 

def create_vn(si_handle, num = 1):
    #allocate vlan, need vn name, vlan and cidr
    si_contents = si_handle.RetrieveContent()
    dc_objs = si_contents.viewManager.CreateContainerView(si_contents.rootFolder, [vim.Datacenter], True).view
    dc_obj = match_obj(dc_objs, dc_name)
    dvs_objs = si_contents.viewManager.CreateContainerView(dc_obj, [vim.dvs.VmwareDistributedVirtualSwitch], True).view
    dvs_obj = match_obj(dvs_objs, guest_dvs_name)
    provisioned_vlans = [(vlan.primaryVlanId, vlan.secondaryVlanId) for vlan in dvs_obj.config.pvlanConfig if vlan.pvlanType == 'isolated']
    mask = netmask
    subnetgen = get_vn_prefix()
    vn_prefix = subnetgen.next()
    for i in range(1, 200):
        vlan = provisioned_vlans.pop(-1)
        vn_name = vn_namepfx + "-%s" %i
        spec = vim.dvs.DistributedVirtualPortgroup.ConfigSpec(name = vn_name, type = 'earlyBinding',
                                                              numPorts = pow(2, (32 - netmask)), 
                                                              defaultPortConfig = vim.dvs.VmwareDistributedVirtualSwitch.VmwarePortConfigPolicy(
                                                                    vlan = vim.dvs.VmwareDistributedVirtualSwitch.PvlanSpec(pvlanId = vlan[1])))
        wait_for_task(dvs_obj.AddDVPortgroup_Task([spec]))
        pgs = si_contents.viewManager.CreateContainerView(dc_obj, [vim.dvs.DistributedVirtualPortgroup], True).view
        pg_obj = match_obj(pgs, vn_name)

        ip_pool = vim.vApp.IpPool(name='ip-pool-for-'+vn_name,
                          ipv4Config = vim.vApp.IpPool.IpPoolConfigInfo(subnetAddress = str(vn_prefix.network),
                                             netmask = str(vn_prefix.netmask),
                                             #range = str(list(vn_prefix.iter_hosts())[0]) + '#' + str(vn_prefix.size - 2),
                                             #ipPoolEnabled = True),
                                             gateway = str(list(vn_prefix.iter_hosts())[0])
                                             ),
                          networkAssociation = [vim.vApp.IpPool.Association(
                                                        network=pg_obj,
                                                        networkName=vn_name)])
        ip_pool_id = si_contents.ipPoolManager.CreateIpPool(dc_obj, ip_pool)
        vn_prefix = vn_prefix.next()
        print "create VN %s pool-id %s" %(vn_name, ip_pool_id)

def delete_vns(si_handle):
    si_contents = si_handle.RetrieveContent()
    dc_objs = si_contents.viewManager.CreateContainerView(si_contents.rootFolder, [vim.Datacenter], True).view
    dc_obj = match_obj(dc_objs, dc_name)
    ippools = si_contents.ipPoolManager.QueryIpPools(dc_obj)
    for obj in ippools:
        if 'ip-pool-for-%s' %vn_namepfx in obj.name:
            si_contents.ipPoolManager.DestroyIpPool(dc_obj, obj.id, True)
        else:
            print "not deleting pool ----  %s" %obj.name
    pgs = si_contents.viewManager.CreateContainerView(dc_obj, [vim.dvs.DistributedVirtualPortgroup], True).view
    for obj in pgs:
        if vn_namepfx in obj.name:
            obj.Destroy()
        else:
            print "not deleting vn --- %s" %obj.name
        
def usage():
    print "run the script like below: "
    print "python create_vns.py create|delete"
    sys.exit(1)

def main():

    if len(sys.argv) < 2:
        usage()
    try:
        si_handle = connect.SmartConnect(host=vcenter_ip, port=vcenter_port, user=vcenter_user, pwd=vcenter_password)
    except:
        raise Exception("unable to connect to vcenter server %s" %vcenter_ip)

    if sys.argv[1] == 'delete':
        delete_vns(si_handle)
    else:
        create_vn(si_handle, 2100)

if __name__ == "__main__":
    main()
