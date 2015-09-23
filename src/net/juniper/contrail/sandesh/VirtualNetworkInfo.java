/* This class will be generated based on struct VirtualNetworkInfo
 * from vcenter.sandesh 
 */

package net.juniper.contrail.sandesh;

import java.util.Comparator;

public class VirtualNetworkInfo implements SandeshObject 
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
   
    private SandeshObjectList<VirtualMachineInfo> vMachines;
    
    public SandeshObjectList<VirtualMachineInfo> getVMachines() {
        return vMachines;
    }
    
    public void setVMachines(SandeshObjectList<VirtualMachineInfo> vMachines) {
        this.vMachines = vMachines;
    }
    
    public VirtualNetworkInfo() {
        vMachines = 
                new SandeshObjectList<VirtualMachineInfo>(VirtualMachineInfo.class,
                        ComparatorVirtualMachineInfo.BY_NAME);
    }
    
    public void writeObject(StringBuilder s,  DetailLevel detail, int identifier)
    {
        s.append("<VirtualNetworkInfo type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        int inner_id = 1;
        writeFieldName(s, inner_id++);
        vMachines.writeObject(s, "VirtualMachines", DetailLevel.PARENT, inner_id++);
        s.append("</VirtualNetworkInfo>");
    }
}
