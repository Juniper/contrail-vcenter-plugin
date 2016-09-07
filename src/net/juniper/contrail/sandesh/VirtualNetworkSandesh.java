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

    private short primaryVlanId;
    public short getPrimaryVlanId() {
        return primaryVlanId;
    }

    public void setPrimaryVlanId(short vlanId) {
        this.primaryVlanId = vlanId;
    }

    private void writeFieldPrimaryVlanId(StringBuilder s, int identifier)
    {
        s.append("<primaryVlanId type=\"int\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(primaryVlanId)
         .append("</primaryVlanId>");
    }

    private short isolatedVlanId;

    public short getIsolatedVlanId() {
        return isolatedVlanId;
    }

    public void setIsolatedVlanId(short vlanId) {
        this.isolatedVlanId = vlanId;
    }

    private void writeFieldSecondaryVlanId(StringBuilder s, int identifier)
    {
        s.append("<isolatedVlanId type=\"int\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(isolatedVlanId)
         .append("</isolatedVlanId>");
    }

    private String subnetAddress;

    public String getSubnetAddress() {
        return subnetAddress;
    }

    public void setSubnetAddress(String subnetAddress) {
        this.subnetAddress = subnetAddress;
    }

    private void writeFieldSubnetAddress(StringBuilder s, int identifier)
    {
        s.append("<subnetAddress type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(subnetAddress)
         .append("</subnetAddress>");
    }

    private String subnetMask;

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }
    private void writeFieldSubnetMask(StringBuilder s, int identifier)
    {
        s.append("<subnetMask type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(subnetMask)
         .append("</subnetMask>");
    }

    private String gatewayAddress;

    public String getGatewayAddress() {
        return gatewayAddress;
    }

    public void setGatewayAddress(String gatewayAddress) {
        this.gatewayAddress = gatewayAddress;
    }
    private void writeFieldGatewayAddress(StringBuilder s, int identifier)
    {
        s.append("<gatewayAddress type=\"string\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(gatewayAddress)
         .append("</gatewayAddress>");
    }

    private boolean externalIpam;

    public boolean getExternalIpam() {
        return externalIpam;
    }

    public void setExternalIpam(boolean externalIpam) {
        this.externalIpam = externalIpam;
    }

    private void writeFieldExternalIpam(StringBuilder s, int identifier)
    {
        s.append("<externalIpam type=\"bool\" identifier=\"")
        .append(identifier)
        .append("\">")
         .append(externalIpam)
         .append("</externalIpam>");
    }

    public void writeObject(StringBuilder s,  DetailLevel detail, int identifier)
    {
        s.append("<VirtualNetworkSandesh type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        int inner_id = 1;
        writeFieldName(s, inner_id++);

        if (detail == DetailLevel.FULL || detail == DetailLevel.PARENT) {
            vInterfaces.writeObject(s, "VirtualMachineInterfaces", DetailLevel.PARENT, inner_id++);
        }

        if (detail != DetailLevel.BRIEF) {
            writeFieldPrimaryVlanId(s, inner_id++);
            writeFieldSecondaryVlanId(s, inner_id++);
            writeFieldExternalIpam(s, inner_id++);
            writeFieldSubnetAddress(s, inner_id++);
            writeFieldSubnetMask(s, inner_id++);
            writeFieldGatewayAddress(s, inner_id++);
        }

        s.append("</VirtualNetworkSandesh>");
    }
}
