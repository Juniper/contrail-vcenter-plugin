/* This class will be generated based on struct VRouterInfo
 * from vcenter.sandesh 
 */

package net.juniper.contrail.sandesh;

public class VRouterInfo implements SandeshObject 
{    
    private String ipAddr;
    public String getIpAddr() {
        return ipAddr;
    }
    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }
    private void writeFieldIpAddr(StringBuilder s, int identifier, DetailLevel detail)
    {
        s.append("<ipAddr type=\"string\" identifier=\"")
         .append(identifier)
         .append("\"");
        if (detail != DetailLevel.FULL) {
          s.append(" link=\"vRouterDetail\"");
        } else {
            s.append(" goto=\"")
            .append(ipAddr)
            .append(":8085\"");
        }
        s.append(">")
         .append(ipAddr)
         .append("</ipAddr>");
    }
   
    boolean state;
    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }
    private void writeFieldState(StringBuilder s, int identifier)
    {
        s.append("<state type=\"bool\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(state)
         .append("</state>");
    }
  
    private String esxiHost;   
    public String getEsxiHost() {
        return esxiHost;
    }
    public void setEsxiHost(String esxiHost) {
        this.esxiHost = esxiHost;
    }
    private void writeFieldEsxiHost(StringBuilder s, int identifier)
    {
        s.append("<EsxiHost type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(esxiHost)
         .append("</EsxiHost>");
    }
    
    private SandeshObjectList<VirtualNetworkSandesh> vNetworks;
    
    public SandeshObjectList<VirtualNetworkSandesh> getVNetworks() {
        return vNetworks;
    }
    
    public void setVNetworks(SandeshObjectList<VirtualNetworkSandesh> vNetworks) {
        this.vNetworks = vNetworks;
    }
    
    public VRouterInfo() {
        this.vNetworks = 
                new SandeshObjectList<VirtualNetworkSandesh>(VirtualNetworkSandesh.class,
                        new ComparatorVirtualNetworkInfo());
    }
    
    public void writeObject(StringBuilder s,  DetailLevel detail, int identifier)
    {
        s.append("<VRouterInfo type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        if (detail == DetailLevel.FULL) {
            s.append("<VRouterInfoStruct>");
        }
        int inner_id = 1;
        writeFieldIpAddr(s, inner_id++, detail);
        writeFieldState(s, inner_id++);
        writeFieldEsxiHost(s, inner_id++);
        if (detail == DetailLevel.FULL) {
            vNetworks.writeObject(s, "VirtualNetworks", 
                    DetailLevel.REGULAR, inner_id++);
        }
        if (detail == DetailLevel.FULL) {
            s.append("</VRouterInfoStruct>");
        }
        s.append("</VRouterInfo>");
    }
}
