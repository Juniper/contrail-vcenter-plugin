package net.juniper.contrail.sandesh;

public class ApiServerInfo {
    
    private String ipAddr;
    
    public String getIpAddr() {
        return ipAddr;
    }
    
    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }
    
    private void writeFieldIpAddr(StringBuilder s, int identifier)
    {
        s.append("<ipAddr type=\"string\" identifier=\"1\">")
         .append(ipAddr)
         .append("</ipAddr>");
    }
   
    private int port;
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    private void writeFieldPort(StringBuilder s, int identifier)
    {
        s.append("<port type=\"int\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(port)
         .append("</port>");
    }
    
    private boolean connected;
    
    public boolean getConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    private void writeFieldConnected(StringBuilder s, int identifier)
    {
        s.append("<connected type=\"bool\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append(connected)
         .append("</connected>");
    }
  
    public void writeObject(StringBuilder s) {
        writeObject(s, 1);
    }
    
    public void writeObject(StringBuilder s, int identifier)
    {
        s.append("<ApiServerInfo type=\"struct\" identifier=\"")
         .append(identifier)
         .append("\">");
        s.append("<ApiServerStruct>");
        int inner_id = 1;
        writeFieldIpAddr(s, inner_id++);
        writeFieldPort(s, inner_id++);
        writeFieldConnected(s, inner_id++);
        s.append("</ApiServerStruct>");
        s.append("</ApiServerInfo>");
    }
}
