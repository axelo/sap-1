# SAP-1

My processor build using the SAP-1 (Simple-As-Possible-1) architecture.

## Simulation

Before breadboarding I design the modules in [Digital](https://github.com/hneemann/Digital). This helps me better understand the problems and kind of "proves" a design.

![Processor simulation](./Schematics/Simulation%20Processor.svg)

## Modules

[Processor dig file](./Simulation/Processor.dig)

### Register

[dig file](./Simulation/Register.dig)

![Register simulation](./Schematics/Simulation%20Register.svg)

### Program counter

[dig file](./Simulation/Pc.dig)

![Register simulation](./Schematics/Simulation%20Pc.svg)

### Arithmetic logic unit

[dig file](./Simulation/Alu.dig)

![Register simulation](./Schematics/Simulation%20Alu.svg)

### Memory address register

[dig file](./Simulation/mar.dig)

![Register simulation](./Schematics/Simulation%20Mar.svg)

### Ram

[dig file](./Simulation/Ram.dig)

![Register simulation](./Schematics/Simulation%20Ram.svg)

### Out register

[dig file](./Simulation/Out.dig)

![Register simulation](./Schematics/Simulation%20Out.svg)

#### Lookup table

[rom hex file](./Simulation/rom/Out.hex)

8 bit to decimal representation is done with a look up table. Using 2 extra bits for outputting 1s, 10s, 100s or signed position and 1 extra bit for signed mode. 2^11 combinations in total making up 2 kB.

### Instruction register

[dig file](./Simulation/Ir.dig)

![Register simulation](./Schematics/Simulation%20Ir.svg)

### Control unit

[dig file](./Simulation/Control.dig)

![Register simulation](./Schematics/Simulation%20Control.svg)
