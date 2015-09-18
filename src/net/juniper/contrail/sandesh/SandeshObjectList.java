package net.juniper.contrail.sandesh;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class SandeshObjectList<T extends SandeshObject> {

    private SortedSet<T> list;
    final Class<T> typeParameterClass;
    
    public SandeshObjectList(Class<T> typeParameterClass) {
        list = new TreeSet<T>();
        this.typeParameterClass = typeParameterClass;
    }
    
    public SandeshObjectList(Class<T> typeParameterClass, Comparator<? super T> comparator) {
        list = new TreeSet<T>(comparator);
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
