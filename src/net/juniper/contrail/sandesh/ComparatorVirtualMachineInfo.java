package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVirtualMachineInfo
{
    public static final Comparator<VirtualMachineSandesh> BY_NAME =
            new Comparator<VirtualMachineSandesh>() {

        @Override
        public int compare(VirtualMachineSandesh vm1, VirtualMachineSandesh vm2) {
            int cmp = SandeshUtils.nullSafeComparator(vm1.getName(),
                    vm2.getName());
            if (cmp != 0) {
                return cmp;
            }
            return SandeshUtils.nullSafeComparator(vm1.getMacAddr(),
                    vm2.getMacAddr());
        }
    };
}
