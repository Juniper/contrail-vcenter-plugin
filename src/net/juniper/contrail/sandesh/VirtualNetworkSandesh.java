/* This class will be generated based on struct VirtualNetworkSandesh
 * from vcenter.sandesh 
 */

package net.juniper.contrail.sandesh;

public class VirtualNetworkSandesh implements SandeshObject 
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
   
    private SandeshObjectList<VirtualMachineInterfaceSandesh> vInterfaces;
    
    public SandeshObjectList<VirtualMachineInterfaceSandesh> getVInterfaces() {
        return vInterfaces;
    }
    
    public void setVInterfaces(SandeshObjectList<VirtualMachineInterfaceSandesh> vInterfaces) {
        this.vInterfaces = vInterfaces;
    }
    
    public VirtualNetworkSandesh() {
        vInterfaces = 
                new SandeshObjectList<VirtualMachineInterfaceSandesh>(
                        VirtualMachineInterfaceSandesh.class,
                        ComparatorVirtualMachineInterfaceSandesh.BY_NAME);
    }
    
    public void writeObject(StringBuilder s,  DetailLevel detail, int identifier)
    {
        s.append("<VirtualNetworkSandesh type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        int inner_id = 1;
        writeFieldName(s, inner_id++);
        vInterfaces.writeObject(s, "VirtualMachineInterfaces", DetailLevel.PARENT, inner_id++);
        s.append("</VirtualNetworkSandesh>");
    }
}
