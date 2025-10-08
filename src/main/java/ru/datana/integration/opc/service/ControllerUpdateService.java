package ru.datana.integration.opc.service;

import static java.util.Collections.singletonMap;

import java.util.Objects;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.dto.TagValue;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControllerUpdateService {
        static final String UPDATE_SUFFIX = ".Update";
        private final ControllerApiClient client;

        public void handleValueChange(String controllerId, String env, String mappingKey, TagValue previous, TagValue current) {
                if (mappingKey == null) {
                        log.debug("Skip [{}@{}] update without mapping key", controllerId, env);
                        return;
                }
                if (!mappingKey.endsWith(UPDATE_SUFFIX)) {
                        log.debug("Skip [{}@{}:{}] update without suffix", controllerId, env, mappingKey);
                        return;
                }
                var currentValue = current == null ? null : current.getValue();
                if (currentValue == null) {
                        log.debug("Skip [{}@{}:{}] update without value", controllerId, env, mappingKey);
                        return;
                }
                var previousValue = previous == null ? null : previous.getValue();
                if (previousValue != null && Objects.equals(previousValue, currentValue)) {
                        log.debug("Skip [{}@{}:{}] unchanged value {}", controllerId, env, mappingKey, currentValue);
                        return;
                }

                log.debug("Handle [{}@{}:{}] update prev={} current={}", controllerId, env, mappingKey, previousValue,
                                currentValue);
                var keyWithoutSuffix = mappingKey.substring(0, mappingKey.length() - UPDATE_SUFFIX.length());
                var parts = keyWithoutSuffix.split("\\.");
                if (parts.length == 0) {
                        log.debug("Skip [{}@{}:{}] empty mapping key", controllerId, env, mappingKey);
                        return;
                }

                if (parts.length == 1) {
                        handleControllerCommand(controllerId, env, parts[0], currentValue);
                } else {
                        var variable = parts[0];
                        var property = joinParts(parts, 1);
                        handleVariableUpdate(controllerId, env, variable, property, currentValue);
                }
        }

        private void handleControllerCommand(String controllerId, String env, String command, Double value) {
                int numericValue = value.intValue();
                log.debug("Execute controller command [{}] with value {} for {}@{}", command, value, controllerId, env);
                switch (command) {
                case "State" -> {
                        switch (numericValue) {
                        case 1 -> client.stop(env, controllerId);
                        case 2 -> client.startPredict(env, controllerId);
                        case 3 -> client.start(env, controllerId);
                        default -> log.warn("Unknown controller state value [{}] for {}@{}", numericValue, controllerId, env);
                        }
                }
                case "OptimizationState" -> {
                        var enabled = numericValue == 2;
                        if (numericValue == 1 || numericValue == 2) {
                                client.updateOptimization(env, controllerId, singletonMap("enabled", enabled));
                        } else {
                                log.warn("Unknown optimization state value [{}] for {}@{}", numericValue, controllerId, env);
                        }
                }
                default -> log.debug("Unsupported controller command [{}]", command);
                }
        }

        private void handleVariableUpdate(String controllerId, String env, String variable, String property, Double value) {
                log.debug("Execute variable update [{}].[{}] = {} for {}@{}", variable, property, value, controllerId, env);
                switch (property) {
                case "state" -> {
                        var numericValue = value.intValue();
                        if (numericValue == 1 || numericValue == 2) {
                                var stateValue = numericValue == 1 ? "OFF" : "ON";
                                client.updateStates(env, controllerId, singletonMap(variable, stateValue));
                        } else {
                                log.warn("Unknown state value [{}] for variable [{}] of {}@{}", numericValue, variable, controllerId,
                                                env);
                        }
                }
                case "limit_bottom", "limit_top", "set_point" -> {
                        client.updateLimits(env, controllerId, singletonMap(variable, singletonMap(property, value)));
                }
                case "coef_line_opt", "coef_quad_opt", "target" -> {
                        client.updateOptimization(env, controllerId,
                                        singletonMap("vars", singletonMap(variable, singletonMap(property, value))));
                }
                default -> log.debug("Unsupported property [{}] for variable [{}]", property, variable);
                }
        }

        private static String joinParts(String[] parts, int from) {
                if (from >= parts.length) {
                        return "";
                }
                if (from == parts.length - 1) {
                        return parts[from];
                }
                var builder = new StringBuilder();
                for (int i = from; i < parts.length; i++) {
                        if (i > from) {
                                builder.append('.');
                        }
                        builder.append(parts[i]);
                }
                return builder.toString();
        }
}
