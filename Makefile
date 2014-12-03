
all: contrail-vcenter-plugin-packages-deb
	@echo "Build complete"

contrail-vijava-deb:
	(cd vijava; mvn install; debuild -i -us -uc -b)

contrail-java-api-deb:
	(cd java-api; mvn install; debuild -i -us -uc -b)

contrail-vrouter-java-api-deb:
	(cd vrouter-java-api; mvn install; debuild -i -us -uc -b)

contrail-vcenter-plugin-deb:
	(cd vcenter-plugin; mvn install; debuild -i -us -uc -b)

contrail-vcenter-plugin-packages-deb: contrail-vijava-deb contrail-java-api-deb contrail-vrouter-java-api-deb contrail-vcenter-plugin-deb
	@echo "Building contrail vcenter plugin package.."

clean:
	(rm -rf *.deb *.changes *.build)
