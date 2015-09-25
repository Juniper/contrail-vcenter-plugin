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
    
    boolean pluginState;
    public boolean getPluginState() {
        return pluginState;
    }

    public void setPluginState(boolean state) {
        this.pluginState = state;
    }
    private void writeFieldPluginState(StringBuilder s, int identifier)
    {
        s.append("<pluginState type=\"bool\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(pluginState)
         .append("</pluginState>");
    }
  
    private VRouterStats vRouterStats;
    
    public VRouterStats getVRouterStats() {
        return vRouterStats;
    }
    
    public void setVRouterStats(VRouterStats vRouterStats) {
        this.vRouterStats = vRouterStats;
    }
       
    private void writeFieldVRouterStats(StringBuilder s, int identifier) {
        vRouterStats.writeObject(s, identifier);
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
    
    public void writeObject(StringBuilder s) {
        writeObject(s, 1);
    }
    
    public VCenterPlugin() {
        vRouterStats = new VRouterStats();
        apiServerInfo = new ApiServerInfo();
        vCenterServerInfo = new VCenterServerInfo();
    }
    
    public void writeObject(StringBuilder s, int identifier)
    {
        s.append("<VCenterPlugin type=\"struct\" identifier=\"")
        .append(identifier)
        .append("\">");
        s.append("<VCenterPluginStruct>");
        
        int inner_identifier = 1;
        writeFieldMaster(s, inner_identifier++);
        writeFieldPluginState(s, inner_identifier++);
        writeFieldVRouterStats(s, inner_identifier++);
        writeFieldApiServerInfo(s, inner_identifier++);
        writeFieldVCenterServerInfo(s, inner_identifier++);
        s.append("</VCenterPluginStruct>");
        s.append("</VCenterPlugin>");
    }
}
