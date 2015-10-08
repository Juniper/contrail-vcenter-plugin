/* This class will be generated based on struct TaskInfo
 * from vcenter.sandesh
 */
package net.juniper.contrail.watchdog;

import net.juniper.contrail.sandesh.DetailLevel;
import net.juniper.contrail.sandesh.SandeshObject;
import net.juniper.contrail.sandesh.SandeshObjectList;

public class TaskInfo implements SandeshObject
{
    private String name;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    private void writeFieldName(StringBuilder s, int identifier,
            DetailLevel detail)
    {
        s.append("<name type=\"string\" identifier=\"")
        .append(identifier)
        .append("\"");
        s.append(">")
        .append(name)
        .append("</name>");
    }

    private boolean blocked;

    public boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    private void writeFieldBlocked(StringBuilder s, int identifier,
            DetailLevel detail)
    {
        s.append("<blocked type=\"bool\" identifier=\"")
        .append(identifier)
        .append("\">")
        .append(blocked)
        .append("</blocked>");
    }

    private String startTime;
    public String getStartTime() {
        return startTime;
    }
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    private void writeFieldStartTime(StringBuilder s, int identifier,
            DetailLevel detail)
    {
        s.append("<startTime type=\"string\" identifier=\"")
        .append(identifier)
        .append("\"");
        s.append(">")
        .append(startTime)
        .append("</startTime>");
    }

    private String timeout;
    public String getTimeout() {
        return timeout;
    }
    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }
    private void writeFieldTimeout(StringBuilder s, int identifier,
            DetailLevel detail)
    {
        s.append("<timeout type=\"string\" identifier=\"")
        .append(identifier)
        .append("\"");
        s.append(">")
        .append(timeout)
        .append("</timeout>");
    }

    private SandeshObjectList<String> stackTrace;

    public SandeshObjectList<String> getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(SandeshObjectList<String> stackTrace) {
        this.stackTrace = stackTrace;
    }

    private void writeFieldStackTrace(StringBuilder s, int identifier,
            DetailLevel detail)
    {
        stackTrace.writeObject(s, "StackTrace", detail, identifier);
    }

    public TaskInfo() {
        this.stackTrace = new SandeshObjectList<String>(String.class);
    }

    @Override
    public void writeObject(StringBuilder s,  DetailLevel detail, int identifier)
    {
        s.append("<TaskInfo type=\"struct\" identifier=\"")
        .append(identifier)
        .append("\">");
        if (detail == DetailLevel.FULL) {
            s.append("<TaskInfoStruct>");
        }
        int inner_id = 1;
        writeFieldName(s, inner_id++, detail);
        writeFieldBlocked(s, inner_id++, detail);
        writeFieldStartTime(s, inner_id++, detail);
        writeFieldTimeout(s, inner_id++, detail);
        writeFieldStackTrace(s, inner_id++, detail);

        if (detail == DetailLevel.FULL) {
            s.append("</TaskInfoStruct>");
        }
        s.append("</TaskInfo>");
    }
}
