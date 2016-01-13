/* This class will be generated based on struct VirtualMachineSandesh
 * from vcenter.sandesh 
 */

package net.juniper.contrail.sandesh;

public class VirtualMachineInterfaceSandesh implements SandeshObject 
{   
    private String macAddress;
    public String getMacAddress() {
        return macAddress;
    }
    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }
    private void writeFieldMacAddress(StringBuilder s, int identifier)
    {
        s.append("<macAddress type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(macAddress)
         .append("</macAddress>");
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
    
    private String virtualMachine;   
    public String getVirtualMachine() {
        return virtualMachine;
    }
    public void setVirtualMachine(String virtualMachine) {
        this.virtualMachine = virtualMachine;
    }
    private void writeFieldVirtualMachine(StringBuilder s, int identifier)
    {
        s.append("<virtualMachine type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(virtualMachine)
         .append("</virtualMachine>");
    }
    
    private String ipAddress;
    public String getIpAddress() {
        return ipAddress;
    }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    private void writeFieldIpAddress(StringBuilder s, int identifier)
    {
        s.append("<ipAddress type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(ipAddress)
         .append("</ipAddress>");
    }
    
    boolean poweredOn;
    
    public boolean poweredOn() {
        return poweredOn;
    }

    public void setPoweredOn(boolean poweredOn) {
        this.poweredOn = poweredOn;
    }
 
    private void writeFieldPoweredOn(StringBuilder s, int identifier)
    {
        s.append("<poweredOn type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(poweredOn)
         .append("</poweredOn>");
    }

    boolean portAdded;
    
    public boolean getPortAdded() {
        return portAdded;
    }

    public void setPortAdded(boolean portAdded) {
        this.portAdded = portAdded;
    }
 
    private void writeFieldPortAdded(StringBuilder s, int identifier)
    {
        s.append("<portAdded type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(portAdded)
         .append("</portAdded>");
    }
  
    public VirtualMachineInterfaceSandesh() {

    }
    
    public void writeObject(StringBuilder s, DetailLevel detail, int identifier)
    {
        s.append("<VirtualMachineInterfaceSandesh type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        int inner_id = 1;
        writeFieldNetwork(s, inner_id++);
        writeFieldVirtualMachine(s, inner_id++);
        writeFieldMacAddress(s, inner_id++);
        writeFieldIpAddress(s, inner_id++);
        writeFieldPoweredOn(s, inner_id++);
        writeFieldPortAdded(s, inner_id++);
        s.append("</VirtualMachineInterfaceSandesh>");
    }
}
