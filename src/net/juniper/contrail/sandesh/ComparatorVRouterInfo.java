package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVRouterInfo 
    implements Comparator<VRouterInfo> {
    
    public int compare(VRouterInfo vr1, VRouterInfo vr2) {
        int cmp1 = vr1.getIpAddr().compareTo(vr2.getIpAddr());
        if (cmp1 != 0) {
            return cmp1;
        }
        return vr1.getEsxiHost().compareTo(vr2.getEsxiHost());
    }
}
