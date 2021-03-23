OUT = ../../out/production/proj1
SRC_FROM_OUT = ../../../../src/g03
FILES = *.java Protocols/*.java
JFLAGS =  -d $(OUT)
JC = javac
JVM = java

all: killrmi
	$(JC) $(JFLAGS) $(FILES)

rmi:
	cd "$(OUT)/g03" && "$(SRC_FROM_OUT)/rmi.bat"

killrmi:
	tasklist | find /i "rmiregistry.exe" && taskkill /im rmiregistry.exe /F
	# taskkill /im rmiregistry.exe

clean:
	del /q /s $(OUT)


