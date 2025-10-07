package ru.datana.integration.opc.component;

import static java.time.Instant.now;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.dto.Mapping;
import ru.datana.integration.opc.dto.TagValue;
import ru.datana.integration.opc.request.MappingDesc;
import ru.datana.integration.opc.service.ControllerUpdateService;

@Component
@Slf4j
@RequiredArgsConstructor

public class ValueManager {
        private static final Map<String, TagValue> EMPTY_MAP = Collections.emptyMap();
        private final Map<String, Map<String, TagValue>> mappings = new HashMap<>();
        private final Map<String, Map<String, String>> mappingKeys = new HashMap<>();
        private final Map<String, Instant> updateMap = new HashMap<>();
        private final ControllerUpdateService controllerUpdateService;

        public void registerMappings(String name, String env, Set<MappingDesc> descs) {
                var key = buildKey(name, env);
                if (descs == null || descs.isEmpty()) {
                        mappingKeys.remove(key);
                        return;
                }
                var map = mappingKeys.computeIfAbsent(key, k -> new HashMap<>());
                map.clear();
                descs.forEach(desc -> {
                        var nodeId = Mapping.create(desc).getNodeId().getIdentifier().toString();
                        map.put(nodeId, desc.getKey());
                });
        }

        public void setValue(String name, String env, String id, TagValue value) {
                log.debug("[{}@{}] {} -> {}", name, env, id, value);
                var key = buildKey(name, env);
                var valueMap = mappings.get(key);
                if (valueMap == null) {
                        log.debug("[{}@{}] init value cache", name, env);
                        valueMap = new HashMap<>();
                        mappings.put(key, valueMap);
                }
                var previous = valueMap.put(id, value);
                updateMap.put(env, now());
                var keyMap = mappingKeys.get(key);
                if (keyMap != null) {
                        var mappingKey = keyMap.get(id);
                        if (mappingKey != null) {
                                log.debug("[{}@{}] resolved mapping key [{}] for node [{}]", name, env, mappingKey, id);
                                controllerUpdateService.handleValueChange(name, env, mappingKey, previous, value);
                        } else {
                                log.debug("[{}@{}] no mapping key for node [{}]", name, env, id);
                        }
                } else {
                        log.debug("[{}@{}] no registered mapping keys", name, env);
                }
        }

        public Map<String, TagValue> getValues(String name, String env) {
                log.debug("[{}@{}] getValues", name, env);
                return mappings.getOrDefault(buildKey(name, env), EMPTY_MAP);
        }

        public void remove(String name, String env) {
                log.debug("[{}@{}] remove", name, env);
                var key = buildKey(name, env);
                mappings.remove(key);
                mappingKeys.remove(key);
        }

	private String buildKey(String name, String env) {
		return "%s@%s".formatted(name, env);
	}

	public Instant getUpdateTS(String env) {
		return updateMap.getOrDefault(env, Instant.EPOCH);
	}
}
