package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVirtualNetworkInfo 
    implements Comparator<VirtualNetworkInfo> {
    
    public int compare(VirtualNetworkInfo vn1, VirtualNetworkInfo vn2) {
        return vn1.getName().compareTo(vn2.getName());
    }
}
