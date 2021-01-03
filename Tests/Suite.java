package Tests;

import static Tests.MutableTestModel.clock;
import static Tests.MutableTestModel.init;
import static Tests.MutableTestModel.readRam;
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
    private static final int REGA_OUT = 4;
    private static final int ALU_OUT = 6;

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
    private static final int OUT_SIGNED_IN = 1 << 9;
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

    private static void assertMovRegAImmediateAddressSteps(MutableTestModel model) {
        step(model); // Latch next control signals. Need to do this before testing IR as IR is
                     // latched on the same clock as control signals.

        final int ir = signal(model, Signal.IR.name());
        final int movRegAImmediate = ir & 0xf0;
        final int immediate = ir & 0x0f;

        assertEquals(0x10, movRegAImmediate, () -> "High 4-bits of IR should be 0b0001");

        assertAll(controlSignalEquals(model, IR_OUT | MAR_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, immediate), // Lower 4 bit of IR are outputted to the bus.
                signalEquals(model, Signal.MAR, immediate));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_OUT | REGA_IN | LAST_STEP));

        final var valueInRamAtAddress = readRam(model, immediate);

        step(model); // Execute control signals.

        // valueInRamAtAddress is stored at address immediate in ram.
        assertAll(signalEquals(model, Signal.REGA, valueInRamAtAddress));
    }

    private static void assertAddRegAImmediateAddressSteps(MutableTestModel model) {
        step(model); // Latch next control signals. Need to do this before testing IR as IR is
                     // latched on the same clock as control signals.

        final int regA = signal(model, Signal.REGA.name());

        final int ir = signal(model, Signal.IR.name());
        final int addRegAImmediate = ir & 0xf0;
        final int immediate = ir & 0x0f;

        assertEquals(0x20, addRegAImmediate, () -> "High 4-bits of IR should be 0b0002");

        assertAll(controlSignalEquals(model, IR_OUT | MAR_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, immediate), // Lower 4 bit of IR are outputted to the bus.
                signalEquals(model, Signal.MAR, immediate));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_OUT | REGB_IN));

        final var valueInRamAtAddress = readRam(model, immediate);

        step(model); // Execute control signals.

        // valueInRamAtAddress is stored at address immediate in ram.
        assertAll(signalEquals(model, Signal.REGB, valueInRamAtAddress));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, ALU_OUT | REGA_IN | LAST_STEP));

        step(model); // Execute control signals.

        final int regB = signal(model, Signal.REGB.name());

        final int sum = (regA + regB) & 0xff;

        assertAll(signalEquals(model, Signal.REGA, sum));
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

        assertMovRegAImmediateAddressSteps(model);
    }

    @Test
    @DisplayName("add a, [3]")
    public void addRegAWithValueAtImmediateAddress() {
        final var model = initModelWithRam(0x23, 0, 0, 0xa);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertAddRegAImmediateAddressSteps(model);
    }

    @Test
    @DisplayName("mov a, [14]\nadd a, [15]")
    public void setAndAddRegFromMemoryImmediate() {
        final var model = initModelWithRam(0x1e, 0x2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 20, 22);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertMovRegAImmediateAddressSteps(model);

        assertFetchSteps(model);

        assertAddRegAImmediateAddressSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 42));
    }

    @Test
    @DisplayName("out a, signed")
    public void outputRegAInSigned() {
        final var model = initModelWithRam(0x31);

        assertResetSteps(model);

        assertFetchSteps(model);

        step(model); // Latch next control signals. Need to do this before testing IR as IR is
                     // latched on the same clock as control signals.

        assertAll(signalEquals(model, Signal.IR, 0x31), //
                controlSignalEquals(model, IR_OUT | OUT_SIGNED_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 0x1)); // output flag 1 = signed mode.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, REGA_OUT | OUT_IN | LAST_STEP));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 0x0)); // Reset value of register a.
    }

    public static void main(String[] args) {
        // When debugging.
        new Suite().noOperation();
    }
}