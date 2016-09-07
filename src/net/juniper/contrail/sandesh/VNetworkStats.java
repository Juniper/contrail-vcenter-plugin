/* This class will be generated based on struct VNetworkStats
 * from vcenter.sandesh
 */

package net.juniper.contrail.sandesh;

public class VNetworkStats {

    private int total;
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    private void writeFieldTotal(StringBuilder s) {
        s.append("<Total type=\"struct\" identifier=\"1\">");
        s.append("<vNetworksTotal type=\"string\" identifier=\"1\" link=\"vNetworksTotal\">");
        s.append(total);
        s.append("</vNetworksTotal>");
        s.append("</Total>");
    }

    public void writeObject(StringBuilder s) {
        writeObject(s, 1);
    }

    public void writeObject(StringBuilder s, int identifier) {
        s.append("<vNetworkStats type=\"struct\" identifier=\"")
                .append(identifier)
                .append("\">");
        writeFieldTotal(s);
        s.append("</vNetworkStats>");
    }
}
