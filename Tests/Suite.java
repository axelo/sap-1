package Tests;

import static Tests.MutableTestModel.clock;
import static Tests.MutableTestModel.halted;
import static Tests.MutableTestModel.init;
import static Tests.MutableTestModel.signal;
import static Tests.MutableTestModel.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.function.Executable;

public class Suite {

    // Bus out control signal addresses.
    private static final int RAM_OUT = 1;
    private static final int PC_OUT = 2;

    // Bus in control signal addresses.
    private static final int MAR_IN = 2 << 3;
    private static final int IR_IN = 4 << 3;

    // Control signals.
    private static final int HALT = 1 << 6;
    private static final int PC_COUNT = 1 << 7;
    private static final int ALU_SUBTRACT = 1 << 8;
    private static final int OUT_SIGNED = 1 << 9;
    private static final int LAST_STEP = 1 << 15;

    private static enum Signal {
        BUS, PC, MAR, IR, CONTROL, STEP, LAST_STEP
    }

    private static String toBinaryString(int bits, int i) {
        return "0b" + "0".repeat(Integer.numberOfLeadingZeros(i) - bits) + Integer.toBinaryString(i);
    }

    private static Executable controlSignalEquals(MutableTestModel model, int expected) {
        return () -> assertEquals(toBinaryString(16, expected),
                toBinaryString(16, signal(model, Signal.CONTROL.name())), () -> Signal.CONTROL.name());
    }

    private static Executable signalEquals(MutableTestModel model, Signal signalName, int expected) {
        return () -> assertEquals((expected), (signal(model, signalName.name())), () -> signalName.name());
    }

    @RepeatedTest(1)
    public void reset() {
        final var ram = new int[] { 0xff };
        final var model = init("Simulation/Processor.dig", ram);

        assertEquals(false, clock(model), () -> "CLOCK should start false");

        step(model);
        step(model); // One clock cycle for resetting PC, MAR and CONTROL STEP.

        assertAll(signalEquals(model, Signal.PC, 0), //
                signalEquals(model, Signal.MAR, 0), //
                signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0), //
                signalEquals(model, Signal.IR, 0) // TODO: This is not guaranteed yet. We're missing reset logic for IR.
        );

        // This depends on how we have programmed our microcode.
        // Address 0 in microcode rom should be the fetch operation to get things
        // running.
        assertAll(controlSignalEquals(model, PC_OUT | MAR_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 0), //
                signalEquals(model, Signal.MAR, 0));

        step(model); // Latch next control signals.

        // This depends on how we have programmed our microcode.
        // Address 0 in microcode rom should be the fetch operation to get things
        // running.
        assertAll(controlSignalEquals(model, PC_COUNT | RAM_OUT | IR_IN | LAST_STEP), //
                signalEquals(model, Signal.STEP, 1), //
                signalEquals(model, Signal.LAST_STEP, 1));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, 1), //
                signalEquals(model, Signal.BUS, 0xff), //
                signalEquals(model, Signal.IR, 0xff) // Note that control signals will be affected on this step as high
                                                     // nibble of IR is part of the microcode address.
        );

        step(model); // Latch next control signals, now from memory.

        // Control steps should have been restarted. This is due to 0xff is in microcode
        // programmed as HALT | LAST_STEP.
        assertAll(controlSignalEquals(model, HALT | LAST_STEP), //
                signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 1) //
        );

        step(model); // Execute control signals.

        assertEquals(true, halted(model));
    }

    public static void main(String[] args) {
        // Uncomment this when debugging.
        // new Suite().reset();
    }
}