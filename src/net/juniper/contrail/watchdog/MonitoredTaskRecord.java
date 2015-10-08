package net.juniper.contrail.watchdog;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MonitoredTaskRecord {
    String name;
    Thread thread; // thread in which this task in executed
    long startTime;
    long stopTime;
    long timeout;
    TimeUnit unit;
    boolean blocked;
    Date date;
    StackTraceElement[] stackTrace;
    long minTime;
    long maxTime;
    long avgTime;

    MonitoredTaskRecord(Thread thread, long timeout, TimeUnit unit) {
        this.thread = thread;
        this.timeout = timeout;
        this.unit = unit;
    }
}
