package Tests;

import static Tests.MutableTestModel.clock;
import static Tests.MutableTestModel.init;
import static Tests.MutableTestModel.signal;
import static Tests.MutableTestModel.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class Suite {

    // Bus out control signal addresses.
    private static final int ZERO_OUT = 0;
    private static final int RAM_OUT = 1;
    private static final int PC_OUT = 2;
    private static final int IR_OUT = 3;

    // Bus in control signal addresses.
    private static final int MAR_IN = 2 << 3;
    private static final int PC_IN = 3 << 3;
    private static final int IR_IN = 4 << 3;
    private static final int REGA_IN = 5 << 3;
    private static final int REGB_IN = 6 << 3;
    private static final int OUT_IN = 7 << 3;

    // Control signals.
    private static final int HALT = 1 << 6;
    private static final int PC_COUNT = 1 << 7;
    private static final int ALU_SUBTRACT = 1 << 8;
    private static final int OUT_SIGNED = 1 << 9;
    private static final int LAST_STEP = 1 << 15;

    private static enum Signal {
        BUS, PC, MAR, IR, REGA, REGB, CONTROL, STEP, LAST_STEP
    }

    private static String toBinaryString(int bits, int i) {
        return "0b" + "0".repeat(Integer.numberOfLeadingZeros(i) - bits) + Integer.toBinaryString(i);
    }

    private static Executable controlSignalEquals(MutableTestModel model, int expected) {
        return () -> assertEquals(toBinaryString(16, expected),
                toBinaryString(16, signal(model, Signal.CONTROL.name())), () -> Signal.CONTROL.name());
    }

    private static Executable signalEquals(MutableTestModel model, Signal signalName, int expected) {
        return () -> assertEquals(expected, signal(model, signalName.name()), () -> signalName.name());
    }

    private static Executable signalEquals(MutableTestModel model, Signal signalName, Signal expected) {
        return () -> assertEquals(signal(model, expected.name()), signal(model, signalName.name()),
                () -> signalName.name());
    }

    private static MutableTestModel initModelWithRam(int... ram) {
        final var model = init("Simulation/Processor.dig", ram);

        assertEquals(false, clock(model), () -> "CLOCK should start false");

        return model;
    }

    private static void assertResetSteps(MutableTestModel model) {
        step(model);
        step(model); // One clock cycle for resetting IR and control STEP.

        assertAll(signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0), //
                signalEquals(model, Signal.IR, 0) // TODO: This is not guaranteed yet. We're missing reset logic for IR.
        );

        // This depends on how we have programmed our microcode. We should be using
        // reset steps at 0x400.
        assertAll(controlSignalEquals(model, ZERO_OUT | PC_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 0), //
                signalEquals(model, Signal.PC, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, REGA_IN), //
                signalEquals(model, Signal.STEP, 1), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.REGA, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, REGB_IN), //
                signalEquals(model, Signal.STEP, 2), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.REGB, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, OUT_IN), //
                signalEquals(model, Signal.STEP, 3), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, MAR_IN), //
                signalEquals(model, Signal.STEP, 4), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.MAR, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_OUT | IR_IN | LAST_STEP), //
                signalEquals(model, Signal.STEP, 5), //
                signalEquals(model, Signal.LAST_STEP, 1));

        step(model); // Execute control signals.
    }

    private static void assertFetchSteps(MutableTestModel model) {
        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_OUT | MAR_IN), //
                signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, Signal.PC), //
                signalEquals(model, Signal.MAR, Signal.BUS));

        step(model); // Latch next control signals.

        final int previousPc = signal(model, Signal.PC.name());

        assertAll(controlSignalEquals(model, RAM_OUT | IR_IN | PC_COUNT), //
                signalEquals(model, Signal.STEP, 1), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, previousPc + 1));
    }

    @Test
    @DisplayName("nop")
    public void noOperation() {
        final var model = initModelWithRam(0x00);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertAll(signalEquals(model, Signal.IR, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, LAST_STEP), //
                signalEquals(model, Signal.STEP, 2), //
                signalEquals(model, Signal.LAST_STEP, 1));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0));
    }

    @Test
    @DisplayName("mov a, [4]")
    public void setRegAToValueAtImmediateAddress() {
        final var model = initModelWithRam(0x14, 0, 0, 0, 0xf);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertAll(signalEquals(model, Signal.IR, 0x14));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, IR_OUT | MAR_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 4), // Lower 4 bit of 0x14 are outputted to the bus.
                signalEquals(model, Signal.MAR, 4));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_OUT | REGA_IN | LAST_STEP));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.REGA, 0xf)); // 0xf is stored at address 4 in ram.
    }

    public static void main(String[] args) {
        // Uncomment this when debugging.
        // new Suite().noOperation();
    }
}