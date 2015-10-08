package net.juniper.contrail.watchdog;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public enum TaskWatchDog implements Runnable {
    AKER1, AKER2;

    ConcurrentMap<Runnable, MonitoredTaskRecord> monitored;
    ConcurrentHashMap<String, RunningTimeStats> completed;

    class RunningTimeStats {
        long count;
        long min;
        long max;
        long avg;

        public RunningTimeStats() {}
    }

    private TaskWatchDog() {
        monitored = new ConcurrentHashMap<Runnable, MonitoredTaskRecord>();
        completed = new ConcurrentHashMap<String, RunningTimeStats>();
    }

    public static
    ConcurrentMap<Runnable, MonitoredTaskRecord> getMonitoredTasks() {
        return AKER1.monitored;
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

            computeStats(tRec);
        }
    }

    private void computeStats(MonitoredTaskRecord tRec) {
        RunningTimeStats stats = null;
        long time = tRec.stopTime - tRec.startTime;
        if (AKER1.completed.containsKey(tRec.name)) {
            stats = AKER1.completed.get(tRec.name);
            if (time < stats.min) {
                stats.min = time;
            } else if (time > stats.max) {
                stats.max = time;
            }
            stats.avg = (stats.count * stats.avg + time)/(stats.count+1);
            stats.count++;
        } else {
            stats = AKER1.new RunningTimeStats();
            stats.count = 1;
            stats.min = stats.max = stats.avg = time;
        }
        completed.put(tRec.name, stats);
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
