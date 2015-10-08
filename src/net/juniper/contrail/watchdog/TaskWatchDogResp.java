package net.juniper.contrail.watchdog;

import java.util.Date;
import java.util.Map;
import net.juniper.contrail.sandesh.DetailLevel;
import net.juniper.contrail.sandesh.SandeshObjectList;

public class TaskWatchDogResp {

    private SandeshObjectList<TaskInfo> tasks;

    public TaskWatchDogResp(TaskWatchDogReq req) {
        tasks = new SandeshObjectList<TaskInfo>(TaskInfo.class,
                new ComparatorTaskInfo());

        for (Map.Entry<Runnable, MonitoredTaskRecord> entry:
            TaskWatchDog.getMonitoredTasks().entrySet()) {

            MonitoredTaskRecord rec = entry.getValue();
            TaskInfo taskInfo = new TaskInfo();
            taskInfo.setName(rec.name);
            taskInfo.setBlocked(rec.blocked);
            taskInfo.setStartTime(new Date(rec.startTime).toString());
            taskInfo.setTimeout(Long.toString(rec.timeout) + " " + rec.unit);

            if (rec.stackTrace != null) {
                SandeshObjectList<String> stack = taskInfo.getStackTrace();
                for (int i = 0; i < rec.stackTrace.length; i++) {
                    stack.add(rec.stackTrace[i].toString());
                }
            }
            tasks.add(taskInfo);
        }
    }

    public void writeObject(StringBuilder s) {
        s.append("<TaskWatchDogResp type=\"sandesh\">");
        tasks.writeObject(s, "TaskInfo", DetailLevel.REGULAR, 1);
        s.append("</TaskWatchDogResp>");
    }
}
