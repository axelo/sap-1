package Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.AbstractMap.SimpleEntry;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.neemann.digital.analyse.AnalyseException;
import de.neemann.digital.core.Model;
import de.neemann.digital.core.ModelEvent;
import de.neemann.digital.core.ObservableValue;
import de.neemann.digital.core.Signal;
import de.neemann.digital.core.memory.DataField;
import de.neemann.digital.testing.UnitTester;

public class MutableTestModel {

    public static MutableTestModel init(String digFilename, int... ram) {
        final var pair = tryCreateDigitalModel(digFilename, Arrays.stream(ram).mapToLong(i -> i).toArray());

        return new MutableTestModel(pair.getKey(), pair.getValue());
    }

    public static void step(MutableTestModel model) {
        assertEquals(true, model.model.isRunning(), () -> "Digital model isn't running.");

        model.clock.setBool(!model.clock.getBool());

        model.model.doStep();
    }

    public static boolean clock(MutableTestModel model) {
        return model.clock.getBool();
    }

    public static int signal(MutableTestModel model, String name) {
        return intFromSignal(tryGetSignal(model, name));
    }

    public static boolean halted(MutableTestModel model) {
        return !model.model.isRunning();
    }

    public static int readRam(MutableTestModel model, int address) {
        return (int) model.ram.getDataWord(address);
    }

    private static int intFromSignal(Signal signal) {
        return (int) signal.getValue().getValue();
    }

    private static SimpleEntry<Model, DataField> tryCreateDigitalModel(String filename, long... ram) {
        try {
            final var tester = new UnitTester(new File(filename));
            final var ramDataField = new DataField(ram);

            tester.writeDataTo(pm -> pm.getLabel().equals("RAM"), ramDataField);

            return new SimpleEntry<>(tester.getModel(), ramDataField);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct Digital model from file.", e);
        }
    };

    private static Model tryStartDigitalModel(Model model) {
        try {
            model.checkForInvalidSignals();
        } catch (AnalyseException e) {
            throw new IllegalStateException(e);
        }

        try {
            final var fireEvent = model.getClass().getDeclaredMethod("fireEvent", ModelEvent.class);
            fireEvent.setAccessible(true);
            fireEvent.invoke(model, ModelEvent.FASTRUN);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set Digital model in FASTRUN.", e);
        }

        if (!model.isRunning()) {
            throw new IllegalStateException("Digital model isn't running.");
        } else {
            return model;
        }
    };

    private static Signal tryGetSignal(MutableTestModel model, String name) {
        return Optional.ofNullable(model.signalsByName.get(name))
                .orElseThrow(() -> new IllegalStateException("Missing signal " + name + " in Digital model."));
    }

    private MutableTestModel(Model digitalModel, DataField digitalRamDataField) {
        signalsByName = digitalModel.getSignalsCopy().stream()
                .collect(Collectors.toUnmodifiableMap(Signal::getName, Function.identity()));

        clock = Optional.ofNullable(digitalModel.getClocks().get(0))
                .orElseThrow(() -> new IllegalStateException("Missing clock in Digital model.")).getClockOutput();

        ram = digitalRamDataField;

        model = tryStartDigitalModel(digitalModel);
    }

    private final Model model;
    private final ObservableValue clock;
    private final DataField ram;
    private final Map<String, Signal> signalsByName;
}
