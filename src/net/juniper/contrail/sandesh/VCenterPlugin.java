/* This class will be generated based on struct VCenterPlugin
 * from vcenter.sandesh
 */

package net.juniper.contrail.sandesh;

public class VCenterPlugin {
    private boolean master;

    public boolean getMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }

    private void writeFieldMaster(StringBuilder s, int identifier) {
        s.append("<master type=\"bool\" identifier=\"")
        .append(identifier)
        .append("\">")
        .append(master)
        .append("</master>");
    }

    boolean pluginSessions;
    public boolean getPluginSessions() {
        return pluginSessions;
    }

    public void setPluginSessions(boolean state) {
        this.pluginSessions = state;
    }
    private void writeFieldPluginSessions(StringBuilder s, int identifier)
    {
        s.append("<pluginSessions type=\"bool\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(pluginSessions)
         .append("</pluginSessions>");
    }

    private volatile ApiServerInfo apiServerInfo;

    public ApiServerInfo getApiServerInfo() {
        return apiServerInfo;
    }

    public void setApiServerInfo(ApiServerInfo apiServerInfo) {
        this.apiServerInfo = apiServerInfo;
    }

    private void writeFieldApiServerInfo(StringBuilder s, int identifier) {
        apiServerInfo.writeObject(s, identifier);
    }

    private volatile VCenterServerInfo vCenterServerInfo;

    public VCenterServerInfo getVCenterServerInfo() {
        return vCenterServerInfo;
    }

    public void setVCenterServerInfo(VCenterServerInfo vCenterServerInfo) {
        this.vCenterServerInfo = vCenterServerInfo;
    }

    private void writeFieldVCenterServerInfo(StringBuilder s, int identifier) {
        vCenterServerInfo.writeObject(s, identifier);
    }

    private VNetworkStats vNetworkStats;

    public VNetworkStats getVNetworkStats() {
        return vNetworkStats;
    }

    public void setVNetworkStats(VNetworkStats vNetworkStats) {
        this.vNetworkStats = vNetworkStats;
    }

    private void writeFieldVNetworkStats(StringBuilder s, int identifier) {
        vNetworkStats.writeObject(s, identifier);
    }

    public void writeObject(StringBuilder s) {
        writeObject(s, 1);
    }


    public VCenterPlugin() {
        apiServerInfo = new ApiServerInfo();
        vCenterServerInfo = new VCenterServerInfo();
        vNetworkStats = new VNetworkStats();
    }

    public void writeObject(StringBuilder s, int identifier)
    {
        s.append("<VCenterPlugin type=\"struct\" identifier=\"")
        .append(identifier)
        .append("\">");
        s.append("<VCenterPluginStruct>");

        int inner_identifier = 1;
        writeFieldMaster(s, inner_identifier++);
        if (master) {
            writeFieldPluginSessions(s, inner_identifier++);
            writeFieldApiServerInfo(s, inner_identifier++);
            writeFieldVCenterServerInfo(s, inner_identifier++);
            writeFieldVNetworkStats(s, inner_identifier++);
        }
        s.append("</VCenterPluginStruct>");
        s.append("</VCenterPlugin>");
    }
}
