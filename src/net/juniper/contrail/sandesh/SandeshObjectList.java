package net.juniper.contrail.sandesh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class SandeshObjectList<T> {

    private Collection<T> list;
    final Class<T> typeParameterClass;

    public SandeshObjectList(Class<T> typeParameterClass) {
        list = new ArrayList<T>();
        this.typeParameterClass = typeParameterClass;
    }

    public SandeshObjectList(Class<T> typeParameterClass, boolean sort) {
        if (sort == true) {
            // no comparator provided, sort by natural order
            list = new TreeSet<T>();
        } else {
            list = new ArrayList<T>();
        }
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
        ;
        s.append("<list type=\"struct\" size=\"")
        .append(list.size())
        .append("\">");
        for (T obj: list) {
            if (obj instanceof SandeshObject) {
                ((SandeshObject)obj).writeObject(s, detail, 1);
            } else {
                s.append("<ElemInfo type=\"struct\" identifier=\"1\">")
                .append("<Element type=\"string\" identifier=\"1\">")
                .append(obj.toString())
                .append("</Element>")
                .append("</ElemInfo>");
            }
        }

        s.append("</list>")
        .append("</")
        .append(tag)
        .append(">");
    }
}
