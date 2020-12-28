DIGITAL_JAR_PATH := .digital/Digital.jar

DIG_FILES := $(shell ls Simulation/*.dig)
SVG_FILES := $(addprefix Schematics/Simulation\ , $(notdir $(DIG_FILES:.dig=.svg)))

TESTS_FILES := $(shell ls Tests/*.java)
TESTS_CLASS_FILES :=  $(addprefix .javabuild/, $(TESTS_FILES:.java=.class))
TESTS_CLASSPATH := "$(DIGITAL_JAR_PATH):Tests/lib/junit-platform-console-standalone-1.7.0-all.jar"

.PHONY: run-digital simulation-svgs test microcode

run-digital: .digital
	java -jar $(DIGITAL_JAR_PATH) Simulation/Processor.dig &

simulation-svgs: .digital $(SVG_FILES)
	@echo Done!

test: .digital .javabuild $(TESTS_CLASS_FILES)
	java -cp $(TESTS_CLASSPATH):.javabuild org.junit.platform.console.ConsoleLauncher --disable-banner --select-class=Tests.Suite

.digital:
	unzip -o Digital-0.25.zip -d .digital

Schematics/Simulation\ %.svg: Simulation/%.dig
	@echo Creating svg from $(notdir $<)..
	@java -cp $(DIGITAL_JAR_PATH) CLI svg -dig "$<" -svg "$@" -ieee -noPinMarker -thinnerLines

.javabuild:
	mkdir .javabuild

$(TESTS_CLASS_FILES): $(TESTS_FILES)
	javac -cp $(TESTS_CLASSPATH) -d .javabuild $(TESTS_FILES)

microcode: .cbuild .cbuild/Microcode
	@echo Built as .cbuild/Microcode

.cbuild:
	mkdir .cbuild

.cbuild/Microcode: Microcode.c
	cc $< -std=c17 -Wall -Wextra -Wpedantic -O1 -g -o $@