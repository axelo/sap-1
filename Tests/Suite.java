package Tests;

import static Tests.MutableTestModel.clock;
import static Tests.MutableTestModel.init;
import static Tests.MutableTestModel.readRam;
import static Tests.MutableTestModel.signal;
import static Tests.MutableTestModel.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.function.Executable;

@TestMethodOrder(MethodOrderer.DisplayName.class)
public class Suite {

    // Bus out control signal addresses.
    private static final int ZERO_BUS_OUT = 0;
    private static final int RAM_BUS_OUT = 1;
    private static final int PC_BUS_OUT = 2;
    private static final int IR_BUS_OUT = 3;
    private static final int REGA_BUS_OUT = 4;
    private static final int ALU_BUS_OUT = 6;

    // Bus in control signal addresses.
    private static final int RAM_BUS_IN = 1 << 3;
    private static final int MAR_BUS_IN = 2 << 3;
    private static final int PC_BUS_IN = 3 << 3;
    private static final int IR_BUS_IN = 4 << 3;
    private static final int REGA_BUS_IN = 5 << 3;
    private static final int REGB_BUS_IN = 6 << 3;
    private static final int OUT_BUS_IN = 7 << 3;

    // Control signals.
    private static final int HALT = 1 << 6;
    private static final int PC_COUNT = 1 << 7;
    private static final int ALU_SUBTRACT = 1 << 8;
    private static final int OUT_SIGNED_IN = 1 << 9;
    private static final int FLAGS_IN = 1 << 10;
    private static final int LAST_STEP = 1 << 15;

    // Flags
    private static final int CARRY_FLAG = 0b01;
    private static final int ZERO_FLAG = 0b10;

    private static enum Signal {
        BUS, PC, MAR, IR, REGA, REGB, FLAGS, CONTROL, STEP, LAST_STEP
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
        assertAll(signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0));

        // This depends on how we have programmed our microcode. We should be using
        // reset steps at 0x2000.
        assertAll(controlSignalEquals(model, ZERO_BUS_OUT | PC_BUS_IN));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 0), //
                signalEquals(model, Signal.PC, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, REGA_BUS_IN), //
                signalEquals(model, Signal.STEP, 1), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.REGA, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, REGB_BUS_IN), //
                signalEquals(model, Signal.STEP, 2), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.REGB, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, OUT_BUS_IN), //
                signalEquals(model, Signal.STEP, 3), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, MAR_BUS_IN), //
                signalEquals(model, Signal.STEP, 4), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.MAR, 0));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | IR_BUS_IN | LAST_STEP), //
                signalEquals(model, Signal.STEP, 5), //
                signalEquals(model, Signal.LAST_STEP, 1));

        step(model); // Execute control signals.
    }

    private static void assertFetchSteps(MutableTestModel model) {
        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN), //
                signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, Signal.PC), //
                signalEquals(model, Signal.MAR, Signal.BUS));

        step(model); // Latch next control signals.

        final int previousPc = signal(model, Signal.PC.name());

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | IR_BUS_IN | PC_COUNT), //
                signalEquals(model, Signal.STEP, 1), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, previousPc + 1));
    }

    private static void assertSetRegAToMemoryAtImmediateAddressSteps(MutableTestModel model) {
        final int ir = signal(model, Signal.IR.name());
        final int pc = signal(model, Signal.PC.name());
        final int immediate = readRam(model, pc);
        final var valueInRamAtAddress = readRam(model, immediate);

        assertEquals(0x10, ir, () -> "IR should be 0x10");

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | MAR_BUS_IN | PC_COUNT));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.MAR, immediate));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | REGA_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        // valueInRamAtAddress is stored at address immediate in ram.
        assertAll(signalEquals(model, Signal.REGA, valueInRamAtAddress), //
                signalEquals(model, Signal.PC, pc + 1));
    }

    private static void assertAddRegAWithMemoryAtImmediateAddressSteps(MutableTestModel model) {
        final int regA = signal(model, Signal.REGA.name());
        final int ir = signal(model, Signal.IR.name());
        final int pc = signal(model, Signal.PC.name());
        final int immediate = readRam(model, pc);
        final var valueInRamAtAddress = readRam(model, immediate);

        assertEquals(0x20, ir, () -> "IR should be 0x20");

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | MAR_BUS_IN | PC_COUNT));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | REGB_BUS_IN));

        step(model); // Execute control signals.

        // valueInRamAtAddress is stored at address immediate in ram.
        assertAll(signalEquals(model, Signal.REGB, valueInRamAtAddress));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, ALU_BUS_OUT | FLAGS_IN | REGA_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        final int regB = signal(model, Signal.REGB.name());

        final int sum = (regA + regB) & 0xff;

        assertAll(signalEquals(model, Signal.REGA, sum));
    }

    private static void assertSubRegAWithMemoryAtImmediateAddressSteps(MutableTestModel model) {
        final int regA = signal(model, Signal.REGA.name());
        final int ir = signal(model, Signal.IR.name());
        final int pc = signal(model, Signal.PC.name());
        final int immediate = readRam(model, pc);
        final var valueInRamAtAddress = readRam(model, immediate);

        assertEquals(0x30, ir, () -> "IR should be 0x30");

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | MAR_BUS_IN | PC_COUNT));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | REGB_BUS_IN));

        step(model); // Execute control signals.

        // valueInRamAtAddress is stored at address immediate in ram.
        assertAll(signalEquals(model, Signal.REGB, valueInRamAtAddress));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, ALU_SUBTRACT | ALU_BUS_OUT | FLAGS_IN | REGA_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        final int regB = signal(model, Signal.REGB.name());

        final int sum = (regA - regB) & 0xff;

        assertAll(signalEquals(model, Signal.REGA, sum));
    }

    private static void assertSetRegAImmediateSteps(MutableTestModel model) {
        final int ir = signal(model, Signal.IR.name());
        final int pc = signal(model, Signal.PC.name());
        final int immediate = readRam(model, pc);

        assertEquals(0x50, ir & 0xf0, () -> "IR should be 0x50");

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | REGA_BUS_IN | PC_COUNT | LAST_STEP));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.REGA, immediate), //
                signalEquals(model, Signal.PC, pc + 1));
    }

    @Test
    @DisplayName("nop")
    public void noOperation() {
        final var model = initModelWithRam(0x00);

        assertResetSteps(model);

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN), //
                signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, Signal.PC), //
                signalEquals(model, Signal.MAR, Signal.BUS));

        step(model); // Latch next control signals.

        final int previousPc = signal(model, Signal.PC.name());

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | IR_BUS_IN | PC_COUNT | LAST_STEP),
                signalEquals(model, Signal.STEP, 1), //
                signalEquals(model, Signal.LAST_STEP, 1));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, previousPc + 1));

        step(model); // Latch next control signals.

        assertAll(signalEquals(model, Signal.STEP, 0), //
                signalEquals(model, Signal.LAST_STEP, 0));
    }

    @Test
    @DisplayName("a = mem[4]")
    public void setRegAToValueAtImmediateAddress() {
        final var model = initModelWithRam(//
                0x10, 0x04, // a = mem[4]
                0x00, 0x00, 15);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAToMemoryAtImmediateAddressSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 15));
    }

    @Test
    @DisplayName("a += mem[3]")
    public void addRegAWithValueAtImmediateAddress() {
        final var model = initModelWithRam(0x20, 0x03, 0x00, 42);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertAddRegAWithMemoryAtImmediateAddressSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 42));
    }

    @Test
    @DisplayName("a += mem[4] # Should set carry flag")
    public void addRegAWithValueAtImmediateAddressSetCarryFlag() {
        final var model = initModelWithRam(0x50, 0x0f, 0x20, 0x04, 0xff);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAImmediateSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 15), //
                signalEquals(model, Signal.FLAGS, 0));

        assertFetchSteps(model);

        assertAddRegAWithMemoryAtImmediateAddressSteps(model); // 0xf + 0xff => 270, 14 with carry flag

        assertAll(signalEquals(model, Signal.REGA, 14), //
                signalEquals(model, Signal.FLAGS, CARRY_FLAG));
    }

    @Test
    @DisplayName("a += mem[4] # Should set carry and zero flag")
    public void addRegAWithValueAtImmediateAddressSetZeroFlag() {
        final var model = initModelWithRam(0x50, 0x0f, 0x20, 0x04, 0xf1);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAImmediateSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 15), //
                signalEquals(model, Signal.FLAGS, 0));

        assertFetchSteps(model);

        assertAddRegAWithMemoryAtImmediateAddressSteps(model); // 0xf + 0xf1 => 256, 0 with carry and zero flag.

        assertAll(signalEquals(model, Signal.REGA, 0), //
                signalEquals(model, Signal.FLAGS, ZERO_FLAG | CARRY_FLAG));
    }

    @Test
    @DisplayName("a -= mem[2] # Should set carry flag")
    public void subRegAWithValueAtImmediateAddress() {
        final var model = initModelWithRam(//
                0x30, 0x02, // a -= mem[2]
                1);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSubRegAWithMemoryAtImmediateAddressSteps(model); // 0 - 1 => 255, carry set

        assertAll(signalEquals(model, Signal.REGA, 255), //
                signalEquals(model, Signal.FLAGS, CARRY_FLAG));
    }

    @Test
    @DisplayName("a -= mem[4] # Should clear carry flag")
    public void subRegAWithValueAtImmediateAddressSetCarryFlag() {
        final var model = initModelWithRam(//
                0x50, 0x0f, // a = 0xf
                0x30, 0x04, // a -= mem[4]
                0x01);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAImmediateSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 0xf), //
                signalEquals(model, Signal.FLAGS, 0));

        assertFetchSteps(model);

        assertSubRegAWithMemoryAtImmediateAddressSteps(model); // 0xf - 0x1 => 0xe, carry flag clear.

        assertAll(signalEquals(model, Signal.REGA, 0xe), //
                signalEquals(model, Signal.FLAGS, 0));
    }

    @Test
    @DisplayName("a -= mem[4] # Should set zero flag")
    public void subRegAWithValueAtImmediateAddressSetZeroFlag() {
        final var model = initModelWithRam(//
                0x50, 0x0f, // a = 0xf
                0x30, 0x04, // a -= mem[3]
                0x0f);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAImmediateSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 0xf), //
                signalEquals(model, Signal.FLAGS, 0));

        assertFetchSteps(model);

        assertSubRegAWithMemoryAtImmediateAddressSteps(model); // 0xf - 0x1 => 0xe, carry flag set (not borrow).

        assertAll(signalEquals(model, Signal.REGA, 0), //
                signalEquals(model, Signal.FLAGS, ZERO_FLAG));
    }

    @Test
    @DisplayName("mem[immediate] = a")
    public void setMemAtAddressImmediateToValueOfRegA() {
        final var model = initModelWithRam(//
                0x50, 0x08, // a = 8
                0x40, 0x04, // mem[4] = a
                0x00);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAImmediateSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 8));

        assertFetchSteps(model);

        step(model); // Latch next control signals.

        assertAll(signalEquals(model, Signal.IR, 0x40), //
                controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | MAR_BUS_IN | PC_COUNT));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, REGA_BUS_OUT | RAM_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        assertEquals(8, readRam(model, 4), () -> "mem[4] should be 8");
    }

    @Test
    @DisplayName("a = immediate")
    public void setRegAToImmediate() {
        final var model = initModelWithRam(0x50, 0x07);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAImmediateSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 7));
    }

    @Test
    @DisplayName("goto immediate")
    public void setPcToImmediate() {
        final var model = initModelWithRam(0x60, 0x0a);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertAll(signalEquals(model, Signal.IR, 0x60));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | PC_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, 10));
    }

    @Test
    @DisplayName("goto immediate when carry")
    public void setPcToImmediateWhenCarry() {
        final var model = initModelWithRam(//
                0x30, 0x04, // a -= mem[3]
                0x70, 0x05, // goto 5 when carry
                0x01, //
                0xf0 // halt
        );

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSubRegAWithMemoryAtImmediateAddressSteps(model); // a -= 1

        assertFetchSteps(model);

        assertAll(signalEquals(model, Signal.IR, 0x70));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | PC_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, 5));
    }

    @Test
    @DisplayName("goto immediate when zero")
    public void setPcToImmediateWhenZero() {
        final var model = initModelWithRam(//
                0x20, 0x04, // a += mem[4]
                0x80, 0x05, // goto 6 when zero
                0x00, //
                0xf0 // halt
        );

        assertResetSteps(model);

        assertFetchSteps(model);

        assertAddRegAWithMemoryAtImmediateAddressSteps(model); // a = 0 + 0

        assertFetchSteps(model);

        assertAll(signalEquals(model, Signal.IR, 0x80));

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, PC_BUS_OUT | MAR_BUS_IN));

        step(model); // Execute control signals.

        step(model); // Latch next control signals.

        assertAll(controlSignalEquals(model, RAM_BUS_OUT | PC_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.PC, 5));
    }

    @Test
    @DisplayName("out = a")
    public void setOutputToValueInRegA() {
        final var model = initModelWithRam(0xe0);

        assertResetSteps(model);

        assertFetchSteps(model);

        step(model); // Latch next control signals. Need to do this before testing IR as IR is
                     // latched on the same clock as control signals.

        assertAll(signalEquals(model, Signal.IR, 0xe0), //
                controlSignalEquals(model, REGA_BUS_OUT | OUT_BUS_IN | LAST_STEP));

        step(model); // Execute control signals.

        assertAll(signalEquals(model, Signal.BUS, 0x0)); // Reset value of register a.
    }

    @Test
    @DisplayName("a = mem[14]; a += mem[15]; a -= mem[13]")
    public void setAndAddRegFromMemoryImmediate() {
        final var model = initModelWithRam(//
                0x10, 0x0e, // a = mem[e]
                0x20, 0x0f, // a += mem[f]
                0x30, 0x0d, // a -= mem[d]
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 2, 20, 24);

        assertResetSteps(model);

        assertFetchSteps(model);

        assertSetRegAToMemoryAtImmediateAddressSteps(model);

        assertFetchSteps(model);

        assertAddRegAWithMemoryAtImmediateAddressSteps(model);

        assertFetchSteps(model);

        assertSubRegAWithMemoryAtImmediateAddressSteps(model);

        assertAll(signalEquals(model, Signal.REGA, 42));
    }

    public static void main(String[] args) {
        // When debugging.
        new Suite().noOperation();
    }
}