all: compile install
compile:
	mvn clean install -DskipTests
install:
	onos-app localhost install! target/onos-demo-app-1.0-SNAPSHOT.oar
reinstall:
	onos-app localhost reinstall! target/onos-demo-app-1.0-SNAPSHOT.oar

.PHONY: clean
clean:
	rm -r target
	onos-app localhost deactivate tw.ken.demo
	onos-app localhost uninstall tw.ken.demo