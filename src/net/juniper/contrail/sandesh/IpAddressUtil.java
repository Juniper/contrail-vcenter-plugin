package net.juniper.contrail.sandesh;

public class IpAddressUtil {
    public static final int INADDR4SZ = 4;
    public static final int INADDR6SZ = 16;

    public static long getIpv4Address(String sAddr) 
        throws IllegalArgumentException {

        if (sAddr == null) {
            return 0;
        }

        String[] addr = sAddr.split("\\.", INADDR4SZ + 1);
        if (addr.length != INADDR4SZ) {
            throw new IllegalArgumentException("Malformed IPv4 address");
        }

        long result = 0;
        for (int i = addr.length - 1; i >= 0; i--) {
            int part = Integer.parseInt(addr[i]);
            if (part < 0 || part > 255 ) {
                throw new IllegalArgumentException("Malformed IPv4 address");
            }
            result |= part << (8* (addr.length - 1 - i));
        }
        return result & 0xFFFFFFFFL;
    }

    public static int compareV4(String sAddr1, String sAddr2) 
            throws IllegalArgumentException {
        long addr1 = getIpv4Address(sAddr1);
        long addr2 = getIpv4Address(sAddr2);
        return compareV4(addr1, addr2);
    }

    public static int compareV4(long addr1, long addr2) 
            throws IllegalArgumentException {
 
        if (addr1 < addr2) {
            return -1;
        }
        if (addr1 > addr2) {
            return 1;
        }
        return 0;
    }
    
    public static int[] getIpv6Address(String sAddr) 
            throws IllegalArgumentException {

        int[] result = new int[INADDR6SZ];
        
        if (sAddr == null) {
            return result;
        }

        String[] addr = sAddr.split(":", INADDR6SZ + 1);
        for (int i = addr.length - 1; i >= 0; i--) {
            
            if (addr[i].trim().equals("")) {
                continue;
            }
            int part = Integer.parseInt(addr[i], 16);
            if (part < 0 || part > 0xFF ) {
                throw new IllegalArgumentException("Malformed IPv6 address");
            }
            result[INADDR6SZ - 1 - (addr.length - 1 - i)] = part;
        }

        return result;
    }

    public static int compareV6(String sAddr1, String sAddr2) 
            throws IllegalArgumentException {
        int[] addr1 = getIpv6Address(sAddr1);
        int[] addr2 = getIpv6Address(sAddr2);

        return compareV6(addr1, addr2);
    }

    public static int compareV6(int[] addr1, int[] addr2) 
            throws IllegalArgumentException {
        if (addr1.length != INADDR6SZ || addr2.length != INADDR6SZ) {
            throw new IllegalArgumentException();
        }
      
        for (int i = 0; i < INADDR6SZ; i++) {
            if ((addr1[i] & 0xFF) < (addr2[i] & 0xFF)) {
                return -1;
            }   
            if ((addr1[i] & 0xFF) > ( addr2[i] & 0xFF)) {
                return 1;
            }
        }
        return 0;
    }
    
    public static int compare(String sAddr1, String sAddr2) 
            throws IllegalArgumentException {
        
        if (sAddr1 == null && sAddr2 == null) {
            return 0;
        }

        boolean addr1IpV6 = false, addr2IpV6 = false;
        long addr1 = 0, addr2 = 0;
        int[] addr1v6 = null, addr2v6 = null;
        if (sAddr1 != null) {
            if (sAddr1.indexOf(':') != -1) {
                addr1IpV6 = true;
                addr1v6 = getIpv6Address(sAddr1);
            } else {
                addr1 = getIpv4Address(sAddr1);
            }
        }
        
        if (sAddr2 != null) {
            if (sAddr2.indexOf(':') != -1) {
                addr2IpV6 = true;
                addr2v6 = getIpv6Address(sAddr2);
            } else {
                addr2 = getIpv4Address(sAddr2);
            }
        }
        
        if ((sAddr1 != null && sAddr2 == null)
            || (!addr1IpV6 && addr2IpV6)) {
            return -1;
        }
        if ((sAddr1 == null && sAddr2 != null)
                || ((addr1IpV6 && !addr2IpV6))) {
            return 1;
        }
        
        int cmp = 0;
        if (!addr1IpV6 && !addr2IpV6) {
            cmp = compareV4(addr1, addr2);
        } else {
            cmp = compareV6(addr1v6, addr2v6);
        }
        return cmp;
    }
}
