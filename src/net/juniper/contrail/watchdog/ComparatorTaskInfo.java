package net.juniper.contrail.watchdog;

import java.util.Comparator;
import net.juniper.contrail.sandesh.SandeshUtils;

public class ComparatorTaskInfo
implements Comparator<TaskInfo> {

    @Override
    public int compare(TaskInfo t1, TaskInfo t2) {
        if (t1.getBlocked() ^ t2.getBlocked()) {
            return t1.getBlocked() ? -1 : 1;
        }

        int cmp = SandeshUtils.nullSafeComparator(t1.getName(),
                t2.getName());
        if (cmp != 0) {
            return cmp;
        }

        return SandeshUtils.nullSafeComparator(t1.getStartTime(),
                t2.getStartTime());
    }
}
