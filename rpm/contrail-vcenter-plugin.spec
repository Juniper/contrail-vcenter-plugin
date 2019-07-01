%if 0%{?_buildTag:1}
%define         _relstr      %{_buildTag}
%else
%define         _relstr      %(date -u +%y%m%d%H%M)
%endif
%{echo: "Building release %{_relstr}\n"}
%if 0%{?_srcVer:1}
%define         _verstr      %{_srcVer}
%else
%define         _verstr      1
%endif

Release: %{_relstr}%{?dist}
Summary: Contrail vCenter Plugin Application %{?_gitVer}
Name:    contrail-vcenter-plugin
Version:  %{_verstr}
Group:   Applications/System
License: Commercial
URL:     http://www.juniper.net/
Vendor:  Juniper Networks Inc

Requires: java-1.7.0-openjdk

%{echo: "Build dir %{_topdir}\n"}
%description
Contrail vCenter Plugin Application running on Contrail config node

%build

%install
echo %{_builddir}
echo %{buildroot}
echo ""

mkdir -p %{buildroot}/usr/share/contrail-vcenter-plugin/lib
mkdir -p %{buildroot}/usr/bin
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/commons/commons-exec/1.2/commons-exec-1.2.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/commons-net/commons-net/3.3/commons-net-3.3.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/dom4j/dom4j/1.6.1/dom4j-1.6.1.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/com/google/guava/guava/14.0/guava-14.0.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.3.1/gson-2.3.1.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/httpcomponents/httpclient/4.3.6/httpclient-4.3.6.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/httpcomponents/httpcore/4.2.1/httpcore-4.2.1.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/thrift/libthrift/0.8.0/libthrift-0.8.0.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/log4j/log4j/1.2.16/log4j-1.2.16.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.5.8/slf4j-api-1.5.8.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/slf4j/slf4j-simple/1.5.8/slf4j-simple-1.5.8.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/curator/curator-framework/2.7.0/curator-framework-2.7.0.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/curator/curator-client/2.7.0/curator-client-2.7.0.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/curator/curator-recipes/2.7.0/curator-recipes-2.7.0.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/pacesys/openstack4j-core/2.0.1/openstack4j-core-2.0.1.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/pacesys/openstack4j/connectors/openstack4j-jersey2/2.0.1/openstack4j-jersey2-2.0.1.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/pacesys/openstack4j/2.0.1/openstack4j-2.0.1-withdeps.jar
wget --directory-prefix=../target http://repo.maven.apache.org/maven2/org/apache/zookeeper/zookeeper/3.4.5/zookeeper-3.4.5.jar

install -p -m 755 ../target/juniper-contrail-vcenter-3.0-SNAPSHOT.jar %{buildroot}/usr/share/contrail-vcenter-plugin/juniper-contrail-vcenter-3.0-SNAPSHOT.jar
install -p -m 755 ../target/commons-exec-1.2.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/commons-lang-2.6.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/commons-net-3.3.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/curator-client-2.7.0.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/curator-framework-2.7.0.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/curator-recipes-2.7.0.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/dom4j-1.6.1.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/guava-14.0.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/gson-2.3.1.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/httpclient-4.3.6.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/httpcore-4.2.1.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/libthrift-0.8.0.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/log4j-1.2.16.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/slf4j-api-1.5.8.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/slf4j-simple-1.5.8.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/openstack4j-jersey2-2.0.1.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/openstack4j-2.0.1-withdeps.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/openstack4j-core-2.0.1.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../target/zookeeper-3.4.5.jar %{buildroot}/usr/share/contrail-vcenter-plugin/lib
install -p -m 755 ../control_files/contrail-vcenter-plugin %{buildroot}/usr/bin
install -p -m 755 ../log4j.properties %{buildroot}/usr/share/contrail-vcenter-plugin

pushd %{buildroot}
ln -s /usr/share/contrail-vcenter-plugin/juniper-contrail-vcenter-3.0-SNAPSHOT.jar  usr/share/contrail-vcenter-plugin/juniper-contrail-vcenter.jar
popd

%files
%defattr(-, root, root)
/usr/share/*
/usr/bin/*

%changelog
* Tue Mar 6 2018 <ryadav@juniper.net>
- Initial build.
