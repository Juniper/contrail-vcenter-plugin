package net.juniper.contrail.sandesh;

import java.util.LinkedList;
import java.util.List;

public class SandeshObjectList<T extends SandeshObject> {

    List<T> list;
    final Class<T> typeParameterClass;
    
    SandeshObjectList(Class<T> typeParameterClass) {
        list = new LinkedList<T>();
        this.typeParameterClass = typeParameterClass;
    }
    
    public void add(T obj) {
        list.add(obj);
    }
    
    public void remove(T obj) {
        list.remove(obj);
    }
    
    public int size() {
        return list.size();
    }
        
    public void writeObject(StringBuilder s, String tag, 
            DetailLevel detail, int identifier) {
        
        if (list.size() == 0) {
            return;
        }
        s.append("<")
         .append(tag)
         .append(" type=\"list\" identifier=\"")
         .append(identifier)
         .append("\">")
         .append("<list type=\"struct\" size=\"")
         .append(list.size())
         .append("\">");
        
        for (T obj: list) {
            obj.writeObject(s, detail, 1);
        }
        
        s.append("</list>")
         .append("</")
         .append(tag)
         .append(">");
    }
}
