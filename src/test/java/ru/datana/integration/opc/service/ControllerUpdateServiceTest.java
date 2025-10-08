package ru.datana.integration.opc.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.datana.integration.opc.dto.TagValue;

@ExtendWith(MockitoExtension.class)
class ControllerUpdateServiceTest {

    private static final String CONTROLLER_ID = "controller";
    private static final String ENV = "env";

    @Mock
    private ControllerApiClient client;

    @InjectMocks
    private ControllerUpdateService service;

    @Test
    void stateCommandOneStopsController() {
        service.handleValueChange(CONTROLLER_ID, ENV, "State.Update", null, tagValue(1));

        verify(client).stop(ENV, CONTROLLER_ID);
        verifyNoMoreInteractions(client);
    }

    @Test
    void stateCommandTwoStartsPredict() {
        service.handleValueChange(CONTROLLER_ID, ENV, "State.Update", null, tagValue(2));

        verify(client).startPredict(ENV, CONTROLLER_ID);
        verifyNoMoreInteractions(client);
    }

    @Test
    void stateCommandThreeStartsController() {
        service.handleValueChange(CONTROLLER_ID, ENV, "State.Update", null, tagValue(3));

        verify(client).start(ENV, CONTROLLER_ID);
        verifyNoMoreInteractions(client);
    }

    @Test
    void optimizationStateOneDisablesOptimization() {
        service.handleValueChange(CONTROLLER_ID, ENV, "OptimizationState.Update", null, tagValue(1));

        verify(client).updateOptimization(ENV, CONTROLLER_ID, Map.of("enabled", false));
        verifyNoMoreInteractions(client);
    }

    @Test
    void optimizationStateTwoEnablesOptimization() {
        service.handleValueChange(CONTROLLER_ID, ENV, "OptimizationState.Update", null, tagValue(2));

        verify(client).updateOptimization(ENV, CONTROLLER_ID, Map.of("enabled", true));
        verifyNoMoreInteractions(client);
    }

    @Test
    void variableStateOneSendsOffValue() {
        service.handleValueChange(CONTROLLER_ID, ENV, "Pump.state.Update", null, tagValue(1));

        verify(client).updateStates(ENV, CONTROLLER_ID, Map.of("Pump", "OFF"));
        verifyNoMoreInteractions(client);
    }

    @Test
    void variableStateTwoSendsOnValue() {
        service.handleValueChange(CONTROLLER_ID, ENV, "Pump.state.Update", null, tagValue(2));

        verify(client).updateStates(ENV, CONTROLLER_ID, Map.of("Pump", "ON"));
        verifyNoMoreInteractions(client);
    }

    private static TagValue tagValue(double value) {
        return TagValue.builder().value(value).build();
    }
}
