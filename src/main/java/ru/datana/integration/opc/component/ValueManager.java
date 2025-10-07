package ru.datana.integration.opc.component;

import static java.time.Instant.now;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.dto.TagValue;

@Component
@Slf4j

public class ValueManager {
        private static final Map<String, TagValue> EMPTY_MAP = Collections.emptyMap();
        private final Map<String, Map<String, TagValue>> mappings = new HashMap<>();
	private final Map<String, Instant> updateMap = new HashMap<>();

        public void setValue(String name, String env, String id, TagValue value) {
                log.debug("[{}@{}] {} -> {}", name, env, id, value);
		var key = buildKey(name, env);
		var valueMap = mappings.get(key);
		if (valueMap == null) {
			valueMap = new HashMap<>();
			mappings.put(key, valueMap);
		}
                valueMap.put(id, value);
                updateMap.put(env, now());
        }

        public Map<String, TagValue> getValues(String name, String env) {
                log.debug("[{}@{}] getValues", name, env);
                return mappings.getOrDefault(buildKey(name, env), EMPTY_MAP);
        }

	public void remove(String name, String env) {
		log.debug("[{}@{}] remove", name, env);
		mappings.remove(buildKey(name, env));
	}

	private String buildKey(String name, String env) {
		return "%s@%s".formatted(name, env);
	}

	public Instant getUpdateTS(String env) {
		return updateMap.getOrDefault(env, Instant.EPOCH);
	}
}
