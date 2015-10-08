/* This class will be generated based on struct VRouterStats 
 * from vcenter.sandesh 
 */

package net.juniper.contrail.sandesh;

public class VRouterStats {

    private int total;
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    private void writeFieldTotal(StringBuilder s) {
        s.append("<Total type=\"struct\" identifier=\"1\">");
        s.append("<vRoutersTotal type=\"string\" identifier=\"1\" link=\"vRoutersTotal\">");
        s.append(total);
        s.append("</vRoutersTotal>");
        s.append("</Total>");
    }
    
    private int up;
    public int getUp() { return up;}
    public void setUp(int up) { this.up = up;}
    private void writeFieldUp(StringBuilder s) {
        s.append("<Up type=\"struct\" identifier=\"2\">");
        s.append("<vRoutersUp type=\"string\" identifier=\"1\" link=\"vRoutersUp\">");
        s.append(up);
        s.append("</vRoutersUp>");
        s.append("</Up>");
    }
    
    private int down;
    public int getDown() { return down; }
    public void setDown(int down) {  this.down = down; }
    private void writeFieldDown(StringBuilder s) {
        s.append("<Down type=\"struct\" identifier=\"3\">");
        s.append("<vRoutersDown type=\"string\" identifier=\"1\" link=\"vRoutersDown\">");
        s.append(down);
        s.append("</vRoutersDown>");
        s.append("</Down>");
    }
    
    public void writeObject(StringBuilder s) {
        writeObject(s, 1);
    }
    
    public void writeObject(StringBuilder s, int identifier) {       
        s.append("<vRouterStats type=\"struct\" identifier=\"")
                .append(identifier)
                .append("\">");
        writeFieldTotal(s);
        writeFieldUp(s);
        writeFieldDown(s);
        s.append("</vRouterStats>");
    }
}
