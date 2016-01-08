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
    
    String vrouterIpAddress;
    
    public String getVrouterIpAddress() {
        return vrouterIpAddress;
    }

    public void setVrouterIpAddress(String vrouterIpAddress) {
        this.vrouterIpAddress = vrouterIpAddress;
    }
    
    private void writeFieldVrouterIpAddress(StringBuilder s, int identifier)
    {
        s.append("<vRouterIpAddress type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(vrouterIpAddress)
         .append("</vRouterIpAddress>");
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

    boolean toolsRunningStatus;
    public boolean getToolsRunningStatus() {
        return toolsRunningStatus;
    }

    public void setToolsRunningStatus(boolean toolsRunningStatus) {
        this.toolsRunningStatus = toolsRunningStatus;
    }

    private void writeFieldToolsRunningStatus(StringBuilder s, int identifier)
    {
        s.append("<toolsRunningStatus type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(toolsRunningStatus)
         .append("</toolsRunningStatus>");
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
        
        if (detail != DetailLevel.PARENT) {
            writeFieldEsxiHost(s,inner_id++);
           
        }
        if (detail != DetailLevel.PARENT) {
            writeFieldPowerState(s, inner_id++);
        }
        if (detail != DetailLevel.PARENT) {
            writeFieldVrouterIpAddress(s, inner_id++);
        }
        if (detail != DetailLevel.PARENT) {
            writeFieldToolsRunningStatus(s, inner_id++);
        }
        s.append("</VirtualMachineSandesh>");
    }
}
