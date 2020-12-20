package Tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.RepeatedTest;
import static Tests.TestModel.init;

public class Suite {

        private static final int RAM_OUT = 1;
        private static final int PC_OUT = 2;
        private static final int MAR_IN = 2 << 3;
        private static final int IR_IN = 4 << 3;

        private static final int HALT = 1 << 6;
        private static final int PC_COUNT = 1 << 7;
        private static final int ALU_SUBTRACT = 1 << 8;
        private static final int OUT_SIGNED = 1 << 9;
        private static final int LAST_STEP = 1 << 15;

        @RepeatedTest(1)
        public void nop() {
                final var model = init("Simulation/Processor.dig", 0x00, 0xff);

                assertEquals(false, model.clock(), () -> "CLOCK should start false");

                model.step().step(); // One machine cycle for resetting PC, MAR and CONTROL STEP.

                assertAll(() -> assertEquals(PC_OUT | MAR_IN, model.control(),
                                () -> "CONTROL should be PC_OUT | MAR_IN"),
                                () -> assertEquals(0, model.pc(), () -> "PC should be 0"),
                                () -> assertEquals(0, model.mar(), () -> "MAR should be 0"),
                                () -> assertEquals(0, model.ir(), () -> "IR should be 0"),
                                () -> assertEquals(0, model.controlStep(), () -> "STEP should be 0"),
                                () -> assertEquals(0, model.controlLastStep(), () -> "LAST STEP should be 0"));

                model.step().step(); // Latch next control signals.

                assertAll(() -> assertEquals(PC_COUNT | RAM_OUT | IR_IN | LAST_STEP, model.control(),
                                () -> "CONTROL should be PC_COUNT | RAM_OUT | IR_IN | LAST_STEP"),
                                () -> assertEquals(1, model.controlStep(), () -> "STEP should be 1"),
                                () -> assertEquals(1, model.controlLastStep(), () -> "LAST STEP should be 1"));

                model.step(); // Execute control signals.

                assertAll(() -> assertEquals(1, model.pc(), () -> "PC should be 1"),
                                () -> assertEquals(0, model.bus(), () -> "BUS should be 0"),
                                () -> assertEquals(0, model.ir(), () -> "IR should be 0"));

                model.step(); // Latch next control signals.

                assertAll(() -> assertEquals(PC_OUT | MAR_IN, model.control(),
                                () -> "CONTROL should be PC_OUT | MAR_IN"),
                                () -> assertEquals(0, model.controlStep(), () -> "STEP should be 0"),
                                () -> assertEquals(0, model.controlLastStep(), () -> "LAST STEP should be 0"));

                model.step(); // Execute control signals.

                assertAll(() -> assertEquals(1, model.bus(), () -> "BUS should be 1"),
                                () -> assertEquals(1, model.mar(), () -> "MAR should be 1"),
                                () -> assertEquals(0xff, model.ram(), () -> "RAM should be 0xff"));

                model.step(); // Latch next control signals.

                assertAll(() -> assertEquals(PC_COUNT | RAM_OUT | IR_IN | LAST_STEP, model.control(),
                                () -> "CONTROL should be PC_COUNT | RAM_OUT | IR_IN | LAST_STEP"),
                                () -> assertEquals(1, model.controlStep(), () -> "STEP should be 1"),
                                () -> assertEquals(1, model.controlLastStep(), () -> "LAST STEP should be 1"));

                model.step(); // Execute control signals.

                assertAll(() -> assertEquals(2, model.pc(), () -> "PC should be 2"),
                                () -> assertEquals(0xff, model.bus(), () -> "BUS should be 2"),
                                () -> assertEquals(0xff, model.ir(), () -> "IR should be 0xff"));

                model.step(); // Latch next control signals.

                assertAll(() -> assertEquals(HALT, model.control(), () -> "CONTROL should be HALT"));

                model.step(); // Execute control signals.

                assertEquals(true, model.halted());
        }

        // public static void main(String[] args) {
        // new Suite().nop2();
        // }
}