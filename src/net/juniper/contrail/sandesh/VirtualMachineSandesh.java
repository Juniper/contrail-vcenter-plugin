/* This class will be generated based on struct VirtualMachineSandesh
 * from vcenter.sandesh 
 */

package net.juniper.contrail.sandesh;

public class VirtualMachineSandesh implements SandeshObject 
{    
    private String name;   
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    private void writeFieldName(StringBuilder s, int identifier)
    {
        s.append("<name type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(name)
         .append("</name>");
    }
    
    private String ipAddr;
    public String getIpAddr() {
        return ipAddr;
    }
    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    @SuppressWarnings("unused")
    private void writeFieldIpAddr(StringBuilder s, int identifier)
    {
        s.append("<ipAddr type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(ipAddr)
         .append("</ipAddr>");
    }
   
    private String macAddr;
    public String getMacAddr() {
        return macAddr;
    }
    public void setMacAddr(String macAddr) {
        this.macAddr = macAddr;
    }
    private void writeFieldMacAddr(StringBuilder s, int identifier)
    {
        s.append("<macAddr type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(macAddr)
         .append("</macAddr>");
    }
   
    String powerState;
    public String getPowerState() {
        return powerState;
    }

    public void setPowerState(String powerState) {
        this.powerState = powerState;
    }
    private void writeFieldPowerState(StringBuilder s, int identifier)
    {
        s.append("<powerState type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(powerState)
         .append("</powerState>");
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
    
    private String network;   
    public String getNetwork() {
        return network;
    }
    public void setNetwork(String network) {
        this.network = network;
    }
    private void writeFieldNetwork(StringBuilder s, int identifier)
    {
        s.append("<network type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(network)
         .append("</network>");
    }
    
    public VirtualMachineSandesh() {

    }
    
    public void writeObject(StringBuilder s, DetailLevel detail, int identifier)
    {
        s.append("<VirtualMachineSandesh type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        int inner_id = 1;
        writeFieldName(s, inner_id++);
        // TODO VCenterDB does not have the correct value
        // comment out until fixed to avoid misleading info
        // writeFieldIpAddr(s, inner_id++);
        writeFieldMacAddr(s, inner_id++);
        writeFieldPowerState(s, inner_id++);
        if (detail != DetailLevel.PARENT) {
            writeFieldEsxiHost(s,inner_id++);
           
        }
        if (detail != DetailLevel.PARENT) {
            writeFieldNetwork(s, inner_id++);
        }
        s.append("</VirtualMachineSandesh>");
    }
}
