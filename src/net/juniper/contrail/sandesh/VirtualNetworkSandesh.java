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
   
    private SandeshObjectList<VirtualMachineSandesh> vMachines;
    
    public SandeshObjectList<VirtualMachineSandesh> getVMachines() {
        return vMachines;
    }
    
    public void setVMachines(SandeshObjectList<VirtualMachineSandesh> vMachines) {
        this.vMachines = vMachines;
    }
    
    public VirtualNetworkSandesh() {
        vMachines = 
                new SandeshObjectList<VirtualMachineSandesh>(VirtualMachineSandesh.class,
                        ComparatorVirtualMachineInfo.BY_NAME);
    }
    
    public void writeObject(StringBuilder s,  DetailLevel detail, int identifier)
    {
        s.append("<VirtualNetworkSandesh type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        int inner_id = 1;
        writeFieldName(s, inner_id++);
        vMachines.writeObject(s, "VirtualMachines", DetailLevel.PARENT, inner_id++);
        s.append("</VirtualNetworkSandesh>");
    }
}
