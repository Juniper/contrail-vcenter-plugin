SRC_VER ?= $(shell cat ./../controller/src/base/version.info)
BUILDNUM ?= $(shell date -u +%m%d%Y)
export BUILDTAG ?= $(SRC_VER)-$(BUILDNUM)

build:
	$(eval BUILDDIR=./../build/vcenter-plugin)
	mkdir -p ${BUILDDIR}
	cp -ar * ${BUILDDIR}
	(cd ${BUILDDIR}; mvn install -DskipTests)

deb: build
	(cd ${BUILDDIR}; debuild --preserve-envvar=BUILDTAG -i -us -uc -b)
	@echo "Wrote: ${BUILDDIR}/../contrail-vcenter-plugin_all.deb"

rpm: build
	$(eval BUILDDIR=$(realpath ./../build/vcenter-plugin))
	cp rpm/contrail-vcenter-plugin.spec ${BUILDDIR}
	mkdir -p ${BUILDDIR}/{BUILD,RPMS,SOURCES,SPECS,SRPMS,TOOLS}
	rpmbuild -bb --define "_topdir ${BUILDDIR}" --define "_buildTag $(BUILDNUM)" \
                  --define "_srcVer $(SRC_VER)" rpm/contrail-vcenter-plugin.spec

clean:
	$(eval BUILDDIR=./../build/vcenter-plugin)
	(cd ${BUILDDIR}; mvn clean)
	rm -rf ${BUILDDIR}/../contrail-vcenter-plugin*.*
	rm -rf ${BUILDDIR}/*
