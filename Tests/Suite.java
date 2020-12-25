package Tests;

import static Tests.MutableTestModel.clock;
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

    private static Executable signalEquals(MutableTestModel model, Signal signalName, int expected) {
        return () -> assertEquals(expected, signal(model, signalName.name()),
                () -> signalName.name() + " should be " + expected);
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
        assertAll(signalEquals(model, Signal.CONTROL, PC_OUT | MAR_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 0), //
                signalEquals(model, Signal.MAR, 0));

        step(model); // Latch next control signals.

        // This depends on how we have programmed our microcode.
        // Address 0 in microcode rom should be the fetch operation to get things
        // running.
        assertAll(signalEquals(model, Signal.CONTROL, PC_COUNT | RAM_OUT | IR_IN | LAST_STEP), //
                signalEquals(model, Signal.STEP, 1), //
                signalEquals(model, Signal.LAST_STEP, 1));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, 1), //
                signalEquals(model, Signal.BUS, 0xff), //
                signalEquals(model, Signal.IR, 0) // TODO: Should control signals be affected direct our wait on clock?
                                                  // (CLOCK or ~CLOCK for IR) Should IR latch on same signal as control?
        );

        step(model); // Latch next control signals, now from memory.

        // Control steps should have been restarted. This is due to 0xff is in microcode
        // programmed as HALT | LAST_STEP.
        assertAll(signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 1) //
        );
    }

    public static void main(String[] args) {
        // Uncomment this when debugging.
        // new Suite().reset();
    }
}