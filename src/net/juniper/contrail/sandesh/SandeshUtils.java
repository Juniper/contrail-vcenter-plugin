package net.juniper.contrail.sandesh;

public class SandeshUtils {
    public static int nullSafeComparator(String s1, String s2) {
        if (s1 == null ^ s2 == null) {
            return (s1 == null) ? -1 : 1;
        }

        if (s1 == null && s2 == null) {
            return 0;
        }

        return s1.compareTo(s2);
    }
}
