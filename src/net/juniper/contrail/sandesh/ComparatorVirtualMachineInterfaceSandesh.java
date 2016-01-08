package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVirtualMachineInterfaceSandesh 
{

    public static final Comparator<VirtualMachineInterfaceSandesh> BY_NAME =
            new Comparator<VirtualMachineInterfaceSandesh>() {

        @Override
        public int compare(VirtualMachineInterfaceSandesh vmi1, 
                           VirtualMachineInterfaceSandesh vmi2) {
            return SandeshUtils.nullSafeComparator(vmi1.getMacAddress(), 
                                                   vmi2.getMacAddress());
        }
    };
}
