package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class ComparatorVRouterInfo
    implements Comparator<VRouterInfo> {

    public int compare(VRouterInfo vr1, VRouterInfo vr2) {
        int cmp = IpAddressUtil.compare(vr1.getIpAddr(), vr2.getIpAddr());
        if (cmp != 0) {
            return cmp;
        }     
        return vr1.getEsxiHost().compareTo(vr2.getEsxiHost());
    }
}
