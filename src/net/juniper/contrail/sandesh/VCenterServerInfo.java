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

    private String operationalStatus;

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    private void writeFieldOperationalStatus(StringBuilder s)
    {
        s.append("<operationalStatus type=\"string\" identifier=\"3\">")
         .append(operationalStatus)
         .append("</operationalStatus>");
    }

    private String datacenterMor;

    public String getDatacenterMor() {
        return datacenterMor;
    }

    public void setDatacenterMor(String datacenterMor) {
        this.datacenterMor = datacenterMor;
    }

    private void writeFieldDatacenterMor(StringBuilder s)
    {
        s.append("<datacenterMor type=\"string\" identifier=\"4\">")
         .append(datacenterMor)
         .append("</datacenterMor>");
    }

    private String dvsMor;

    public String getDvsMor() {
        return dvsMor;
    }

    public void setDvsMor(String dvsMor) {
        this.dvsMor = dvsMor;
    }

    private void writeFieldDvsMor(StringBuilder s)
    {
        s.append("<dvsMor type=\"string\" identifier=\"5\">")
         .append(dvsMor)
         .append("</dvsMor>");
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
        writeFieldOperationalStatus(s);
        writeFieldDatacenterMor(s);
        writeFieldDvsMor(s);
        s.append("</VCenterServerStruct>");
        s.append("</VCenterServerInfo>");
    }
}
