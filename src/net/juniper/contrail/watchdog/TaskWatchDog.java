package net.juniper.contrail.watchdog;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public enum TaskWatchDog implements Runnable, MonitoredTask {
    AKER1, AKER2;

    private volatile long timestamp;

    ConcurrentMap<MonitoredTask, MonitoredTaskRecord> monitored;

    private TaskWatchDog() {
        monitored = new ConcurrentHashMap<MonitoredTask, MonitoredTaskRecord>();
        timestamp = System.currentTimeMillis();
    }

    public static
    ConcurrentMap<MonitoredTask, MonitoredTaskRecord> getMonitoredTasks() {
        return AKER1.monitored;
    }

    public static void startMonitoring(MonitoredTask task,
            long timeout, TimeUnit unit) {
        if (task == null) {
            throw new IllegalArgumentException("Null argument");
        }

        MonitoredTaskRecord tRec =
                new MonitoredTaskRecord(Thread.currentThread(), timeout, unit);
        tRec.timestamp = task.getLastTimeStamp();
        tRec.stackTrace = tRec.thread.getStackTrace();
        AKER1.monitored.put(task, tRec);
    }

    public static void stopMonitoring(MonitoredTask task) {
        if (AKER1.monitored.containsKey(task)) {
            AKER1.monitored.remove(task);
        }
    }

    @Override
    public long getLastTimeStamp() {
        return timestamp;
    }

    @Override
    public void run() {
        timestamp = System.currentTimeMillis();

        // this watch dog will be monitored by all other watch dogs
        MonitoredTaskRecord akerTRec =
                new MonitoredTaskRecord(Thread.currentThread(),
                        60000, TimeUnit.MILLISECONDS);
        akerTRec.timestamp = timestamp;
        for (TaskWatchDog aker: TaskWatchDog.values()) {
            if (this != aker) {
                aker.monitored.putIfAbsent(this, akerTRec);
            }
        }

        for (ConcurrentHashMap.Entry<MonitoredTask, MonitoredTaskRecord> entry:
            monitored.entrySet()) {
            MonitoredTask task = entry.getKey();
            long checkpointValue = task.getLastTimeStamp();
            MonitoredTaskRecord tRec = entry.getValue();
            if (tRec.blocked) {
                continue;
            }
            if (checkpointValue == tRec.timestamp) {
                long time_now = System.currentTimeMillis();
                if (tRec.timestamp + tRec.timeout < time_now ) {
                    // task is blocked or taking too long
                    tRec.blocked = true;
                    tRec.stackTrace = tRec.thread.getStackTrace();
                    continue;
                }
            }
            tRec.stackTrace = tRec.thread.getStackTrace();
        }

        for (TaskWatchDog aker: TaskWatchDog.values()) {
            if (this != aker) {
                aker.monitored.remove(this);
            }
        }

        // update again the timestamp if the process is run only one time
        timestamp = System.currentTimeMillis();
    }
}
