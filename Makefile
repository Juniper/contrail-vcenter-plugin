SRC_VER := $(shell cat ./../controller/src/base/version.info)
BUILDTIME := $(shell date -u +%y%m%d%H%M)
VERSION = $(SRC_VER)-$(BUILDTIME)

all:
	$(eval BUILDDIR=./../build/vcenter-plugin)
	mkdir -p ${BUILDDIR}
	cp -ar * ${BUILDDIR}
	mvn install
	#(cd ${BUILDDIR}; fakeroot debian/rules clean)
	#(cd ${BUILDDIR}; fakeroot debian/rules binary)
	(cd ${BUILDDIR}; debuild -i -us -uc -b)
	@echo "Wrote: ${BUILDDIR}/../contrail-vcenter-plugin_all.deb"

clean:
	$(eval BUILDDIR=./../build/vcenter-plugin)
	rm -rf ${BUILDDIR}/../contrail-vcenter-plugin*.*
	rm -rf ${BUILDDIR}/*

