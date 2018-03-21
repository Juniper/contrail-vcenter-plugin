#!/bin/bash

set -o nounset

TARGET_DIR=${1:-target/dependencies}
WGET="wget --no-verbose --timestamping --directory-prefix=${TARGET_DIR}"

DEPENDENCIES="http://repo.maven.apache.org/maven2/org/apache/commons/commons-exec/1.2/commons-exec-1.2.jar
http://repo.maven.apache.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6.jar
http://repo.maven.apache.org/maven2/commons-net/commons-net/3.3/commons-net-3.3.jar
http://repo.maven.apache.org/maven2/dom4j/dom4j/1.6.1/dom4j-1.6.1.jar
http://repo.maven.apache.org/maven2/com/google/guava/guava/14.0/guava-14.0.jar
http://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.3.1/gson-2.3.1.jar
http://repo.maven.apache.org/maven2/org/apache/httpcomponents/httpclient/4.2.1/httpclient-4.2.1.jar
http://repo.maven.apache.org/maven2/org/apache/httpcomponents/httpcore/4.2.1/httpcore-4.2.1.jar
http://repo.maven.apache.org/maven2/org/apache/thrift/libthrift/0.8.0/libthrift-0.8.0.jar
http://repo.maven.apache.org/maven2/log4j/log4j/1.2.16/log4j-1.2.16.jar
http://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.5.8/slf4j-api-1.5.8.jar
http://repo.maven.apache.org/maven2/org/slf4j/slf4j-simple/1.5.8/slf4j-simple-1.5.8.jar
http://repo.maven.apache.org/maven2/org/apache/curator/curator-framework/2.7.0/curator-framework-2.7.0.jar
http://repo.maven.apache.org/maven2/org/apache/curator/curator-client/2.7.0/curator-client-2.7.0.jar
http://repo.maven.apache.org/maven2/org/apache/curator/curator-recipes/2.7.0/curator-recipes-2.7.0.jar
http://repo.maven.apache.org/maven2/org/pacesys/openstack4j-core/2.0.1/openstack4j-core-2.0.1.jar
http://repo.maven.apache.org/maven2/org/pacesys/openstack4j/connectors/openstack4j-jersey2/2.0.1/openstack4j-jersey2-2.0.1.jar
http://repo.maven.apache.org/maven2/org/pacesys/openstack4j/2.0.1/openstack4j-2.0.1-withdeps.jar
http://repo.maven.apache.org/maven2/org/apache/zookeeper/zookeeper/3.4.5/zookeeper-3.4.5.jar"

echo Pulling runtime dependencies for vcenter-plugin to ${TARGET_DIR}...
mkdir -p ${TARGET_DIR}

while read line; do
  $WGET $line
done <<< "$DEPENDENCIES"
echo Done!
