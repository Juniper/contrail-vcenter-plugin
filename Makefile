
all: contrail-install-vcenter-plugin-deb
	@echo "Build complete"

contrail-vijava-deb:
	@echo "Building contrail vijava api package.."
	(cd vijava; debuild -i -us -uc -b)

contrail-java-api-deb:
	@echo "Building contrail java api package.."
	(cd java-api; debuild -i -us -uc -b)

contrail-vrouter-java-api-deb:
	@echo "Building contrail vrouter java api package.."
	(cd vrouter-java-api; debuild -i -us -uc -b)

contrail-vcenter-plugin-deb:
	@echo "Building contrail vcenter plugin package.."
	(cd vcenter-plugin; debuild -i -us -uc -b)

contrail-install-vcenter-plugin-deb: contrail-vijava-deb contrail-java-api-deb contrail-vrouter-java-api-deb contrail-vcenter-plugin-deb
	@echo "Building contrail vcenter plugin install package.."
	(cd install-package; debuild -i -us -uc -b)

clean:
	(rm -rf *.deb *.changes *.build)
