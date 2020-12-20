package Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.neemann.digital.analyse.AnalyseException;
import de.neemann.digital.core.Model;
import de.neemann.digital.core.ModelEvent;
import de.neemann.digital.core.ObservableValue;
import de.neemann.digital.core.Signal;
import de.neemann.digital.core.memory.DataField;
import de.neemann.digital.testing.UnitTester;

public class TestModel {
    public static class MutableModel {
        private final Model digitalModel;
        private final ObservableValue clockOutput;
        private final Signal ram;
        private final Signal pc;
        private final Signal mar;
        private final Signal ir;
        private final Signal control;
        private final Signal controlStep;
        private final Signal controlLastStep;
        private final Signal bus;

        private MutableModel(Model digitalModel) {
            var signalsByName = digitalModel.getSignalsCopy().stream()
                    .collect(Collectors.toUnmodifiableMap(Signal::getName, Function.identity()));

            Function<String, Signal> signal = name -> Optional.ofNullable(signalsByName.get(name))
                    .orElseThrow(() -> new IllegalStateException(("Missing signal " + name + " in Digital model.")));

            clockOutput = Optional.ofNullable(digitalModel.getClocks().get(0))
                    .orElseThrow(() -> new IllegalStateException("Missing clock in Digital model.")).getClockOutput();

            this.ram = signal.apply("RAM2");
            pc = signal.apply("PC");
            mar = signal.apply("MAR");
            ir = signal.apply("IR");
            control = signal.apply("CONTROL");
            controlStep = signal.apply("STEP");
            controlLastStep = signal.apply("LAST_STEP");
            bus = signal.apply("BUS");

            try {
                digitalModel.checkForInvalidSignals();
            } catch (AnalyseException e) {
                throw new IllegalStateException(e);
            }

            this.digitalModel = tryStartDigitalModel(digitalModel);
        }

        public MutableModel step() {
            assertEquals(true, digitalModel.isRunning(), () -> "Digital model isn't running.");

            clockOutput.setBool(!clockOutput.getBool());

            digitalModel.doStep();

            return this;
        }

        public boolean clock() {
            return clockOutput.getBool();
        }

        public long ram() {
            return ram.getValue().getValue();
        }

        public long pc() {
            return pc.getValue().getValue();
        }

        public long mar() {
            return mar.getValue().getValue();
        }

        public long ir() {
            return ir.getValue().getValue();
        }

        public long controlStep() {
            return controlStep.getValue().getValue();
        }

        public long controlLastStep() {
            return controlLastStep.getValue().getValue();
        }

        public long control() {
            return control.getValue().getValue();
        }

        public long bus() {
            return bus.getValue().getValue();
        }

        public boolean halted() {
            return !digitalModel.isRunning();
        }
    }

    private TestModel() {
    }

    public static MutableModel init(String digFilename, int... ram) {
        return new MutableModel(tryCreateDigitalModel(digFilename, Arrays.stream(ram).mapToLong(i -> i).toArray()));
    }

    private static Model tryCreateDigitalModel(String filename, long... ram) {
        try {
            final var tester = new UnitTester(new File(filename));

            tester.writeDataTo(pm -> pm.getLabel().equals("RAM"), new DataField(ram));

            return tester.getModel();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct Digital model from file.", e);
        }
    };

    private static Model tryStartDigitalModel(Model digitalModel) {
        try {
            final var fireEvent = digitalModel.getClass().getDeclaredMethod("fireEvent", ModelEvent.class);
            fireEvent.setAccessible(true);
            fireEvent.invoke(digitalModel, ModelEvent.FASTRUN);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set Digital model in FASTRUN.", e);
        }

        if (!digitalModel.isRunning()) {
            throw new IllegalStateException("Digital model isn't running.");
        } else {
            return digitalModel;
        }
    };
}
