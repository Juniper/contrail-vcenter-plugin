package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVirtualMachineInfo 
{
     public static final Comparator<VirtualMachineInfo> BY_NAME = 
         new Comparator<VirtualMachineInfo>() {
    
        public int compare(VirtualMachineInfo vm1, VirtualMachineInfo vm2) {
            int cmp1 = vm1.getName().compareTo(vm2.getName());
            if (cmp1 != 0) {
                return cmp1;
            }
            return vm1.getMacAddr().compareTo(vm2.getMacAddr());
        }
     };
}
