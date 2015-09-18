package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVirtualMachineInfo 
    implements Comparator<VirtualMachineInfo> {
    
    public int compare(VirtualMachineInfo vm1, VirtualMachineInfo vm2) {
        return vm1.getName().compareTo(vm2.getName());
    }
}
