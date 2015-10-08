package net.juniper.contrail.watchdog;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import net.juniper.contrail.watchdog.TaskWatchDog;

@RunWith(JUnit4.class)
public class TaskWatchDogTest {
    private final static int TASK_COUNT = 50;
    CountDownLatch latch;

    @Test
    public void test1() {
        latch = new CountDownLatch(TASK_COUNT);
        ScheduledExecutorService ex = Executors.newScheduledThreadPool(TASK_COUNT);

        MonitoredTaskTest tasks[] = new MonitoredTaskTest[TASK_COUNT];
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int i = 0; i < TASK_COUNT; i++) {
            tasks[i] = new MonitoredTaskTest(0);
            futures.add(ex.submit(tasks[i]));
        }

        try {
            // wait for all the tasks to get blocked
            latch.await();
        } catch (InterruptedException e) {
            assertTrue("Task interrupted " + e.getMessage(), false);
            e.printStackTrace();
        }

        for (Future<?> future : futures) {
            assertFalse(future.isDone());
        }

        // launch watch dogs
        ScheduledExecutorService watchDogExecutor =
                Executors.newScheduledThreadPool(2);
        List<Future<?>> futuresAker = new ArrayList<Future<?>>();
        for (Runnable aker : TaskWatchDog.values()) {
            futuresAker.add(watchDogExecutor.schedule(aker, 0,
                    TimeUnit.SECONDS));
        }

        // wait for watch dogs to finish running
        for (Future<?> future : futuresAker) {
            try {
                future.get();
            } catch (InterruptedException e1) {
                assertTrue("Watchdogs interrupted " + e1.getMessage(),
                        false);
                e1.printStackTrace();
            } catch (ExecutionException e2) {
                assertTrue("Watchdogs execution exception " + e2.getMessage(),
                        false);
                e2.printStackTrace();
            }
        }

        for (Future<?> future : futuresAker) {
            assertTrue(future.isDone());
        }

        for (TaskWatchDog aker : TaskWatchDog.values()) {
            assertNotEquals(aker.monitored, null);
        }

        assertFalse(TaskWatchDog.getMonitoredTasks().isEmpty());

        for (ConcurrentHashMap.Entry<Runnable, MonitoredTaskRecord> entry:
            TaskWatchDog.getMonitoredTasks().entrySet()) {
            MonitoredTaskRecord tRec = entry.getValue();
            assertTrue(tRec.blocked);
        }

        for (MonitoredTaskTest task: tasks) {
            assertTrue(TaskWatchDog.getMonitoredTasks().containsKey(task));
        }
    }

    class MonitoredTaskTest implements Runnable {
        private long timeout;
        Random r;

        MonitoredTaskTest(long timeout) {
            this.timeout = timeout;
            r = new Random();
        }

        @Override
        public void run() {

            TaskWatchDog.startMonitoring(this, "Test", timeout, TimeUnit.MILLISECONDS);
            latch.countDown();

            // loop forever
            for (;;) {
                try {
                    Thread.sleep(100000);
                } catch (InterruptedException e) {
                }
            }
        }
    }
}
