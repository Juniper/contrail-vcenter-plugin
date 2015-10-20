package net.juniper.contrail.watchdog;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public enum TaskWatchDog implements Runnable {
    AKER1, AKER2;

    ConcurrentMap<Runnable, MonitoredTaskRecord> monitored;
    ConcurrentHashMap<Runnable, MonitoredTaskRecord> completed;

    private TaskWatchDog() {
        monitored = new ConcurrentHashMap<Runnable, MonitoredTaskRecord>();
        completed = new ConcurrentHashMap<Runnable, MonitoredTaskRecord>();
    }

    public static
    ConcurrentMap<Runnable, MonitoredTaskRecord> getMonitoredTasks() {
        return AKER1.monitored;
    }

    public static
    ConcurrentMap<Runnable, MonitoredTaskRecord> getCompletedTasks() {
        return AKER1.completed;
    }
    
    public static void startMonitoring(Runnable task,
            String name,
            long timeout, TimeUnit unit) {

        AKER1.start(task, name, timeout, unit);
    }

    private void start(Runnable task,
            String name,
            long timeout, TimeUnit unit) {
        if (task == null) {
            throw new IllegalArgumentException("Null argument");
        }

        MonitoredTaskRecord tRec =
                new MonitoredTaskRecord(Thread.currentThread(), timeout, unit);
        tRec.name = name;
        tRec.startTime = System.currentTimeMillis();
        tRec.stackTrace = tRec.thread.getStackTrace();
        monitored.put(task, tRec);
    }

    public static void stopMonitoring(Runnable task) {
        AKER1.stop(task);
    }

    private void stop(Runnable task) {
        if (monitored.containsKey(task)) {
            MonitoredTaskRecord tRec = monitored.get(task);
            tRec.stopTime = System.currentTimeMillis();
            monitored.remove(task);

            computeStats(task, tRec);
        }
    }

    private void computeStats(Runnable task, MonitoredTaskRecord tRec) {
        long time = tRec.stopTime - tRec.startTime;
        if (AKER1.completed.containsKey(task)) {
            if (time < tRec.minTime) {
                tRec.minTime = time;
            } else if (time > tRec.maxTime) {
                tRec.maxTime = time;
            }
            tRec.avgTime = (tRec.count * tRec.avgTime)/(tRec.count+1)
                    + time/(tRec.count+1);
            tRec.count++;
        } else {
            tRec.count = 1;
            tRec.minTime = tRec.maxTime = time;
            tRec.avgTime = (double)time;
            completed.put(task, tRec);
        }
    }

    @Override
    public void run() {
        for (TaskWatchDog aker: TaskWatchDog.values()) {
            if (this != aker) {
                // this watch dog will be monitored by all other watch dogs
                aker.start(this, name(),  60000, TimeUnit.MILLISECONDS);
            }
        }

        for (ConcurrentHashMap.Entry<Runnable, MonitoredTaskRecord> entry:
            monitored.entrySet()) {
            MonitoredTaskRecord tRec = entry.getValue();
            if (tRec.blocked) {
                continue;
            }

            long time_now = System.currentTimeMillis();
            if (tRec.startTime + tRec.timeout < time_now ) {
                // task is blocked or taking too long
                tRec.blocked = true;
            }

            tRec.stackTrace = tRec.thread.getStackTrace();
        }

        for (TaskWatchDog aker: TaskWatchDog.values()) {
            if (this != aker) {
                aker.stop(this);
            }
        }
    }
}
