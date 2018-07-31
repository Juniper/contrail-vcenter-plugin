package net.juniper.contrail.vcenter;

public abstract class VCenterObject {
    abstract void create(VncDB vnc) throws Exception;
    abstract void update(VCenterObject obj, VncDB vnc) throws Exception;
    abstract void delete(VncDB vnc) throws Exception;
    abstract void sync(VCenterObject obj, VncDB vncDB) throws Exception;
}
