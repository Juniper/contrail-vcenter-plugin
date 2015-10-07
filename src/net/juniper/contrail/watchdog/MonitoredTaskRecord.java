package net.juniper.contrail.watchdog;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MonitoredTaskRecord {
    Thread thread; // thread in which this task in executed
    long timestamp;
    long timeout;
    TimeUnit unit;
    boolean blocked;
    Date date;
    StackTraceElement[] stackTrace;

    MonitoredTaskRecord(Thread thread, long timeout, TimeUnit unit) {
        this.thread = thread;
        this.timeout = timeout;
        this.unit = unit;
    }
}
