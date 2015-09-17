package net.juniper.contrail.sandesh;

public class VCenterServerInfo {
    
    private String url;
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    private void writeFieldUrl(StringBuilder s)
    {
        s.append("<url type=\"string\" identifier=\"1\">")
         .append(url)
         .append("</url>");
    }
    
    private boolean connected;
    
    public boolean getConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    
    private void writeFieldConnected(StringBuilder s)
    {
        s.append("<connected type=\"bool\" identifier=\"2\">")
         .append(connected)
         .append("</connected>");
    }
  
    public void writeObject(StringBuilder s) {
        writeObject(s, 1);
    }
    
    public void writeObject(StringBuilder s, int identifier)
    {
        s.append("<VCenterServerInfo type=\"struct\" identifier=\"").
          append(identifier).
          append("\">");
        s.append("<VCenterServerStruct>");
        writeFieldUrl(s);
        writeFieldConnected(s);
        s.append("</VCenterServerStruct>");
        s.append("</VCenterServerInfo>");
    }
}
