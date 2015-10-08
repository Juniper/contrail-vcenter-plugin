package net.juniper.contrail.sandesh;

public enum ContentType
{
    XML, XSL, HTML, JSON, CSS, REQUEST;
    
    public String toString() {
        switch (this) {
        case XML:
            return "text/xml";
        case XSL:
            return "text/xsl";
        case HTML:
            return "text/html";
        case JSON:
            return "application/json";
        case CSS:
            return "text/css";
        case REQUEST:
            return "text/xml";
        default:
            return "unsupported content type";
        }
    }
    
    public static ContentType getContentType(String uri) {
        ContentType contentType = uri.endsWith(".xml") ? XML :
                                  uri.endsWith(".xsl") ? XSL :
                                  uri.endsWith(".html") ? HTML :
                                  uri.endsWith(".js") ? JSON :
                                  uri.endsWith(".css") ? CSS :
                                  REQUEST;
        return contentType;
    }
    
    public static void main(String[] args)
    {
        for (;;)
        {}
    }
};
