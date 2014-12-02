
all: contrail-vcenter-plugin-packages-deb
	@echo "Build complete"

contrail-vijava-deb:
	(cd vijava; debuild -i -us -uc -b)

contrail-java-api-deb:
	(cd vrouter-java-api; debuild -i -us -uc -b)

contrail-vrouter-java-api-deb:
	(cd java-api; debuild -i -us -uc -b)

contrail-vcenter-plugin-deb:
	(cd java-api; debuild -i -us -uc -b)

contrail-vcenter-plugin-packages-deb: contrail-vijava-deb contrail-java-api-deb contrail-vrouter-java-api-deb 
	@echo "Building contrail vcenter plugin package.."

