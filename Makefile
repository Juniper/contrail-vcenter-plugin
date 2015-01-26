SRC_VER := $(shell cat ./../controller/src/base/version.info)
BUILDTIME := $(shell date -u +%m%d%Y)

ifdef SRC_VER
BUILDTAG = $(SRC_VER)-$(BUILDTIME)
else
BUILDTAG = $(BUILDTIME)
endif


all:
	$(eval BUILDDIR=./../build/vcenter-plugin)
	mkdir -p ${BUILDDIR}
	cp -ar * ${BUILDDIR}
	(cd ${BUILDDIR}; mvn install)
	#(cd ${BUILDDIR}; fakeroot debian/rules clean)
	#(cd ${BUILDDIR}; fakeroot debian/rules binary)
	(cd ${BUILDDIR}; debuild -i -us -uc -b)
	@echo "Wrote: ${BUILDDIR}/../contrail-vcenter-plugin_all.deb"

clean:
	$(eval BUILDDIR=./../build/vcenter-plugin)
	(cd ${BUILDDIR}; mvn clean)
	rm -rf ${BUILDDIR}/../contrail-vcenter-plugin*.*
	rm -rf ${BUILDDIR}/*

