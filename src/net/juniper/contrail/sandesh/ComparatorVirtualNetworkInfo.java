package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVirtualNetworkInfo 
    implements Comparator<VirtualNetworkSandesh> {

    public int compare(VirtualNetworkSandesh vn1, VirtualNetworkSandesh vn2) {
        return SandeshUtils.nullSafeComparator(vn1.getName(), vn2.getName());
    }
}
