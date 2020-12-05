DIG_FILES := $(shell ls Simulation/*.dig)
SVG_FILES := $(addprefix Schematics/Simulation\ , $(notdir $(DIG_FILES:.dig=.svg)))

DIGITAL_JAR_PATH ?= Digital/Digital.jar

run-digital:
	@java -jar "$(DIGITAL_JAR_PATH)"

digital-svgs: $(SVG_FILES)
	@echo Done!

Schematics/Simulation\ %.svg: Simulation/%.dig
	@echo Creating svg from $(notdir $<)..
	@java -cp "$(DIGITAL_JAR_PATH)" CLI svg -dig "$<" -svg "$@" -ieee -noPinMarker -thinnerLines
