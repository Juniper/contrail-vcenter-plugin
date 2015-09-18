package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVRouterInfo 
    implements Comparator<VRouterInfo> {
    
    public int compare(VRouterInfo vr1, VRouterInfo vr2) {
        return vr1.getIpAddr().compareTo(vr2.getIpAddr());
    }
}
